package com.droidnova.screenrecorder.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

enum class AppColorTheme { MINT, OCEAN, VIOLET, AMBER }

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

private data class AccentPalette(
    val lightPrimary: Color,
    val lightContainer: Color,
    val lightOnPrimary: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
    val darkOnPrimary: Color,
)

private fun AppColorTheme.palette() = when (this) {
    AppColorTheme.MINT -> AccentPalette(Color(0xFF146C43), Color(0xFFB8F5D0), Color.White, RecorderPrimaryBright, Color(0xFF173C20), RecorderOnPrimary)
    AppColorTheme.OCEAN -> AccentPalette(Color(0xFF006493), Color(0xFFC9E6FF), Color.White, Color(0xFF72C7FF), Color(0xFF004B70), Color(0xFF002438))
    AppColorTheme.VIOLET -> AccentPalette(Color(0xFF65558F), Color(0xFFE9DDFF), Color.White, Color(0xFFD0BCFF), Color(0xFF4F378B), Color(0xFF2B175B))
    AppColorTheme.AMBER -> AccentPalette(Color(0xFF855400), Color(0xFFFFDDB1), Color.White, Color(0xFFFFB951), Color(0xFF633F00), Color(0xFF2C1700))
}

fun resolveColorScheme(colorTheme: AppColorTheme, darkTheme: Boolean) = colorTheme.palette().let { accent -> if (darkTheme) darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = accent.darkOnPrimary,
    primaryContainer = accent.darkContainer,
    onPrimaryContainer = RecorderTextPrimary,
    secondary = accent.darkPrimary,
    onSecondary = accent.darkOnPrimary,
    secondaryContainer = accent.darkContainer,
    onSecondaryContainer = accent.darkPrimary,
    background = RecorderBackground,
    onBackground = RecorderTextPrimary,
    surface = RecorderSurface,
    onSurface = RecorderTextPrimary,
    surfaceVariant = RecorderSurfaceElevated,
    onSurfaceVariant = RecorderTextSecondary,
    surfaceContainer = RecorderNavigation,
    surfaceContainerHigh = RecorderSurfaceElevated,
    outline = accent.darkPrimary.copy(alpha = 0.8f),
    outlineVariant = Color(0xFF3D423E),
    error = RecorderDanger,
    onError = Color.Black,
) else lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = accent.lightOnPrimary,
    primaryContainer = accent.lightContainer,
    onPrimaryContainer = Color(0xFF102018),
    secondary = accent.lightPrimary,
    onSecondary = accent.lightOnPrimary,
    secondaryContainer = accent.lightContainer,
    onSecondaryContainer = Color(0xFF102018),
    background = Color(0xFFF9F8FC),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFF1EEF4),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainer = Color(0xFFF4F1F7),
    surfaceContainerHigh = Color(0xFFECE8F0),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
) }

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
fun ScreenRecorderTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    colorTheme: AppColorTheme = AppColorTheme.MINT,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val activity = view.context as? ComponentActivity ?: return@SideEffect
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = !useDarkTheme
            isAppearanceLightNavigationBars = !useDarkTheme
        }
    }
    MaterialTheme(
        colorScheme = resolveColorScheme(colorTheme, useDarkTheme),
        typography = ScreenRecorderTypography,
        shapes = ScreenRecorderShapes,
        content = content,
    )
}
