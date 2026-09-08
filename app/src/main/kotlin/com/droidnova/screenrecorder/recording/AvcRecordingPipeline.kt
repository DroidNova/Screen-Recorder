package com.droidnova.screenrecorder.recording

import android.annotation.SuppressLint
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Process
import android.view.Surface
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.domain.recording.FailureStage
import com.droidnova.screenrecorder.domain.recording.RecordingFailure
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class AvcRecordingPipeline(
    private val projection: MediaProjection,
    private val sourceWidth: Int,
    private val sourceHeight: Int,
    private val densityDpi: Int,
    private val audioMode: AudioMode,
    private val videoConfiguration: SelectedVideoConfiguration,
    private var output: RecordingOutput?,
) {
    private var videoEncoder: MediaCodec? = null
    private var videoInputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var audioRecord: AudioRecord? = null
    private var audioEncoder: MediaCodec? = null
    private var audioThread: Thread? = null
    private var muxerCoordinator: MuxerCoordinator? = null
    private var videoEncoderStarted = false
    private var audioEncoderStarted = false
    private var projectionCallbackRegistered = false
    private var released = false
    private val sessionStartNanos = System.nanoTime()
    private val stopRequested = AtomicBoolean(false)
    private val audioPauseRequested = AtomicBoolean(false)
    private val audioPauseMonitor = Object()
    private val pauseTimeline = PauseTimeline(sessionStartNanos)
    private var videoSurfaceDetached = false
    private val runtimeFailure = AtomicReference<RecordingFailure?>()
    private val audioFailure = AtomicReference<RecordingFailure.AudioFailure?>()
    private val audioInputEosQueued = AtomicBoolean(audioMode == AudioMode.None)
    private val audioReachedEos = AtomicBoolean(audioMode == AudioMode.None)
    private var lastAudioPresentationTimeUs = -1L

    fun start(callbackHandler: Handler, projectionCallback: MediaProjection.Callback) {
        val selected = videoConfiguration
        val videoFormat = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            selected.resolution.width,
            selected.resolution.height,
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, selected.bitsPerSecond)
            setInteger(MediaFormat.KEY_FRAME_RATE, selected.framesPerSecond)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        try {
            MediaCodec.createByCodecName(selected.encoderName).also {
                videoEncoder = it
                it.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                videoInputSurface = it.createInputSurface()
                it.start()
                videoEncoderStarted = true
            }
        } catch (_: RuntimeException) {
            throw RecordingPipelineException(RecordingFailure.VideoEncoderFailure(FailureStage.Initialization))
        }
        muxerCoordinator = try {
            MuxerCoordinator(requireNotNull(output).createMuxer(), audioMode != AudioMode.None)
        } catch (_: RuntimeException) {
            throw RecordingPipelineException(RecordingFailure.OutputInitializationFailure)
        }
        if (audioMode != AudioMode.None) startAudio()
        try {
            projection.registerCallback(projectionCallback, callbackHandler)
            projectionCallbackRegistered = true
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenRecorderCapture",
                selected.resolution.width,
                selected.resolution.height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                videoInputSurface,
                null,
                callbackHandler,
            )
        } catch (_: RuntimeException) {
            throw RecordingPipelineException(RecordingFailure.ProjectionUnavailable)
        }
    }

    fun requestStop() {
        stopRequested.set(true)
        synchronized(audioPauseMonitor) { audioPauseMonitor.notifyAll() }
    }

    @Synchronized
    fun pause() {
        check(!released)
        val pauseNanos = System.nanoTime()
        val suspended = try {
            videoEncoder?.setParameters(Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    putLong(
                        MediaCodec.PARAMETER_KEY_SUSPEND_TIME,
                        pauseNanos / NANOS_PER_MICROSECOND,
                    )
                }
            })
            true
        } catch (_: RuntimeException) {
            false
        }
        if (!suspended) {
            try {
                virtualDisplay?.setSurface(null)
                videoSurfaceDetached = true
            } catch (_: RuntimeException) {
                throw fail(RecordingFailure.VideoEncoderFailure(FailureStage.Runtime))
            }
        }
        pauseTimeline.pause(pauseNanos)
        if (audioMode != AudioMode.None) {
            audioPauseRequested.set(true)
            try {
                audioRecord?.stop()
            } catch (_: RuntimeException) {
                audioPauseRequested.set(false)
                throw fail(RecordingFailure.AudioFailure(FailureStage.Runtime))
            }
        }
    }

    @Synchronized
    fun resume() {
        check(!released)
        val resumeNanos = System.nanoTime()
        try {
            if (videoSurfaceDetached) {
                virtualDisplay?.setSurface(videoInputSurface)
                videoSurfaceDetached = false
            } else {
                videoEncoder?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 0) })
            }
            videoEncoder?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
        } catch (_: RuntimeException) {
            throw fail(RecordingFailure.VideoEncoderFailure(FailureStage.Runtime))
        }
        pauseTimeline.resume(resumeNanos)
        audioPauseRequested.set(false)
        synchronized(audioPauseMonitor) { audioPauseMonitor.notifyAll() }
    }

    @Synchronized
    fun resizeCapturedContent(width: Int, height: Int) {
        if (released || width <= 0 || height <= 0) return
        val canvas = videoConfiguration.resolution
        val scale = minOf(canvas.width.toDouble() / width, canvas.height.toDouble() / height)
        val fittedWidth = (width * scale).toInt().coerceAtLeast(1)
        val fittedHeight = (height * scale).toInt().coerceAtLeast(1)
        try { virtualDisplay?.resize(fittedWidth, fittedHeight, densityDpi) } catch (_: RuntimeException) { }
    }

    fun drainUntilStopped(): OutputCompletion {
        val codec = requireNotNull(videoEncoder)
        val info = MediaCodec.BufferInfo()
        var eosSignalled = false
        var videoEos = false
        var deadlineNanos = Long.MAX_VALUE
        var lastVideoPresentationTimeUs = -1L
        while (!videoEos) {
            runtimeFailure.get()?.let {
                stopRequested.set(true)
                if (!eosSignalled) deadlineNanos = System.nanoTime() + FINALIZATION_TIMEOUT_NANOS
            }
            audioFailure.get()?.let { failure ->
                stopRequested.set(true)
                if (!eosSignalled) deadlineNanos = System.nanoTime() + FINALIZATION_TIMEOUT_NANOS
            }
            if (stopRequested.get() && !eosSignalled) {
                if (!audioInputEosQueued.get() && audioFailure.get() == null) {
                    if (deadlineNanos == Long.MAX_VALUE) deadlineNanos = System.nanoTime() + FINALIZATION_TIMEOUT_NANOS
                    if (System.nanoTime() >= deadlineNanos) {
                        throw RecordingPipelineException(RecordingFailure.AudioFailure(FailureStage.Runtime))
                    }
                } else {
                    codec.signalEndOfInputStream()
                    eosSignalled = true
                    deadlineNanos = System.nanoTime() + FINALIZATION_TIMEOUT_NANOS
                }
            }
            if (eosSignalled && System.nanoTime() >= deadlineNanos) {
                throw RecordingPipelineException(RecordingFailure.VideoEncoderFailure(FailureStage.Runtime))
            }
            when (val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> requireNotNull(muxerCoordinator).setVideoFormat(codec.outputFormat)
                else -> if (index >= 0) {
                    try {
                        val buffer = requireNotNull(codec.getOutputBuffer(index))
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0) {
                            val normalized = (info.presentationTimeUs - sessionStartNanos / NANOS_PER_MICROSECOND).coerceAtLeast(0)
                            pauseTimeline.toActivePresentationTimeUs(normalized)?.let { activePresentationTimeUs ->
                                info.presentationTimeUs = maxOf(activePresentationTimeUs, lastVideoPresentationTimeUs + 1)
                                lastVideoPresentationTimeUs = info.presentationTimeUs
                                requireNotNull(muxerCoordinator).writeVideo(buffer, info)
                            }
                        }
                        videoEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
        }
        audioThread?.join(FINALIZATION_TIMEOUT_MILLIS)
        if (audioThread?.isAlive == true) {
            throw RecordingPipelineException(RecordingFailure.AudioFailure(FailureStage.Runtime))
        }
        audioFailure.get()?.let { throw RecordingPipelineException(it) }
        runtimeFailure.get()?.let { throw RecordingPipelineException(it) }
        val coordinator = requireNotNull(muxerCoordinator)
        return OutputCompletion(
            hasVideoTrack = coordinator.hasVideoTrack,
            writtenVideoSamples = coordinator.videoSamples,
            videoReachedEndOfStream = videoEos,
            hasAudioTrack = coordinator.hasAudioTrack,
            writtenAudioSamples = coordinator.audioSamples,
            audioReachedEndOfStream = audioReachedEos.get(),
            audioRequired = audioMode != AudioMode.None,
        )
    }

    @SuppressLint("MissingPermission")
    private fun startAudio() {
        val channelCounts = if (audioMode == AudioMode.DeviceAudio) listOf(2, 1) else listOf(1)
        val configured = AUDIO_SAMPLE_RATES.firstNotNullOfOrNull { sampleRate ->
            channelCounts.firstNotNullOfOrNull { channelCount -> createAudioResources(sampleRate, channelCount) }
        }
            ?: throw RecordingPipelineException(RecordingFailure.AudioFailure(FailureStage.Initialization))
        audioRecord = configured.record
        audioEncoder = configured.encoder
        audioEncoderStarted = true
        audioThread = Thread({ captureAudio(configured) }, "ScreenRecorderAudio").also { it.start() }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioResources(sampleRate: Int, channelCount: Int): AudioResources? {
        val channelMask = if (channelCount == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
        val minimum = try {
            AudioRecord.getMinBufferSize(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        } catch (_: RuntimeException) {
            return null
        }
        if (minimum <= 0) return null
        val bufferSize = (minimum * 2).coerceIn(MIN_AUDIO_BUFFER_BYTES, MAX_AUDIO_BUFFER_BYTES)
        var record: AudioRecord? = null
        var encoder: MediaCodec? = null
        return try {
            record = createAudioRecord(sampleRate, channelMask, bufferSize)
            if (record.state != AudioRecord.STATE_INITIALIZED) error("audio source unavailable")
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            AudioResources(record, encoder, sampleRate, channelCount, bufferSize)
        } catch (_: RuntimeException) {
            try { encoder?.release() } catch (_: RuntimeException) { }
            try { record?.release() } catch (_: RuntimeException) { }
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(sampleRate: Int, channelMask: Int, bufferSize: Int): AudioRecord {
        if (audioMode == AudioMode.DeviceAudio) {
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            val captureConfiguration = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build()
            return AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(captureConfiguration)
                .build()
        }
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
    }

    private fun captureAudio(resources: AudioResources) {
        val pcm = ByteArray(resources.bufferSize)
        var capturedFrames = 0L
        var zeroReads = 0
        val captureStartOffsetUs = ((System.nanoTime() - sessionStartNanos) / NANOS_PER_MICROSECOND).coerceAtLeast(0)
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            resources.record.startRecording()
            if (resources.record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw RecordingPipelineException(RecordingFailure.AudioFailure(FailureStage.Initialization))
            }
            while (!stopRequested.get()) {
                if (audioPauseRequested.get()) {
                    drainAudioOutput(resources.encoder, false)
                    synchronized(audioPauseMonitor) {
                        while (audioPauseRequested.get() && !stopRequested.get()) audioPauseMonitor.wait()
                    }
                    if (stopRequested.get()) break
                    resources.record.startRecording()
                    if (resources.record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        throw RecordingPipelineException(RecordingFailure.AudioFailure(FailureStage.Runtime))
                    }
                }
                val count = resources.record.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                when {
                    audioPauseRequested.get() -> Unit
                    count > 0 -> {
                        zeroReads = 0
                        var offset = 0
                        while (offset < count) {
                            drainAudioOutput(resources.encoder, false)
                            val inputIndex = resources.encoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                            if (inputIndex < 0) continue
                            val input = requireNotNull(resources.encoder.getInputBuffer(inputIndex))
                            input.clear()
                            val bytesPerFrame = PCM_BYTES_PER_SAMPLE * resources.channelCount
                            val size = minOf(input.remaining(), count - offset).let { it - it % bytesPerFrame }
                            if (size == 0) throw RecordingPipelineException(RecordingFailure.AudioFailure(FailureStage.Runtime))
                            input.put(pcm, offset, size)
                            val presentationTimeUs = captureStartOffsetUs +
                                capturedFrames * MICROSECONDS_PER_SECOND / resources.sampleRate
                            resources.encoder.queueInputBuffer(inputIndex, 0, size, presentationTimeUs, 0)
                            capturedFrames += size / bytesPerFrame
                            offset += size
                        }
                    }
                    count == 0 && ++zeroReads <= MAX_ZERO_READS -> drainAudioOutput(resources.encoder, false)
                    else -> throw RecordingPipelineException(RecordingFailure.AudioFailure(FailureStage.Runtime))
                }
            }
            try { resources.record.stop() } catch (_: IllegalStateException) { }
            queueAudioEos(resources.encoder, captureStartOffsetUs + capturedFrames * MICROSECONDS_PER_SECOND / resources.sampleRate)
            drainAudioOutput(resources.encoder, true)
        } catch (error: RecordingPipelineException) {
            val failure = error.failure as? RecordingFailure.AudioFailure
                ?: RecordingFailure.AudioFailure(FailureStage.Runtime)
            audioFailure.compareAndSet(null, failure)
            stopRequested.set(true)
        } catch (_: RuntimeException) {
            audioFailure.compareAndSet(null, RecordingFailure.AudioFailure(FailureStage.Runtime))
            stopRequested.set(true)
        }
    }

    private fun queueAudioEos(codec: MediaCodec, presentationTimeUs: Long) {
        val deadline = System.nanoTime() + FINALIZATION_TIMEOUT_NANOS
        while (System.nanoTime() < deadline) {
            drainAudioOutput(codec, false)
            val index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index >= 0) {
                codec.queueInputBuffer(index, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                audioInputEosQueued.set(true)
                return
            }
        }
        throw RecordingPipelineException(RecordingFailure.AudioFailure(FailureStage.Runtime))
    }

    private fun drainAudioOutput(codec: MediaCodec, untilEos: Boolean) {
        val info = MediaCodec.BufferInfo()
        val deadline = if (untilEos) System.nanoTime() + FINALIZATION_TIMEOUT_NANOS else Long.MAX_VALUE
        while (true) {
            if (untilEos && System.nanoTime() >= deadline) {
                throw RecordingPipelineException(RecordingFailure.AudioFailure(FailureStage.Runtime))
            }
            when (val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> if (!untilEos) return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> requireNotNull(muxerCoordinator).setAudioFormat(codec.outputFormat)
                else -> if (index >= 0) {
                    try {
                        val buffer = requireNotNull(codec.getOutputBuffer(index))
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0) {
                            info.presentationTimeUs = maxOf(
                                info.presentationTimeUs.coerceAtLeast(0L),
                                lastAudioPresentationTimeUs + 1L,
                            )
                            lastAudioPresentationTimeUs = info.presentationTimeUs
                            requireNotNull(muxerCoordinator).writeAudio(buffer, info)
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            audioReachedEos.set(true)
                            return
                        }
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                }
            }
        }
    }

    @Synchronized
    fun release(projectionCallback: MediaProjection.Callback): Boolean {
        if (released) return true
        released = true
        stopRequested.set(true)
        try { virtualDisplay?.release() } catch (_: RuntimeException) { } finally { virtualDisplay = null }
        try {
            if (projectionCallbackRegistered) projection.unregisterCallback(projectionCallback)
        } catch (_: RuntimeException) {
        } finally {
            projectionCallbackRegistered = false
        }
        try { projection.stop() } catch (_: RuntimeException) { }
        try { audioThread?.join(FINALIZATION_TIMEOUT_MILLIS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        if (audioThread?.isAlive == true) {
            try { audioRecord?.stop() } catch (_: RuntimeException) { }
            try { audioThread?.join(FINALIZATION_TIMEOUT_MILLIS) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
        try { if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord?.stop() } catch (_: RuntimeException) { }
        try { audioRecord?.release() } catch (_: RuntimeException) { } finally { audioRecord = null }
        try { if (audioEncoderStarted) audioEncoder?.stop() } catch (_: RuntimeException) { } finally { audioEncoderStarted = false }
        try { audioEncoder?.release() } catch (_: RuntimeException) { } finally { audioEncoder = null }
        try { videoInputSurface?.release() } catch (_: RuntimeException) { } finally { videoInputSurface = null }
        try { if (videoEncoderStarted) videoEncoder?.stop() } catch (_: RuntimeException) { } finally { videoEncoderStarted = false }
        try { videoEncoder?.release() } catch (_: RuntimeException) { } finally { videoEncoder = null }
        return try {
            muxerCoordinator?.stopAndRelease() ?: false
        } catch (_: RuntimeException) {
            false
        } finally {
            muxerCoordinator = null
        }
    }

    fun takeOutput(): RecordingOutput? = output.also { output = null }

    private fun fail(failure: RecordingFailure): RecordingPipelineException {
        runtimeFailure.compareAndSet(null, failure)
        stopRequested.set(true)
        synchronized(audioPauseMonitor) { audioPauseMonitor.notifyAll() }
        return RecordingPipelineException(failure)
    }

    private data class AudioResources(
        val record: AudioRecord,
        val encoder: MediaCodec,
        val sampleRate: Int,
        val channelCount: Int,
        val bufferSize: Int,
    )

    private class PauseTimeline(private val sessionStartNanos: Long) {
        private var pauseStartedNanos: Long? = null
        private val completedPauses = mutableListOf<PauseInterval>()

        @Synchronized
        fun pause(atNanos: Long) {
            check(pauseStartedNanos == null)
            pauseStartedNanos = atNanos.coerceAtLeast(sessionStartNanos)
        }

        @Synchronized
        fun resume(atNanos: Long) {
            val started = requireNotNull(pauseStartedNanos)
            completedPauses += PauseInterval(started, atNanos.coerceAtLeast(started))
            pauseStartedNanos = null
        }

        @Synchronized
        fun toActivePresentationTimeUs(normalizedPresentationTimeUs: Long): Long? {
            val presentationNanos = sessionStartNanos +
                normalizedPresentationTimeUs * NANOS_PER_MICROSECOND
            val currentPause = pauseStartedNanos
            if (currentPause != null && presentationNanos >= currentPause) return null

            var pausedBeforePresentationNanos = 0L
            completedPauses.forEach { interval ->
                if (presentationNanos in interval.startNanos until interval.endNanos) return null
                if (presentationNanos >= interval.endNanos) {
                    pausedBeforePresentationNanos += interval.endNanos - interval.startNanos
                }
            }
            return (normalizedPresentationTimeUs -
                pausedBeforePresentationNanos / NANOS_PER_MICROSECOND).coerceAtLeast(0)
        }

        private data class PauseInterval(val startNanos: Long, val endNanos: Long)
    }

    private class MuxerCoordinator(
        private var muxer: MediaMuxer?,
        private val audioRequired: Boolean,
    ) {
        private var started = false
        private var videoTrack = -1
        private var audioTrack = -1
        private var pendingBytes = 0
        private val pending = ArrayDeque<PendingSample>()
        var videoSamples = 0
            private set
        var audioSamples = 0
            private set
        val hasVideoTrack: Boolean get() = videoTrack >= 0
        val hasAudioTrack: Boolean get() = audioTrack >= 0

        @Synchronized fun setVideoFormat(format: MediaFormat) {
            check(videoTrack < 0 && !started)
            videoTrack = requireNotNull(muxer).addTrack(format)
            startIfReady()
        }

        @Synchronized fun setAudioFormat(format: MediaFormat) {
            check(audioRequired && audioTrack < 0 && !started)
            audioTrack = requireNotNull(muxer).addTrack(format)
            startIfReady()
        }

        fun writeVideo(buffer: ByteBuffer, info: MediaCodec.BufferInfo) = write(Track.Video, buffer, info)
        fun writeAudio(buffer: ByteBuffer, info: MediaCodec.BufferInfo) = write(Track.Audio, buffer, info)

        @Synchronized private fun write(track: Track, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            if (started) {
                writeStarted(track, buffer, info)
                return
            }
            check(pendingBytes + info.size <= MAX_PENDING_MUXER_BYTES)
            val bytes = ByteArray(info.size)
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            buffer.get(bytes)
            pending.addLast(PendingSample(track, bytes, info.presentationTimeUs, info.flags))
            pendingBytes += bytes.size
        }

        private fun startIfReady() {
            if (videoTrack < 0 || audioRequired && audioTrack < 0) return
            requireNotNull(muxer).start()
            started = true
            while (pending.isNotEmpty()) {
                val sample = pending.removeFirst()
                val info = MediaCodec.BufferInfo().apply { set(0, sample.bytes.size, sample.presentationTimeUs, sample.flags) }
                writeStarted(sample.track, ByteBuffer.wrap(sample.bytes), info)
            }
            pendingBytes = 0
        }

        private fun writeStarted(track: Track, buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            val trackIndex = if (track == Track.Video) videoTrack else audioTrack
            check(trackIndex >= 0)
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            requireNotNull(muxer).writeSampleData(trackIndex, buffer, info)
            if (track == Track.Video) videoSamples++ else audioSamples++
        }

        @Synchronized fun stopAndRelease(): Boolean {
            val ownedMuxer = muxer ?: return true
            var success = started
            try {
                if (started) ownedMuxer.stop()
            } catch (_: RuntimeException) {
                success = false
            } finally {
                started = false
                pending.clear()
                pendingBytes = 0
                ownedMuxer.release()
                muxer = null
            }
            return success
        }

        private enum class Track { Video, Audio }
        private data class PendingSample(val track: Track, val bytes: ByteArray, val presentationTimeUs: Long, val flags: Int)
    }

    private companion object {
        val AUDIO_SAMPLE_RATES = listOf(48_000, 44_100)
        const val AUDIO_BIT_RATE = 128_000
        const val PCM_BYTES_PER_SAMPLE = 2
        const val MIN_AUDIO_BUFFER_BYTES = 8_192
        const val MAX_AUDIO_BUFFER_BYTES = 262_144
        const val MAX_PENDING_MUXER_BYTES = 2 * 1024 * 1024
        const val MAX_ZERO_READS = 20
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val FINALIZATION_TIMEOUT_NANOS = 5_000_000_000L
        const val FINALIZATION_TIMEOUT_MILLIS = 5_000L
        const val NANOS_PER_MICROSECOND = 1_000L
        const val MICROSECONDS_PER_SECOND = 1_000_000L
    }
}

internal class RecordingPipelineException(val failure: RecordingFailure) : RuntimeException()
