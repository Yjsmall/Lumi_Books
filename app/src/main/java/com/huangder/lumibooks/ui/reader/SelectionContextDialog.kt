package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.LiquidGlassDialog
import com.huangder.lumibooks.ui.components.LiquidGlassTextButton
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppRadius
import com.huangder.lumibooks.ui.theme.AppRoundedCornerShape
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SelectionContextDialog(
    selectedText: String,
    loadContext: suspend () -> String?,
    onSave: (String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop? = null
) {
    var paragraph by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val title = selectedText
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
    LiquidGlassDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        modifier = Modifier.semantics { paneTitle = title },
        backdrop = backdrop,
        shape = AppRoundedCornerShape(AppRadius.xl)
    ) {
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
                .padding(AppSpace.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpace.md)
        ) {
            Text(
                title,
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
            Column(
                Modifier.weight(1f, fill = false).fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpace.md)
            ) {
                when {
                    loading -> Text(stringResource(R.string.selection_context_loading),
                        fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
                    paragraph == null -> Text(stringResource(R.string.selection_context_unavailable),
                        fontSize = AppType.BodySmall, color = AppColors.TextSecondary)
                    else -> {
                        SelectionContainer {
                            Text(paragraph.orEmpty(), fontSize = AppType.Body,
                                color = AppColors.TextPrimary)
                        }
                    }
                }
                if (saveFailed) Text(stringResource(R.string.selection_context_save_failed),
                    fontSize = AppType.BodySmall, color = MaterialTheme.colorScheme.error)
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpace.sm, androidx.compose.ui.Alignment.End),
                verticalArrangement = Arrangement.spacedBy(AppSpace.sm)
            ) {
                LiquidGlassTextButton(
                    text = stringResource(R.string.cancel),
                    enabled = !saving,
                    onClick = onDismiss
                )
                LiquidGlassTextButton(
                    text = stringResource(R.string.selection_context_save),
                    tintedColor = AppColors.Accent,
                    enabled = !loading && !saving && paragraph != null,
                    onClick = {
                        val text = paragraph ?: return@LiquidGlassTextButton
                        saving = true
                        saveFailed = false
                        onSave(text) { success ->
                            saving = false
                            if (success) onDismiss() else saveFailed = true
                        }
                    }
                )
            }
        }
    }
}
