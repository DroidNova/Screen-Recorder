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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
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
import com.droidnova.screenrecorder.recording.ScreenRecordingService
import com.droidnova.screenrecorder.recording.AvailableVideoConfiguration
import com.droidnova.screenrecorder.recording.AvcCapabilityProvider
import com.droidnova.screenrecorder.recording.RecordingStorage
import com.droidnova.screenrecorder.recording.StorageCheck
import com.droidnova.screenrecorder.recording.RecordingPreferences
import com.droidnova.screenrecorder.recording.RecordingPreferencesRepository
import com.droidnova.screenrecorder.ui.ScreenRecorderApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    private enum class StartStep { Explanation, NotificationPermission, NotificationDenied, AudioPermission, AudioDenied, Projection, Starting }
    private var pendingStartStep = mutableStateOf<StartStep?>(null)
    private var pendingVideo: AvailableVideoConfiguration? = null
    private var pendingCountdown = 0
    private lateinit var preferencesRepository: RecordingPreferencesRepository
    private var preferences = RecordingPreferences()
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
        if (pendingStartStep.value != StartStep.NotificationPermission) return@registerForActivityResult
        if (granted) continueAudioPermission() else pendingStartStep.value = StartStep.NotificationDenied
    }

    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        microphonePermissionPending = false
        if (pendingStartStep.value != StartStep.AudioPermission) return@registerForActivityResult
        if (granted) launchProjectionConsent() else pendingStartStep.value = StartStep.AudioDenied
    }

    private val projectionConsent = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        projectionConsentPending = false
        if (pendingStartStep.value != StartStep.Projection) return@registerForActivityResult
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            backgroundWhenRecordingStarts = false
            statusMessage.value = R.string.recording_consent_denied
            clearPendingStart()
            return@registerForActivityResult
        }
        val configuration = resources.configuration
        val density = resources.displayMetrics.density
        val width = (configuration.screenWidthDp * density).toInt()
        val height = (configuration.screenHeightDp * density).toInt()
        val video = pendingVideo ?: return@registerForActivityResult
        val settings = RecordingSettings(
            preset = video.preset,
            audioMode = requestedSessionAudioMode,
            resolution = video.resolution,
            frameRate = video.frameRate,
            videoBitrate = video.bitrate,
            countdown = if (pendingCountdown == 0) CountdownConfiguration.None else CountdownConfiguration.Duration(pendingCountdown),
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
            pendingStartStep.value = StartStep.Starting
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
        } catch (_: RuntimeException) {
            backgroundWhenRecordingStarts = false
            statusMessage.value = R.string.recording_start_failed
            clearPendingStart()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesRepository = RecordingPreferencesRepository(this)
        pendingStartStep.value = savedInstanceState?.getString(STATE_PENDING_START_STEP)?.let { runCatching { StartStep.valueOf(it) }.getOrNull() }
        requestedSessionAudioMode = savedInstanceState?.getString(STATE_REQUESTED_AUDIO_MODE)?.toAudioMode() ?: AudioMode.None
        pendingCountdown = savedInstanceState?.getInt(STATE_PENDING_COUNTDOWN) ?: 0
        val pendingShortEdge = savedInstanceState?.getInt(STATE_PENDING_SHORT_EDGE) ?: 0
        val pendingFps = savedInstanceState?.getInt(STATE_PENDING_FPS) ?: 0
        val pendingBitrate = savedInstanceState?.getInt(STATE_PENDING_BITRATE) ?: 0
        lifecycleScope.launch {
            preferencesRepository.preferences.collect { stored ->
                preferences = stored
                _selectedAudioMode.value = stored.audioMode
                countdownSeconds.value = stored.countdownSeconds
                val options = availableVideoConfigurations.value
                if (options.isNotEmpty()) selectedVideoConfiguration.value = resolvePreference(options, stored)
            }
        }
        projectionConsentPending = savedInstanceState?.getBoolean(STATE_PROJECTION_CONSENT_PENDING) == true
        notificationPermissionPending = savedInstanceState?.getBoolean(STATE_NOTIFICATION_PERMISSION_PENDING) == true
        notificationPermissionRequested = savedInstanceState?.getBoolean(STATE_NOTIFICATION_PERMISSION_REQUESTED) == true
        microphonePermissionPending = savedInstanceState?.getBoolean(STATE_MICROPHONE_PERMISSION_PENDING) == true
        microphonePermissionRequested = savedInstanceState?.getBoolean(STATE_MICROPHONE_PERMISSION_REQUESTED) == true
        backgroundWhenRecordingStarts = savedInstanceState?.getBoolean(STATE_BACKGROUND_WHEN_RECORDING) == true
        showNotificationPermissionExplanation.value = savedInstanceState?.getBoolean(STATE_SHOW_NOTIFICATION_EXPLANATION) == true
        showMicrophonePermissionExplanation.value = savedInstanceState?.getBoolean(STATE_SHOW_MICROPHONE_EXPLANATION) == true
        lifecycleScope.launch {
            val configuration = resources.configuration
            val density = resources.displayMetrics.density
            val width = (configuration.screenWidthDp * density).toInt()
            val height = (configuration.screenHeightDp * density).toInt()
            val options = withContext(Dispatchers.Default) { AvcCapabilityProvider.query(width, height) }
            availableVideoConfigurations.value = options
            pendingVideo = options.firstOrNull { it.shortEdge == pendingShortEdge && it.frameRate.framesPerSecond == pendingFps && it.bitrate.bitsPerSecond == pendingBitrate }
            if (pendingStartStep.value != null && pendingVideo == null) clearPendingStart()
            selectedVideoConfiguration.value = resolvePreference(options, preferences)
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
                pendingOutcome = runtime.outcome,
                onOutcomeShown = { serviceBinder?.acknowledgeOutcome(it) },
                onStartRecording = ::requestRecordingPermissions,
                onStopRecording = { serviceBinder?.requestStop() ?: startService(ScreenRecordingService.stopIntent(this)) },
                onPauseRecording = { serviceBinder?.requestPause() ?: startService(ScreenRecordingService.pauseIntent(this)) },
                onResumeRecording = { serviceBinder?.requestResume() ?: startService(ScreenRecordingService.resumeIntent(this)) },
                onTerminalStateShown = {
                    statusMessage.value = if (recordingRuntime.value.state is RecordingState.Completed) {
                        R.string.recording_completed
                    } else R.string.recording_failed
                    serviceBinder?.acknowledgeTerminal()
                },
                audioMode = audioMode,
                onAudioModeSelected = { lifecycleScope.launch { preferencesRepository.saveAudio(it) } },
                videoOptions = videoOptions,
                selectedVideo = selectedVideo,
                settingsValid = videoSettingsValid,
                onVideoSelected = { lifecycleScope.launch { preferencesRepository.saveVideo(it) } },
                countdownSeconds = countdown,
                onCountdownSelected = { lifecycleScope.launch { preferencesRepository.saveCountdown(it) } },
                onResetSettings = { lifecycleScope.launch { preferencesRepository.reset() } },
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
            pendingStartStep.value?.takeIf {
                it == StartStep.Explanation || it == StartStep.NotificationDenied || it == StartStep.AudioDenied
            }?.let { step ->
                ModalBottomSheet(onDismissRequest = { clearPendingStart() }) {
                    PermissionSheet(
                        step = step,
                        audioMode = requestedSessionAudioMode,
                        notificationGranted = hasPermission(Manifest.permission.POST_NOTIFICATIONS),
                        audioGranted = hasPermission(Manifest.permission.RECORD_AUDIO),
                        onContinue = {
                            when (step) {
                                StartStep.Explanation -> advancePendingStart()
                                StartStep.NotificationDenied -> continueAudioPermission()
                                else -> Unit
                            }
                        },
                        onWithoutAudio = {
                            requestedSessionAudioMode = AudioMode.None
                            launchProjectionConsent()
                        },
                        onSettings = { openApplicationSettingsForPending() },
                        onCancel = { clearPendingStart() },
                    )
                }
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
        outState.putString(STATE_REQUESTED_AUDIO_MODE, requestedSessionAudioMode.name)
        outState.putString(STATE_PENDING_START_STEP, pendingStartStep.value?.name)
        outState.putInt(STATE_PENDING_COUNTDOWN, pendingCountdown)
        pendingVideo?.let {
            outState.putInt(STATE_PENDING_SHORT_EDGE, it.shortEdge); outState.putInt(STATE_PENDING_FPS, it.frameRate.framesPerSecond)
            outState.putInt(STATE_PENDING_BITRATE, it.bitrate.bitsPerSecond)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        serviceBound = bindService(Intent(this, ScreenRecordingService::class.java), serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        when (pendingStartStep.value) {
            StartStep.NotificationDenied -> if (hasPermission(Manifest.permission.POST_NOTIFICATIONS)) continueAudioPermission()
            StartStep.AudioDenied -> if (hasPermission(Manifest.permission.RECORD_AUDIO)) launchProjectionConsent()
            else -> Unit
        }
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
            pendingVideo = selectedVideo
            pendingCountdown = countdownSeconds.value
            pendingStartStep.value = StartStep.Explanation
        }
    }

    private fun continueAudioPermission() {
        if (recordingRuntime.value.state != RecordingState.Idle) return
        if (requestedSessionAudioMode != AudioMode.None &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            if (preferences.audioRequestedBefore && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                pendingStartStep.value = StartStep.AudioDenied
            } else {
                lifecycleScope.launch { preferencesRepository.markAudioRequested() }
                microphonePermissionPending = true
                pendingStartStep.value = StartStep.AudioPermission
                microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            return
        }
        launchProjectionConsent()
    }

    private fun continueNotificationPermission() {
        if (recordingRuntime.value.state != RecordingState.Idle) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (preferences.notificationRequestedBefore && !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)) {
                pendingStartStep.value = StartStep.NotificationDenied
            } else {
                lifecycleScope.launch { preferencesRepository.markNotificationRequested() }
                notificationPermissionPending = true
                pendingStartStep.value = StartStep.NotificationPermission
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return
        }
        continueAudioPermission()
    }

    private fun launchProjectionConsent() {
        if (projectionConsentPending || recordingRuntime.value.state != RecordingState.Idle) return
        projectionConsentPending = true
        pendingStartStep.value = StartStep.Projection
        statusMessage.value = null
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionConsent.launch(manager.createScreenCaptureIntent())
    }

    private fun requestInProgress(): Boolean =
        pendingStartStep.value != null || storagePreflightPending || microphonePermissionPending || notificationPermissionPending || projectionConsentPending ||
            showMicrophonePermissionExplanation.value || showNotificationPermissionExplanation.value

    private fun applyRuntimeSnapshot(snapshot: RecordingRuntimeSnapshot) {
        recordingRuntime.value = snapshot
        if (snapshot.state is RecordingState.Countdown) {
            serviceBinder?.acknowledgeBackgrounded()
        } else if (snapshot.state is RecordingState.Recording && pendingStartStep.value == StartStep.Starting) {
            clearPendingStart()
            moveTaskToBack(true)
        } else if (
            snapshot.state == RecordingState.Idle ||
            snapshot.state is RecordingState.Failed ||
            snapshot.state is RecordingState.Completed
        ) {
            backgroundWhenRecordingStarts = false
            if (pendingStartStep.value == StartStep.Starting) clearPendingStart()
        }
    }

    private fun advancePendingStart() {
        if (pendingStartStep.value != StartStep.Explanation) return
        continueNotificationPermission()
    }

    private fun clearPendingStart() {
        pendingStartStep.value = null; pendingVideo = null; pendingCountdown = 0
        projectionConsentPending = false; notificationPermissionPending = false; microphonePermissionPending = false
    }

    private fun hasPermission(permission: String): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && permission == Manifest.permission.POST_NOTIFICATIONS ||
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun openApplicationSettingsForPending() {
        try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))) }
        catch (_: android.content.ActivityNotFoundException) { statusMessage.value = R.string.recording_start_failed }
    }

    private fun resolvePreference(options: List<AvailableVideoConfiguration>, stored: RecordingPreferences): AvailableVideoConfiguration? =
        if (stored.preset == com.droidnova.screenrecorder.domain.recording.RecordingPreset.Custom) options.firstOrNull {
            it.shortEdge == stored.customShortEdge && it.frameRate.framesPerSecond == stored.customFps && it.bitrate.bitsPerSecond == stored.customBitrate
        }?.copy(preset = stored.preset) else options.firstOrNull { it.preset == stored.preset }
            ?: options.firstOrNull { it.preset == com.droidnova.screenrecorder.domain.recording.RecordingPreset.Balanced }

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

    @androidx.compose.runtime.Composable
    private fun PermissionSheet(
        step: StartStep,
        audioMode: AudioMode,
        notificationGranted: Boolean,
        audioGranted: Boolean,
        onContinue: () -> Unit,
        onWithoutAudio: () -> Unit,
        onSettings: () -> Unit,
        onCancel: () -> Unit,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.permission_sheet_title), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.permission_sheet_supporting))
            Text(stringResource(R.string.screen_capture_permission), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.screen_capture_permission_detail))
            Text(stringResource(R.string.permission_required_every_recording), color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(stringResource(R.string.notifications), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.notification_permission_detail))
                Text(stringResource(if (notificationGranted) R.string.permission_allowed else if (step == StartStep.NotificationDenied) R.string.permission_off_settings else R.string.permission_required))
            }
            Text(stringResource(if (audioMode == AudioMode.Microphone) R.string.audio_microphone else if (audioMode == AudioMode.DeviceAudio) R.string.audio_device else R.string.audio), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(stringResource(if (audioMode == AudioMode.None) R.string.audio_not_used_detail else if (audioMode == AudioMode.Microphone) R.string.microphone_permission_detail else R.string.device_audio_permission_detail))
            Text(stringResource(if (audioMode == AudioMode.None) R.string.permission_not_used else if (audioGranted) R.string.permission_allowed else if (step == StartStep.AudioDenied) R.string.permission_off_settings else R.string.permission_required))
            Text(stringResource(R.string.permission_privacy_note), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            if (step == StartStep.AudioDenied) {
                TextButton(onClick = onWithoutAudio) { Text(stringResource(R.string.record_without_audio)) }
                TextButton(onClick = onSettings) { Text(stringResource(R.string.open_settings)) }
            } else if (step == StartStep.NotificationDenied) {
                TextButton(onClick = onContinue) { Text(stringResource(R.string.continue_without_notification)) }
                TextButton(onClick = onSettings) { Text(stringResource(R.string.open_settings)) }
            } else {
                TextButton(onClick = onContinue) { Text(stringResource(R.string.continue_label)) }
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
        }
    }

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
        const val STATE_PENDING_START_STEP = "pending_start_step"
        const val STATE_PENDING_COUNTDOWN = "pending_countdown"
        const val STATE_PENDING_SHORT_EDGE = "pending_short_edge"
        const val STATE_PENDING_FPS = "pending_fps"
        const val STATE_PENDING_BITRATE = "pending_bitrate"
    }
}
