package com.droidnova.screenrecorder.feature.rating

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.droidnova.screenrecorder.R
import com.droidnova.screenrecorder.rating.RatePromptAction
import com.droidnova.screenrecorder.rating.ratePromptAction
import com.droidnova.screenrecorder.utils.IntentUtils
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatePromptSheet(
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    val context = LocalContext.current
    var selectedStars by remember { mutableIntStateOf(0) }
    var destinationLaunched by remember { mutableStateOf(false) }

    fun launchOnce(action: RatePromptAction) {
        if (destinationLaunched || action == RatePromptAction.None) return
        destinationLaunched = true
        when (action) {
            RatePromptAction.Feedback -> IntentUtils.sendFeedback(context)
            RatePromptAction.PlayStore -> IntentUtils.rateUs(context)
            RatePromptAction.None -> Unit
        }
        onCompleted()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.rate_prompt_title, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.rate_prompt_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                (1..5).forEach { star ->
                    RatingStar(
                        selected = star <= selectedStars,
                        description = pluralStringResource(R.plurals.rate_stars_description, star, star),
                        onClick = {
                            if (!destinationLaunched) {
                                selectedStars = star
                                if (star == 5) launchOnce(RatePromptAction.PlayStore)
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (ratePromptAction(selectedStars) == RatePromptAction.Feedback) {
                Button(
                    onClick = { launchOnce(RatePromptAction.Feedback) },
                    enabled = !destinationLaunched,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.give_feedback)) }
                Spacer(Modifier.height(12.dp))
            }
            OutlinedButton(
                onClick = onDismiss,
                enabled = !destinationLaunched,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.rate_later)) }
        }
    }
}

@Composable
private fun RatingStar(selected: Boolean, description: String, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, label = "starScale")
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "starColor",
    )
    Text(
        text = if (selected) "★" else "☆",
        color = color,
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
    )
}
