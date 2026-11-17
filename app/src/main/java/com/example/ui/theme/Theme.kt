package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalStudyTheme = staticCompositionLocalOf { StudyThemeOption.BUNTS_ROYAL_LIGHT }

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LearnTalentTheme(
    themeOption: StudyThemeOption = StudyThemeOption.BUNTS_ROYAL_LIGHT,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (themeOption.isLight) {
        lightColorScheme(
            primary = themeOption.primary,
            onPrimary = themeOption.onPrimary,
            primaryContainer = themeOption.primaryContainer,
            onPrimaryContainer = themeOption.onPrimaryContainer,
            secondary = themeOption.secondary,
            onSecondary = themeOption.onSecondary,
            secondaryContainer = themeOption.secondaryContainer,
            onSecondaryContainer = themeOption.onSecondaryContainer,
            tertiary = themeOption.tertiary,
            background = themeOption.background,
            onBackground = themeOption.onBackground,
            surface = themeOption.surface,
            onSurface = themeOption.onSurface,
            surfaceVariant = themeOption.surfaceElevated,
            onSurfaceVariant = themeOption.onSurfaceVariant,
            outline = themeOption.outline,
            error = themeOption.error,
        )
    } else {
        darkColorScheme(
            primary = themeOption.primary,
            onPrimary = themeOption.onPrimary,
            primaryContainer = themeOption.primaryContainer,
            onPrimaryContainer = themeOption.onPrimaryContainer,
            secondary = themeOption.secondary,
            onSecondary = themeOption.onSecondary,
            secondaryContainer = themeOption.secondaryContainer,
            onSecondaryContainer = themeOption.onSecondaryContainer,
            tertiary = themeOption.tertiary,
            background = themeOption.background,
            onBackground = themeOption.onBackground,
            surface = themeOption.surface,
            onSurface = themeOption.onSurface,
            surfaceVariant = themeOption.surfaceElevated,
            onSurfaceVariant = themeOption.onSurfaceVariant,
            outline = themeOption.outline,
            error = themeOption.error,
        )
    }

    CompositionLocalProvider(LocalStudyTheme provides themeOption) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
