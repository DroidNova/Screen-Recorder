package com.droidnova.screenrecorder.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.ui.theme.AppColorTheme
import com.droidnova.screenrecorder.ui.theme.AppThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    countdownSeconds: Int,
    onScreenToolsEnabled: Boolean,
    canDrawOverlays: Boolean,
    themeMode: AppThemeMode,
    colorTheme: AppColorTheme,
    settingsEnabled: Boolean,
    onCountdownSelected: (Int) -> Unit,
    onScreenToolsChanged: (Boolean) -> Unit,
    onOverlayPermissionRequest: () -> Unit,
    onAppearance: () -> Unit,
    onNotificationSettings: () -> Unit,
    onApplicationSettings: () -> Unit,
    privacyOptionsRequired: Boolean,
    onPrivacyChoices: () -> Unit,
    onConfigurationOverlayChanged: (Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCountdown by remember { mutableStateOf(false) }
    var showOverlayExplanation by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val notificationAllowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val microphoneAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(showCountdown, showOverlayExplanation, showReset) {
        onConfigurationOverlayChanged(showCountdown || showOverlayExplanation || showReset)
    }
    DisposableEffect(Unit) { onDispose { onConfigurationOverlayChanged(false) } }

    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 4.dp).semantics { heading() }) }
        item {
            SettingsGroup(R.string.appearance) {
                CompactSettingsRow(R.string.appearance, valueText = "${themeMode.label()} · ${colorTheme.label()}", onClick = onAppearance)
            }
        }
        item {
            SettingsGroup(R.string.capture) {
                val countdownSummary = if (countdownSeconds == 0) stringResource(R.string.countdown_off) else {
                    val location = stringResource(if (onScreenToolsEnabled) R.string.countdown_over_apps else R.string.countdown_in_app)
                    "${stringResource(R.string.countdown_seconds, countdownSeconds)} · $location"
                }
                CompactSettingsRow(R.string.countdown, valueText = countdownSummary, enabled = settingsEnabled) { showCountdown = true }
            }
        }
        item {
            SettingsGroup(R.string.permissions) {
                CompactSettingsRow(R.string.notifications, value = if (notificationAllowed) R.string.permission_allowed else R.string.not_allowed, onClick = onNotificationSettings)
                DividerRow()
                CompactSettingsRow(R.string.audio_microphone, value = if (microphoneAllowed) R.string.permission_allowed else R.string.not_allowed, onClick = onApplicationSettings)
                DividerRow()
                CompactSettingsRow(R.string.screen_capture_permission, summary = stringResource(R.string.screen_capture_settings_detail))
            }
        }
        item { SettingsGroup(R.string.storage) { CompactSettingsRow(R.string.recording_location, summary = stringResource(R.string.recording_location_value)) } }
        item {
            SettingsGroup(R.string.about) {
                val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
                CompactSettingsRow(R.string.version, valueText = version)
                if (privacyOptionsRequired) { DividerRow(); CompactSettingsRow(R.string.privacy_choices, summary = stringResource(R.string.privacy_choices_summary), onClick = onPrivacyChoices) }
                DividerRow(); CompactSettingsRow(R.string.reset_recording_settings, onClick = { showReset = true })
            }
        }
    }

    if (showCountdown) CountdownSheet(
        countdown = countdownSeconds,
        overlayEnabled = onScreenToolsEnabled,
        onDismiss = { showCountdown = false },
        onCountdownSelected = { seconds ->
            onCountdownSelected(seconds)
            if (seconds == 0 && onScreenToolsEnabled) onScreenToolsChanged(false)
        },
        onOverlayChanged = { enabled ->
            if (!enabled || canDrawOverlays) onScreenToolsChanged(enabled) else showOverlayExplanation = true
        },
    )
    if (showOverlayExplanation) AlertDialog(
        onDismissRequest = { showOverlayExplanation = false },
        title = { Text(stringResource(R.string.overlay_explanation_title)) },
        text = { Text(stringResource(R.string.overlay_explanation_text)) },
        dismissButton = { TextButton({ showOverlayExplanation = false }) { Text(stringResource(R.string.not_now)) } },
        confirmButton = { TextButton({ showOverlayExplanation = false; onOverlayPermissionRequest() }) { Text(stringResource(R.string.continue_action)) } },
    )
    if (showReset) AlertDialog(
        onDismissRequest = { showReset = false },
        title = { Text(stringResource(R.string.reset_recording_settings_question)) },
        text = { Text(stringResource(R.string.reset_recording_settings_detail)) },
        confirmButton = { TextButton({ showReset = false; onReset() }) { Text(stringResource(R.string.reset)) } },
        dismissButton = { TextButton({ showReset = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountdownSheet(countdown: Int, overlayEnabled: Boolean, onDismiss: () -> Unit, onCountdownSelected: (Int) -> Unit, onOverlayChanged: (Boolean) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
            Text(stringResource(R.string.recording_countdown), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp).semantics { heading() })
            listOf(0, 3, 5, 15).forEach { seconds ->
                val label = if (seconds == 0) stringResource(R.string.countdown_off) else stringResource(R.string.countdown_seconds, seconds)
                Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).selectable(seconds == countdown, role = Role.RadioButton) { onCountdownSelected(seconds) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(seconds == countdown, null); Text(label, Modifier.padding(start = 8.dp))
                }
            }
            DividerRow()
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 72.dp).clickable(enabled = countdown > 0) { onOverlayChanged(!overlayEnabled) }.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.show_countdown_over_apps), color = if (countdown > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(if (countdown > 0) R.string.show_countdown_over_apps_summary else R.string.choose_countdown_first), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp)); Switch(overlayEnabled, onCheckedChange = onOverlayChanged, enabled = countdown > 0)
            }
        }
    }
}

@Composable private fun SettingsGroup(title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title).uppercase(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp).semantics { heading() })
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(content = content) }
    }
}

@Composable private fun CompactSettingsRow(title: Int, value: Int? = null, valueText: String? = null, summary: String? = null, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = if (summary == null) 56.dp else 68.dp).then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(stringResource(title), maxLines = 1, overflow = TextOverflow.Ellipsis); summary?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        (valueText ?: value?.let { stringResource(it) })?.let { Spacer(Modifier.width(12.dp)); Text(it, Modifier.widthIn(max = 190.dp), maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (onClick != null) Text("›", Modifier.width(28.dp), fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable private fun DividerRow() = Divider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
