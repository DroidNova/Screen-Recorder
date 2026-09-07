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
import androidx.compose.runtime.Composable
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

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
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
                SummaryRow(R.string.quality, R.string.balanced)
                SummaryRow(R.string.resolution, R.string.resolution_value)
                SummaryRow(R.string.frame_rate, R.string.frame_rate_value)
                SummaryRow(R.string.storage, R.string.app_storage)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.Small), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_record), contentDescription = null)
                Text(stringResource(R.string.start_recording), modifier = Modifier.padding(start = Spacing.Small))
            }
            Text(stringResource(R.string.recording_unavailable), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun SummaryRow(label: Int, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Preview(name = "Home light", showBackground = true)
@Preview(name = "Home dark", showBackground = true, uiMode = 0x20)
@Preview(name = "Home large text", showBackground = true, fontScale = 2f)
@Composable private fun HomePreview() { ScreenRecorderTheme { HomeScreen() } }
