package com.huangder.lumibooks.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Clear controls retain the original reader toolbar optics without changing content cards. */
enum class LiquidGlassSurfaceStyle { Frosted, ClearControl }

/** Shared frosting: clarity changes the material without exposing sharp background lettering. */
internal object LiquidGlassMaterial {
    fun blurRadius(transparency: Float, style: LiquidGlassSurfaceStyle = LiquidGlassSurfaceStyle.Frosted) =
        if (style == LiquidGlassSurfaceStyle.ClearControl) (6f * (1f - transparency.coerceIn(0f, 1f))).dp
        else (20f - 8f * transparency.coerceIn(0f, 1f)).dp

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
