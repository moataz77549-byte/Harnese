package com.jarves.mh.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val PocketOrange = Color(0xFF159B91)
val PocketBlue = Color(0xFFA4B8FF)
val PocketGreen = Color(0xFF72D6A4)
val PocketBackground = Color(0xFF0C1424)
val PocketSurface = Color(0xFF162238)
val PocketSurfaceVariant = Color(0xFF223149)
val PocketOutline = Color(0xFF34465D)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FE4D0),
    onPrimary = Color(0xFF062B28),
    primaryContainer = Color(0xFF164843),
    onPrimaryContainer = Color(0xFFBDFFF2),
    secondary = PocketBlue,
    onSecondary = Color(0xFF001F58),
    tertiary = PocketGreen,
    onTertiary = Color(0xFF00391E),
    background = PocketBackground,
    onBackground = Color(0xFFEBF3FC),
    surface = PocketSurface,
    onSurface = Color(0xFFEBF3FC),
    surfaceVariant = PocketSurfaceVariant,
    onSurfaceVariant = Color(0xFFABBBD0),
    outline = PocketOutline,
    outlineVariant = Color(0xFF43556D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00796F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCEF5EE),
    onPrimaryContainer = Color(0xFF073C37),
    secondary = Color(0xFF4157A6),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF1B8A5A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF3F6FA),
    onBackground = Color(0xFF17273C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17273C),
    surfaceVariant = Color(0xFFE5EDF5),
    onSurfaceVariant = Color(0xFF465A71),
    outline = Color(0xFFBDC9D7),
    outlineVariant = Color(0xFFCED9E4),
)

enum class AppThemeMode { SYSTEM, DARK, LIGHT }

@Composable
fun PocketTheme(themeMode: AppThemeMode = AppThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val isDark = when (themeMode) {
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = if (isDark) DarkColors else LightColors,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)),
        content = content,
    )
}
