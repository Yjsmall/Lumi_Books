package com.huangder.lumibooks.ui.reader

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.huangder.lumibooks.R
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

    @Test fun saveWaitsForPersistenceAndKeepsPreviewOnFailure() {
        val context = RuntimeEnvironment.getApplication()
        var complete: ((Boolean) -> Unit)? = null
        var savedText: String? = null
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                SelectionContextDialog("word", { "A word in its paragraph." },
                    onSave = { text, result -> savedText = text; complete = result },
                    onDismiss = { dismissed = true })
            }
        }
        compose.onNodeWithText("A word in its paragraph.").assertIsDisplayed()
        val save = compose.onNodeWithText(context.getString(R.string.selection_context_save))
        save.performClick().assertIsNotEnabled()
        compose.runOnIdle {
            assertEquals("A word in its paragraph.", savedText)
            assertFalse(dismissed)
            complete!!(false)
        }
        compose.onNodeWithText(context.getString(R.string.selection_context_save_failed)).assertIsDisplayed()
        save.assertIsEnabled().performClick()
        compose.runOnIdle { complete!!(true) }
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun missingContextCannotBeSaved() {
        val context = RuntimeEnvironment.getApplication()
        compose.setContent {
            MaterialTheme {
                SelectionContextDialog("word", { null }, onSave = { _, _ -> fail("Unavailable context was saved") }, onDismiss = {})
            }
        }
        compose.onNodeWithText(context.getString(R.string.selection_context_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.selection_context_save)).assertIsNotEnabled()
    }
}
