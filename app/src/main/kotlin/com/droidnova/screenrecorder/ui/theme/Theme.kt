package com.droidnova.screenrecorder.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val RecorderBackground = Color(0xFF001008)
val RecorderNavigation = Color(0xFF000000)
val RecorderSurface = Color(0xFF1D1F1E)
val RecorderSurfaceElevated = Color(0xFF29282C)
val RecorderPrimary = Color(0xFF49B853)
val RecorderPrimaryBright = Color(0xFF55D35E)
val RecorderOnPrimary = Color(0xFF001A05)
val RecorderTextPrimary = Color(0xFFF1EFF2)
val RecorderTextSecondary = Color(0xFFB8B5BA)
val RecorderOutline = Color(0xFF4DAA54)
val RecorderDisabled = Color(0xFF696B69)
val RecorderDanger = Color(0xFFFF6B6B)

private val RecorderColors = darkColorScheme(
    primary = RecorderPrimaryBright,
    onPrimary = RecorderOnPrimary,
    primaryContainer = Color(0xFF173C20),
    onPrimaryContainer = RecorderTextPrimary,
    secondary = RecorderPrimary,
    onSecondary = RecorderOnPrimary,
    secondaryContainer = Color(0x2949B853),
    onSecondaryContainer = RecorderPrimaryBright,
    background = RecorderBackground,
    onBackground = RecorderTextPrimary,
    surface = RecorderSurface,
    onSurface = RecorderTextPrimary,
    surfaceVariant = RecorderSurfaceElevated,
    onSurfaceVariant = RecorderTextSecondary,
    surfaceContainer = RecorderNavigation,
    surfaceContainerHigh = RecorderSurfaceElevated,
    outline = RecorderOutline,
    outlineVariant = Color(0xFF3D423E),
    error = RecorderDanger,
    onError = Color.Black,
)

val ScreenRecorderTypography = Typography(
    headlineMedium = Typography().headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 34.sp),
    headlineSmall = Typography().headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    titleLarge = Typography().titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = Typography().bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = Typography().bodyMedium.copy(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelLarge = Typography().labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    labelMedium = Typography().labelMedium.copy(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
)

val ScreenRecorderShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

object Spacing {
    val Tiny = 4.dp
    val Small = 8.dp
    val Component = 12.dp
    val Standard = 16.dp
    val Page = 20.dp
    val Section = 24.dp
    val Large = 32.dp
    val Card = 20.dp
}

@Composable
fun ScreenRecorderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RecorderColors, typography = ScreenRecorderTypography, shapes = ScreenRecorderShapes, content = content)
}
