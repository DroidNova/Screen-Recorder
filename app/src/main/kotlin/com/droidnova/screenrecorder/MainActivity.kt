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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.droidnova.screenrecorder.domain.recording.RecordingState
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.domain.recording.CountdownConfiguration
import com.droidnova.screenrecorder.domain.recording.MaximumDurationPolicy
import com.droidnova.screenrecorder.domain.recording.RecordingSettings
import com.droidnova.screenrecorder.recording.RecordingRuntimeSnapshot
import com.droidnova.screenrecorder.recording.RecordingOutcome
import com.droidnova.screenrecorder.recording.ScreenRecordingService
import com.droidnova.screenrecorder.recording.AvailableVideoConfiguration
import com.droidnova.screenrecorder.recording.AvcCapabilityProvider
import com.droidnova.screenrecorder.recording.RecordingStorage
import com.droidnova.screenrecorder.recording.StorageCheck
import com.droidnova.screenrecorder.ui.ScreenRecorderApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var projectionConsentPending = false
    private var notificationPermissionPending = false
    private var notificationPermissionRequested = false
    private var microphonePermissionPending = false
    private var microphonePermissionRequested = false
    private var backgroundWhenRecordingStarts = false
    private val showNotificationPermissionExplanation = mutableStateOf(false)
    private val showMicrophonePermissionExplanation = mutableStateOf(false)
    private val storageDialog = mutableStateOf<StorageCheck?>(null)
    private val availableStorageBytes = mutableStateOf<Long?>(null)
    private var storagePreflightPending = false
    private val _selectedAudioMode = MutableStateFlow(AudioMode.None)
    private val selectedAudioMode = _selectedAudioMode.asStateFlow()
    private var requestedSessionAudioMode = AudioMode.None
    private val availableVideoConfigurations = MutableStateFlow<List<AvailableVideoConfiguration>>(emptyList())
    private val selectedVideoConfiguration = MutableStateFlow<AvailableVideoConfiguration?>(null)
    private val countdownSeconds = MutableStateFlow(0)
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

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphonePermissionPending = false
        if (granted) continueNotificationPermission() else showMicrophonePermissionExplanation.value = true
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
        val video = selectedVideoConfiguration.value ?: return@registerForActivityResult
        val settings = RecordingSettings(
            preset = video.preset,
            audioMode = requestedSessionAudioMode,
            resolution = video.resolution,
            frameRate = video.frameRate,
            videoBitrate = video.bitrate,
            countdown = if (countdownSeconds.value == 0) CountdownConfiguration.None else CountdownConfiguration.Duration(countdownSeconds.value),
            maximumDuration = MaximumDurationPolicy.Unlimited,
        )
        val serviceIntent = ScreenRecordingService.startIntent(
            this,
            result.resultCode,
            data,
            width,
            height,
            configuration.densityDpi,
            settings,
            video.shortEdge,
        )
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
        microphonePermissionPending = savedInstanceState?.getBoolean(STATE_MICROPHONE_PERMISSION_PENDING) == true
        microphonePermissionRequested = savedInstanceState?.getBoolean(STATE_MICROPHONE_PERMISSION_REQUESTED) == true
        backgroundWhenRecordingStarts = savedInstanceState?.getBoolean(STATE_BACKGROUND_WHEN_RECORDING) == true
        showNotificationPermissionExplanation.value = savedInstanceState?.getBoolean(STATE_SHOW_NOTIFICATION_EXPLANATION) == true
        showMicrophonePermissionExplanation.value = savedInstanceState?.getBoolean(STATE_SHOW_MICROPHONE_EXPLANATION) == true
        _selectedAudioMode.value = savedInstanceState?.getString(STATE_SELECTED_AUDIO_MODE)?.toAudioMode() ?: AudioMode.None
        requestedSessionAudioMode = savedInstanceState?.getString(STATE_REQUESTED_AUDIO_MODE)?.toAudioMode() ?: selectedAudioMode.value
        countdownSeconds.value = savedInstanceState?.getInt(STATE_COUNTDOWN_SECONDS) ?: 0
        val savedShortEdge = savedInstanceState?.getInt(STATE_VIDEO_SHORT_EDGE) ?: 720
        val savedFrameRate = savedInstanceState?.getInt(STATE_VIDEO_FRAME_RATE) ?: 30
        val savedBitrate = savedInstanceState?.getInt(STATE_VIDEO_BITRATE) ?: 6_000_000
        lifecycleScope.launch {
            val configuration = resources.configuration
            val density = resources.displayMetrics.density
            val width = (configuration.screenWidthDp * density).toInt()
            val height = (configuration.screenHeightDp * density).toInt()
            val options = withContext(Dispatchers.Default) { AvcCapabilityProvider.query(width, height) }
            availableVideoConfigurations.value = options
            selectedVideoConfiguration.value = options.firstOrNull {
                it.shortEdge == savedShortEdge && it.frameRate.framesPerSecond == savedFrameRate &&
                    it.bitrate.bitsPerSecond == savedBitrate
            } ?: options.firstOrNull { it.preset == com.droidnova.screenrecorder.domain.recording.RecordingPreset.Balanced }
                ?: options.firstOrNull()
            availableStorageBytes.value = withContext(Dispatchers.IO) { RecordingStorage(this@MainActivity).availableBytes() }
        }
        enableEdgeToEdge()
        setContent {
            val runtime = recordingRuntime.value
            val audioMode by selectedAudioMode.collectAsState()
            val videoOptions by availableVideoConfigurations.collectAsState()
            val selectedVideo by selectedVideoConfiguration.collectAsState()
            val videoSettingsValid = selectedVideo?.let { selected ->
                videoOptions.any { it.sameEncodingAs(selected) }
            } == true
            val countdown by countdownSeconds.collectAsState()
            ScreenRecorderApp(
                recordingState = runtime.state,
                elapsedSeconds = runtime.elapsedSeconds,
                countdownRemainingSeconds = runtime.countdownRemainingSeconds,
                availableStorageBytes = availableStorageBytes.value,
                statusMessage = statusMessage.value,
                onStartRecording = ::requestRecordingPermissions,
                onStopRecording = { serviceBinder?.requestStop() ?: startService(ScreenRecordingService.stopIntent(this)) },
                onPauseRecording = { serviceBinder?.requestPause() ?: startService(ScreenRecordingService.pauseIntent(this)) },
                onResumeRecording = { serviceBinder?.requestResume() ?: startService(ScreenRecordingService.resumeIntent(this)) },
                onTerminalStateShown = {
                    statusMessage.value = when (recordingRuntime.value.outcome) {
                        RecordingOutcome.StorageLow -> R.string.recording_storage_low_stopped
                        RecordingOutcome.FinalizationFailed -> R.string.recording_failed
                        else -> if (recordingRuntime.value.state is RecordingState.Completed) R.string.recording_completed else R.string.recording_failed
                    }
                    serviceBinder?.acknowledgeTerminal()
                },
                audioMode = audioMode,
                onAudioModeSelected = { _selectedAudioMode.value = it },
                videoOptions = videoOptions,
                selectedVideo = selectedVideo,
                settingsValid = videoSettingsValid,
                onVideoSelected = { selectedVideoConfiguration.value = it },
                countdownSeconds = countdown,
                onCountdownSelected = { countdownSeconds.value = it },
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
            if (showMicrophonePermissionExplanation.value) {
                AlertDialog(
                    onDismissRequest = { showMicrophonePermissionExplanation.value = false },
                    title = { Text(stringResource(R.string.microphone_permission_title)) },
                    text = { Text(stringResource(R.string.microphone_permission_explanation)) },
                    confirmButton = {
                        TextButton(onClick = {
                            showMicrophonePermissionExplanation.value = false
                            requestedSessionAudioMode = AudioMode.None
                            continueNotificationPermission()
                        }) { Text(stringResource(R.string.record_without_audio)) }
                    },
                    dismissButton = {
                        TextButton(onClick = ::openApplicationSettings) {
                            Text(stringResource(R.string.app_settings))
                        }
                        TextButton(onClick = { showMicrophonePermissionExplanation.value = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
            storageDialog.value?.let { failure ->
                val minimum = (failure as? StorageCheck.Insufficient)?.policy?.minimumStartBytes
                AlertDialog(
                    onDismissRequest = { storageDialog.value = null },
                    title = { Text(stringResource(if (failure is StorageCheck.Insufficient) R.string.storage_low_title else R.string.storage_unavailable_title)) },
                    text = { Text(if (minimum == null) stringResource(R.string.storage_unavailable_explanation) else getString(R.string.storage_low_explanation, (minimum + 1024L * 1024L - 1L) / (1024L * 1024L))) },
                    confirmButton = { TextButton(onClick = ::openStorageSettings) { Text(stringResource(R.string.manage_storage)) } },
                    dismissButton = { TextButton(onClick = { storageDialog.value = null }) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PROJECTION_CONSENT_PENDING, projectionConsentPending)
        outState.putBoolean(STATE_NOTIFICATION_PERMISSION_PENDING, notificationPermissionPending)
        outState.putBoolean(STATE_NOTIFICATION_PERMISSION_REQUESTED, notificationPermissionRequested)
        outState.putBoolean(STATE_MICROPHONE_PERMISSION_PENDING, microphonePermissionPending)
        outState.putBoolean(STATE_MICROPHONE_PERMISSION_REQUESTED, microphonePermissionRequested)
        outState.putBoolean(STATE_BACKGROUND_WHEN_RECORDING, backgroundWhenRecordingStarts)
        outState.putBoolean(STATE_SHOW_NOTIFICATION_EXPLANATION, showNotificationPermissionExplanation.value)
        outState.putBoolean(STATE_SHOW_MICROPHONE_EXPLANATION, showMicrophonePermissionExplanation.value)
        outState.putString(STATE_SELECTED_AUDIO_MODE, selectedAudioMode.value.name)
        outState.putString(STATE_REQUESTED_AUDIO_MODE, requestedSessionAudioMode.name)
        outState.putInt(STATE_COUNTDOWN_SECONDS, countdownSeconds.value)
        selectedVideoConfiguration.value?.let {
            outState.putInt(STATE_VIDEO_SHORT_EDGE, it.shortEdge)
            outState.putInt(STATE_VIDEO_FRAME_RATE, it.frameRate.framesPerSecond)
            outState.putInt(STATE_VIDEO_BITRATE, it.bitrate.bitsPerSecond)
        }
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
        val selectedVideo = selectedVideoConfiguration.value
        if (selectedVideo == null || availableVideoConfigurations.value.none { it.sameEncodingAs(selectedVideo) }) {
            statusMessage.value = R.string.recording_settings_unavailable
            return
        }
        requestedSessionAudioMode = selectedAudioMode.value
        if (requestedSessionAudioMode == AudioMode.DeviceAudio && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            statusMessage.value = R.string.device_audio_unavailable
            return
        }
        storagePreflightPending = true
        lifecycleScope.launch {
            val check = withContext(Dispatchers.IO) {
                RecordingStorage(this@MainActivity).check(selectedVideo.bitrate.bitsPerSecond, requestedSessionAudioMode)
            }
            storagePreflightPending = false
            availableStorageBytes.value = when (check) {
                is StorageCheck.Available -> check.bytes
                is StorageCheck.Insufficient -> check.bytes
                StorageCheck.Unavailable -> null
            }
            if (check !is StorageCheck.Available) {
                statusMessage.value = R.string.storage_preflight_failed
                storageDialog.value = check
                return@launch
            }
            continueAudioPermission()
        }
    }

    private fun continueAudioPermission() {
        if (requestInProgress() || recordingRuntime.value.state != RecordingState.Idle) return
        if (requestedSessionAudioMode != AudioMode.None &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            if (microphonePermissionRequested) {
                showMicrophonePermissionExplanation.value = true
            } else {
                microphonePermissionRequested = true
                microphonePermissionPending = true
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            return
        }
        continueNotificationPermission()
    }

    private fun continueNotificationPermission() {
        if (requestInProgress() || recordingRuntime.value.state != RecordingState.Idle) return
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
        storagePreflightPending || microphonePermissionPending || notificationPermissionPending || projectionConsentPending ||
            showMicrophonePermissionExplanation.value || showNotificationPermissionExplanation.value

    private fun applyRuntimeSnapshot(snapshot: RecordingRuntimeSnapshot) {
        recordingRuntime.value = snapshot
        when (snapshot.outcome) {
            RecordingOutcome.RecoveredSaved -> statusMessage.value = R.string.recording_recovered_saved
            RecordingOutcome.RecoveredRemoved -> statusMessage.value = R.string.recording_recovered_removed
            else -> Unit
        }
        if (snapshot.outcome == RecordingOutcome.RecoveredSaved || snapshot.outcome == RecordingOutcome.RecoveredRemoved) {
            serviceBinder?.acknowledgeOutcome()
        }
        if (snapshot.state is RecordingState.Countdown && backgroundWhenRecordingStarts) {
            backgroundWhenRecordingStarts = false
            moveTaskToBack(true)
            serviceBinder?.acknowledgeBackgrounded()
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

    private fun openApplicationSettings() {
        showMicrophonePermissionExplanation.value = false
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
    }

    private fun openStorageSettings() {
        storageDialog.value = null
        val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else statusMessage.value = R.string.storage_settings_unavailable
    }

    private fun String.toAudioMode(): AudioMode? =
        AudioMode.entries.firstOrNull {
            it.name == this && (it != AudioMode.DeviceAudio || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        }

    private fun AvailableVideoConfiguration.sameEncodingAs(other: AvailableVideoConfiguration): Boolean =
        encoderName == other.encoderName && resolution == other.resolution && frameRate == other.frameRate && bitrate == other.bitrate

    private companion object {
        const val STATE_PROJECTION_CONSENT_PENDING = "projection_consent_pending"
        const val STATE_NOTIFICATION_PERMISSION_PENDING = "notification_permission_pending"
        const val STATE_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        const val STATE_MICROPHONE_PERMISSION_PENDING = "microphone_permission_pending"
        const val STATE_MICROPHONE_PERMISSION_REQUESTED = "microphone_permission_requested"
        const val STATE_BACKGROUND_WHEN_RECORDING = "background_when_recording"
        const val STATE_SHOW_NOTIFICATION_EXPLANATION = "show_notification_explanation"
        const val STATE_SHOW_MICROPHONE_EXPLANATION = "show_microphone_explanation"
        const val STATE_SELECTED_AUDIO_MODE = "selected_audio_mode"
        const val STATE_REQUESTED_AUDIO_MODE = "requested_audio_mode"
        const val STATE_COUNTDOWN_SECONDS = "countdown_seconds"
        const val STATE_VIDEO_SHORT_EDGE = "video_short_edge"
        const val STATE_VIDEO_FRAME_RATE = "video_frame_rate"
        const val STATE_VIDEO_BITRATE = "video_bitrate"
    }
}
