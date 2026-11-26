package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = SilangBlue,
    onPrimary = SilangWhite,
    primaryContainer = SilangBlueContainer,
    onPrimaryContainer = SilangBlueDark,
    secondary = SilangOrange,
    onSecondary = SilangWhite,
    secondaryContainer = SilangOrangeContainer,
    onSecondaryContainer = SilangSlate900,
    tertiary = SilangGreen,
    onTertiary = SilangWhite,
    tertiaryContainer = SilangGreenContainer,
    onTertiaryContainer = SilangSlate900,
    background = SilangSurface,
    onBackground = SilangSlate900,
    surface = SilangWhite,
    onSurface = SilangSlate900,
    surfaceVariant = SilangSlate200,
    onSurfaceVariant = SilangSlate600,
    outline = SilangSlate200,
    error = SilangRed,
    onError = SilangWhite,
    errorContainer = SilangRedContainer,
    onErrorContainer = SilangRed
)

private val DarkColorScheme = darkColorScheme(
    primary = SilangOrangeBright,
    onPrimary = SilangSlate950,
    primaryContainer = SilangSlate800,
    onPrimaryContainer = SilangWhite,
    secondary = SilangBlue,
    onSecondary = SilangWhite,
    tertiary = SilangGreen,
    onTertiary = SilangWhite,
    background = SilangSlate950,
    onBackground = SilangWhite,
    surface = SilangSlate900,
    onSurface = SilangWhite,
    surfaceVariant = SilangSlate800,
    onSurfaceVariant = SilangSlate400,
    outline = SilangSlate800,
    error = SilangRed,
    onError = SilangWhite
)

val SilangShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SilangMiniMartTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = SilangTypography,
        shapes = SilangShapes,
        content = content
    )
}
