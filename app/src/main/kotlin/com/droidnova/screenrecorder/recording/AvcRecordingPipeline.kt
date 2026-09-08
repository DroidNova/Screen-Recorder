package com.droidnova.screenrecorder.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.view.Surface
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import com.droidnova.screenrecorder.domain.recording.FailureStage
import com.droidnova.screenrecorder.domain.recording.RecordingFailure
import java.util.concurrent.atomic.AtomicBoolean

internal class AvcRecordingPipeline(
    private val projection: MediaProjection,
    private val sourceWidth: Int,
    private val sourceHeight: Int,
    private val densityDpi: Int,
    private var output: RecordingOutput?,
) {
    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var muxer: MediaMuxer? = null
    private var muxerStarted = false
    private var encoderStarted = false
    private var videoTrack = -1
    private var samplesWritten = 0
    private var lastPresentationTimeUs = -1L
    private val stopRequested = AtomicBoolean(false)
    private var projectionCallbackRegistered = false
    private var released = false

    fun start(callbackHandler: Handler, projectionCallback: MediaProjection.Callback) {
        val selected = selectConfiguration()
            ?: throw RecordingPipelineException(RecordingFailure.VideoEncoderFailure(FailureStage.Initialization))
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, selected.resolution.width, selected.resolution.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, selected.bitsPerSecond)
            setInteger(MediaFormat.KEY_FRAME_RATE, selected.framesPerSecond)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        try {
            MediaCodec.createByCodecName(selected.encoderName).also {
                encoder = it
                it.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = it.createInputSurface()
                it.start()
                encoderStarted = true
            }
        } catch (_: RuntimeException) {
            throw RecordingPipelineException(RecordingFailure.VideoEncoderFailure(FailureStage.Initialization))
        }
        muxer = try {
            requireNotNull(output).createMuxer()
        } catch (_: RuntimeException) {
            throw RecordingPipelineException(RecordingFailure.OutputInitializationFailure)
        }
        try {
            projection.registerCallback(projectionCallback, callbackHandler)
            projectionCallbackRegistered = true
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenRecorderCapture",
                selected.resolution.width,
                selected.resolution.height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                callbackHandler,
            )
        } catch (_: RuntimeException) {
            throw RecordingPipelineException(RecordingFailure.ProjectionUnavailable)
        }
    }

    fun requestStop() { stopRequested.set(true) }

    fun drainUntilStopped(): OutputCompletion {
        val codec = requireNotNull(encoder)
        val info = MediaCodec.BufferInfo()
        var eosSignalled = false
        var deadlineNanos = Long.MAX_VALUE
        while (true) {
            if (stopRequested.get() && !eosSignalled) {
                codec.signalEndOfInputStream()
                eosSignalled = true
                deadlineNanos = System.nanoTime() + FINALIZATION_TIMEOUT_NANOS
            }
            if (eosSignalled && System.nanoTime() >= deadlineNanos) error("encoder EOS timeout")
            when (val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted)
                    videoTrack = requireNotNull(muxer).addTrack(codec.outputFormat)
                    requireNotNull(muxer).start()
                    muxerStarted = true
                }
                else -> if (index >= 0) {
                    try {
                        val buffer = requireNotNull(codec.getOutputBuffer(index))
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0) {
                            check(muxerStarted)
                            info.presentationTimeUs = maxOf(info.presentationTimeUs, lastPresentationTimeUs + 1)
                            lastPresentationTimeUs = info.presentationTimeUs
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            requireNotNull(muxer).writeSampleData(videoTrack, buffer, info)
                            samplesWritten++
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            return OutputCompletion(muxerStarted, samplesWritten, true)
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
        var finalizationSucceeded = true
        try { virtualDisplay?.release() } catch (_: RuntimeException) {} finally { virtualDisplay = null }
        try {
            if (projectionCallbackRegistered) projection.unregisterCallback(projectionCallback)
        } catch (_: RuntimeException) {
        } finally {
            projectionCallbackRegistered = false
        }
        try { projection.stop() } catch (_: RuntimeException) {}
        try { inputSurface?.release() } catch (_: RuntimeException) {} finally { inputSurface = null }
        try { if (encoderStarted) encoder?.stop() } catch (_: RuntimeException) {} finally { encoderStarted = false }
        try { encoder?.release() } catch (_: RuntimeException) {} finally { encoder = null }
        try { if (muxerStarted) muxer?.stop() } catch (_: RuntimeException) { finalizationSucceeded = false } finally { muxerStarted = false }
        try { muxer?.release() } catch (_: RuntimeException) {} finally { muxer = null }
        output?.closeDescriptor()
        return finalizationSucceeded
    }

    fun takeOutput(): RecordingOutput? = output.also { output = null }

    private fun selectConfiguration(): SelectedVideoConfiguration? {
        val candidates = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.asSequence()
            .filter { it.isEncoder && it.supportedTypes.any { type -> type.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
            .mapNotNull { info ->
                runCatching {
                    val capabilities = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
                    if (MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface !in capabilities.colorFormats) return@runCatching null
                    val video = capabilities.videoCapabilities ?: return@runCatching null
                    EncoderCandidate(
                        name = info.name,
                        hardwareAccelerated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.isHardwareAccelerated else !isKnownSoftwareCodec(info.name),
                        widthAlignment = video.widthAlignment,
                        heightAlignment = video.heightAlignment,
                        minimumWidth = video.supportedWidths.lower,
                        maximumWidth = video.supportedWidths.upper,
                        minimumHeight = video.supportedHeights.lower,
                        maximumHeight = video.supportedHeights.upper,
                        minimumBitsPerSecond = video.bitrateRange.lower,
                        maximumBitsPerSecond = video.bitrateRange.upper,
                        supports = { width, height, fps, bitrate ->
                            video.areSizeAndRateSupported(width, height, fps.toDouble()) && bitrate in video.bitrateRange
                        },
                    )
                }.getOrNull()
            }.toList()
        return VideoConfigurationSelector.select(sourceWidth, sourceHeight, candidates)
    }

    private fun isKnownSoftwareCodec(name: String): Boolean =
        name.startsWith("OMX.google.", true) || name.startsWith("c2.android.", true)

    private companion object {
        const val DEQUEUE_TIMEOUT_US = 10_000L
        const val FINALIZATION_TIMEOUT_NANOS = 5_000_000_000L
    }
}

internal class RecordingPipelineException(val failure: RecordingFailure) : RuntimeException()
