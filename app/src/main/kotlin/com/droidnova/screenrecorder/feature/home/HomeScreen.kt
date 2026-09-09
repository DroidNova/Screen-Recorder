package com.droidnova.screenrecorder.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.domain.recording.RecordingPreset
import com.droidnova.screenrecorder.domain.recording.RecordingState
import com.droidnova.screenrecorder.recording.AvailableVideoConfiguration
import com.droidnova.screenrecorder.ui.theme.Spacing
import java.text.NumberFormat

private enum class HomeSheet { Quality, Resolution, FrameRate }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recordingState: RecordingState = RecordingState.Idle,
    elapsedSeconds: Long = 0,
    countdownRemainingSeconds: Int? = null,
    availableStorageBytes: Long? = null,
    statusMessage: String? = null,
    onStartRecording: () -> Unit = {}, onStopRecording: () -> Unit = {},
    onPauseRecording: () -> Unit = {}, onResumeRecording: () -> Unit = {},
    videoOptions: List<AvailableVideoConfiguration> = emptyList(),
    selectedVideo: AvailableVideoConfiguration? = null,
    settingsValid: Boolean = false,
    onVideoSelected: (AvailableVideoConfiguration) -> Unit = {},
    audioMode: AudioMode = AudioMode.None,
    modifier: Modifier = Modifier,
) {
    var openSheet by rememberSaveable { mutableStateOf<HomeSheet?>(null) }
    val editable = recordingState == RecordingState.Idle
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight
        val landscape = viewportWidth >= 600.dp && viewportHeight < 600.dp
        val largeText = LocalDensity.current.fontScale > 1.3f
        val contentModifier = Modifier.fillMaxSize().widthIn(max = 840.dp).align(Alignment.TopCenter)
            .verticalScroll(rememberScrollState()).padding(horizontal = Spacing.Page, vertical = Spacing.Standard)
        if (landscape) {
            Row(contentModifier, horizontalArrangement = Arrangement.spacedBy(Spacing.Large)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                    Header(); Metrics(videoOptions, selectedVideo, availableStorageBytes, editable, largeText, { openSheet = it })
                }
                TimerAndControls(recordingState, elapsedSeconds, countdownRemainingSeconds, settingsValid,
                    onStartRecording, onStopRecording, onPauseRecording, onResumeRecording, statusMessage, Modifier.weight(1f))
            }
        } else {
            Column(contentModifier, verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
                Header()
                Metrics(videoOptions, selectedVideo, availableStorageBytes, editable, largeText || viewportWidth < 350.dp, { openSheet = it })
                Spacer(Modifier.height((viewportHeight - 560.dp).coerceIn(32.dp, 180.dp)))
                TimerAndControls(recordingState, elapsedSeconds, countdownRemainingSeconds, settingsValid,
                    onStartRecording, onStopRecording, onPauseRecording, onResumeRecording, statusMessage)
            }
        }
    }
    openSheet?.let { sheet ->
        ConfigurationSheet(sheet, videoOptions, selectedVideo, onDismiss = { openSheet = null }) {
            onVideoSelected(it); openSheet = null
        }
    }
}

@Composable private fun Header() = Text(
    stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.semantics { heading() },
)

@Composable
private fun Metrics(
    options: List<AvailableVideoConfiguration>, selected: AvailableVideoConfiguration?, storage: Long?,
    editable: Boolean, grid: Boolean, open: (HomeSheet) -> Unit,
) {
    val storageValue = storage?.let { stringResource(R.string.storage_value, formatStorageNumber(it)) } ?: "—"
    val metrics: @Composable RowScope.() -> Unit = {
        StorageMetric(storageValue, storage != null, Modifier.weight(1f))
        MetricIndicator(selected?.let { stringResource(R.string.bitrate_value, it.bitrate.bitsPerSecond / 1_000_000) } ?: "—", stringResource(R.string.quality), editable,
            { open(HomeSheet.Quality) }, Modifier.weight(1f))
        MetricIndicator(selected?.let { stringResource(R.string.resolution_label, it.shortEdge) } ?: "—", stringResource(R.string.resolution), editable,
            { open(HomeSheet.Resolution) }, Modifier.weight(1f))
        MetricIndicator(selected?.let { stringResource(R.string.fps_value, it.frameRate.framesPerSecond) } ?: "—", stringResource(R.string.fps_label), editable,
            { open(HomeSheet.FrameRate) }, Modifier.weight(1f))
    }
    if (!grid) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.Small), content = metrics)
    else Column(verticalArrangement = Arrangement.spacedBy(Spacing.Standard)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.Standard)) {
            StorageMetric(storageValue, storage != null, Modifier.weight(1f))
            MetricIndicator(selected?.let { stringResource(R.string.bitrate_value, it.bitrate.bitsPerSecond / 1_000_000) } ?: "—", stringResource(R.string.quality), editable, { open(HomeSheet.Quality) }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.Standard)) {
            MetricIndicator(selected?.let { stringResource(R.string.resolution_label, it.shortEdge) } ?: "—", stringResource(R.string.resolution), editable, { open(HomeSheet.Resolution) }, Modifier.weight(1f))
            MetricIndicator(selected?.let { stringResource(R.string.fps_value, it.frameRate.framesPerSecond) } ?: "—", stringResource(R.string.fps_label), editable, { open(HomeSheet.FrameRate) }, Modifier.weight(1f))
        }
    }
}

@Composable private fun RowScope.StorageMetric(value: String, known: Boolean, modifier: Modifier) {
    val description = stringResource(R.string.storage_accessibility, value)
    Column(modifier.semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.sizeIn(minWidth = 64.dp, minHeight = 64.dp, maxWidth = 84.dp, maxHeight = 84.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.outlineVariant
            val primary = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 5.dp.toPx(); val inset = stroke / 2
                drawArc(track, 120f, 300f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(primary, 120f, if (known) 240f else 0f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                Text(stringResource(R.string.free), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(Spacing.Small)); Text(stringResource(R.string.storage), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable private fun RowScope.MetricIndicator(value: String, label: String, enabled: Boolean, click: () -> Unit, modifier: Modifier) {
    val description = stringResource(R.string.metric_accessibility, label, value)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.sizeIn(minWidth = 64.dp, minHeight = 64.dp, maxWidth = 84.dp, maxHeight = 84.dp).aspectRatio(1f)
            .clip(CircleShape).border(2.dp, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = click)
            .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
        }
        Spacer(Modifier.height(Spacing.Small)); Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable private fun TimerAndControls(
    state: RecordingState, elapsed: Long, countdown: Int?, valid: Boolean,
    start: () -> Unit, stop: () -> Unit, pause: () -> Unit, resume: () -> Unit,
    message: String?, modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.Standard)) {
        val shown = if (state is RecordingState.Countdown) countdown?.toLong() ?: 0 else elapsed
        Box(Modifier.width(224.dp).height(88.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Text(formatElapsed(shown), fontSize = 48.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
        if (state is RecordingState.Paused) Text(stringResource(R.string.recording_paused), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        when (state) {
            RecordingState.Idle, is RecordingState.Completed, is RecordingState.Failed -> Button(start, enabled = valid,
                modifier = Modifier.widthIn(min = 184.dp).heightIn(min = 64.dp), shape = RoundedCornerShape(22.dp)) { Text(stringResource(R.string.start)) }
            is RecordingState.Countdown -> Button(stop, modifier = Modifier.widthIn(min = 184.dp).heightIn(min = 60.dp)) { Text(stringResource(R.string.cancel)) }
            is RecordingState.Preparing -> ProgressAction(R.string.starting)
            is RecordingState.Recording -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Component)) {
                OutlinedButton(pause, modifier = Modifier.width(112.dp).heightIn(min = 56.dp)) { Text(stringResource(R.string.pause_recording)) }
                Button(stop, modifier = Modifier.width(112.dp).heightIn(min = 56.dp)) { Text(stringResource(R.string.stop_recording)) }
            }
            is RecordingState.Paused -> Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Component)) {
                Button(resume, modifier = Modifier.width(112.dp).heightIn(min = 56.dp)) { Text(stringResource(R.string.resume_recording)) }
                OutlinedButton(stop, modifier = Modifier.width(112.dp).heightIn(min = 56.dp)) { Text(stringResource(R.string.stop_recording)) }
            }
            is RecordingState.Stopping -> ProgressAction(R.string.saving)
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
    }
}

@Composable private fun ProgressAction(label: Int) = Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Component)) {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text(stringResource(label))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ConfigurationSheet(
    sheet: HomeSheet, options: List<AvailableVideoConfiguration>, selected: AvailableVideoConfiguration?,
    onDismiss: () -> Unit, choose: (AvailableVideoConfiguration) -> Unit,
) {
    val choices = when (sheet) {
        HomeSheet.Quality -> listOfNotNull(
            resolveNamedPreset(options, RecordingPreset.DataSaver), resolveNamedPreset(options, RecordingPreset.Balanced),
            resolveNamedPreset(options, RecordingPreset.HighQuality), selected?.copy(preset = RecordingPreset.Custom),
        ).distinctBy { it.preset }
        HomeSheet.Resolution -> options.distinctBy { it.shortEdge }.sortedBy { it.shortEdge }
        HomeSheet.FrameRate -> options.filter { selected == null || it.shortEdge == selected.shortEdge }.distinctBy { it.frameRate.framesPerSecond }.sortedBy { it.frameRate.framesPerSecond }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.Section, vertical = Spacing.Small)) {
            Text(stringResource(when (sheet) { HomeSheet.Quality -> R.string.recording_quality; HomeSheet.Resolution -> R.string.resolution; HomeSheet.FrameRate -> R.string.frame_rate }),
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Spacing.Standard).semantics { heading() })
            choices.forEach { option ->
                val isSelected = when (sheet) { HomeSheet.Quality -> selected?.preset == option.preset; HomeSheet.Resolution -> selected?.shortEdge == option.shortEdge; HomeSheet.FrameRate -> selected?.frameRate == option.frameRate }
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.medium)
                    .selectable(isSelected, role = Role.RadioButton) {
                        choose(when (sheet) {
                            HomeSheet.Quality -> option
                            HomeSheet.Resolution -> option.copy(preset = RecordingPreset.Custom)
                            HomeSheet.FrameRate -> option.copy(preset = RecordingPreset.Custom, bitrate = selected?.bitrate ?: option.bitrate)
                        })
                    }.padding(horizontal = Spacing.Component), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(isSelected, null)
                    Column(Modifier.padding(start = Spacing.Small)) {
                        Text(when (sheet) {
                            HomeSheet.Quality -> stringResource(option.preset.labelResource())
                            HomeSheet.Resolution -> stringResource(R.string.resolution_label, option.shortEdge)
                            HomeSheet.FrameRate -> stringResource(R.string.fps_value_uppercase, option.frameRate.framesPerSecond)
                        })
                        if (sheet == HomeSheet.Quality) Text(stringResource(R.string.capture_summary_value, option.shortEdge, option.frameRate.framesPerSecond, option.bitrate.bitsPerSecond / 1_000_000), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.Standard))
        }
    }
}

private fun formatElapsed(seconds: Long) = if (seconds >= 3600) "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%02d:%02d".format(seconds / 60, seconds % 60)
private fun formatStorageNumber(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val formatter = NumberFormat.getNumberInstance().apply { maximumFractionDigits = if (gb < 10) 1 else 0 }
    return formatter.format(gb)
}

private fun resolveNamedPreset(options: List<AvailableVideoConfiguration>, preset: RecordingPreset): AvailableVideoConfiguration? {
    val matching = options.filter { it.preset == preset }
    return when (preset) {
        RecordingPreset.DataSaver -> matching.minByOrNull { kotlin.math.abs(it.shortEdge - 480) + kotlin.math.abs(it.frameRate.framesPerSecond - 30) }
        RecordingPreset.Balanced -> matching.minByOrNull { kotlin.math.abs(it.shortEdge - 720) + kotlin.math.abs(it.frameRate.framesPerSecond - 30) }
        RecordingPreset.HighQuality -> matching.maxWithOrNull(compareBy<AvailableVideoConfiguration> { it.shortEdge }.thenBy { it.frameRate.framesPerSecond })
        RecordingPreset.Custom -> null
    }
}
private fun RecordingPreset.labelResource() = when (this) {
    RecordingPreset.DataSaver -> R.string.preset_data_saver; RecordingPreset.Balanced -> R.string.balanced
    RecordingPreset.HighQuality -> R.string.preset_high_quality; RecordingPreset.Custom -> R.string.preset_custom
}
