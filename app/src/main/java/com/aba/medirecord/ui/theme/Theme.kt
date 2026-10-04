package com.aba.medirecord.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val HealthLightColorScheme = lightColorScheme(
    primary = HealthPrimary,
    onPrimary = HealthOnPrimary,
    primaryContainer = HealthPrimaryContainer,
    onPrimaryContainer = HealthOnPrimaryContainer,
    secondary = HealthSecondary,
    onSecondary = HealthOnSecondary,
    secondaryContainer = HealthSecondaryContainer,
    onSecondaryContainer = HealthOnSecondaryContainer,
    background = HealthBackground,
    surface = HealthSurface,
    onSurface = HealthOnSurface,
    onSurfaceVariant = HealthOnSurfaceVariant
)

private val HealthDarkColorScheme = darkColorScheme(
    primary = HealthPrimaryDark,
    onPrimary = HealthOnPrimaryDark,
    primaryContainer = HealthPrimaryContainerDark,
    onPrimaryContainer = HealthOnPrimaryContainerDark,
    background = HealthBackgroundDark,
    surface = HealthSurfaceDark
)

@Composable
fun MediRecordTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HealthDarkColorScheme else HealthLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
