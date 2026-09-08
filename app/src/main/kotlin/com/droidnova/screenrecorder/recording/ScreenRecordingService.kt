package com.droidnova.screenrecorder.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.hardware.display.DisplayManager
import android.graphics.Point
import android.view.WindowManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.droidnova.screenrecorder.MainActivity
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.domain.recording.FailureStage
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.domain.recording.CapabilityRejection
import com.droidnova.screenrecorder.domain.recording.CountdownConfiguration
import com.droidnova.screenrecorder.domain.recording.FrameRate
import com.droidnova.screenrecorder.domain.recording.RecordingPreset
import com.droidnova.screenrecorder.domain.recording.RecordingSettings
import com.droidnova.screenrecorder.domain.recording.VideoBitrate
import com.droidnova.screenrecorder.domain.recording.CapabilityValidation
import com.droidnova.screenrecorder.domain.recording.RecordingCommand
import com.droidnova.screenrecorder.domain.recording.RecordingEvent
import com.droidnova.screenrecorder.domain.recording.RecordingFailure
import com.droidnova.screenrecorder.domain.recording.RecordingInput
import com.droidnova.screenrecorder.domain.recording.RequiredPermission
import com.droidnova.screenrecorder.domain.recording.RecordingState
import com.droidnova.screenrecorder.domain.recording.RecordingStateMachine
import com.droidnova.screenrecorder.domain.recording.StopReason
import com.droidnova.screenrecorder.domain.recording.TransitionResult
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class RecordingRuntimeSnapshot(
    val state: RecordingState = RecordingState.Idle,
    val elapsedSeconds: Long = 0,
    val countdownRemainingSeconds: Int? = null,
    val recordingStartedAtNanos: Long? = null,
    val pauseStartedAtNanos: Long? = null,
    val accumulatedPausedNanos: Long = 0,
    val outcome: RecordingOutcome? = null,
)

enum class RecordingOutcome { StorageLow, RecoveredSaved, RecoveredRemoved, FinalizationFailed }

class ScreenRecordingService : Service() {
    inner class LocalBinder : Binder() {
        val runtime: StateFlow<RecordingRuntimeSnapshot> get() = this@ScreenRecordingService.runtime
        fun requestStop() { this@ScreenRecordingService.requestStop(StopReason.UserRequested) }
        fun requestPause() { this@ScreenRecordingService.requestPause() }
        fun requestResume() { this@ScreenRecordingService.requestResume() }
        fun acknowledgeTerminal() = dispatch(RecordingCommand.Acknowledge)
        fun acknowledgeOutcome() { _runtime.value = _runtime.value.copy(outcome = null) }
        fun acknowledgeBackgrounded() { countdownHandoff.countDown() }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val encoderExecutor = Executors.newSingleThreadExecutor()
    private val storageExecutor = Executors.newSingleThreadScheduledExecutor()
    private val binder = LocalBinder()
    private val transitionLock = Any()
    private val cleanupStarted = AtomicBoolean(false)
    private var pipeline: AvcRecordingPipeline? = null
    private var storageMonitor: ScheduledFuture<*>? = null
    private var activeStoragePolicy: StoragePolicy? = null
    private var countdownHandoff = CountDownLatch(1)
    private val _runtime = MutableStateFlow(RecordingRuntimeSnapshot())
    private val runtime: StateFlow<RecordingRuntimeSnapshot> = _runtime.asStateFlow()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { requestStop(StopReason.ProjectionRevoked) }
        override fun onCapturedContentResize(width: Int, height: Int) {
            pipeline?.resizeCapturedContent(width, height)
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) updateLegacyDisplaySize()
        }
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, mainHandler)
        encoderExecutor.execute {
            val recovered = runCatching { RecordingOutput.recover(this) }.getOrNull() ?: return@execute
            _runtime.value = _runtime.value.copy(
                outcome = if (recovered == RecoveryOutcome.Saved) RecordingOutcome.RecoveredSaved else RecordingOutcome.RecoveredRemoved,
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startGrantedSession(intent)
            ACTION_STOP -> if (!requestStop(StopReason.UserRequested)) stopIfIdle()
            ACTION_PAUSE -> requestPause()
            ACTION_RESUME -> requestResume()
            null -> stopIfIdle()
            else -> Unit
        }
        return START_NOT_STICKY
    }

    private fun startGrantedSession(intent: Intent) {
        val consentData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CONSENT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_CONSENT_DATA)
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        val width = intent.getIntExtra(EXTRA_SOURCE_WIDTH, 0)
        val height = intent.getIntExtra(EXTRA_SOURCE_HEIGHT, 0)
        val densityDpi = intent.getIntExtra(EXTRA_DENSITY_DPI, 0)
        val audioMode = intent.getStringExtra(EXTRA_AUDIO_MODE)?.let { value ->
            AudioMode.entries.firstOrNull { it.name == value }
        }
        val preset = intent.getStringExtra(EXTRA_PRESET)?.let { value -> RecordingPreset.entries.firstOrNull { it.name == value } }
        val shortEdge = intent.getIntExtra(EXTRA_SHORT_EDGE, 0)
        val frameRate = intent.getIntExtra(EXTRA_FRAME_RATE, 0)
        val bitrate = intent.getIntExtra(EXTRA_BITRATE, 0)
        val encodedWidth = intent.getIntExtra(EXTRA_ENCODED_WIDTH, 0)
        val encodedHeight = intent.getIntExtra(EXTRA_ENCODED_HEIGHT, 0)
        val countdownSeconds = intent.getIntExtra(EXTRA_COUNTDOWN_SECONDS, -1)
        if (consentData == null || resultCode == Int.MIN_VALUE || width <= 0 || height <= 0 || densityDpi <= 0 ||
            audioMode == null || preset == null || shortEdge <= 0 || frameRate <= 0 || bitrate <= 0 || encodedWidth <= 0 || encodedHeight <= 0 ||
            countdownSeconds !in setOf(0, 3, 5, 15) ||
            audioMode == AudioMode.DeviceAudio && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        ) {
            stopIfIdle()
            return
        }
        synchronized(transitionLock) {
            if (_runtime.value.state != RecordingState.Idle) return
            cleanupStarted.set(false)
            countdownHandoff = CountDownLatch(1)
            val requestedSettings = Milestone5Settings.default.copy(
                preset = requireNotNull(preset),
                audioMode = requireNotNull(audioMode),
                resolution = com.droidnova.screenrecorder.domain.recording.CaptureResolution(encodedWidth, encodedHeight),
                frameRate = FrameRate(frameRate),
                videoBitrate = VideoBitrate(bitrate),
                countdown = if (countdownSeconds == 0) CountdownConfiguration.None else CountdownConfiguration.Duration(countdownSeconds),
            )
            applyTransitionLocked(RecordingCommand.Start(requestedSettings))
        }
        if (audioMode != AudioMode.None && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            terminalFailure(RecordingFailure.RequiredPermissionDenied(RequiredPermission.Microphone))
            return
        }
        try {
            promoteToForeground(requireNotNull(audioMode))
        } catch (_: RuntimeException) {
            terminalFailure(RecordingFailure.UnexpectedInternalFailure)
            return
        }
        encoderExecutor.execute {
            runSession(
                resultCode, consentData, width, height, densityDpi, requireNotNull(audioMode),
                shortEdge, frameRate, bitrate, encodedWidth, encodedHeight,
            )
        }
    }

    private fun stopIfIdle() {
        if (_runtime.value.state == RecordingState.Idle) stopSelf()
    }

    private fun runSession(
        resultCode: Int,
        consentData: Intent,
        width: Int,
        height: Int,
        densityDpi: Int,
        audioMode: AudioMode,
        shortEdge: Int,
        frameRate: Int,
        bitrate: Int,
        encodedWidth: Int,
        encodedHeight: Int,
    ) {
        try {
            val available = AvcCapabilityProvider.query(width, height)
            val selected = available.firstOrNull {
                it.shortEdge == shortEdge && it.resolution.width == encodedWidth && it.resolution.height == encodedHeight &&
                    it.frameRate.framesPerSecond == frameRate && it.bitrate.bitsPerSecond == bitrate
            } ?: throw RecordingPipelineException(
                RecordingFailure.UnsupportedCapability(CapabilityRejection.FrameRateForResolution),
            )
            val activeSettings = (_runtime.value.state as? RecordingState.Preparing)?.settings
                ?: throw RecordingPipelineException(RecordingFailure.UnexpectedInternalFailure)
            val validation = AvcCapabilityProvider.domainCapabilities(available)?.validate(activeSettings)
            if (validation is CapabilityValidation.Unsupported) {
                throw RecordingPipelineException(RecordingFailure.UnsupportedCapability(validation.reason))
            }
            dispatch(RecordingEvent.PreparationReady)
            if (!runCountdown()) {
                completeWithoutCapture()
                return
            }
            val storageCheck = runBlocking { withContext(Dispatchers.IO) { RecordingStorage(this@ScreenRecordingService).check(bitrate, audioMode) } }
            val policy = when (storageCheck) {
                is StorageCheck.Available -> storageCheck.policy
                is StorageCheck.Insufficient -> throw RecordingPipelineException(RecordingFailure.StorageUnavailable)
                StorageCheck.Unavailable -> throw RecordingPipelineException(RecordingFailure.StorageUnavailable)
            }
            activeStoragePolicy = policy
            val manager = getSystemService(MediaProjectionManager::class.java)
            val projection = manager.getMediaProjection(resultCode, consentData)
                ?: throw RecordingPipelineException(RecordingFailure.ProjectionUnavailable)
            val output = try {
                RecordingOutput.create(this, System.currentTimeMillis(), filenameSequence.incrementAndGet())
            } catch (_: RuntimeException) {
                try { projection.stop() } catch (_: RuntimeException) { }
                throw RecordingPipelineException(RecordingFailure.StorageWriteFailed)
            }
            val videoConfiguration = SelectedVideoConfiguration(
                selected.encoderName,
                selected.resolution,
                selected.frameRate.framesPerSecond,
                selected.bitrate.bitsPerSecond,
            )
            val ownedPipeline = AvcRecordingPipeline(projection, width, height, densityDpi, audioMode, videoConfiguration, output)
            pipeline = ownedPipeline
            ownedPipeline.start(mainHandler, projectionCallback)
            dispatch(RecordingEvent.CountdownFinished)
            synchronized(transitionLock) {
                val current = _runtime.value
                if (current.state is RecordingState.Recording) {
                    _runtime.value = current.copy(recordingStartedAtNanos = SystemClock.elapsedRealtimeNanos())
                }
            }
            mainHandler.post(elapsedUpdater)
            startStorageMonitor()
            finalizeSession(ownedPipeline, ownedPipeline.drainUntilStopped(), null)
        } catch (error: RecordingPipelineException) {
            finalizeSession(pipeline, null, error.failure)
        } catch (_: RuntimeException) {
            finalizeSession(pipeline, null, RecordingFailure.VideoEncoderFailure(FailureStage.Runtime))
        }
    }

    private fun runCountdown(): Boolean {
        val state = _runtime.value.state as? RecordingState.Countdown ?: return false
        val seconds = (state.settings.countdown as? CountdownConfiguration.Duration)?.seconds ?: 0
        if (seconds == 0) {
            try {
                countdownHandoff.await()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                requestStop(StopReason.ApplicationShutdown)
                return false
            }
            return _runtime.value.state is RecordingState.Countdown
        }
        val deadline = SystemClock.elapsedRealtime() + seconds * 1_000L
        var previous = -1
        while (_runtime.value.state is RecordingState.Countdown) {
            val remaining = ((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999) / 1_000
            if (remaining.toInt() != previous) {
                previous = remaining.toInt()
                updateCountdown(previous)
            }
            if (remaining == 0L) return true
            try {
                Thread.sleep(minOf(200L, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(1)))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                requestStop(StopReason.ApplicationShutdown)
                return false
            }
        }
        return false
    }

    private fun updateCountdown(remaining: Int) = synchronized(transitionLock) {
        val current = _runtime.value
        if (current.state is RecordingState.Countdown) {
            _runtime.value = current.copy(countdownRemainingSeconds = remaining)
            updateNotification(current.state)
        }
    }

    private fun completeWithoutCapture() {
        if (!cleanupStarted.compareAndSet(false, true)) return
        dispatch(RecordingEvent.FinalizationSucceeded)
        finishService()
    }

    private fun requestStop(reason: StopReason): Boolean {
        val accepted = dispatch(RecordingCommand.Stop(reason)) is TransitionResult.Accepted
        if (accepted) {
            countdownHandoff.countDown()
            pipeline?.requestStop()
        }
        return accepted
    }

    private fun startStorageMonitor() {
        check(storageMonitor == null)
        storageMonitor = storageExecutor.scheduleWithFixedDelay({
            val policy = activeStoragePolicy ?: return@scheduleWithFixedDelay
            val check = RecordingStorage(this).availableBytes()
            if (check == null || check <= policy.lowStorageStopBytes) requestStop(StopReason.LowStorage)
        }, STORAGE_MONITOR_SECONDS, STORAGE_MONITOR_SECONDS, TimeUnit.SECONDS)
    }

    private fun cancelStorageMonitor() {
        storageMonitor?.cancel(false)
        storageMonitor = null
        activeStoragePolicy = null
    }

    private fun requestPause(): Boolean = synchronized(transitionLock) {
        val current = _runtime.value
        if (current.state !is RecordingState.Recording) return@synchronized false
        try {
            pipeline?.pause() ?: return@synchronized false
        } catch (error: RecordingPipelineException) {
            applyTransitionLocked(RecordingEvent.FatalFailure(error.failure))
            pipeline?.requestStop()
            return@synchronized false
        }
        val result = applyTransitionLocked(RecordingCommand.Pause)
        if (result is TransitionResult.Accepted) {
            val now = SystemClock.elapsedRealtimeNanos()
            val updated = _runtime.value.copy(pauseStartedAtNanos = now)
            _runtime.value = updated.copy(elapsedSeconds = activeElapsedSeconds(updated, now))
        }
        result is TransitionResult.Accepted
    }

    private fun requestResume(): Boolean = synchronized(transitionLock) {
        val current = _runtime.value
        if (current.state !is RecordingState.Paused) return@synchronized false
        try {
            pipeline?.resume() ?: return@synchronized false
        } catch (error: RecordingPipelineException) {
            applyTransitionLocked(RecordingEvent.FatalFailure(error.failure))
            pipeline?.requestStop()
            return@synchronized false
        }
        val now = SystemClock.elapsedRealtimeNanos()
        val pauseStarted = current.pauseStartedAtNanos ?: now
        val result = applyTransitionLocked(RecordingCommand.Resume)
        if (result is TransitionResult.Accepted) {
            _runtime.value = _runtime.value.copy(
                pauseStartedAtNanos = null,
                accumulatedPausedNanos = current.accumulatedPausedNanos +
                    (now - pauseStarted).coerceAtLeast(0L),
            )
            mainHandler.removeCallbacks(elapsedUpdater)
            mainHandler.post(elapsedUpdater)
        }
        result is TransitionResult.Accepted
    }

    private fun finalizeSession(
        ownedPipeline: AvcRecordingPipeline?,
        completion: OutputCompletion?,
        failure: RecordingFailure?,
    ) {
        if (!cleanupStarted.compareAndSet(false, true)) return
        cancelStorageMonitor()
        val output = ownedPipeline?.takeOutput()
        var publishable = false
        try {
            val resourcesFinalized = ownedPipeline?.release(projectionCallback) == true
            output?.closeDescriptor()
            publishable = failure == null && completion?.canPublish() == true && resourcesFinalized
            if (publishable) {
                try {
                    output?.publish()
                } catch (_: RuntimeException) {
                    // READY_TO_PUBLISH remains durable so recovery can retry publication.
                    publishable = false
                }
            } else output?.discard()
        } catch (_: RuntimeException) {
            publishable = false
            try { output?.closeDescriptor(); output?.discard() } catch (_: RuntimeException) { }
        } finally {
            pipeline = null
        }
        if (publishable) {
            dispatch(RecordingEvent.FinalizationSucceeded)
            val reason = (_runtime.value.state as? RecordingState.Completed)?.reason
            if (reason == StopReason.LowStorage) _runtime.value = _runtime.value.copy(outcome = RecordingOutcome.StorageLow)
        } else {
            val current = _runtime.value.state
            if (current is RecordingState.Stopping) {
                dispatch(RecordingEvent.FinalizationFailed(RecordingFailure.FinalizationFailure))
            } else {
                dispatch(RecordingEvent.FatalFailure(failure ?: RecordingFailure.FinalizationFailure))
            }
            _runtime.value = _runtime.value.copy(outcome = RecordingOutcome.FinalizationFailed)
        }
        finishService()
    }

    private fun terminalFailure(failure: RecordingFailure) {
        dispatch(RecordingEvent.FatalFailure(failure))
        finishService()
    }

    private fun finishService() {
        mainHandler.removeCallbacks(elapsedUpdater)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun updateLegacyDisplaySize() {
        val display = getSystemService(WindowManager::class.java).defaultDisplay
        val size = Point()
        display.getRealSize(size)
        pipeline?.resizeCapturedContent(size.x, size.y)
    }

    private fun dispatch(input: RecordingInput): TransitionResult = synchronized(transitionLock) {
        applyTransitionLocked(input)
    }

    private fun applyTransitionLocked(input: RecordingInput): TransitionResult {
        return RecordingStateMachine.transition(_runtime.value.state, input).also { result ->
            if (result is TransitionResult.Accepted) {
                val elapsed = if (result.newState is RecordingState.Preparing) 0 else _runtime.value.elapsedSeconds
                val current = _runtime.value
                _runtime.value = current.copy(
                    state = result.newState,
                    elapsedSeconds = elapsed,
                    countdownRemainingSeconds = null,
                    recordingStartedAtNanos = if (result.newState is RecordingState.Preparing) null else current.recordingStartedAtNanos,
                    pauseStartedAtNanos = if (result.newState is RecordingState.Preparing) null else current.pauseStartedAtNanos,
                    accumulatedPausedNanos = if (result.newState is RecordingState.Preparing) 0 else current.accumulatedPausedNanos,
                )
                if (result.newState !is RecordingState.Preparing) updateNotification(result.newState)
            }
        }
    }

    private val elapsedUpdater = object : Runnable {
        override fun run() {
            synchronized(transitionLock) {
                val current = _runtime.value
                if (current.state is RecordingState.Recording) {
                    _runtime.value = current.copy(elapsedSeconds = activeElapsedSeconds(current))
                    updateNotification(current.state)
                    mainHandler.postDelayed(this, 1_000)
                }
            }
        }
    }

    private fun activeElapsedSeconds(
        snapshot: RecordingRuntimeSnapshot,
        nowNanos: Long = SystemClock.elapsedRealtimeNanos(),
    ): Long {
        val started = snapshot.recordingStartedAtNanos ?: return 0
        val currentPauseNanos = snapshot.pauseStartedAtNanos?.let {
            (nowNanos - it).coerceAtLeast(0L)
        } ?: 0L
        return (nowNanos - started - snapshot.accumulatedPausedNanos - currentPauseNanos)
            .coerceAtLeast(0L) / NANOS_PER_SECOND
    }

    private fun promoteToForeground(audioMode: AudioMode) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel_name), NotificationManager.IMPORTANCE_LOW))
        }
        val notification = buildNotification(_runtime.value.state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && audioMode == AudioMode.Microphone) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: RecordingState) {
        if (state is RecordingState.Preparing || state is RecordingState.Countdown || state is RecordingState.Recording || state is RecordingState.Paused || state is RecordingState.Stopping) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    private fun buildNotification(state: RecordingState): Notification {
        val reopen = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_recording)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(
                when {
                    state is RecordingState.Paused -> getString(R.string.recording_notification_paused)
                    _runtime.value.countdownRemainingSeconds != null -> getString(
                        R.string.recording_countdown_notification,
                        _runtime.value.countdownRemainingSeconds,
                    )
                    state is RecordingState.Recording -> getString(
                        R.string.recording_notification_elapsed,
                        _runtime.value.elapsedSeconds / 60,
                        _runtime.value.elapsedSeconds % 60,
                    )
                    else -> getString(R.string.recording_notification_body)
                },
            )
            .setContentIntent(reopen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (state is RecordingState.Recording) {
            builder.addAction(
                R.drawable.ic_record,
                getString(R.string.pause_recording),
                PendingIntent.getService(
                    this, PAUSE_REQUEST_CODE, pauseIntent(this),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else if (state is RecordingState.Paused) {
            builder.addAction(
                R.drawable.ic_record,
                getString(R.string.resume_recording),
                PendingIntent.getService(
                    this, RESUME_REQUEST_CODE, resumeIntent(this),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        if (state is RecordingState.Countdown || state is RecordingState.Recording || state is RecordingState.Paused) {
            val stop = PendingIntent.getService(
                this, STOP_REQUEST_CODE, stopIntent(this), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.ic_record, getString(R.string.stop_recording), stop)
        }
        return builder.build()
    }

    override fun onDestroy() {
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        mainHandler.removeCallbacks(elapsedUpdater)
        if (!cleanupStarted.get()) {
            requestStop(StopReason.ApplicationShutdown)
            pipeline?.requestStop()
        }
        encoderExecutor.shutdown()
        cancelStorageMonitor()
        storageExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.droidnova.screenrecorder.action.START_RECORDING"
        private const val ACTION_STOP = "com.droidnova.screenrecorder.action.STOP_RECORDING"
        private const val ACTION_PAUSE = "com.droidnova.screenrecorder.action.PAUSE_RECORDING"
        private const val ACTION_RESUME = "com.droidnova.screenrecorder.action.RESUME_RECORDING"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_CONSENT_DATA = "consent_data"
        private const val EXTRA_SOURCE_WIDTH = "source_width"
        private const val EXTRA_SOURCE_HEIGHT = "source_height"
        private const val EXTRA_DENSITY_DPI = "density_dpi"
        private const val EXTRA_AUDIO_MODE = "audio_mode"
        private const val EXTRA_PRESET = "preset"
        private const val EXTRA_SHORT_EDGE = "short_edge"
        private const val EXTRA_FRAME_RATE = "frame_rate"
        private const val EXTRA_BITRATE = "bitrate"
        private const val EXTRA_ENCODED_WIDTH = "encoded_width"
        private const val EXTRA_ENCODED_HEIGHT = "encoded_height"
        private const val EXTRA_COUNTDOWN_SECONDS = "countdown_seconds"
        private const val CHANNEL_ID = "screen_recording"
        private const val NOTIFICATION_ID = 41
        private const val STOP_REQUEST_CODE = 1
        private const val PAUSE_REQUEST_CODE = 2
        private const val RESUME_REQUEST_CODE = 3
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val STORAGE_MONITOR_SECONDS = 30L
        private val filenameSequence = AtomicLong()

        fun startIntent(
            context: Context,
            resultCode: Int,
            data: Intent,
            width: Int,
            height: Int,
            densityDpi: Int,
            settings: RecordingSettings,
            shortEdge: Int,
        ) =
            Intent(context, ScreenRecordingService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode).putExtra(EXTRA_CONSENT_DATA, data)
                .putExtra(EXTRA_SOURCE_WIDTH, width).putExtra(EXTRA_SOURCE_HEIGHT, height).putExtra(EXTRA_DENSITY_DPI, densityDpi)
                .putExtra(EXTRA_AUDIO_MODE, settings.audioMode.name)
                .putExtra(EXTRA_PRESET, settings.preset.name)
                .putExtra(EXTRA_SHORT_EDGE, shortEdge)
                .putExtra(EXTRA_FRAME_RATE, settings.frameRate.framesPerSecond)
                .putExtra(EXTRA_BITRATE, settings.videoBitrate.bitsPerSecond)
                .putExtra(EXTRA_ENCODED_WIDTH, settings.resolution.width)
                .putExtra(EXTRA_ENCODED_HEIGHT, settings.resolution.height)
                .putExtra(EXTRA_COUNTDOWN_SECONDS, (settings.countdown as? CountdownConfiguration.Duration)?.seconds ?: 0)

        fun stopIntent(context: Context) = Intent(context, ScreenRecordingService::class.java).setAction(ACTION_STOP)
        fun pauseIntent(context: Context) = Intent(context, ScreenRecordingService::class.java).setAction(ACTION_PAUSE)
        fun resumeIntent(context: Context) = Intent(context, ScreenRecordingService::class.java).setAction(ACTION_RESUME)
    }
}
