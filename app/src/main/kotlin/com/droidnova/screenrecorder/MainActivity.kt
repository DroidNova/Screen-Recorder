package com.droidnova.screenrecorder

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.droidnova.screenrecorder.recording.ScreenRecordingService
import com.droidnova.screenrecorder.ui.ScreenRecorderApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var consentLaunchPending = false
    private val statusMessage = mutableStateOf<Int?>(null)
    private val recordingRuntime = mutableStateOf(com.droidnova.screenrecorder.recording.RecordingRuntimeSnapshot())
    private var serviceBinder: ScreenRecordingService.LocalBinder? = null
    private var runtimeCollection: Job? = null
    private var serviceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val connected = service as ScreenRecordingService.LocalBinder
            serviceBinder = connected
            recordingRuntime.value = connected.runtime.value
            runtimeCollection = lifecycleScope.launch {
                connected.runtime.collect { recordingRuntime.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            runtimeCollection?.cancel()
            runtimeCollection = null
            serviceBinder = null
            recordingRuntime.value = com.droidnova.screenrecorder.recording.RecordingRuntimeSnapshot()
        }
    }
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
            val runtime = recordingRuntime.value
            ScreenRecorderApp(
                recordingState = runtime.state,
                elapsedSeconds = runtime.elapsedSeconds,
                statusMessage = statusMessage.value,
                onStartRecording = ::requestProjectionConsent,
                onStopRecording = { serviceBinder?.requestStop() ?: startService(ScreenRecordingService.stopIntent(this)) },
                onTerminalStateShown = {
                    statusMessage.value = if (recordingRuntime.value.state is com.droidnova.screenrecorder.domain.recording.RecordingState.Completed) {
                        R.string.recording_completed
                    } else {
                        R.string.recording_failed
                    }
                    serviceBinder?.acknowledgeTerminal()
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        serviceBound = bindService(Intent(this, ScreenRecordingService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        runtimeCollection?.cancel()
        runtimeCollection = null
        if (serviceBound) unbindService(serviceConnection)
        serviceBound = false
        serviceBinder = null
        super.onStop()
    }

    private fun requestProjectionConsent() {
        if (consentLaunchPending || recordingRuntime.value.state != com.droidnova.screenrecorder.domain.recording.RecordingState.Idle) return
        consentLaunchPending = true
        statusMessage.value = null
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionConsent.launch(manager.createScreenCaptureIntent())
    }
}
