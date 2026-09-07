package com.droidnova.screenrecorder

import android.os.Bundle
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.droidnova.screenrecorder.ui.ScreenRecorderApp
import com.droidnova.screenrecorder.recording.ScreenRecordingService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var consentLaunchPending = false
    private val statusMessage = mutableStateOf<Int?>(null)
    private val projectionConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        consentLaunchPending = false
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            statusMessage.value = R.string.recording_consent_denied
            return@registerForActivityResult
        }
        val configuration = resources.configuration
        val density = resources.displayMetrics.density
        val width = (configuration.screenWidthDp * density).toInt()
        val height = (configuration.screenHeightDp * density).toInt()
        val serviceIntent = ScreenRecordingService.startIntent(this, result.resultCode, data, width, height, configuration.densityDpi)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
            moveTaskToBack(true)
        } catch (_: RuntimeException) {
            statusMessage.value = R.string.recording_start_failed
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val recordingState by ScreenRecordingService.state.collectAsState()
            val elapsedSeconds by ScreenRecordingService.elapsedSeconds.collectAsState()
            ScreenRecorderApp(
                recordingState = recordingState,
                elapsedSeconds = elapsedSeconds,
                statusMessage = statusMessage.value,
                onStartRecording = ::requestProjectionConsent,
                onStopRecording = { startService(ScreenRecordingService.stopIntent(this)) },
                onTerminalStateShown = {
                    statusMessage.value = if (recordingState is com.droidnova.screenrecorder.domain.recording.RecordingState.Completed) {
                        R.string.recording_completed
                    } else {
                        R.string.recording_failed
                    }
                    ScreenRecordingService.acknowledgeTerminal()
                },
            )
        }
    }

    private fun requestProjectionConsent() {
        if (consentLaunchPending || ScreenRecordingService.state.value != com.droidnova.screenrecorder.domain.recording.RecordingState.Idle) return
        consentLaunchPending = true
        statusMessage.value = null
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionConsent.launch(manager.createScreenCaptureIntent())
    }
}
