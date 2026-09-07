package com.droidnova.screenrecorder.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.droidnova.screenrecorder.MainActivity
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.domain.recording.RecordingCommand
import com.droidnova.screenrecorder.domain.recording.RecordingEvent
import com.droidnova.screenrecorder.domain.recording.RecordingFailure
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

class ScreenRecordingService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val encoderExecutor = Executors.newSingleThreadExecutor()
    private val stopStarted = AtomicBoolean(false)
    private var pipeline: AvcRecordingPipeline? = null
    private var recordingStartedAtMillis: Long? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            requestStop(StopReason.ProjectionRevoked)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startGrantedSession(intent)
            ACTION_STOP -> requestStop(StopReason.UserRequested)
        }
        return START_NOT_STICKY
    }

    private fun startGrantedSession(intent: Intent) {
        synchronized(stateLock) {
            if (_state.value != RecordingState.Idle) return
            updateState(RecordingCommand.Start(Milestone5Settings.default))
        }
        try {
            promoteToForeground()
        } catch (_: RuntimeException) {
            fail(RecordingFailure.UnexpectedInternalFailure)
            return
        }
        val consentData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_CONSENT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_CONSENT_DATA)
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        val width = intent.getIntExtra(EXTRA_SOURCE_WIDTH, 0)
        val height = intent.getIntExtra(EXTRA_SOURCE_HEIGHT, 0)
        val densityDpi = intent.getIntExtra(EXTRA_DENSITY_DPI, 0)
        if (consentData == null || resultCode == Int.MIN_VALUE || width <= 0 || height <= 0 || densityDpi <= 0) {
            fail(RecordingFailure.ConsentUnavailable)
            return
        }
        encoderExecutor.execute {
            var projection: MediaProjection? = null
            try {
                val manager = getSystemService(MediaProjectionManager::class.java)
                val acquiredProjection = requireNotNull(manager.getMediaProjection(resultCode, consentData))
                projection = acquiredProjection
                val output = try {
                    RecordingOutput.create(this, System.currentTimeMillis(), filenameSequence.incrementAndGet())
                } catch (_: RuntimeException) {
                    try { acquiredProjection.stop() } catch (_: RuntimeException) {}
                    fail(RecordingFailure.StorageFailure)
                    return@execute
                }
                val ownedPipeline = AvcRecordingPipeline(
                    acquiredProjection, width, height, densityDpi, output,
                )
                pipeline = ownedPipeline
                ownedPipeline.start(mainHandler, projectionCallback)
                updateState(RecordingEvent.PreparationReady)
                updateState(RecordingEvent.CountdownFinished)
                recordingStartedAtMillis = SystemClock.elapsedRealtime()
                mainHandler.post(elapsedUpdater)
                val completion = ownedPipeline.drainUntilStopped()
                finishPipeline(ownedPipeline, completion)
            } catch (error: RecordingPipelineException) {
                fail(error.failure)
            } catch (_: RuntimeException) {
                if (projection == null) fail(RecordingFailure.ProjectionUnavailable)
                else fail(RecordingFailure.VideoEncoderFailure(com.droidnova.screenrecorder.domain.recording.FailureStage.Runtime))
            }
        }
    }

    private fun requestStop(reason: StopReason) {
        if (!stopStarted.compareAndSet(false, true)) return
        val result = updateState(RecordingCommand.Stop(reason))
        if (result is TransitionResult.Accepted) pipeline?.requestStop() else stopStarted.set(false)
    }

    private fun finishPipeline(ownedPipeline: AvcRecordingPipeline, completion: OutputCompletion) {
        var success = false
        val output = ownedPipeline.takeOutput()
        try {
            val resourcesFinalized = ownedPipeline.release(projectionCallback)
            output?.closeDescriptor()
            if (completion.canPublish() && resourcesFinalized) {
                // The output descriptor is closed before the pending entry is published.
                output?.publish()
                success = true
            }
        } catch (_: RuntimeException) {
            success = false
        } finally {
            if (!success) output?.discard()
            pipeline = null
        }
        if (success) updateState(RecordingEvent.FinalizationSucceeded)
        else updateState(RecordingEvent.FinalizationFailed(RecordingFailure.FinalizationFailure))
        finishService()
    }

    private fun fail(failure: RecordingFailure) {
        if (_state.value is RecordingState.Stopping) {
            updateState(RecordingEvent.FinalizationFailed(RecordingFailure.FinalizationFailure))
        } else {
            updateState(RecordingEvent.FatalFailure(failure))
        }
        val currentPipeline = pipeline
        try { currentPipeline?.release(projectionCallback) } finally {
            currentPipeline?.takeOutput()?.discard()
            pipeline = null
        }
        finishService()
    }

    private fun finishService() {
        recordingStartedAtMillis = null
        mainHandler.removeCallbacks(elapsedUpdater)
        _elapsedSeconds.value = 0
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val elapsedUpdater = object : Runnable {
        override fun run() {
            val started = recordingStartedAtMillis ?: return
            if (_state.value is RecordingState.Recording) {
                _elapsedSeconds.value = (SystemClock.elapsedRealtime() - started) / 1_000
                mainHandler.postDelayed(this, 1_000)
            }
        }
    }

    private fun promoteToForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel_name), NotificationManager.IMPORTANCE_LOW))
        }
        val reopen = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_recording)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_body))
            .setContentIntent(reopen)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        pipeline?.requestStop()
        mainHandler.removeCallbacks(elapsedUpdater)
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
        private const val CHANNEL_ID = "screen_recording"
        private const val NOTIFICATION_ID = 41
        private val stateLock = Any()
        private val filenameSequence = AtomicLong()
        private val _state = MutableStateFlow<RecordingState>(RecordingState.Idle)
        val state: StateFlow<RecordingState> = _state.asStateFlow()
        private val _elapsedSeconds = MutableStateFlow(0L)
        val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

        fun startIntent(context: Context, resultCode: Int, data: Intent, width: Int, height: Int, densityDpi: Int) =
            Intent(context, ScreenRecordingService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode).putExtra(EXTRA_CONSENT_DATA, data)
                .putExtra(EXTRA_SOURCE_WIDTH, width).putExtra(EXTRA_SOURCE_HEIGHT, height).putExtra(EXTRA_DENSITY_DPI, densityDpi)

        fun stopIntent(context: Context) = Intent(context, ScreenRecordingService::class.java).setAction(ACTION_STOP)

        fun acknowledgeTerminal() {
            synchronized(stateLock) {
                val result = RecordingStateMachine.transition(_state.value, RecordingCommand.Acknowledge)
                if (result is TransitionResult.Accepted) _state.value = result.newState
            }
        }

        private fun updateState(input: com.droidnova.screenrecorder.domain.recording.RecordingInput): TransitionResult = synchronized(stateLock) {
            RecordingStateMachine.transition(_state.value, input).also { if (it is TransitionResult.Accepted) _state.value = it.newState }
        }

    }
}
