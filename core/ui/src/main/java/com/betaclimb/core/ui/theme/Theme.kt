package com.betaclimb.core.ui.theme

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

private val Chalk = Color(0xFFF5F3EF)
private val Granite = Color(0xFF2B2A28)
private val Crimp = Color(0xFFD1495B)
private val Jug = Color(0xFF2E86AB)
private val Sloper = Color(0xFFF2A65A)

private val LightScheme = lightColorScheme(
    primary = Jug,
    secondary = Crimp,
    tertiary = Sloper,
    background = Chalk,
)

private val DarkScheme = darkColorScheme(
    primary = Jug,
    secondary = Crimp,
    tertiary = Sloper,
    background = Granite,
)

@Composable
fun BetaClimbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
