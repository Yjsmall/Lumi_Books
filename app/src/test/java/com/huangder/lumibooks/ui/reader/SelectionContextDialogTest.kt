package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.R
import com.huangder.lumibooks.translation.ContextDocument
import com.huangder.lumibooks.translation.ContextState
import com.huangder.lumibooks.ui.components.LiquidGlassDialogHost
import com.huangder.lumibooks.ui.theme.LocalAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SelectionContextDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingContextCannotBeSaved() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                SelectionContextDialog(ContextState(unavailable = true), onAnalyze = { fail("Unavailable context was analyzed") },
                    onRetry = {}, onRetrySave = {}, onSettings = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.selection_context_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.context_continue)).assertDoesNotExist()
    }

    @Test fun glassPreviewIsModalAndRestoresReaderAfterDismissal() {
        val context = RuntimeEnvironment.getApplication()
        val visible = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppTheme provides "liquid_glass") {
                    LiquidGlassDialogHost {
                        Text("Reader content")
                        if (visible.value) {
                            SelectionContextDialog(ContextState(document = ContextDocument("word", "A word in its paragraph.")),
                                onAnalyze = {}, onRetry = {}, onRetrySave = {}, onSettings = {},
                                onDismiss = { visible.value = false })
                        }
                    }
                }
            }
        }
        compose.onNode(isDialog()).assertExists().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, context.getString(R.string.selection_context))
        )
        compose.onNodeWithText("Reader content").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.reader_close)).performClick()
        compose.waitUntil { !visible.value }
        // The shared host retains the outgoing surface for its exit animation.
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithText("Reader content").assertIsDisplayed()
    }
}
