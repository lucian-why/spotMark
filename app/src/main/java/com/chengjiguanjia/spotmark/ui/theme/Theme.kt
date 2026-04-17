package com.chengjiguanjia.spotmark.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = Candle80,
    secondary = Umber80,
    tertiary = Oxide80,
    background = Color(0xFF110D0A),
    surface = Color(0xFF2B1B13),
    surfaceVariant = Color(0xFF372216),
    primaryContainer = Color(0xFF4C2D15),
    secondaryContainer = Color(0xFF2F241D),
    tertiaryContainer = Color(0xFF4B2418),
    onPrimary = Color(0xFF20140D),
    onSecondary = Color(0xFF20140D),
    onTertiary = Color(0xFF20140D),
    onBackground = Color(0xFFF4E6CF),
    onSurface = Color(0xFFF4E6CF),
    onSurfaceVariant = Color(0xFFBCA78B),
    outline = Color(0xFF8C7052),
    outlineVariant = Color(0xFF6A4D35),
)

private val LightColorScheme = lightColorScheme(
    primary = Candle40,
    secondary = Umber40,
    tertiary = Oxide40,
    background = Color(0xFFF8F0E4),
    surface = Color(0xFFFFF8EE),
    primaryContainer = Color(0xFFFFE2A9),
    secondaryContainer = Color(0xFFEBD8BE),
    tertiaryContainer = Color(0xFFF4C1A7),

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun SpotMarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
