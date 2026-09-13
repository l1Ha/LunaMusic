package com.luna.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import com.luna.music.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6C4DF6),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFE6DEFF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF21005D),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFE8DEF8),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF1D192B),
    background = androidx.compose.ui.graphics.Color(0xFFF7F5FB),
    surface = androidx.compose.ui.graphics.Color(0xFFF7F5FB),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFE7E0EC),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF49454F),
)

private val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFCBBEFF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF32009A),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF4B2FB8),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE6DEFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF332D41),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFE8DEF8),
    background = androidx.compose.ui.graphics.Color(0xFF121216),
    surface = androidx.compose.ui.graphics.Color(0xFF121216),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2A2A30),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC9C5D1),
)

@Composable
fun LunaTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
