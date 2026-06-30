package com.example.thoughtvault.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 品牌色
private val LightPrimary = Color(0xFF1A73E8)
private val DarkPrimary = Color(0xFF8AB4F8)
private val LightBackground = Color(0xFFFFFBFE)
private val DarkBackground = Color(0xFF1C1B1F)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFF5F6368),
    background = LightBackground,
    surface = LightBackground,
    surfaceVariant = Color(0xFFF1F3F4),
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF003D8B),
    primaryContainer = Color(0xFF004BA0),
    secondary = Color(0xFFBDC1C6),
    background = DarkBackground,
    surface = DarkBackground,
    surfaceVariant = Color(0xFF303134),
)

@Composable
fun ThoughtVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
        typography = Typography(),
        content = content
    )
}
