package com.huangder.lumibooks.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.R
import com.huangder.lumibooks.translation.*
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SelectionContextCardTest {
    @get:Rule val compose = createComposeRule()
    private val document = ContextDocument("bank", "He sat on the bank.", "河岸", "他坐在河岸上。")

    @Test fun reducedMotionShowsLoadingTextWithoutAnAnimatedSpinner() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMotionEnabled provides false) {
                    SelectionContextDialog(ContextState(document = document, configured = true, loadingParts = setOf(ContextPart.GRAMMAR)),
                        onAnalyze = {}, onRetry = {}, onRetrySave = {}, onDismiss = {})
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.context_analyzing)).performScrollTo().assertIsDisplayed()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
        compose.onNodeWithContentDescription(context.getString(R.string.context_analysis_menu)).assertIsNotEnabled()
    }

    @Test @Config(qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun lumiDarkUsesTheSameActionsAndMenu() = renderTheme("lumi-dark", "lumi", dark = true)

    @Test @Config(qualifiers = "en-rUS-w411dp-h891dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun materialLightUsesTheSameActionsAndMenu() = renderTheme("material-light", "material3")

    @Test @Config(qualifiers = "en-rUS-w411dp-h891dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun materialDarkUsesTheSameActionsAndMenu() = renderTheme("material-dark", "material3", dark = true)

    @Test @Config(qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun glassLightKeepsItsHostedCardAndMenu() = renderTheme("glass-light", "liquid_glass")

    @Test @Config(qualifiers = "en-rUS-w411dp-h891dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun glassDarkKeepsItsHostedCardAndMenu() = renderTheme("glass-dark", "liquid_glass", dark = true)

    @Test @Config(qualifiers = "en-rUS-w411dp-h891dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun reducedMotionKeepsTheManualActions() = renderTheme("reduced-motion", "lumi", motion = false)

    @Test @Config(qualifiers = "en-rUS-w411dp-h891dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun einkKeepsTheManualActions() = renderTheme("eink", "lumi", eink = true, motion = false)

    @Test @Config(qualifiers = "en-rUS-w411dp-h891dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun rtlKeepsTheManualActions() = renderTheme("rtl", "lumi", rtl = true)

    @Test @Config(sdk = [28], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun olderAndroidKeepsTheSolidCardAndManualMenu() = renderTheme("api28", "lumi")

    private fun renderTheme(name: String, theme: String, dark: Boolean = false, eink: Boolean = false, motion: Boolean = true, rtl: Boolean = false) {
        val context = RuntimeEnvironment.getApplication()
        var rootView: View? = null
        var selections = 0
        compose.setContent {
            val colors = if (dark) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(
                    LocalAppTheme provides theme,
                    LocalUseMaterial3Theme provides (theme == "material3"),
                    LocalIsDarkTheme provides dark,
                    LocalEInkMode provides eink,
                    LocalMotionEnabled provides motion,
                    LocalAppAccentColor provides when { eink -> Color.Black; theme == "material3" -> colors.primary; else -> LocalAppAccentColor.current },
                    LocalOnAppAccentColor provides if (theme == "material3") colors.onPrimary else Color.White,
                    LocalLiquidGlassCapability provides LiquidGlassCapability(theme == "liquid_glass", false),
                    LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    rootView = LocalView.current
                    val backdrop = rememberLayerBackdrop()
                    LiquidGlassDialogHost(Modifier.fillMaxSize(), backdrop = backdrop) {
                        Box(Modifier.fillMaxSize().background(AppColors.WindowBg).layerBackdrop(backdrop)) { Text("Reader preview") }
                        SelectionContextDialog(ContextState(document = document, configured = true, saved = true),
                            onAnalyze = { selections++ }, onRetry = {}, onRetrySave = {}, onDismiss = {}, backdrop = backdrop)
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.context_analysis_menu))
            .assertIsDisplayed().assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
        compose.onNodeWithContentDescription(context.getString(R.string.translation_settings)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.reader_close)).assertDoesNotExist()
        fun snapshot(suffix: String) = compose.runOnIdle {
            val view = if (theme == "liquid_glass") rootView!! else ShadowDialog.getLatestDialog().window!!.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/context-ui/$name-$suffix.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        snapshot("card")
        val before = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription(context.getString(R.string.context_analysis_menu)).performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        assertEquals(before, compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText(context.getString(R.string.context_grammar)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.context_vocabulary)).assertIsDisplayed()
        snapshot("menu")
        compose.onNodeWithText(context.getString(R.string.context_grammar)).performClick()
        compose.runOnIdle { assertEquals(1, selections) }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi")
    fun continuedAnalysisUsesAnOverlayWithoutChangingCardBounds() {
        val context = RuntimeEnvironment.getApplication()
        var selections = 0
        compose.setContent {
            MaterialTheme {
                SelectionContextDialog(ContextState(document = document, configured = true, saved = true),
                    onAnalyze = { selections++ }, onRetry = {}, onRetrySave = {}, onDismiss = {})
            }
        }
        val before = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription(context.getString(R.string.context_analysis_menu)).performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.waitForIdle()
        val after = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        assertEquals("Opening a menu must not move or resize the context card", before, after)
        compose.onNodeWithText(context.getString(R.string.context_grammar)).performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(1, selections) }
        compose.onNodeWithText(context.getString(R.string.context_grammar)).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "en-rUS-w640dp-h360dp-hdpi")
    fun compactLargeTextCardKeepsHeaderActionsAndLongResultsReachable() {
        val context = RuntimeEnvironment.getApplication()
        val longDocument = document.copy(grammar = List(20) { "A clause explains how the sentence works." }.joinToString("\n"))
        compose.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    SelectionContextDialog(ContextState(document = longDocument, configured = true, saved = true),
                        onAnalyze = {}, onRetry = {}, onRetrySave = {}, onDismiss = {})
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.context_analysis_menu)).assertIsDisplayed()
        compose.onNodeWithText(longDocument.grammar!!).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.context_analysis_menu)).assertIsDisplayed()
    }

    @Test fun backDismissesTheMenuBeforeTheContextCard() {
        val context = RuntimeEnvironment.getApplication()
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                SelectionContextDialog(ContextState(document = document, configured = true, saved = true),
                    onAnalyze = {}, onRetry = {}, onRetrySave = {}, onDismiss = { dismissed = true })
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.context_analysis_menu)).performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertFalse(dismissed) }
        compose.onNodeWithText(context.getString(R.string.context_grammar)).assertDoesNotExist()
        compose.onNode(isDialog()).assertExists()
        compose.runOnIdle { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun displaysSeparateSectionsAndManualOptionsWhenAutomaticOptionsAreOff() {
        val context = RuntimeEnvironment.getApplication()
        var requested: ContextPart? = null
        compose.setContent {
            MaterialTheme {
                SelectionContextDialog(ContextState(document = document, configured = true, saved = true),
                    onAnalyze = { requested = it }, onRetry = {}, onRetrySave = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("bank").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.context_saved)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.context_regenerate_basic)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.context_continue)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.context_automatic_hint)).assertDoesNotExist()
        compose.onNodeWithText("He sat on the bank.").assertIsDisplayed()
        compose.onNodeWithText("河岸").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("他坐在河岸上。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.context_analysis_menu)).performClick()
        compose.onNodeWithText(context.getString(R.string.context_analyze)).performClick()
        compose.runOnIdle { assertEquals(ContextPart.BASIC, requested) }
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithContentDescription(context.getString(R.string.context_analysis_menu)).performClick()
        compose.onNodeWithText(context.getString(R.string.context_grammar)).performClick()
        compose.runOnIdle { assertEquals(ContextPart.GRAMMAR, requested) }
    }

    @Test fun failedSaveShowsRetryAndKeepsGeneratedResultsVisible() {
        val context = RuntimeEnvironment.getApplication()
        var retried = false
        compose.setContent {
            MaterialTheme {
                SelectionContextDialog(ContextState(document = document, saveFailed = true), onAnalyze = {},
                    onRetry = {}, onRetrySave = { retried = true }, onDismiss = {})
            }
        }
        compose.onNodeWithText("河岸").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.context_retry_save)).performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp-xxhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun contextCardShowsItsSectionsAndContinuedAnalysisOnPhone() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                SelectionContextDialog(ContextState(document = document, configured = true, saved = true),
                    onAnalyze = {}, onRetry = {}, onRetrySave = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("河岸").assertIsDisplayed()
        compose.onNodeWithText("他坐在河岸上。").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.context_analysis_menu)).performClick()
        compose.onNodeWithText(context.getString(R.string.context_grammar)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.context_vocabulary)).assertIsDisplayed()
        compose.runOnIdle {
            val view = ShadowDialog.getLatestDialog().window!!.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/context/context-card.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
