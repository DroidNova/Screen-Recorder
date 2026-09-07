package com.droidnova.screenrecorder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF388E3C), onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9), onPrimaryContainer = Color(0xFF123A18),
    secondary = Color(0xFF40683F), onSecondary = Color.White,
    background = Color(0xFFF9FCF7), onBackground = Color(0xFF191C19),
    surface = Color(0xFFF9FCF7), onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDEE5DA), onSurfaceVariant = Color(0xFF424940),
    outline = Color(0xFF727970), error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D784), onPrimary = Color(0xFF00390B),
    primaryContainer = Color(0xFF14521F), onPrimaryContainer = Color(0xFFB6F2B7),
    secondary = Color(0xFFA5D0A2), onSecondary = Color(0xFF103912),
    background = Color(0xFF101510), onBackground = Color(0xFFE0E4DC),
    surface = Color(0xFF101510), onSurface = Color(0xFFE0E4DC),
    surfaceVariant = Color(0xFF424940), onSurfaceVariant = Color(0xFFC2C9BE),
    outline = Color(0xFF8C9388), error = Color(0xFFFFB4AB),
)

val ScreenRecorderTypography = Typography(
    headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = Typography().labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
)

val ScreenRecorderShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

object Spacing {
    val Small = 8.dp
    val Component = 12.dp
    val Page = 20.dp
    val Section = 24.dp
    val Card = 20.dp
}

@Composable
fun ScreenRecorderTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ScreenRecorderTypography,
        shapes = ScreenRecorderShapes,
        content = content,
    )
}
