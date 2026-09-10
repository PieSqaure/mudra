package com.pisquarelabs.mudra.ui.theme

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

private val MudraLight = lightColorScheme(
    primary = Color(0xFF2E5E4E),
    secondary = Color(0xFF4C6B5A),
    background = Color(0xFFFBFDF9)
)

private val MudraDark = darkColorScheme(
    primary = Color(0xFF9ED9C0),
    secondary = Color(0xFFB2CDBE),
    background = Color(0xFF101410)
)

@Composable
fun MudraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> MudraDark
        else -> MudraLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
