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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecordingRuntimeSnapshot(
    val state: RecordingState = RecordingState.Idle,
    val elapsedSeconds: Long = 0,
)

class ScreenRecordingService : Service() {
    inner class LocalBinder : Binder() {
        val runtime: StateFlow<RecordingRuntimeSnapshot> get() = this@ScreenRecordingService.runtime
        fun requestStop() { this@ScreenRecordingService.requestStop(StopReason.UserRequested) }
        fun acknowledgeTerminal() = dispatch(RecordingCommand.Acknowledge)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val encoderExecutor = Executors.newSingleThreadExecutor()
    private val binder = LocalBinder()
    private val transitionLock = Any()
    private val cleanupStarted = AtomicBoolean(false)
    private var pipeline: AvcRecordingPipeline? = null
    private var recordingStartedAtMillis: Long? = null
    private val _runtime = MutableStateFlow(RecordingRuntimeSnapshot())
    private val runtime: StateFlow<RecordingRuntimeSnapshot> = _runtime.asStateFlow()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { requestStop(StopReason.ProjectionRevoked) }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startGrantedSession(intent)
            ACTION_STOP -> if (!requestStop(StopReason.UserRequested)) stopIfIdle()
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
        if (consentData == null || resultCode == Int.MIN_VALUE || width <= 0 || height <= 0 || densityDpi <= 0 ||
            audioMode != AudioMode.None && audioMode != AudioMode.Microphone
        ) {
            stopIfIdle()
            return
        }
        synchronized(transitionLock) {
            if (_runtime.value.state != RecordingState.Idle) return
            cleanupStarted.set(false)
            applyTransitionLocked(RecordingCommand.Start(Milestone5Settings.default.copy(audioMode = requireNotNull(audioMode))))
        }
        if (audioMode == AudioMode.Microphone && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            terminalFailure(RecordingFailure.RequiredPermissionDenied(RequiredPermission.Microphone))
            return
        }
        try {
            promoteToForeground(requireNotNull(audioMode))
        } catch (_: RuntimeException) {
            terminalFailure(RecordingFailure.UnexpectedInternalFailure)
            return
        }
        encoderExecutor.execute { runSession(resultCode, consentData, width, height, densityDpi, requireNotNull(audioMode)) }
    }

    private fun stopIfIdle() {
        if (_runtime.value.state == RecordingState.Idle) stopSelf()
    }

    private fun runSession(resultCode: Int, consentData: Intent, width: Int, height: Int, densityDpi: Int, audioMode: AudioMode) {
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val projection = manager.getMediaProjection(resultCode, consentData)
                ?: throw RecordingPipelineException(RecordingFailure.ProjectionUnavailable)
            val output = try {
                RecordingOutput.create(this, System.currentTimeMillis(), filenameSequence.incrementAndGet())
            } catch (_: RuntimeException) {
                try { projection.stop() } catch (_: RuntimeException) { }
                throw RecordingPipelineException(RecordingFailure.StorageFailure)
            }
            val ownedPipeline = AvcRecordingPipeline(projection, width, height, densityDpi, audioMode, output)
            pipeline = ownedPipeline
            ownedPipeline.start(mainHandler, projectionCallback)
            dispatch(RecordingEvent.PreparationReady)
            dispatch(RecordingEvent.CountdownFinished)
            recordingStartedAtMillis = SystemClock.elapsedRealtime()
            mainHandler.post(elapsedUpdater)
            finalizeSession(ownedPipeline, ownedPipeline.drainUntilStopped(), null)
        } catch (error: RecordingPipelineException) {
            finalizeSession(pipeline, null, error.failure)
        } catch (_: RuntimeException) {
            finalizeSession(pipeline, null, RecordingFailure.VideoEncoderFailure(FailureStage.Runtime))
        }
    }

    private fun requestStop(reason: StopReason): Boolean {
        val accepted = dispatch(RecordingCommand.Stop(reason)) is TransitionResult.Accepted
        if (accepted) pipeline?.requestStop()
        return accepted
    }

    private fun finalizeSession(
        ownedPipeline: AvcRecordingPipeline?,
        completion: OutputCompletion?,
        failure: RecordingFailure?,
    ) {
        if (!cleanupStarted.compareAndSet(false, true)) return
        val output = ownedPipeline?.takeOutput()
        var publishable = false
        try {
            val resourcesFinalized = ownedPipeline?.release(projectionCallback) == true
            output?.closeDescriptor()
            publishable = failure == null && completion?.canPublish() == true && resourcesFinalized
            if (publishable) output?.publish() else output?.discard()
        } catch (_: RuntimeException) {
            publishable = false
            try { output?.discard() } catch (_: RuntimeException) { }
        } finally {
            pipeline = null
        }
        if (publishable) {
            dispatch(RecordingEvent.FinalizationSucceeded)
        } else {
            val current = _runtime.value.state
            if (current is RecordingState.Stopping) {
                dispatch(RecordingEvent.FinalizationFailed(RecordingFailure.FinalizationFailure))
            } else {
                dispatch(RecordingEvent.FatalFailure(failure ?: RecordingFailure.FinalizationFailure))
            }
        }
        finishService()
    }

    private fun terminalFailure(failure: RecordingFailure) {
        dispatch(RecordingEvent.FatalFailure(failure))
        finishService()
    }

    private fun finishService() {
        recordingStartedAtMillis = null
        mainHandler.removeCallbacks(elapsedUpdater)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun dispatch(input: RecordingInput): TransitionResult = synchronized(transitionLock) {
        applyTransitionLocked(input)
    }

    private fun applyTransitionLocked(input: RecordingInput): TransitionResult {
        return RecordingStateMachine.transition(_runtime.value.state, input).also { result ->
            if (result is TransitionResult.Accepted) {
                val elapsed = if (result.newState is RecordingState.Preparing) 0 else _runtime.value.elapsedSeconds
                _runtime.value = RecordingRuntimeSnapshot(result.newState, elapsed)
                if (result.newState !is RecordingState.Preparing) updateNotification(result.newState)
            }
        }
    }

    private val elapsedUpdater = object : Runnable {
        override fun run() {
            val started = recordingStartedAtMillis ?: return
            synchronized(transitionLock) {
                val current = _runtime.value
                if (current.state is RecordingState.Recording) {
                    _runtime.value = current.copy(elapsedSeconds = (SystemClock.elapsedRealtime() - started) / 1_000)
                    mainHandler.postDelayed(this, 1_000)
                }
            }
        }
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
        if (state is RecordingState.Preparing || state is RecordingState.Countdown || state is RecordingState.Recording || state is RecordingState.Stopping) {
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
            .setContentText(getString(R.string.recording_notification_body))
            .setContentIntent(reopen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        if (state is RecordingState.Countdown || state is RecordingState.Recording) {
            val stop = PendingIntent.getService(
                this, 1, stopIntent(this), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.ic_record, getString(R.string.stop_recording), stop)
        }
        return builder.build()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(elapsedUpdater)
        val ownedPipeline = pipeline
        if (ownedPipeline != null && !cleanupStarted.get()) {
            requestStop(StopReason.ApplicationShutdown)
            ownedPipeline.requestStop()
        }
        encoderExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.droidnova.screenrecorder.action.START_RECORDING"
        private const val ACTION_STOP = "com.droidnova.screenrecorder.action.STOP_RECORDING"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_CONSENT_DATA = "consent_data"
        private const val EXTRA_SOURCE_WIDTH = "source_width"
        private const val EXTRA_SOURCE_HEIGHT = "source_height"
        private const val EXTRA_DENSITY_DPI = "density_dpi"
        private const val EXTRA_AUDIO_MODE = "audio_mode"
        private const val CHANNEL_ID = "screen_recording"
        private const val NOTIFICATION_ID = 41
        private val filenameSequence = AtomicLong()

        fun startIntent(
            context: Context,
            resultCode: Int,
            data: Intent,
            width: Int,
            height: Int,
            densityDpi: Int,
            audioMode: AudioMode,
        ) =
            Intent(context, ScreenRecordingService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode).putExtra(EXTRA_CONSENT_DATA, data)
                .putExtra(EXTRA_SOURCE_WIDTH, width).putExtra(EXTRA_SOURCE_HEIGHT, height).putExtra(EXTRA_DENSITY_DPI, densityDpi)
                .putExtra(EXTRA_AUDIO_MODE, audioMode.name)

        fun stopIntent(context: Context) = Intent(context, ScreenRecordingService::class.java).setAction(ACTION_STOP)
    }
}
