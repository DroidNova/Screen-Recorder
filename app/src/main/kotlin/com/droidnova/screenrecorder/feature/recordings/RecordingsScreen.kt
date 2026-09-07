package com.droidnova.screenrecorder.feature.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.ui.theme.ScreenRecorderTheme
import com.droidnova.screenrecorder.ui.theme.Spacing

@Composable fun RecordingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.Page),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(painterResource(R.drawable.ic_recordings_outline), stringResource(R.string.recordings_icon_description), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(Spacing.Component))
        Text(stringResource(R.string.no_recordings), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.no_recordings_supporting), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

@Preview(showBackground = true, widthDp = 840, heightDp = 600)
@Composable private fun RecordingsPreview() { ScreenRecorderTheme { RecordingsScreen() } }
