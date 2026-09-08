package com.droidnova.screenrecorder

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.droidnova.screenrecorder.domain.recording.RecordingState
import com.droidnova.screenrecorder.recording.RecordingRuntimeSnapshot
import com.droidnova.screenrecorder.recording.ScreenRecordingService
import com.droidnova.screenrecorder.ui.ScreenRecorderApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var projectionConsentPending = false
    private var notificationPermissionPending = false
    private var notificationPermissionRequested = false
    private var backgroundWhenRecordingStarts = false
    private val showNotificationPermissionExplanation = mutableStateOf(false)
    private val statusMessage = mutableStateOf<Int?>(null)
    private val recordingRuntime = mutableStateOf(RecordingRuntimeSnapshot())
    private var serviceBinder: ScreenRecordingService.LocalBinder? = null
    private var runtimeCollection: Job? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val connected = service as ScreenRecordingService.LocalBinder
            serviceBinder = connected
            applyRuntimeSnapshot(connected.runtime.value)
            runtimeCollection = lifecycleScope.launch {
                connected.runtime.collect(::applyRuntimeSnapshot)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            runtimeCollection?.cancel()
            runtimeCollection = null
            serviceBinder = null
            recordingRuntime.value = RecordingRuntimeSnapshot()
        }
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionPending = false
        if (granted) launchProjectionConsent() else showNotificationPermissionExplanation.value = true
    }

    private val projectionConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        projectionConsentPending = false
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            backgroundWhenRecordingStarts = false
            statusMessage.value = R.string.recording_consent_denied
            return@registerForActivityResult
        }
        val configuration = resources.configuration
        val density = resources.displayMetrics.density
        val width = (configuration.screenWidthDp * density).toInt()
        val height = (configuration.screenHeightDp * density).toInt()
        val serviceIntent = ScreenRecordingService.startIntent(this, result.resultCode, data, width, height, configuration.densityDpi)
        try {
            backgroundWhenRecordingStarts = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
        } catch (_: RuntimeException) {
            backgroundWhenRecordingStarts = false
            statusMessage.value = R.string.recording_start_failed
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionConsentPending = savedInstanceState?.getBoolean(STATE_PROJECTION_CONSENT_PENDING) == true
        notificationPermissionPending = savedInstanceState?.getBoolean(STATE_NOTIFICATION_PERMISSION_PENDING) == true
        notificationPermissionRequested = savedInstanceState?.getBoolean(STATE_NOTIFICATION_PERMISSION_REQUESTED) == true
        backgroundWhenRecordingStarts = savedInstanceState?.getBoolean(STATE_BACKGROUND_WHEN_RECORDING) == true
        showNotificationPermissionExplanation.value = savedInstanceState?.getBoolean(STATE_SHOW_NOTIFICATION_EXPLANATION) == true
        enableEdgeToEdge()
        setContent {
            val runtime = recordingRuntime.value
            ScreenRecorderApp(
                recordingState = runtime.state,
                elapsedSeconds = runtime.elapsedSeconds,
                statusMessage = statusMessage.value,
                onStartRecording = ::requestRecordingPermissions,
                onStopRecording = { serviceBinder?.requestStop() ?: startService(ScreenRecordingService.stopIntent(this)) },
                onTerminalStateShown = {
                    statusMessage.value = if (recordingRuntime.value.state is RecordingState.Completed) {
                        R.string.recording_completed
                    } else {
                        R.string.recording_failed
                    }
                    serviceBinder?.acknowledgeTerminal()
                },
            )
            if (showNotificationPermissionExplanation.value) {
                AlertDialog(
                    onDismissRequest = { showNotificationPermissionExplanation.value = false },
                    title = { Text(stringResource(R.string.notification_permission_title)) },
                    text = { Text(stringResource(R.string.notification_permission_explanation)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showNotificationPermissionExplanation.value = false
                            launchProjectionConsent()
                        }) { Text(stringResource(R.string.continue_without_notification)) }
                    },
                    dismissButton = {
                        TextButton(onClick = ::openNotificationSettings) {
                            Text(stringResource(R.string.notification_settings))
                        }
                        TextButton(onClick = { showNotificationPermissionExplanation.value = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PROJECTION_CONSENT_PENDING, projectionConsentPending)
        outState.putBoolean(STATE_NOTIFICATION_PERMISSION_PENDING, notificationPermissionPending)
        outState.putBoolean(STATE_NOTIFICATION_PERMISSION_REQUESTED, notificationPermissionRequested)
        outState.putBoolean(STATE_BACKGROUND_WHEN_RECORDING, backgroundWhenRecordingStarts)
        outState.putBoolean(STATE_SHOW_NOTIFICATION_EXPLANATION, showNotificationPermissionExplanation.value)
        super.onSaveInstanceState(outState)
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

    private fun requestRecordingPermissions() {
        if (requestInProgress() || recordingRuntime.value.state != RecordingState.Idle) return
        statusMessage.value = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (notificationPermissionRequested) {
                showNotificationPermissionExplanation.value = true
            } else {
                notificationPermissionRequested = true
                notificationPermissionPending = true
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return
        }
        launchProjectionConsent()
    }

    private fun launchProjectionConsent() {
        if (requestInProgress() || recordingRuntime.value.state != RecordingState.Idle) return
        projectionConsentPending = true
        statusMessage.value = null
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionConsent.launch(manager.createScreenCaptureIntent())
    }

    private fun requestInProgress(): Boolean =
        notificationPermissionPending || projectionConsentPending || showNotificationPermissionExplanation.value

    private fun applyRuntimeSnapshot(snapshot: RecordingRuntimeSnapshot) {
        recordingRuntime.value = snapshot
        if (snapshot.state is RecordingState.Recording && backgroundWhenRecordingStarts) {
            backgroundWhenRecordingStarts = false
            moveTaskToBack(true)
        } else if (
            snapshot.state == RecordingState.Idle ||
            snapshot.state is RecordingState.Failed ||
            snapshot.state is RecordingState.Completed
        ) {
            backgroundWhenRecordingStarts = false
        }
    }

    private fun openNotificationSettings() {
        showNotificationPermissionExplanation.value = false
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    private companion object {
        const val STATE_PROJECTION_CONSENT_PENDING = "projection_consent_pending"
        const val STATE_NOTIFICATION_PERMISSION_PENDING = "notification_permission_pending"
        const val STATE_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        const val STATE_BACKGROUND_WHEN_RECORDING = "background_when_recording"
        const val STATE_SHOW_NOTIFICATION_EXPLANATION = "show_notification_explanation"
    }
}
