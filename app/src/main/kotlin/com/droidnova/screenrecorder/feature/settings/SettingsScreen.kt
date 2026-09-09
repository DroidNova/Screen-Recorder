package com.droidnova.screenrecorder.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.domain.recording.RecordingPreset
import com.droidnova.screenrecorder.recording.AvailableVideoConfiguration
import com.droidnova.screenrecorder.ui.theme.ScreenRecorderTheme

private enum class SettingsSheet { Quality, Audio, Countdown, Resolution, FrameRate, Bitrate }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    audioMode: AudioMode = AudioMode.None,
    audioModeEnabled: Boolean = true,
    deviceAudioAvailable: Boolean = true,
    countdownSeconds: Int = 0,
    onCountdownSelected: (Int) -> Unit = {},
    onAudioModeSelected: (AudioMode) -> Unit = {},
    videoOptions: List<AvailableVideoConfiguration> = emptyList(),
    selectedVideo: AvailableVideoConfiguration? = null,
    onVideoSelected: (AvailableVideoConfiguration) -> Unit = {},
    onNotificationSettings: () -> Unit = {},
    onApplicationSettings: () -> Unit = {},
    onReset: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    var showReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationAllowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val microphoneAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val enabled = audioModeEnabled

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 4.dp).semantics { heading() }) }
        item {
            SettingsGroup(R.string.capture) {
                CompactSettingsRow(R.string.quality, selectedVideo?.preset?.labelResource(), enabled = enabled) { sheet = SettingsSheet.Quality }
                DividerRow()
                CompactSettingsRow(R.string.audio, audioMode.labelResource(), enabled = enabled) { sheet = SettingsSheet.Audio }
                DividerRow()
                CompactSettingsRow(R.string.countdown, if (countdownSeconds == 0) R.string.countdown_off else null,
                    valueText = if (countdownSeconds == 0) null else stringResource(R.string.countdown_seconds, countdownSeconds), enabled = enabled) { sheet = SettingsSheet.Countdown }
                if (selectedVideo?.preset == RecordingPreset.Custom) {
                    DividerRow(); CompactSettingsRow(R.string.resolution, valueText = stringResource(R.string.resolution_label, selectedVideo.shortEdge), enabled = enabled) { sheet = SettingsSheet.Resolution }
                    DividerRow(); CompactSettingsRow(R.string.frame_rate, valueText = stringResource(R.string.fps_value_uppercase, selectedVideo.frameRate.framesPerSecond), enabled = enabled) { sheet = SettingsSheet.FrameRate }
                    DividerRow(); CompactSettingsRow(R.string.video_bitrate, valueText = stringResource(R.string.bitrate_value, selectedVideo.bitrate.bitsPerSecond / 1_000_000), enabled = enabled) { sheet = SettingsSheet.Bitrate }
                }
            }
        }
        item {
            SettingsGroup(R.string.permissions) {
                CompactSettingsRow(R.string.notifications, if (notificationAllowed) R.string.permission_allowed else R.string.not_allowed, onClick = onNotificationSettings)
                DividerRow()
                CompactSettingsRow(R.string.audio_microphone, if (microphoneAllowed) R.string.permission_allowed else R.string.not_allowed, onClick = onApplicationSettings)
                DividerRow()
                CompactSettingsRow(R.string.screen_capture_permission, summary = stringResource(R.string.screen_capture_settings_detail))
            }
        }
        item {
            SettingsGroup(R.string.storage) {
                CompactSettingsRow(R.string.recording_location, summary = stringResource(R.string.recording_location_value))
            }
        }
        item {
            SettingsGroup(R.string.about) {
                val versionName = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
                CompactSettingsRow(R.string.version, valueText = versionName)
                DividerRow()
                CompactSettingsRow(R.string.reset_recording_settings, onClick = { showReset = true })
            }
        }
        if (!enabled) item { Text(stringResource(R.string.applies_next_recording), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }

    sheet?.let { current ->
        SelectorSheet(current, audioMode, deviceAudioAvailable, countdownSeconds, videoOptions, selectedVideo, onDismiss = { sheet = null }) { audio, seconds, video ->
            audio?.let(onAudioModeSelected); seconds?.let(onCountdownSelected); video?.let(onVideoSelected); sheet = null
        }
    }
    if (showReset) AlertDialog(onDismissRequest = { showReset = false }, title = { Text(stringResource(R.string.reset_recording_settings_question)) },
        text = { Text(stringResource(R.string.reset_recording_settings_detail)) },
        confirmButton = { TextButton({ showReset = false; onReset() }) { Text(stringResource(R.string.reset)) } },
        dismissButton = { TextButton({ showReset = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun SettingsGroup(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() })
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(content = content)
        }
    }
}

@Composable
private fun CompactSettingsRow(
    title: Int,
    value: Int? = null,
    valueText: String? = null,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = if (summary == null) 56.dp else 68.dp)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            summary?.let { Text(it, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        val shownValue = valueText ?: value?.let { stringResource(it) }
        shownValue?.let {
            Spacer(Modifier.width(12.dp)); Text(it, Modifier.widthIn(max = 148.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Text("›", modifier = Modifier.width(48.dp), fontSize = 28.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

@Composable private fun DividerRow() = Divider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorSheet(
    sheet: SettingsSheet,
    audioMode: AudioMode,
    deviceAudioAvailable: Boolean,
    countdown: Int,
    options: List<AvailableVideoConfiguration>,
    selected: AvailableVideoConfiguration?,
    onDismiss: () -> Unit,
    choose: (AudioMode?, Int?, AvailableVideoConfiguration?) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(sheet.title()), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp).semantics { heading() })
            when (sheet) {
                SettingsSheet.Audio -> AudioMode.entries.filter { it != AudioMode.DeviceAudio || deviceAudioAvailable }.forEach { mode ->
                    ChoiceRow(stringResource(mode.labelResource()), mode == audioMode, mode == AudioMode.DeviceAudio, { choose(mode, null, null) })
                }
                SettingsSheet.Countdown -> listOf(0, 3, 5, 15).forEach { seconds ->
                    ChoiceRow(if (seconds == 0) stringResource(R.string.countdown_off) else stringResource(R.string.countdown_seconds, seconds), seconds == countdown, false) { choose(null, seconds, null) }
                }
                else -> sheet.videoChoices(options, selected).forEach { option ->
                    val chosen = when (sheet) {
                        SettingsSheet.Quality -> selected?.preset == option.preset
                        SettingsSheet.Resolution -> selected?.shortEdge == option.shortEdge
                        SettingsSheet.FrameRate -> selected?.frameRate == option.frameRate
                        SettingsSheet.Bitrate -> selected?.bitrate == option.bitrate
                        else -> false
                    }
                    val label = when (sheet) {
                        SettingsSheet.Quality -> stringResource(option.preset.labelResource())
                        SettingsSheet.Resolution -> stringResource(R.string.resolution_label, option.shortEdge)
                        SettingsSheet.FrameRate -> stringResource(R.string.fps_value_uppercase, option.frameRate.framesPerSecond)
                        else -> stringResource(R.string.bitrate_value, option.bitrate.bitsPerSecond / 1_000_000)
                    }
                    ChoiceRow(label, chosen, false) { choose(null, null, option) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable private fun ChoiceRow(label: String, selected: Boolean, deviceNote: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).selectable(selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, null, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
        Column(Modifier.padding(start = 8.dp)) {
            Text(label)
            if (deviceNote) Text(stringResource(R.string.device_audio_supporting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun SettingsSheet.title() = when (this) {
    SettingsSheet.Quality -> R.string.quality; SettingsSheet.Audio -> R.string.audio; SettingsSheet.Countdown -> R.string.countdown
    SettingsSheet.Resolution -> R.string.resolution; SettingsSheet.FrameRate -> R.string.frame_rate; SettingsSheet.Bitrate -> R.string.video_bitrate
}
private fun SettingsSheet.videoChoices(options: List<AvailableVideoConfiguration>, selected: AvailableVideoConfiguration?) = when (this) {
    SettingsSheet.Quality -> (options.filter { it.preset != RecordingPreset.Custom } +
        listOfNotNull(selected?.takeIf { it.preset == RecordingPreset.Custom })).distinctBy { it.preset }
    SettingsSheet.Resolution -> options.distinctBy { it.shortEdge }.map { it.copy(preset = RecordingPreset.Custom) }
    SettingsSheet.FrameRate -> options.filter { selected == null || it.shortEdge == selected.shortEdge }.distinctBy { it.frameRate }.map { it.copy(preset = RecordingPreset.Custom, bitrate = selected?.bitrate ?: it.bitrate) }
    SettingsSheet.Bitrate -> options.filter { selected == null || it.shortEdge == selected.shortEdge && it.frameRate == selected.frameRate }.distinctBy { it.bitrate }.map { it.copy(preset = RecordingPreset.Custom) }
    else -> emptyList()
}
private fun AudioMode.labelResource() = when (this) { AudioMode.None -> R.string.audio_none; AudioMode.Microphone -> R.string.audio_microphone; AudioMode.DeviceAudio -> R.string.audio_device }
private fun RecordingPreset.labelResource() = when (this) { RecordingPreset.DataSaver -> R.string.preset_data_saver; RecordingPreset.Balanced -> R.string.balanced; RecordingPreset.HighQuality -> R.string.preset_high_quality; RecordingPreset.Custom -> R.string.preset_custom }

@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable private fun SettingsPreview() { ScreenRecorderTheme { SettingsScreen() } }
