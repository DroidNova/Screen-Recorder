package com.droidnova.screenrecorder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
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
import com.droidnova.screenrecorder.feature.home.HomeScreen
import com.droidnova.screenrecorder.feature.recordings.RecordingsScreen
import com.droidnova.screenrecorder.feature.settings.SettingsScreen
import com.droidnova.screenrecorder.ui.navigation.TopLevelDestination
import com.droidnova.screenrecorder.ui.theme.ScreenRecorderTheme
import com.droidnova.screenrecorder.domain.recording.RecordingState
import com.droidnova.screenrecorder.domain.recording.AudioMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenRecorderApp(
    recordingState: RecordingState = RecordingState.Idle,
    elapsedSeconds: Long = 0,
    statusMessage: Int? = null,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onTerminalStateShown: () -> Unit = {},
    audioMode: AudioMode = AudioMode.None,
    onAudioModeSelected: (AudioMode) -> Unit = {},
) {
    ScreenRecorderTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: TopLevelDestination.Home.route
        val current = TopLevelDestination.entries.firstOrNull { it.route == currentRoute } ?: TopLevelDestination.Home
        LaunchedEffect(recordingState) {
            if (recordingState is RecordingState.Completed || recordingState is RecordingState.Failed) onTerminalStateShown()
        }

        NavigationSuiteScaffold(
            modifier = Modifier.fillMaxSize(),
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentRoute == destination.route
                    item(
                        selected = selected,
                        onClick = {
                            if (!selected) navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            androidx.compose.material3.Icon(
                                painter = painterResource(if (selected) destination.selectedIcon else destination.unselectedIcon),
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            },
        ) {
            androidx.compose.material3.Scaffold(
                modifier = Modifier.safeDrawingPadding(),
                topBar = { TopAppBar(title = { Text(stringResource(current.label)) }) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    NavHost(
                        navController = navController,
                        startDestination = TopLevelDestination.Home.route,
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    ) {
                        composable(TopLevelDestination.Home.route) {
                            HomeScreen(
                                recordingState = recordingState,
                                elapsedSeconds = elapsedSeconds,
                                statusMessage = statusMessage?.let { stringResource(it) },
                                onStartRecording = onStartRecording,
                                onStopRecording = onStopRecording,
                            )
                        }
                        composable(TopLevelDestination.Recordings.route) { RecordingsScreen() }
                        composable(TopLevelDestination.Settings.route) {
                            SettingsScreen(
                                audioMode = audioMode,
                                audioModeEnabled = recordingState == RecordingState.Idle,
                                deviceAudioAvailable = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q,
                                onAudioModeSelected = onAudioModeSelected,
                            )
                        }
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
