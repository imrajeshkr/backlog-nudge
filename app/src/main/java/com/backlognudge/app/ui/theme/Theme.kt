package com.backlognudge.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Purple = Color(0xFF5B4FE9)
private val PurpleDark = Color(0xFF9C8CFF)

private val LightColors = lightColorScheme(
    primary = Purple,
    secondary = Color(0xFF00A389),
    background = Color(0xFFFBFAFF),
    surface = Color(0xFFFFFFFF)
)

private val DarkColors = darkColorScheme(
    primary = PurpleDark,
    secondary = Color(0xFF35C9AA),
    background = Color(0xFF121016),
    surface = Color(0xFF1C1730)
)

@Composable
fun BacklogNudgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
