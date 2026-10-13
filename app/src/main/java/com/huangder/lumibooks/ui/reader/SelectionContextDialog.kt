package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import kotlinx.coroutines.CancellationException

@Composable
internal fun SelectionContextDialog(
    selectedText: String,
    loadContext: suspend () -> String?,
    onSave: (String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var paragraph by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        paragraph = try {
            loadContext()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        loading = false
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.selection_context)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(selectedText, style = MaterialTheme.typography.titleMedium)
                when {
                    loading -> Text(stringResource(R.string.selection_context_loading))
                    paragraph == null -> Text(stringResource(R.string.selection_context_unavailable))
                    else -> {
                        SelectionContainer { Text(paragraph.orEmpty()) }
                        Text(stringResource(R.string.selection_context_description), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (saveFailed) Text(stringResource(R.string.selection_context_save_failed), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !loading && !saving && paragraph != null, onClick = {
                val text = paragraph ?: return@TextButton
                saving = true
                saveFailed = false
                onSave(text) { success ->
                    saving = false
                    if (success) onDismiss() else saveFailed = true
                }
            }) { Text(stringResource(R.string.selection_context_save)) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
