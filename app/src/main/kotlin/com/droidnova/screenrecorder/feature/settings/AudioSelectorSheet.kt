package com.droidnova.screenrecorder.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.domain.recording.AudioMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioSelectorSheet(
    selected: AudioMode,
    deviceAudioAvailable: Boolean,
    onDismiss: () -> Unit,
    onSelected: (AudioMode) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(R.string.audio), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(8.dp).semantics { heading() })
            AudioMode.entries.forEach { mode ->
                val enabled = mode != AudioMode.DeviceAudio || deviceAudioAvailable
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp).selectable(
                        selected = selected == mode,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelected(mode) },
                    ).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected == mode, null, enabled = enabled, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(stringResource(mode.audioLabelResource()))
                        if (mode == AudioMode.DeviceAudio) {
                            Text(
                                stringResource(if (deviceAudioAvailable) R.string.device_audio_supporting else R.string.device_audio_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

internal fun AudioMode.audioLabelResource() = when (this) {
    AudioMode.None -> R.string.audio_none
    AudioMode.Microphone -> R.string.audio_microphone
    AudioMode.DeviceAudio -> R.string.audio_device
}
