package com.backlognudge.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * "Green Light" palette. The app is achromatic — ink, bone and three greys —
 * and green is spent on exactly two things: GOING (primary buttons, the nudge)
 * and FINISHING (the completion tick). Green never appears on headers, icons,
 * chips, section labels, warnings or decoration; use a grey there instead.
 */
object GreenLight {
    // Dark ("night", the primary theme — this app is used at night)
    val DarkBackground = Color(0xFF0C100E)
    val DarkSurface = Color(0xFF141916)
    val DarkSurfaceVariant = Color(0xFF1C231E)
    val DarkHairline = Color(0x14FFFFFF) // white @ 8%
    val DarkText = Color(0xFFEAF0EA)
    val DarkMuted = Color(0xFF8E978F)
    val DarkFaint = Color(0xFF5D665F)
    val DarkGo = Color(0xFF3FD98B)
    val DarkOnGo = Color(0xFF041209)

    // Light ("paper")
    val LightBackground = Color(0xFFF1EFE8)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceVariant = Color(0xFFE6E4DB)
    val LightHairline = Color(0x1A000000) // black @ 10%
    val LightText = Color(0xFF0B0F0C)
    val LightMuted = Color(0xFF5D635C)
    val LightFaint = Color(0xFF878E86)
    val LightGo = Color(0xFF11683F)
    val LightOnGo = Color(0xFFFFFFFF)
}

/**
 * Tokens Material 3's ColorScheme has no slot for. `faint` is the third grey,
 * used for bucket headers and meta lines; `hairline` is the only border colour.
 */
data class ExtraColors(
    val faint: Color,
    val hairline: Color,
    val isDark: Boolean
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(faint = GreenLight.DarkFaint, hairline = GreenLight.DarkHairline, isDark = true)
}

private val DarkColors = darkColorScheme(
    primary = GreenLight.DarkGo,
    onPrimary = GreenLight.DarkOnGo,
    // Deliberately NOT green: secondary/tertiary are greys so stray M3 defaults
    // (selection, chips, sliders' inactive tracks) can't leak colour.
    secondary = GreenLight.DarkMuted,
    onSecondary = GreenLight.DarkBackground,
    tertiary = GreenLight.DarkMuted,
    onTertiary = GreenLight.DarkBackground,
    background = GreenLight.DarkBackground,
    onBackground = GreenLight.DarkText,
    surface = GreenLight.DarkSurface,
    onSurface = GreenLight.DarkText,
    surfaceVariant = GreenLight.DarkSurfaceVariant,
    onSurfaceVariant = GreenLight.DarkMuted,
    secondaryContainer = GreenLight.DarkSurfaceVariant,
    onSecondaryContainer = GreenLight.DarkText,
    primaryContainer = GreenLight.DarkSurfaceVariant,
    onPrimaryContainer = GreenLight.DarkText,
    outline = GreenLight.DarkHairline,
    outlineVariant = GreenLight.DarkHairline,
    error = GreenLight.DarkMuted,
    onError = GreenLight.DarkBackground,
    errorContainer = GreenLight.DarkSurfaceVariant,
    onErrorContainer = GreenLight.DarkText,
    scrim = Color(0xCC000000)
)

private val LightColors = lightColorScheme(
    primary = GreenLight.LightGo,
    onPrimary = GreenLight.LightOnGo,
    secondary = GreenLight.LightMuted,
    onSecondary = GreenLight.LightSurface,
    tertiary = GreenLight.LightMuted,
    onTertiary = GreenLight.LightSurface,
    background = GreenLight.LightBackground,
    onBackground = GreenLight.LightText,
    surface = GreenLight.LightSurface,
    onSurface = GreenLight.LightText,
    surfaceVariant = GreenLight.LightSurfaceVariant,
    onSurfaceVariant = GreenLight.LightMuted,
    secondaryContainer = GreenLight.LightSurfaceVariant,
    onSecondaryContainer = GreenLight.LightText,
    primaryContainer = GreenLight.LightSurfaceVariant,
    onPrimaryContainer = GreenLight.LightText,
    outline = GreenLight.LightHairline,
    outlineVariant = GreenLight.LightHairline,
    error = GreenLight.LightMuted,
    onError = GreenLight.LightSurface,
    errorContainer = GreenLight.LightSurfaceVariant,
    onErrorContainer = GreenLight.LightText,
    scrim = Color(0x99000000)
)

@Composable
fun BacklogNudgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extras = if (darkTheme) {
        ExtraColors(GreenLight.DarkFaint, GreenLight.DarkHairline, isDark = true)
    } else {
        ExtraColors(GreenLight.LightFaint, GreenLight.LightHairline, isDark = false)
    }

    // Status bar matches the background rather than being a coloured brand bar.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(LocalExtraColors provides extras) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BacklogTypography,
            content = content
        )
    }
}
