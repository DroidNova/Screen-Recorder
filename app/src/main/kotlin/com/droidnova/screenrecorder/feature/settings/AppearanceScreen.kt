package com.droidnova.screenrecorder.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.droidnova.screenrecorder.ui.theme.AppColorTheme
import com.droidnova.screenrecorder.ui.theme.AppThemeMode
import com.droidnova.screenrecorder.ui.theme.resolveColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    themeMode: AppThemeMode,
    colorTheme: AppColorTheme,
    onThemeModeSelected: (AppThemeMode) -> Unit,
    onColorThemeSelected: (AppColorTheme) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text("Appearance") },
            navigationIcon = { IconButton(onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) } },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Display mode", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            ModePreview(
                                mode = mode,
                                selected = mode == themeMode,
                                accent = colorTheme,
                                modifier = Modifier.weight(1f),
                                onClick = { onThemeModeSelected(mode) },
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Colour theme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(AppColorTheme.entries, key = { it.name }) { theme ->
                            ColorThemeOption(theme, theme == colorTheme) { onColorThemeSelected(theme) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModePreview(mode: AppThemeMode, selected: Boolean, accent: AppColorTheme, modifier: Modifier, onClick: () -> Unit) {
    val previewDark = mode == AppThemeMode.DARK
    val colors = resolveColorScheme(accent, previewDark)
    Card(
        modifier = modifier.heightIn(min = 132.dp).semantics { this.selected = selected }.clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = colors.background),
    ) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp)).background(colors.surface)) {
                Box(Modifier.padding(8.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(colors.primary))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (selected) Spacer(Modifier.width(4.dp))
                Text(mode.label(), color = colors.onBackground, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ColorThemeOption(theme: AppColorTheme, selected: Boolean, onClick: () -> Unit) {
    val swatch = resolveColorScheme(theme, false).primary
    Card(
        modifier = Modifier.width(124.dp).heightIn(min = 96.dp).semantics { this.selected = selected }.clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(swatch), contentAlignment = Alignment.Center) {
                if (selected) Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(theme.label(), style = MaterialTheme.typography.labelLarge)
        }
    }
}

fun AppThemeMode.label() = when (this) {
    AppThemeMode.SYSTEM -> "System default"
    AppThemeMode.LIGHT -> "Light"
    AppThemeMode.DARK -> "Dark"
}

fun AppColorTheme.label() = when (this) {
    AppColorTheme.MINT -> "Mint"
    AppColorTheme.OCEAN -> "Ocean"
    AppColorTheme.VIOLET -> "Violet"
    AppColorTheme.AMBER -> "Amber"
}
