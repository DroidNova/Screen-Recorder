package com.droidnova.screenrecorder.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.ui.theme.ScreenRecorderTheme
import com.droidnova.screenrecorder.ui.theme.Spacing
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.domain.recording.RecordingPreset
import com.droidnova.screenrecorder.recording.AvailableVideoConfiguration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext

@Composable fun SettingsScreen(
    audioMode: AudioMode = AudioMode.None,
    audioModeEnabled: Boolean = true,
    deviceAudioAvailable: Boolean = true,
    countdownSeconds: Int = 0,
    onCountdownSelected: (Int) -> Unit = {},
    onAudioModeSelected: (AudioMode) -> Unit = {},
    videoOptions: List<AvailableVideoConfiguration> = emptyList(),
    selectedVideo: AvailableVideoConfiguration? = null,
    onVideoSelected: (AvailableVideoConfiguration) -> Unit = {},
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.Page), verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        CaptureSection(
            audioMode, audioModeEnabled, deviceAudioAvailable, onAudioModeSelected,
            countdownSeconds, onCountdownSelected,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            Text(stringResource(R.string.recording_defaults), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(Spacing.Standard)) {
            Text(stringResource(R.string.quality), style = MaterialTheme.typography.titleMedium)
            RecordingPreset.entries.forEach { preset ->
                val option = videoOptions.firstOrNull { it.preset == preset }
                if (option != null) Row(Modifier.fillMaxWidth().selectable(selectedVideo?.preset == preset, enabled = audioModeEnabled, role = Role.RadioButton) { onVideoSelected(option) }.padding(Spacing.Small)) {
                    RadioButton(selectedVideo?.preset == preset, null, enabled = audioModeEnabled)
                    Text(stringResource(preset.labelResource()), Modifier.padding(start = Spacing.Small))
                }
            }
            } }
        }
        Section(R.string.permissions, listOf(
            R.string.notifications to if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < 33) R.string.permission_allowed else R.string.not_allowed,
            R.string.audio_microphone to if (audioMode == AudioMode.None) R.string.not_required_audio else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) R.string.permission_allowed else R.string.not_allowed,
            R.string.screen_capture_permission to R.string.screen_capture_settings_detail,
        ))
        Section(R.string.app_name, listOf(R.string.recording_location to R.string.recording_location_value))
        Card(Modifier.fillMaxWidth()) { TextButton(onClick = { showReset = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reset_recording_settings)) } }
        val versionName = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
        Text("${stringResource(R.string.app_name)} · ${stringResource(R.string.version)} $versionName")
        if (!audioModeEnabled) Text(stringResource(R.string.applies_next_recording), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showReset) AlertDialog(onDismissRequest = { showReset = false }, title = { Text(stringResource(R.string.reset_recording_settings_question)) },
        text = { Text(stringResource(R.string.reset_recording_settings_detail)) },
        confirmButton = { TextButton({ showReset = false; onReset() }) { Text(stringResource(R.string.reset)) } },
        dismissButton = { TextButton({ showReset = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun CaptureSection(
    audioMode: AudioMode,
    enabled: Boolean,
    deviceAudioAvailable: Boolean,
    onSelected: (AudioMode) -> Unit,
    countdownSeconds: Int,
    onCountdownSelected: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(stringResource(R.string.capture), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.Card), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.capture_preset), modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.balanced), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.audio), style = MaterialTheme.typography.titleMedium)
                AudioMode.entries.forEach { mode ->
                    val modeEnabled = enabled && (mode != AudioMode.DeviceAudio || deviceAudioAvailable)
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = audioMode == mode,
                            enabled = modeEnabled,
                            role = Role.RadioButton,
                            onClick = { onSelected(mode) },
                        ).padding(vertical = Spacing.Small),
                    ) {
                        RadioButton(selected = audioMode == mode, onClick = null, enabled = modeEnabled)
                        Column(Modifier.padding(start = Spacing.Small)) {
                            Text(stringResource(mode.labelResource()))
                            if (mode == AudioMode.DeviceAudio) {
                                Text(
                                    stringResource(R.string.device_audio_supporting),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Text(stringResource(R.string.countdown), style = MaterialTheme.typography.titleMedium)
                listOf(0, 3, 5, 15).forEach { seconds ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = countdownSeconds == seconds,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onCountdownSelected(seconds) },
                        ).padding(vertical = Spacing.Small),
                    ) {
                        RadioButton(selected = countdownSeconds == seconds, onClick = null, enabled = enabled)
                        Text(
                            if (seconds == 0) stringResource(R.string.countdown_off)
                            else stringResource(R.string.countdown_seconds, seconds),
                            modifier = Modifier.padding(start = Spacing.Small),
                        )
                    }
                }
            }
        }
    }
}

private fun AudioMode.labelResource(): Int = when (this) {
    AudioMode.None -> R.string.audio_none
    AudioMode.Microphone -> R.string.audio_microphone
    AudioMode.DeviceAudio -> R.string.audio_device
}

private fun RecordingPreset.labelResource(): Int = when (this) {
    RecordingPreset.DataSaver -> R.string.preset_data_saver
    RecordingPreset.Balanced -> R.string.balanced
    RecordingPreset.HighQuality -> R.string.preset_high_quality
    RecordingPreset.Custom -> R.string.preset_custom
}

@Composable private fun Section(title: Int, rows: List<Pair<Int, Int>>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(Spacing.Card), verticalArrangement = Arrangement.spacedBy(Spacing.Component)) {
            rows.forEach { (label, value) -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(label), modifier = Modifier.weight(1f)); Text(stringResource(value), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        } }
    }
}

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Preview(showBackground = true, widthDp = 900, heightDp = 600)
@Composable private fun SettingsPreview() { ScreenRecorderTheme { SettingsScreen() } }
