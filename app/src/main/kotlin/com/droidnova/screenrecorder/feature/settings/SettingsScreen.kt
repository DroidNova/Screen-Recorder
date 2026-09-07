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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.ui.theme.ScreenRecorderTheme
import com.droidnova.screenrecorder.ui.theme.Spacing

@Composable fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(Spacing.Page), verticalArrangement = Arrangement.spacedBy(Spacing.Section)) {
        Text(stringResource(R.string.settings_read_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Section(R.string.capture, listOf(R.string.capture_preset to R.string.balanced, R.string.audio to R.string.not_configured))
        Section(R.string.appearance, listOf(R.string.app_theme to R.string.system_default))
        Section(R.string.about, listOf(R.string.version to R.string.version_value))
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
