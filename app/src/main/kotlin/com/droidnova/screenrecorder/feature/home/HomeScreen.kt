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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
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
    availableStorageBytes: Long? = null,
    statusMessage: String? = null,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onPauseRecording: () -> Unit = {},
    onResumeRecording: () -> Unit = {},
    videoOptions: List<AvailableVideoConfiguration> = emptyList(),
    selectedVideo: AvailableVideoConfiguration? = null,
    settingsValid: Boolean = false,
    onVideoSelected: (AvailableVideoConfiguration) -> Unit = {},
    audioMode: AudioMode = AudioMode.None,
    modifier: Modifier = Modifier,
) {
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
                val presetOptions = listOf(
                    resolveNamedPreset(videoOptions, RecordingPreset.DataSaver),
                    resolveNamedPreset(videoOptions, RecordingPreset.Balanced),
                    resolveNamedPreset(videoOptions, RecordingPreset.HighQuality),
                    selectedVideo?.copy(preset = RecordingPreset.Custom),
                ).filterNotNull().distinctBy { it.preset }
                SelectableSummaryRow(
                    R.string.quality,
                    selectedVideo?.let { stringResource(R.string.bitrate_value, it.bitrate.bitsPerSecond / 1_000_000) } ?: "—",
                    presetOptions.distinct(),
                    editable,
                    onSelected = onVideoSelected,
                    optionKey = { it.preset },
                ) { option ->
                    if (option.preset == RecordingPreset.Custom) stringResource(R.string.preset_custom) else {
                        "${stringResource(option.preset.labelResource())} · ${stringResource(R.string.bitrate_value, option.bitrate.bitsPerSecond / 1_000_000)}"
                    }
                }
                val custom = selectedVideo?.preset == RecordingPreset.Custom
                val resolutionValue = selectedVideo?.let { stringResource(R.string.resolution_label, it.shortEdge) } ?: "—"
                val fpsValue = selectedVideo?.let { stringResource(R.string.fps_value, it.frameRate.framesPerSecond) } ?: "—"
                if (custom) {
                    SelectableSummaryRow(
                        R.string.resolution,
                        resolutionValue,
                        videoOptions.distinctBy { it.shortEdge to it.resolution }.sortedBy { it.shortEdge },
                        editable,
                        { option ->
                            val current = selectedVideo
                            onVideoSelected(
                                if (current == null) option.copy(preset = RecordingPreset.Custom) else option.copy(
                                    preset = RecordingPreset.Custom,
                                    frameRate = current.frameRate,
                                    bitrate = current.bitrate,
                                ),
                            )
                        },
                    ) { stringResource(R.string.resolution_label, it.shortEdge) }
                    SelectableSummaryRow(
                        R.string.frame_rate,
                        fpsValue,
                        videoOptions.filter { it.shortEdge == selectedVideo.shortEdge }
                            .distinctBy { it.frameRate.framesPerSecond }.sortedBy { it.frameRate.framesPerSecond },
                        editable,
                        { option ->
                            onVideoSelected(option.copy(preset = RecordingPreset.Custom, bitrate = selectedVideo.bitrate))
                        },
                    ) { stringResource(R.string.fps_value, it.frameRate.framesPerSecond) }
                    SelectableSummaryRow(
                        R.string.video_bitrate,
                        stringResource(R.string.bitrate_value, selectedVideo.bitrate.bitsPerSecond / 1_000_000),
                        videoOptions.filter {
                            it.shortEdge == selectedVideo.shortEdge && it.frameRate == selectedVideo.frameRate
                        }.distinctBy { it.bitrate.bitsPerSecond }.sortedBy { it.bitrate.bitsPerSecond },
                        editable,
                        { onVideoSelected(it.copy(preset = RecordingPreset.Custom)) },
                    ) { stringResource(R.string.bitrate_value, it.bitrate.bitsPerSecond / 1_000_000) }
                } else {
                    SummaryRow(R.string.resolution, resolutionValue)
                    SummaryRow(R.string.frame_rate, fpsValue)
                }
                SummaryRow(
                    R.string.storage,
                    availableStorageBytes?.let { stringResource(R.string.available_storage, it.toDouble() / (1024.0 * 1024.0 * 1024.0)) }
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
        if (recordingState is RecordingState.Recording || recordingState is RecordingState.Paused) {
            Text(
                if (elapsedSeconds >= 3600L) stringResource(R.string.elapsed_time_long_format, elapsedSeconds / 3600L, elapsedSeconds / 60L % 60L, elapsedSeconds % 60L)
                else stringResource(R.string.elapsed_time_format, elapsedSeconds / 60L, elapsedSeconds % 60L),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            if (recordingState is RecordingState.Paused) {
                Text(
                    stringResource(R.string.recording_paused),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
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
            val recording = control == RecordingControl.Stop || recordingState is RecordingState.Paused
            Button(
                onClick = if (recording) onStopRecording else onStartRecording,
                enabled = recording || idle && settingsValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(painterResource(R.drawable.ic_record), contentDescription = null)
                Text(
                    stringResource(if (recording) R.string.stop_recording else if (idle) R.string.start_recording else R.string.recording_preparing),
                    modifier = Modifier.padding(start = Spacing.Small),
                )
            }
            if (recordingState is RecordingState.Recording || recordingState is RecordingState.Paused) {
                Button(
                    onClick = if (recordingState is RecordingState.Paused) onResumeRecording else onPauseRecording,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (recordingState is RecordingState.Paused) {
                                R.string.resume_recording
                            } else {
                                R.string.pause_recording
                            },
                        ),
                    )
                }
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
    optionKey: (AvailableVideoConfiguration) -> Any = {
        "${it.resolution}:${it.frameRate}:${it.bitrate}"
    },
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
                key(optionKey(option)) {
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = { expanded = false; onSelected(option) },
                    )
                }
            }
        }
    }
}

private fun resolveNamedPreset(
    options: List<AvailableVideoConfiguration>,
    preset: RecordingPreset,
): AvailableVideoConfiguration? {
    val matching = options.filter { it.preset == preset }
    return when (preset) {
        RecordingPreset.DataSaver -> matching.minWithOrNull(
            compareBy<AvailableVideoConfiguration> { kotlin.math.abs(it.shortEdge - 480) }
                .thenBy { kotlin.math.abs(it.frameRate.framesPerSecond - 30) }
                .thenBy { kotlin.math.abs(it.bitrate.bitsPerSecond - 2_000_000) },
        )
        RecordingPreset.Balanced -> matching.minWithOrNull(
            compareBy<AvailableVideoConfiguration> { kotlin.math.abs(it.shortEdge - 720) }
                .thenBy { kotlin.math.abs(it.frameRate.framesPerSecond - 30) }
                .thenBy { kotlin.math.abs(it.bitrate.bitsPerSecond - 6_000_000) },
        )
        RecordingPreset.HighQuality -> (matching.ifEmpty { options.filter { it.shortEdge < 1080 } }).maxWithOrNull(
            compareBy<AvailableVideoConfiguration> { it.shortEdge }
                .thenBy { it.frameRate.framesPerSecond }
                .thenBy { it.bitrate.bitsPerSecond },
        )?.copy(preset = RecordingPreset.HighQuality)
        RecordingPreset.Custom -> null
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
