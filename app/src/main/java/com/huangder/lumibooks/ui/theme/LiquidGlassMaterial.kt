package com.huangder.lumibooks.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared frosting: clarity changes the material without exposing sharp background lettering. */
internal object LiquidGlassMaterial {
    fun blurRadius(transparency: Float) = (20f - 8f * transparency.coerceIn(0f, 1f)).dp

    fun surfaceTint(
        color: Color,
        transparency: Float,
        contentSurface: Boolean = true,
        minimumAlpha: Float = 0f
    ): Color {
        val clarity = transparency.coerceIn(0f, 1f)
        val alpha = if (contentSurface) 0.94f - 0.10f * clarity else 0.86f - 0.14f * clarity
        return color.copy(alpha = maxOf(alpha, minimumAlpha).coerceIn(0f, 1f))
    }

    fun secondaryTextColor(dark: Boolean): Color =
        if (dark) Color(0xFFB8B8C0) else Color(0xFF54545C)
}
