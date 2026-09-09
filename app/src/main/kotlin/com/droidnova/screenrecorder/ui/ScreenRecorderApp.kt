package com.droidnova.screenrecorder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.droidnova.screenrecorder.feature.settings.SettingsScreen
import com.droidnova.screenrecorder.feature.recordings.RecordingsScreen
import com.droidnova.screenrecorder.ui.navigation.TopLevelDestination
import com.droidnova.screenrecorder.ui.theme.ScreenRecorderTheme
import com.droidnova.screenrecorder.domain.recording.RecordingState
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.recording.AvailableVideoConfiguration
import com.droidnova.screenrecorder.recording.PendingRecordingOutcome
import com.droidnova.screenrecorder.recording.RecordingOutcomeType
import com.droidnova.screenrecorder.ui.theme.RecorderNavigation
import com.droidnova.screenrecorder.ui.theme.RecorderPrimary
import com.droidnova.screenrecorder.ui.theme.RecorderPrimaryBright
import com.droidnova.screenrecorder.ui.theme.RecorderTextSecondary
import kotlinx.coroutines.launch

@Composable
fun ScreenRecorderApp(
    recordingState: RecordingState = RecordingState.Idle,
    elapsedSeconds: Long = 0,
    countdownRemainingSeconds: Int? = null,
    availableStorageBytes: Long? = null,
    statusMessage: Int? = null,
    onStatusMessageShown: () -> Unit = {},
    onStatusMessageAction: () -> Unit = {},
    onNotificationSettings: () -> Unit = {},
    onApplicationSettings: () -> Unit = {},
    pendingOutcome: PendingRecordingOutcome? = null,
    onOutcomeShown: (Long) -> Unit = {},
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onPauseRecording: () -> Unit = {},
    onResumeRecording: () -> Unit = {},
    onTerminalStateShown: () -> Unit = {},
    audioMode: AudioMode = AudioMode.None,
    onAudioModeSelected: (AudioMode) -> Unit = {},
    videoOptions: List<AvailableVideoConfiguration> = emptyList(),
    selectedVideo: AvailableVideoConfiguration? = null,
    settingsValid: Boolean = false,
    onVideoSelected: (AvailableVideoConfiguration) -> Unit = {},
    countdownSeconds: Int = 0,
    onCountdownSelected: (Int) -> Unit = {},
    onResetSettings: () -> Unit = {},
) {
    ScreenRecorderTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: TopLevelDestination.Home.route
        val snackbarHostState = remember { SnackbarHostState() }
        val snackbarScope = rememberCoroutineScope()
        val settingsResetMessage = stringResource(com.droidnova.screenrecorder.R.string.recording_settings_reset)
        val transientMessage = statusMessage?.let { stringResource(it) }
        val permissionMessage = statusMessage == com.droidnova.screenrecorder.R.string.notification_permission_required_message ||
            statusMessage == com.droidnova.screenrecorder.R.string.microphone_permission_required_message
        val settingsAction = stringResource(com.droidnova.screenrecorder.R.string.open_settings)
        val outcomeMessage = pendingOutcome?.let {
            stringResource(
                when (it.type) {
                    RecordingOutcomeType.IncompleteRecordingRemoved -> com.droidnova.screenrecorder.R.string.recording_recovered_removed
                    RecordingOutcomeType.RecoveredRecordingSaved -> com.droidnova.screenrecorder.R.string.recording_recovered_saved
                    RecordingOutcomeType.StorageLow -> com.droidnova.screenrecorder.R.string.recording_storage_low_stopped
                    RecordingOutcomeType.FinalizationFailed -> com.droidnova.screenrecorder.R.string.recording_failed
                },
            )
        }
        LaunchedEffect(pendingOutcome?.id) {
            val outcome = pendingOutcome ?: return@LaunchedEffect
            val message = outcomeMessage ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
            onOutcomeShown(outcome.id)
        }
        LaunchedEffect(recordingState) {
            if (recordingState is RecordingState.Completed || recordingState is RecordingState.Failed) onTerminalStateShown()
        }
        LaunchedEffect(statusMessage) {
            if (statusMessage == null || transientMessage == null) return@LaunchedEffect
            val result = snackbarHostState.showSnackbar(
                message = transientMessage,
                actionLabel = if (permissionMessage) settingsAction else null,
                duration = SnackbarDuration.Long,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onStatusMessageAction()
            onStatusMessageShown()
        }

        androidx.compose.material3.Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(containerColor = RecorderNavigation) {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { androidx.compose.material3.Icon(painterResource(if (selected) destination.selectedIcon else destination.unselectedIcon), null) },
                            label = { Text(stringResource(destination.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RecorderPrimaryBright,
                                selectedTextColor = RecorderPrimaryBright,
                                indicatorColor = RecorderPrimary.copy(alpha = 0.16f),
                                unselectedIconColor = RecorderTextSecondary,
                                unselectedTextColor = RecorderTextSecondary,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    NavHost(
                        navController = navController,
                        startDestination = TopLevelDestination.Home.route,
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    ) {
                        composable(TopLevelDestination.Home.route) {
                            com.droidnova.screenrecorder.feature.home.HomeScreen(
                                recordingState = recordingState,
                                elapsedSeconds = elapsedSeconds,
                                countdownRemainingSeconds = countdownRemainingSeconds,
                                availableStorageBytes = availableStorageBytes,
                                onStartRecording = onStartRecording,
                                onStopRecording = onStopRecording,
                                onPauseRecording = onPauseRecording,
                                onResumeRecording = onResumeRecording,
                                videoOptions = videoOptions,
                                selectedVideo = selectedVideo,
                                settingsValid = settingsValid,
                                onVideoSelected = onVideoSelected,
                                audioMode = audioMode,
                            )
                        }
                        composable(TopLevelDestination.Recordings.route) {
                            RecordingsScreen(showMessage = { message ->
                                snackbarScope.launch { snackbarHostState.showSnackbar(message) }
                            })
                        }
                        composable(TopLevelDestination.Settings.route) {
                            SettingsScreen(
                                audioMode = audioMode,
                                audioModeEnabled = recordingState == RecordingState.Idle,
                                deviceAudioAvailable = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q,
                                onAudioModeSelected = onAudioModeSelected,
                                countdownSeconds = countdownSeconds,
                                onCountdownSelected = onCountdownSelected,
                                videoOptions = videoOptions,
                                selectedVideo = selectedVideo,
                                onVideoSelected = onVideoSelected,
                                onNotificationSettings = onNotificationSettings,
                                onApplicationSettings = onApplicationSettings,
                                onReset = {
                                    onResetSettings()
                                    snackbarScope.launch { snackbarHostState.showSnackbar(settingsResetMessage) }
                                },
                            )
                        }
                    }
                }
        }
    }
}

@Preview(name = "Compact", widthDp = 360, heightDp = 800)
@Preview(name = "Landscape medium", widthDp = 700, heightDp = 400)
@Preview(name = "Expanded", widthDp = 1200, heightDp = 800)
@Composable
private fun AppShellPreview() {
    ScreenRecorderApp()
}
