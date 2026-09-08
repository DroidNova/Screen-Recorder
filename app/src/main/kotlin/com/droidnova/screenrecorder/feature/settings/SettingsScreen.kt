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

@Composable fun SettingsScreen(
    audioMode: AudioMode = AudioMode.None,
    audioModeEnabled: Boolean = true,
    onAudioModeSelected: (AudioMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(Spacing.Page), verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Text(stringResource(R.string.settings_supporting), color = MaterialTheme.colorScheme.onSurfaceVariant)
        CaptureSection(audioMode, audioModeEnabled, onAudioModeSelected)
        Section(R.string.appearance, listOf(R.string.app_theme to R.string.system_default))
        Section(R.string.about, listOf(R.string.version to R.string.version_value))
    }
}

@Composable
private fun CaptureSection(audioMode: AudioMode, enabled: Boolean, onSelected: (AudioMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(stringResource(R.string.capture), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.Card), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.capture_preset), modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.balanced), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.audio), style = MaterialTheme.typography.titleMedium)
                AudioMode.entries.filter { it != AudioMode.DeviceAudio }.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = audioMode == mode,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelected(mode) },
                        ).padding(vertical = Spacing.Small),
                    ) {
                        RadioButton(selected = audioMode == mode, onClick = null, enabled = enabled)
                        Text(
                            stringResource(if (mode == AudioMode.None) R.string.audio_none else R.string.audio_microphone),
                            modifier = Modifier.padding(start = Spacing.Small),
                        )
                    }
                }
            }
        }
    }
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
