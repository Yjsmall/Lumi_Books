package com.huangder.lumibooks.translation

import com.huangder.lumibooks.domain.model.Note
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

data class ContextState(
    val document: ContextDocument? = null,
    val loading: Boolean = false,
    val unavailable: Boolean = false,
    val configured: Boolean = false,
    val loadingParts: Set<ContextPart> = emptySet(),
    val error: TranslationError? = null,
    val failedParts: Set<ContextPart> = emptySet(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val saveFailed: Boolean = false
)

/** UI-scope session. Network work belongs to the open card; queued saves survive its closure. */
class ContextSession(
    private val scope: CoroutineScope,
    private val source: ContextAnalysisSource,
    private val loadNote: suspend (Note) -> Note?,
    private val saveNote: suspend (Note) -> Note,
    private val labels: ContextLabels
) {
    private class Entry(var note: Note, var document: ContextDocument) {
        var pendingSaves = 0
    }
    private val mutableState = MutableStateFlow(ContextState())
    val state = mutableState.asStateFlow()
    private val saves = Mutex()
    private var entry: Entry? = null
    private var openJob: Job? = null
    private var analysisJob: Job? = null
    private var configuration: TranslationConfiguration? = null
    private var generation = 0L

    fun open(note: Note, loadContext: suspend () -> String?) {
        stop()
        val openedGeneration = generation
        mutableState.value = ContextState(loading = true)
        openJob = scope.launch {
            try {
                // Reopening must wait for an earlier card's pending save to acquire its persisted id.
                val existing = saves.withLock { loadNote(note) }
                val savedDocument = ContextDocument.fromJson(existing?.contextJson)
                if (existing?.contextJson != null && savedDocument == null) {
                    mutableState.value = ContextState(unavailable = true)
                    return@launch
                }
                val document = savedDocument ?: loadContext()?.takeIf { it.isNotBlank() }
                    ?.let { ContextDocument(note.selectedText, it) }
                coroutineContext.ensureActive()
                if (generation != openedGeneration) return@launch
                // The analyzed source is immutable; an annotation's anchors/text can later be resized or migrated.
                if (document == null) {
                    mutableState.value = ContextState(unavailable = true)
                    return@launch
                }
                val current = Entry(existing ?: note.copy(syncId = note.syncId.ifBlank { UUID.randomUUID().toString() }), document)
                entry = current
                mutableState.value = ContextState(document = document, saved = savedDocument != null)
                if (savedDocument == null) persist(current)
                source.configuration.collect { config ->
                    analysisJob?.cancel()
                    configuration = config
                    generation++
                    val configured = config.hasToken && config.settings.model.isNotBlank() &&
                        config.settings.baseUrl.isNotBlank() && config.settings.targetLanguage.isNotBlank()
                    mutableState.value = mutableState.value.copy(configured = configured, loadingParts = emptySet())
                    if (configured && config.enabled) {
                        val automatic = buildSet {
                            add(ContextPart.BASIC)
                            if (config.settings.contextGrammarEnabled) add(ContextPart.GRAMMAR)
                            if (config.settings.contextVocabularyEnabled) add(ContextPart.VOCABULARY)
                        }.filterNot { current.document.has(it) }.toSet()
                        if (automatic.isNotEmpty()) analyze(automatic)
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (entry == null) mutableState.value = ContextState(unavailable = true)
            }
        }
    }

    fun analyze(part: ContextPart) = analyze(setOf(part))

    private fun analyze(parts: Set<ContextPart>) {
        val current = entry ?: return
        val config = configuration ?: return
        if (!mutableState.value.configured || mutableState.value.loadingParts.isNotEmpty()) return
        val requestGeneration = ++generation
        mutableState.value = mutableState.value.copy(loadingParts = parts, error = null, failedParts = emptySet())
        analysisJob = scope.launch {
            try {
                val result = source.analyzeContext(ContextAnalysisRequest(current.document.selectedText, current.document.sentence, parts), config)
                coroutineContext.ensureActive()
                if (entry !== current || generation != requestGeneration) return@launch
                current.document = current.document.merge(result, parts, config.settings.targetLanguage)
                mutableState.value = mutableState.value.copy(document = current.document, loadingParts = emptySet(), saved = false)
                persist(current)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (entry === current && generation == requestGeneration) mutableState.value = mutableState.value.copy(
                    loadingParts = emptySet(), error = (error as? TranslationException)?.error ?: TranslationError.SERVICE,
                    failedParts = parts)
            }
        }
    }

    fun retryAnalysis() {
        val parts = mutableState.value.failedParts
        if (parts.isNotEmpty()) analyze(parts)
    }

    fun retrySave() {
        if (mutableState.value.saveFailed && !mutableState.value.saving) entry?.let(::persist)
    }

    private fun persist(current: Entry) {
        val document = current.document
        current.pendingSaves++
        if (entry === current) mutableState.value = mutableState.value.copy(saving = true)
        scope.launch {
            try {
                saves.withLock {
                    current.note = saveNote(current.note.copy(note = document.toNoteText(labels), contextJson = document.toJson(), isNote = true))
                }
                if (entry === current && document == current.document) mutableState.value = mutableState.value.copy(saved = true, saveFailed = false)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (entry === current) mutableState.value = mutableState.value.copy(saveFailed = true, saved = false)
            } finally {
                current.pendingSaves--
                if (entry === current) mutableState.value = mutableState.value.copy(saving = current.pendingSaves > 0)
            }
        }
    }

    fun stop() {
        generation++
        openJob?.cancel(); analysisJob?.cancel()
        openJob = null; analysisJob = null; entry = null; configuration = null
        mutableState.value = ContextState()
    }
}
