package com.jiotvplus.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

private val DarkColorScheme = darkColorScheme(
    primary = JioBlue,
    primaryContainer = JioBlueDark,
    secondary = JioBlueLight,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariant,
    onPrimary = OnBackground,
    onBackground = OnBackground,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 48.sp, color = OnBackground),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, color = OnBackground),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = OnBackground),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = OnBackground),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, color = OnSurface),
    bodyLarge = TextStyle(fontSize = 16.sp, color = OnSurface),
    bodyMedium = TextStyle(fontSize = 14.sp, color = OnSurfaceVariant),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = OnBackground),
    labelMedium = TextStyle(fontSize = 12.sp, color = OnSurfaceVariant)
)

@Composable
fun JioTVPlusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
