package com.huangder.lumibooks.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.ui.theme.*
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiquidGlassReadabilityRenderTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightContentKeepsSecondaryLabelsReadableAcrossTheWholeSurface() = verifyContent(false)
    @Test fun darkContentKeepsSecondaryLabelsReadableAcrossTheWholeSurface() = verifyContent(true)

    private fun verifyContent(dark: Boolean) {
        lateinit var view: View
        var label = Color.Unspecified
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(
                LocalAppTheme provides "liquid_glass",
                LocalIsDarkTheme provides dark,
                LocalLiquidGlassTransparency provides 1f,
                LocalLiquidGlassCapability provides LiquidGlassCapability(true, false)
            ) {
                label = AppColors.TextSecondary
                // Opposite-tone content is the worst case for translucent surfaces.
                Box(Modifier.background(if (dark) Color.White else Color.Black).padding(12.dp)) {
                    LiquidGlassSurface(
                        shape = AppShapes.rounded(22.dp),
                        fallbackColor = AppColors.CardBg,
                        contentScrimColor = AppColors.CardBg.copy(alpha = 0.78f),
                        modifier = Modifier.size(240.dp, 220.dp).testTag("surface"),
                        contentAlignment = androidx.compose.ui.Alignment.TopStart
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("上下文", color = AppColors.TextPrimary)
                            Text("此处词义 · 句子翻译", color = label)
                        }
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("surface").fetchSemanticsNode().boundsInRoot
        lateinit var bitmap: Bitmap
        compose.runOnIdle {
            bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
        }
        try {
            val output = File("build/reports/glass-readability").apply { mkdirs() }
            File(output, if (dark) "dark-content.png" else "light-content.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            // Sample clear areas to the right of the labels, away from the optical rim.
            val x = (bounds.left + bounds.width * 0.90f).toInt()
            listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                val y = (bounds.top + bounds.height * fraction).toInt()
                val surface = Color(bitmap.getPixel(x, y))
                val ratio = contrast(label, surface)
                assertTrue("Secondary label contrast at $fraction must reach 4.5:1, got $ratio", ratio >= 4.5f)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
