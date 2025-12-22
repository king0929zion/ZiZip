package com.autoglm.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlack,
    onPrimary = PrimaryWhite,
    secondary = Accent,
    onSecondary = PrimaryWhite,
    background = Grey50,
    onBackground = PrimaryBlack,
    surface = PrimaryWhite,
    onSurface = PrimaryBlack,
    error = ErrorColor,
    onError = PrimaryWhite,
    outline = Grey200
)

@Composable
fun ZiZipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // For now, we force light theme logic or adapt slightly, but spec implies specific colors
    // Dynamic color is available on Android 12+ but we want strictly defined palette
    content: @Composable () -> Unit
) {
    // Force Light Color Scheme effectively as per design doc, or create a Dark one if needed later.
    // For now, based on "Less is More - low saturation, warm beige", it implies a light theme.
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb() // Or transparent
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZiZipTypography,
        content = content
    )
}
