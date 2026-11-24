package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = SaffronCoral,
    onPrimary = Color.White,
    primaryContainer = PeachBlush,
    onPrimaryContainer = DeepTerracotta,
    secondary = GoldenSesame,
    onSecondary = Color.White,
    secondaryContainer = WarmHoney,
    onSecondaryContainer = RoastedAmber,
    tertiary = BasilGreen,
    onTertiary = Color.White,
    tertiaryContainer = MintTint,
    onTertiaryContainer = DeepBasil,
    background = WarmCream,
    onBackground = DarkEspresso,
    surface = WarmCream,
    onSurface = DarkEspresso,
    surfaceVariant = WarmStoneContainer,
    onSurfaceVariant = WarmMutedInk,
    surfaceContainerLow = SoftBistroSurface,
    surfaceContainer = WarmStoneContainer,
    outline = WarmOutline,
    error = ChiliError,
    errorContainer = ChiliErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = EmberCoral,
    onPrimary = DarkEspresso,
    primaryContainer = DarkCoralContainer,
    onPrimaryContainer = PeachBlush,
    secondary = EmberGold,
    onSecondary = DarkEspresso,
    secondaryContainer = DarkGoldContainer,
    onSecondaryContainer = WarmHoney,
    tertiary = FreshBasilLight,
    onTertiary = DeepBasil,
    tertiaryContainer = DarkBasilContainer,
    onTertiaryContainer = MintTint,
    background = CharcoalEspresso,
    onBackground = WarmParchmentInk,
    surface = CharcoalSurface,
    onSurface = WarmParchmentInk,
    surfaceVariant = CharcoalSurfaceHigh,
    onSurfaceVariant = CharcoalMutedInk,
    surfaceContainerLow = CharcoalSurface,
    surfaceContainer = CharcoalSurfaceHigh,
    outline = CharcoalOutline,
    error = ChiliErrorContainer,
    errorContainer = ChiliError
)

val BunchizShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BunchizTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BunchizTypography,
        shapes = BunchizShapes,
        content = content
    )
}
