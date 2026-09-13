package com.droidnova.screenrecorder.feature.home

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.domain.recording.AudioMode
import com.droidnova.screenrecorder.domain.recording.RecordingPreset
import com.droidnova.screenrecorder.domain.recording.RecordingState
import com.droidnova.screenrecorder.domain.recording.hasActiveOrFinalizingSession
import com.droidnova.screenrecorder.feature.settings.AudioSelectorSheet
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
    onStartRecording: () -> Unit = {}, onStopRecording: () -> Unit = {},
    onPauseRecording: () -> Unit = {}, onResumeRecording: () -> Unit = {},
    videoOptions: List<AvailableVideoConfiguration> = emptyList(),
    selectedVideo: AvailableVideoConfiguration? = null,
    settingsValid: Boolean = false,
    onVideoSelected: (AvailableVideoConfiguration) -> Unit = {},
    audioMode: AudioMode = AudioMode.None,
    onAudioModeSelected: (AudioMode) -> Unit = {},
    deviceAudioAvailable: Boolean = true,
    onConfigurationOverlayChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var openSheet by rememberSaveable { mutableStateOf<HomeSheet?>(null) }
    var showAudioSheet by rememberSaveable { mutableStateOf(false) }
    val editable = recordingState == RecordingState.Idle
    LaunchedEffect(openSheet, showAudioSheet) { onConfigurationOverlayChanged(openSheet != null || showAudioSheet) }
    DisposableEffect(Unit) { onDispose { onConfigurationOverlayChanged(false) } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth >= 600.dp && maxHeight < 600.dp
        val wrapMetrics = LocalDensity.current.fontScale > 1.3f || maxWidth < 330.dp
        if (landscape) {
            Row(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Header()
                    Metrics(selectedVideo, availableStorageBytes, audioMode, editable, wrapMetrics, { openSheet = it }, { showAudioSheet = true })
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TimerPanel(recordingState, elapsedSeconds, countdownRemainingSeconds)
                    RecordingActions(recordingState, settingsValid, onStartRecording, onStopRecording, onPauseRecording, onResumeRecording)
                }
            }
        } else {
            Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 12.dp)) {
                Column(Modifier.align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Header()
                    Metrics(selectedVideo, availableStorageBytes, audioMode, editable, wrapMetrics, { openSheet = it }, { showAudioSheet = true })
                }
                TimerPanel(recordingState, elapsedSeconds, countdownRemainingSeconds, Modifier.align(Alignment.Center))
                RecordingActions(
                    recordingState, settingsValid, onStartRecording, onStopRecording, onPauseRecording, onResumeRecording,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                )
            }
        }
    }
    openSheet?.let { sheet ->
        ConfigurationSheet(sheet, videoOptions, selectedVideo, onDismiss = { openSheet = null }) {
            onVideoSelected(it); openSheet = null
        }
    }
    if (showAudioSheet) {
        AudioSelectorSheet(audioMode, deviceAudioAvailable, onDismiss = { showAudioSheet = false }) {
            onAudioModeSelected(it); showAudioSheet = false
        }
    }
}

@Composable private fun Header() = Text(
    stringResource(R.string.home_title), style = MaterialTheme.typography.headlineMedium,
    modifier = Modifier.semantics { heading() },
)

@Composable
private fun Metrics(
    selected: AvailableVideoConfiguration?,
    storage: Long?,
    audioMode: AudioMode,
    editable: Boolean,
    wrap: Boolean,
    open: (HomeSheet) -> Unit,
    openAudio: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 4.dp
        val availableWidth = maxWidth - gap * 4
        val circleSize = (availableWidth / 5).coerceIn(52.dp, 60.dp)
        val storageValue = storage?.let { stringResource(R.string.storage_value, formatStorageNumber(it)) } ?: "—"
        val items = listOf<@Composable (Modifier) -> Unit>(
            { modifier -> StorageMetric(storageValue, storage != null, circleSize, modifier) },
            { modifier -> QuickMetric(selected?.let { stringResource(R.string.bitrate_value, it.bitrate.bitsPerSecond / 1_000_000) } ?: "—", stringResource(R.string.quality), circleSize, editable, modifier) { open(HomeSheet.Quality) } },
            { modifier -> QuickMetric(selected?.let { stringResource(R.string.resolution_label, it.shortEdge) } ?: "—", stringResource(R.string.resolution), circleSize, editable, modifier) { open(HomeSheet.Resolution) } },
            { modifier -> QuickMetric(selected?.let { stringResource(R.string.fps_value_uppercase, it.frameRate.framesPerSecond) } ?: "—", stringResource(R.string.fps_label), circleSize, editable, modifier) { open(HomeSheet.FrameRate) } },
            { modifier -> AudioMetric(audioMode, circleSize, editable, modifier, openAudio) },
        )
        if (!wrap) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                items.forEach { it(Modifier.weight(1f)) }
            }
        } else {
            val wrappedRowHorizontalPadding = maxWidth / 6
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) { items.take(3).forEach { it(Modifier.weight(1f)) } }
                Row(Modifier.fillMaxWidth().padding(horizontal = wrappedRowHorizontalPadding), horizontalArrangement = Arrangement.spacedBy(gap)) { items.takeLast(2).forEach { it(Modifier.weight(1f)) } }
            }
        }
    }
}

@Composable private fun StorageMetric(value: String, known: Boolean, circleSize: Dp, modifier: Modifier) {
    val description = stringResource(R.string.storage_accessibility, value)
    Column(modifier.semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(circleSize), contentAlignment = Alignment.Center) {
            val track = MaterialTheme.colorScheme.outlineVariant
            val primary = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 4.dp.toPx(); val inset = stroke / 2
                drawArc(track, 120f, 300f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(primary, 120f, if (known) 240f else 0f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, Modifier.padding(horizontal = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                Text(stringResource(R.string.free), fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            }
        }
        QuickLabel(stringResource(R.string.storage))
    }
}

@Composable private fun QuickMetric(value: String, label: String, circleSize: Dp, enabled: Boolean, modifier: Modifier, click: () -> Unit) {
    val description = stringResource(R.string.metric_accessibility, label, value)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(circleSize).clip(CircleShape)
                .border(1.5.dp, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = click)
                .semantics { contentDescription = description; if (!enabled) disabled() },
            contentAlignment = Alignment.Center,
        ) {
            Text(value, Modifier.padding(horizontal = 3.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
        QuickLabel(label)
    }
}

@Composable private fun AudioMetric(mode: AudioMode, circleSize: Dp, enabled: Boolean, modifier: Modifier, click: () -> Unit) {
    val fullValue = stringResource(mode.fullLabel())
    val description = stringResource(R.string.metric_accessibility, stringResource(R.string.audio), fullValue)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.size(circleSize).clip(CircleShape)
                .border(1.5.dp, if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = click)
                .semantics { contentDescription = description; if (!enabled) disabled() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(mode.icon()), null, Modifier.size(18.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(mode.shortLabel()), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
        QuickLabel(stringResource(R.string.audio))
    }
}

@Composable private fun QuickLabel(label: String) = Text(label, Modifier.padding(top = 5.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)

@Composable private fun TimerPanel(state: RecordingState, elapsed: Long, countdown: Int?, modifier: Modifier = Modifier) {
    val shown = when {
        state is RecordingState.Countdown -> countdown?.toLong() ?: 0
        state.hasActiveOrFinalizingSession -> elapsed
        else -> 0
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(224.dp).height(88.dp).border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Text(formatElapsed(shown), fontSize = 48.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
        }
        if (state is RecordingState.Paused) Text(stringResource(R.string.recording_paused), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable private fun RecordingActions(
    state: RecordingState, valid: Boolean, start: () -> Unit, stop: () -> Unit, pause: () -> Unit, resume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when (state) {
            RecordingState.Idle, is RecordingState.Completed, is RecordingState.Failed -> Button(start, enabled = valid,
                modifier = Modifier.width(184.dp).heightIn(min = 64.dp), shape = RoundedCornerShape(22.dp)) {
                Text(stringResource(R.string.start), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
            is RecordingState.Countdown -> Button(stop, modifier = Modifier.width(184.dp).heightIn(min = 60.dp)) { Text(stringResource(R.string.cancel), maxLines = 1, softWrap = false) }
            is RecordingState.Preparing -> ProgressAction(R.string.starting)
            is RecordingState.Recording -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(pause, modifier = Modifier.width(120.dp).heightIn(min = 56.dp)) { Text(stringResource(R.string.pause_recording), maxLines = 1, softWrap = false) }
                Button(stop, modifier = Modifier.width(120.dp).heightIn(min = 56.dp)) { Text(stringResource(R.string.stop), maxLines = 1, softWrap = false) }
            }
            is RecordingState.Paused -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(resume, modifier = Modifier.width(120.dp).heightIn(min = 56.dp)) { Text(stringResource(R.string.resume_recording), maxLines = 1, softWrap = false) }
                OutlinedButton(stop, modifier = Modifier.width(120.dp).heightIn(min = 56.dp)) { Text(stringResource(R.string.stop), maxLines = 1, softWrap = false) }
            }
            is RecordingState.Stopping -> ProgressAction(R.string.saving)
        }
    }
}

private fun AudioMode.shortLabel() = when (this) { AudioMode.None -> R.string.audio_off_short; AudioMode.Microphone -> R.string.audio_mic_short; AudioMode.DeviceAudio -> R.string.audio_device_short }
private fun AudioMode.fullLabel() = when (this) { AudioMode.None -> R.string.audio_none; AudioMode.Microphone -> R.string.audio_microphone; AudioMode.DeviceAudio -> R.string.audio_device }
@DrawableRes private fun AudioMode.icon() = when (this) { AudioMode.None -> R.drawable.ic_audio_off; AudioMode.Microphone -> R.drawable.ic_microphone; AudioMode.DeviceAudio -> R.drawable.ic_device_audio }

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
            resolveNamedPreset(options, RecordingPreset.HighQuality), selected?.takeIf { it.preset == RecordingPreset.Custom },
        ).distinctBy { it.preset }
        HomeSheet.Resolution -> options.distinctBy { it.shortEdge }.sortedBy { it.shortEdge }
        HomeSheet.FrameRate -> options.filter { selected == null || it.shortEdge == selected.shortEdge }.distinctBy { it.frameRate.framesPerSecond }.sortedBy { it.frameRate.framesPerSecond }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Spacing.Section, vertical = Spacing.Small)) {
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
