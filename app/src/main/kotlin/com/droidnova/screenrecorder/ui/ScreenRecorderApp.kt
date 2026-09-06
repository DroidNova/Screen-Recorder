package com.droidnova.screenrecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.ui.theme.ScreenRecorderTheme

@Composable
fun ScreenRecorderApp() {
    ScreenRecorderTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            FoundationScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun FoundationScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.foundation_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FoundationScreenPreview() {
    ScreenRecorderApp()
}
