package com.droidnova.screenrecorder.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import android.os.Environment
import android.os.StatFs
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.ui.theme.ScreenRecorderTheme
import com.droidnova.screenrecorder.ui.theme.Spacing
import com.droidnova.screenrecorder.domain.recording.RecordingState
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.domain.recording.RecordingPreset
import com.droidnova.screenrecorder.recording.AvailableVideoConfiguration

@Composable
fun HomeScreen(
    recordingState: RecordingState = RecordingState.Idle,
    elapsedSeconds: Long = 0,
    countdownRemainingSeconds: Int? = null,
    statusMessage: String? = null,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    videoOptions: List<AvailableVideoConfiguration> = emptyList(),
    selectedVideo: AvailableVideoConfiguration? = null,
    onVideoSelected: (AvailableVideoConfiguration) -> Unit = {},
    audioMode: AudioMode = AudioMode.None,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val availableGigabytes = remember {
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let { directory ->
            StatFs(directory.path).availableBytes / (1024L * 1024L * 1024L)
        }
    }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(Spacing.Page),
        verticalArrangement = Arrangement.spacedBy(Spacing.Section),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            Text(stringResource(R.string.home_heading), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.home_supporting), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.Card), verticalArrangement = Arrangement.spacedBy(Spacing.Component)) {
                Text(stringResource(R.string.capture_summary), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                val editable = recordingState == RecordingState.Idle
                val presetOptions = RecordingPreset.entries.mapNotNull { preset ->
                    videoOptions.filter { it.preset == preset }.maxByOrNull { it.frameRate.framesPerSecond }
                } + videoOptions.filter { option ->
                    selectedVideo?.let { option.shortEdge == it.shortEdge && option.frameRate == it.frameRate } == true
                }
                SelectableSummaryRow(
                    R.string.quality,
                    selectedVideo?.let { stringResource(R.string.bitrate_value, it.bitrate.bitsPerSecond / 1_000_000) } ?: "—",
                    presetOptions.distinct(),
                    editable,
                    onVideoSelected,
                ) { option ->
                    "${stringResource(option.preset.labelResource())} · ${stringResource(R.string.bitrate_value, option.bitrate.bitsPerSecond / 1_000_000)}"
                }
                SelectableSummaryRow(
                    R.string.resolution,
                    selectedVideo?.let { stringResource(R.string.resolution_label, it.shortEdge) } ?: "—",
                    videoOptions.filter { option ->
                        selectedVideo?.let { option.frameRate == it.frameRate && option.bitrate == it.bitrate } == true
                    }.distinctBy { it.shortEdge },
                    editable,
                    onVideoSelected,
                ) { stringResource(R.string.resolution_label, it.shortEdge) }
                SelectableSummaryRow(
                    R.string.frame_rate,
                    selectedVideo?.let { stringResource(R.string.fps_value, it.frameRate.framesPerSecond) } ?: "—",
                    videoOptions.filter { option ->
                        selectedVideo?.let { option.shortEdge == it.shortEdge && option.bitrate == it.bitrate } == true
                    }.distinctBy { it.frameRate },
                    editable,
                    onVideoSelected,
                ) { stringResource(R.string.fps_value, it.frameRate.framesPerSecond) }
                SummaryRow(
                    R.string.storage,
                    availableGigabytes?.let { stringResource(R.string.available_storage, it) }
                        ?: stringResource(R.string.app_storage),
                )
                selectedVideo?.let {
                    val audioBitrate = if (audioMode == AudioMode.None) 0L else 128_000L
                    val megabytesPerMinute = ((it.bitrate.bitsPerSecond.toLong() + audioBitrate) * 60L * 105L) /
                        (8L * 100L * 1_000_000L)
                    Text(
                        stringResource(R.string.estimated_size, megabytesPerMinute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (recordingState is RecordingState.Recording) {
            Text(
                stringResource(R.string.elapsed_time_format, elapsedSeconds / 60, elapsedSeconds % 60),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        if (recordingState is RecordingState.Countdown) {
            Text(
                stringResource(R.string.recording_countdown_notification, countdownRemainingSeconds ?: 0),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.Small), modifier = Modifier.fillMaxWidth()) {
            val control = recordingState.availableRecordingControl()
            val idle = control == RecordingControl.Start
            val recording = control == RecordingControl.Stop
            Button(
                onClick = if (recording) onStopRecording else onStartRecording,
                enabled = idle || recording,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(painterResource(R.drawable.ic_record), contentDescription = null)
                Text(
                    stringResource(if (recording) R.string.stop_recording else if (idle) R.string.start_recording else R.string.recording_preparing),
                    modifier = Modifier.padding(start = Spacing.Small),
                )
            }
            statusMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun SummaryRow(label: Int, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SelectableSummaryRow(
    label: Int,
    value: String,
    options: List<AvailableVideoConfiguration>,
    enabled: Boolean,
    onSelected: (AvailableVideoConfiguration) -> Unit,
    optionLabel: @Composable (AvailableVideoConfiguration) -> String,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        androidx.compose.material3.TextButton(
            onClick = { expanded = true },
            enabled = enabled && options.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { expanded = false; onSelected(option) },
                )
            }
        }
    }
}

private fun RecordingPreset.labelResource(): Int = when (this) {
    RecordingPreset.DataSaver -> R.string.preset_data_saver
    RecordingPreset.Balanced -> R.string.balanced
    RecordingPreset.HighQuality -> R.string.preset_high_quality
    RecordingPreset.Custom -> R.string.preset_custom
}

@Preview(name = "Home light", showBackground = true)
@Preview(name = "Home dark", showBackground = true, uiMode = 0x20)
@Preview(name = "Home large text", showBackground = true, fontScale = 2f)
@Composable private fun HomePreview() { ScreenRecorderTheme { HomeScreen() } }
