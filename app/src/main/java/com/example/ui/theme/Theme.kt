package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Modo Oscuro Cómodo (Eye-Care Dark Mode)
// Diseñado para lectura nocturna prolongada sin fatiga
private val EyeComfortDarkColorScheme = darkColorScheme(
    primary = BrandNavyLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF263544),
    onPrimaryContainer = Color(0xFFD6E2ED),
    secondary = BrandTealLight,
    onSecondary = Color(0xFF13171D),
    secondaryContainer = Color(0xFF203D32),
    onSecondaryContainer = Color(0xFFD2EADE),
    tertiary = Color(0xFFC7925B),
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    background = BackgroundDark,
    onBackground = TextPrimaryDark,
    error = StatusMorosoRedDark,
    onError = Color(0xFF381010),
    errorContainer = StatusMorosoRedBgDark,
    onErrorContainer = Color(0xFFFFCDD2),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDark
)

// Modo Claro Cómodo (Warm Paper / Eye-Care Light Mode)
// Suprime el blanco fosforescente con un tono marfil mate suave
private val EyeComfortLightColorScheme = lightColorScheme(
    primary = BrandNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EBF1),
    onPrimaryContainer = BrandNavy,
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2EFE9),
    onSecondaryContainer = BrandTeal,
    tertiary = BrandAmber,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    error = StatusMorosoRed,
    onError = Color.White,
    errorContainer = StatusMorosoRedBg,
    onErrorContainer = StatusMorosoRed,
    outline = BorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) EyeComfortDarkColorScheme else EyeComfortLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
