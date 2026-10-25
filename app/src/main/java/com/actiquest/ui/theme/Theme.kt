package com.actiquest.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

object ThemeManager {
    var isDarkMode by mutableStateOf(true)
        private set

    fun setDarkModeEnabled(enabled: Boolean) {
        isDarkMode = enabled
    }

    fun toggleTheme() {
        isDarkMode = !isDarkMode
    }
}

private val DarkAppColorScheme = darkColorScheme(
    primary = RoyalIndigo,
    onPrimary = TextOnCobalt,
    primaryContainer = IndigoPurple,
    onPrimaryContainer = TextPrimary,
    secondary = MintEmerald,
    onSecondary = TextOnMint,
    secondaryContainer = MintEmeraldDark,
    onSecondaryContainer = MintGreenLight,
    tertiary = ElectricBlue,
    onTertiary = TextPrimary,
    tertiaryContainer = VioletAccent,
    onTertiaryContainer = TextPrimary,
    background = DarkNavyBackground,
    onBackground = TextPrimary,
    surface = SurfaceCharcoal,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceCharcoal,
    surfaceContainerHigh = SurfaceElevated,
    error = StatusRejected,
    onError = TextPrimary,
    errorContainer = StatusRejected.copy(alpha = 0.25f),
    onErrorContainer = StatusRejected,
    outline = SlateSecondary,
    outlineVariant = SlateLight.copy(alpha = 0.3f)
)

private val LightAppColorScheme = lightColorScheme(
    primary = CobaltBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF312E81),
    secondary = MintEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F2FE),
    onTertiaryContainer = Color(0xFF0369A1),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceElevatedHigh,
    error = StatusRejected,
    onError = Color.White,
    errorContainer = StatusRejected.copy(alpha = 0.12f),
    onErrorContainer = StatusRejected,
    outline = LightBorder,
    outlineVariant = LightBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = ThemeManager.isDarkMode,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkAppColorScheme
        else -> LightAppColorScheme
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}


