package com.huangder.lumibooks.ui.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.translation.*
import com.huangder.lumibooks.ui.components.*
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.*
import com.kyant.backdrop.Backdrop

@Composable
internal fun ContextNoteDialog(
    note: Note,
    loadContext: suspend () -> String? = { ContextDocument.fromJson(note.contextJson)?.sentence },
    onDismiss: () -> Unit,
    backdrop: Backdrop? = null,
    viewModel: ContextViewModel = hiltViewModel()
) {
    val currentLoader by rememberUpdatedState(loadContext)
    LaunchedEffect(note) { viewModel.open(note) { currentLoader() } }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }
    val state by viewModel.state.collectAsState()
    SelectionContextDialog(state, viewModel::analyze, viewModel::retry, viewModel::retrySave,
        onDismiss = onDismiss, backdrop = backdrop)
}

@Composable
internal fun SelectionContextDialog(
    state: ContextState,
    onAnalyze: (ContextPart) -> Unit,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: Backdrop? = null
) {
    val title = stringResource(R.string.selection_context)
    var menuHost by remember { mutableStateOf<LiquidGlassMenuHostState?>(null) }
    val currentState by rememberUpdatedState(state)
    LiquidGlassDialog(
        onDismissRequest = { if (menuHost?.activeMenu != null) menuHost?.dismiss() else onDismiss() },
        modifier = Modifier.semantics { paneTitle = title },
        backdrop = backdrop,
        forceFallback = true,
        shape = AppRoundedCornerShape(AppRadius.xl)
    ) {
        LiquidGlassMenuHost(Modifier.fillMaxWidth(), backdrop = backdrop, sizeToContent = true) {
            // Read when the hosted card is rendered, including its first layout pass.
            // Capturing the initial state can leave the overlay showing stale loading UI.
            val dialogState = currentState
            val document = dialogState.document
            val busy = dialogState.loadingParts.isNotEmpty()
            val host = LocalLiquidGlassMenuHost.current
            SideEffect { menuHost = host }
            Column(
                Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
                    .padding(AppSpace.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpace.md)
            ) {
                Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
                ) {
                    Text(document?.selectedText ?: title, Modifier.weight(1f), fontSize = AppType.Section, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = AppColors.TextPrimary)
                    if (document != null) ContextAnalysisMenu(dialogState.configured && !busy, onAnalyze)
                }
                ContextContent(dialogState, onRetry, Modifier.weight(1f, fill = false).fillMaxWidth())
                if (dialogState.saveFailed) ContextSaveError(onRetrySave)
            }
        }
    }
}

@Composable
private fun ContextContent(state: ContextState, onRetry: () -> Unit, modifier: Modifier) {
    val document = state.document
    val busy = state.loadingParts.isNotEmpty()
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(AppSpace.md)) {
        when {
            state.loading -> Text(stringResource(R.string.selection_context_loading), color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
            state.unavailable -> Text(stringResource(R.string.selection_context_unavailable), color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
            document != null -> {
                ContextSection(stringResource(R.string.context_sentence), document.sentence, source = true)
                document.meaning?.let { ContextSection(stringResource(R.string.context_meaning), it) }
                document.translation?.let { ContextSection(stringResource(R.string.context_translation), it) }
                document.grammar?.let { ContextSection(stringResource(R.string.context_grammar), it) }
                document.vocabulary?.let { ContextSection(stringResource(R.string.context_vocabulary), it) }
                if (busy) Row(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
                ) {
                    if (LocalMotionEnabled.current && !LocalEInkMode.current) {
                        CircularProgressIndicator(Modifier.size(AppSpace.md), strokeWidth = 1.5.dp, color = AppColors.Accent)
                    }
                    Text(stringResource(R.string.context_analyzing), color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
                }
                state.error?.let {
                    Text(stringResource(it.messageResource()), color = MaterialTheme.colorScheme.error, fontSize = AppType.BodySmall)
                    LiquidGlassTextButton(stringResource(R.string.translation_retry), onRetry, enabled = !busy)
                }
                if (!state.configured) Text(stringResource(R.string.context_configuration_hint),
                    color = AppColors.TextSecondary, fontSize = AppType.BodySmall)
            }
        }
    }
}

@Composable
private fun ContextSaveError(onRetrySave: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.sm)
    ) {
        Text(
            stringResource(R.string.selection_context_save_failed),
            modifier = Modifier.weight(1f), fontSize = AppType.Caption,
            color = MaterialTheme.colorScheme.error
        )
        LiquidGlassTextButton(stringResource(R.string.context_retry_save), onRetrySave)
    }
}

@Composable
private fun ContextAnalysisMenu(enabled: Boolean, onAnalyze: (ContextPart) -> Unit, modifier: Modifier = Modifier) {
    val host = LocalLiquidGlassMenuHost.current
    val sourceId = remember { Any() }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val currentAnalyze by rememberUpdatedState(onAnalyze)
    val currentEnabled by rememberUpdatedState(enabled)
    val items = listOf(
        ContextPart.BASIC to R.string.context_analyze,
        ContextPart.GRAMMAR to R.string.context_grammar,
        ContextPart.VOCABULARY to R.string.context_vocabulary
    ).map { (part, label) ->
        LiquidGlassMenuItem(stringResource(label), onClick = { if (currentEnabled) currentAnalyze(part) })
    }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
    val labels = items.map { it.label }
    val menuWidth = remember(labels, textMeasurer, labelStyle, density) {
        with(density) {
            labels.maxOf { textMeasurer.measure(it, labelStyle, softWrap = false).size.width }.toDp()
        } + AppSpace.lg * 2
    }
    LaunchedEffect(enabled) { if (!enabled) host?.dismiss() }
    LiquidGlassIconButton(AppIcons.Lightning, stringResource(R.string.context_analysis_menu),
        modifier = modifier.liquidGlassMenuAnchor(sourceId).onGloballyPositioned { bounds = it.boundsInRoot() },
        enabled = enabled && host != null, size = 44.dp, normalContainerColor = AppColors.BgGray,
        onClick = {
            host?.toggle(LiquidGlassMenuSpec(anchorBounds = bounds, width = menuWidth,
                items = items, sourceId = sourceId, preferAbove = false, keepTriggerInteractive = false))
        })
}

@Composable
private fun ContextSection(label: String, text: String, source: Boolean = false) {
    val shape = AppRoundedCornerShape(AppRadius.md)
    Column(
        modifier = if (source) Modifier.fillMaxWidth().clip(shape).background(AppColors.BgGray).padding(AppSpace.md) else Modifier,
        verticalArrangement = Arrangement.spacedBy(AppSpace.xs)
    ) {
        Text(label, color = AppColors.TextSecondary, fontSize = AppType.BodySmall, fontWeight = FontWeight.SemiBold)
        SelectionContainer { Text(text, fontSize = AppType.Body, color = AppColors.TextPrimary) }
    }
}
