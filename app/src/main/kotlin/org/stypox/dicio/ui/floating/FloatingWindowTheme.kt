package org.stypox.dicio.ui.floating

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.stypox.dicio.ui.theme.*

/**
 * 专门为悬浮窗设计的主题，避免AppTheme中对Activity的依赖
 */
@Composable
fun FloatingWindowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = EnergyBlue,
            secondary = VioletGlow,
            tertiary = AuroraGreen,
            background = GalaxyGray,
            surface = GalaxyGray,
            onPrimary = NeonWhite,
            onSecondary = NeonWhite,
            onTertiary = DeepSpace,
            onBackground = NeonWhite,
            onSurface = NeonWhite
        )
    } else {
        lightColorScheme(
            primary = EnergyBlue,
            secondary = VioletGlow,
            tertiary = AuroraGreen,
            background = Color.White,
            surface = Color.White,
            onPrimary = DeepSpace,
            onSecondary = DeepSpace,
            onTertiary = DeepSpace,
            onBackground = DeepSpace,
            onSurface = DeepSpace
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}


