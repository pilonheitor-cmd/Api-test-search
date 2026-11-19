package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CivicEmeraldPrimaryDark,
    onPrimary = CivicOnPrimaryDark,
    primaryContainer = CivicPrimaryContainerDark,
    onPrimaryContainer = CivicOnPrimaryContainerDark,
    secondary = CivicTealSecondaryDark,
    onSecondary = CivicOnSecondaryDark,
    secondaryContainer = CivicSecondaryContainerDark,
    onSecondaryContainer = CivicOnSecondaryContainerDark,
    tertiary = CivicGoldTertiaryDark,
    onTertiary = CivicOnTertiaryDark,
    tertiaryContainer = CivicTertiaryContainerDark,
    onTertiaryContainer = CivicOnTertiaryContainerDark,
    background = CivicBackgroundDark,
    onBackground = CivicOnBackgroundDark,
    surface = CivicSurfaceDark,
    onSurface = CivicOnSurfaceDark,
    surfaceVariant = CivicSurfaceVariantDark,
    onSurfaceVariant = CivicOnSurfaceVariantDark,
    outline = CivicOutlineDark,
    error = StatusErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = CivicEmeraldPrimary,
    onPrimary = CivicOnPrimary,
    primaryContainer = CivicPrimaryContainer,
    onPrimaryContainer = CivicOnPrimaryContainer,
    secondary = CivicTealSecondary,
    onSecondary = CivicOnSecondary,
    secondaryContainer = CivicSecondaryContainer,
    onSecondaryContainer = CivicOnSecondaryContainer,
    tertiary = CivicGoldTertiary,
    onTertiary = CivicOnTertiary,
    tertiaryContainer = CivicTertiaryContainer,
    onTertiaryContainer = CivicOnTertiaryContainer,
    background = CivicBackgroundLight,
    onBackground = CivicOnBackgroundLight,
    surface = CivicSurfaceLight,
    onSurface = CivicOnSurfaceLight,
    surfaceVariant = CivicSurfaceVariantLight,
    onSurfaceVariant = CivicOnSurfaceVariantLight,
    outline = CivicOutlineLight,
    error = StatusErrorLight
)

val CivicShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ConsultaPublicaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CivicShapes,
        content = content
    )
}
