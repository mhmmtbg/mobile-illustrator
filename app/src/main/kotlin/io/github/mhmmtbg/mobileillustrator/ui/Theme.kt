package io.github.mhmmtbg.mobileillustrator.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val Pasteboard = Color(0xFF3A3A3D)
    val Panel = Color(0xFF242426)
    val PanelRaised = Color(0xFF323235)
    val Divider = Color(0xFF151516)
    val Accent = Color(0xFFFF9A3C)
    val OnPanel = Color(0xFFE6E6E8)
    val OnPanelMuted = Color(0xFF9A9AA0)
    val Selection = Color(0xFF3B9BFF)
}

private val Scheme = darkColorScheme(
    primary = AppColors.Accent,
    onPrimary = Color(0xFF2B1A0E),
    background = AppColors.Pasteboard,
    onBackground = AppColors.OnPanel,
    surface = AppColors.Panel,
    onSurface = AppColors.OnPanel,
    surfaceVariant = AppColors.PanelRaised,
    onSurfaceVariant = AppColors.OnPanelMuted,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
