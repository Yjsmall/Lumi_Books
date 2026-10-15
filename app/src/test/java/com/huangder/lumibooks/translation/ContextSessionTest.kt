package com.huangder.lumibooks.translation

import com.huangder.lumibooks.domain.model.Note
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContextSessionTest {
    private val labels = ContextLabels("原句", "此处词义", "句子翻译", "语法解析", "词汇积累")
    private fun draft() = Note(bookId = "book", chapterIndex = 0, startPosition = 14, endPosition = 18,
        selectedText = "bank", note = "", color = "#FFFF00", createdAt = 1, syncId = "draft")

    private class Source(enabled: Boolean = true, grammar: Boolean = false, vocabulary: Boolean = false) : ContextAnalysisSource {
        override val configuration = MutableStateFlow(TranslationConfiguration(TranslationSettings.fromJson(
            """{"enabled":$enabled,"model":"test","contextGrammarEnabled":$grammar,"contextVocabularyEnabled":$vocabulary}"""), true))
        val calls = mutableListOf<ContextAnalysisRequest>()
        val replies = mutableListOf<CompletableDeferred<ContextAnalysisResult>>()
        var ignoreCancellation = false
        override suspend fun analyzeContext(request: ContextAnalysisRequest, configuration: TranslationConfiguration): ContextAnalysisResult {
            calls += request
            val reply = CompletableDeferred<ContextAnalysisResult>().also(replies::add)
            return if (ignoreCancellation) withContext(NonCancellable) { reply.await() } else reply.await()
        }
    }

    private class Store {
        var current: Note? = null
        val saves = mutableListOf<Note>()
        var fail = false
        suspend fun load(note: Note): Note? = current
        suspend fun save(note: Note): Note {
            if (fail) error("disk full")
            return note.copy(id = 10).also { current = it; saves += it }
        }
    }

    @Test fun automaticResultsSaveAndSupplementsUpdateTheSameNote() = runTest {
        val source = Source(); val store = Store()
        val session = ContextSession(backgroundScope, source, store::load, store::save, labels)
        session.open(draft()) { "He sat on the bank." }; runCurrent()
        assertEquals(setOf(ContextPart.BASIC), source.calls.single().parts)
        source.replies.single().complete(ContextAnalysisResult("河岸", "他坐在河岸上。")); runCurrent()
        assertEquals("河岸", ContextDocument.fromJson(store.current!!.contextJson)!!.meaning)
        assertTrue(store.current!!.note.contains("句子翻译\n他坐在河岸上。"))
        session.analyze(ContextPart.GRAMMAR); runCurrent()
        assertEquals(setOf(ContextPart.GRAMMAR), source.calls.last().parts)
        source.replies.last().complete(ContextAnalysisResult(grammar = "主谓结构")); runCurrent()
        assertEquals("主谓结构", ContextDocument.fromJson(store.current!!.contextJson)!!.grammar)
        assertEquals(setOf(10L), store.saves.map { it.id }.toSet())
        assertEquals(setOf("draft"), store.saves.map { it.syncId }.toSet())
        session.stop(); session.open(draft()) { error("Saved sentence should be reused") }; runCurrent()
        assertEquals(2, source.calls.size)
        assertEquals("河岸", session.state.value.document!!.meaning)
    }

    @Test fun automaticOptionsAreCombinedIntoOneRequest() = runTest {
        val source = Source(grammar = true, vocabulary = true); val store = Store()
        val session = ContextSession(backgroundScope, source, store::load, store::save, labels)
        session.open(draft()) { "He sat on the bank." }; runCurrent()
        assertEquals(ContextPart.entries.toSet(), source.calls.single().parts)
        source.replies.single().complete(ContextAnalysisResult("河岸", "他坐在河岸上。", "主谓", "sit on：坐在")); runCurrent()
        val restored = ContextDocument.fromJson(store.current!!.contextJson)!!
        assertEquals("主谓", restored.grammar); assertEquals("sit on：坐在", restored.vocabulary)
    }

    @Test fun automaticOffStillSavesSentenceAndManualVocabularyDoesNotEnableTheSetting() = runTest {
        val source = Source(enabled = false); val store = Store()
        val session = ContextSession(backgroundScope, source, store::load, store::save, labels)
        session.open(draft()) { "He sat on the bank." }; runCurrent()
        assertTrue(source.calls.isEmpty())
        assertEquals("He sat on the bank.", ContextDocument.fromJson(store.current!!.contextJson)!!.sentence)
        session.analyze(ContextPart.VOCABULARY); runCurrent()
        source.replies.single().complete(ContextAnalysisResult(vocabulary = "bank：河岸")); runCurrent()
        assertEquals("bank：河岸", ContextDocument.fromJson(store.current!!.contextJson)!!.vocabulary)
        assertFalse(source.configuration.value.settings.enabled)
        assertFalse(source.configuration.value.settings.contextVocabularyEnabled)
    }

    @Test fun failedSaveKeepsGeneratedContentAndRetriesWithoutAnotherAiCall() = runTest {
        val source = Source(); val store = Store()
        val session = ContextSession(backgroundScope, source, store::load, store::save, labels)
        session.open(draft()) { "He sat on the bank." }; runCurrent()
        store.fail = true
        source.replies.single().complete(ContextAnalysisResult("河岸", "他坐在河岸上。")); runCurrent()
        assertTrue(session.state.value.saveFailed)
        assertEquals("河岸", session.state.value.document!!.meaning)
        store.fail = false; session.retrySave(); runCurrent()
        assertFalse(session.state.value.saveFailed)
        assertEquals("河岸", ContextDocument.fromJson(store.current!!.contextJson)!!.meaning)
        assertEquals(1, source.calls.size)
    }

    @Test fun supplementFailureKeepsExistingSectionsAndRetryOnlyRequestsFailedPart() = runTest {
        val source = Source(); val store = Store()
        val session = ContextSession(backgroundScope, source, store::load, store::save, labels)
        session.open(draft()) { "He sat on the bank." }; runCurrent()
        source.replies.single().complete(ContextAnalysisResult("河岸", "他坐在河岸上。")); runCurrent()
        session.analyze(ContextPart.GRAMMAR); runCurrent()
        source.replies.last().completeExceptionally(TranslationException(TranslationError.NETWORK)); runCurrent()
        assertEquals(TranslationError.NETWORK, session.state.value.error)
        assertEquals("河岸", session.state.value.document!!.meaning)
        session.retryAnalysis(); runCurrent()
        assertEquals(setOf(ContextPart.GRAMMAR), source.calls.last().parts)
        source.replies.last().complete(ContextAnalysisResult(grammar = "主谓结构")); runCurrent()
        assertEquals("主谓结构", ContextDocument.fromJson(store.current!!.contextJson)!!.grammar)
    }

    @Test fun closedOrReplacedCardCannotReceiveLateAiResults() = runTest {
        val source = Source().apply { ignoreCancellation = true }; val store = Store()
        val session = ContextSession(backgroundScope, source, store::load, store::save, labels)
        session.open(draft()) { "He sat on the bank." }; runCurrent()
        session.stop()
        source.replies.single().complete(ContextAnalysisResult("late", "late")); runCurrent()
        assertNull(session.state.value.document)
        assertNull(ContextDocument.fromJson(store.current!!.contextJson)!!.meaning)
    }

    @Test fun missingCredentialsNeverSendTextButStillPreserveSentence() = runTest {
        val source = Source().apply { configuration.value = configuration.value.copy(hasToken = false) }; val store = Store()
        val session = ContextSession(backgroundScope, source, store::load, store::save, labels)
        session.open(draft()) { "He sat on the bank." }; runCurrent()
        assertFalse(session.state.value.configured)
        assertTrue(source.calls.isEmpty())
        assertNotNull(store.current)
    }

    @Test fun closingDuringSaveStillPersistsAndReopeningWaitsForThatIdentity() = runTest {
        val source = Source(enabled = false); val store = Store()
        val saveStarted = CompletableDeferred<Unit>()
        val releaseSave = CompletableDeferred<Unit>()
        val session = ContextSession(backgroundScope, source, store::load, {
            saveStarted.complete(Unit); releaseSave.await(); store.save(it)
        }, labels)
        session.open(draft()) { "He sat on the bank." }; runCurrent()
        assertTrue(saveStarted.isCompleted)
        session.stop()
        session.open(draft()) { error("Must reuse saved source") }; runCurrent()
        assertTrue(session.state.value.loading)
        releaseSave.complete(Unit); runCurrent()
        assertEquals("He sat on the bank.", session.state.value.document!!.sentence)
        assertEquals(1, store.saves.size)
        assertTrue(session.state.value.saved)
    }

    @Test fun disablingAutomaticTranslationInvalidatesAnInFlightResponse() = runTest {
        val source = Source().apply { ignoreCancellation = true }; val store = Store()
        val session = ContextSession(backgroundScope, source, store::load, store::save, labels)
        session.open(draft()) { "He sat on the bank." }; runCurrent()
        source.configuration.value = source.configuration.value.let { it.copy(settings = it.settings.copy(enabled = false)) }
        runCurrent()
        source.replies.single().complete(ContextAnalysisResult("late", "late")); runCurrent()
        assertTrue(session.state.value.loadingParts.isEmpty())
        assertNull(session.state.value.document!!.meaning)
        assertNull(ContextDocument.fromJson(store.current!!.contextJson)!!.meaning)
    }

    @Test fun resizedAnnotationReopensOriginalContextAndCanSupplementTheSameNote() = runTest {
        val source = Source(enabled = false); val store = Store()
        val original = ContextDocument("the bank", "He sat on the bank.", "河岸", "他坐在河岸上。")
        store.current = draft().copy(id = 10, selectedText = "bank", note = "Saved analysis", contextJson = original.toJson())
        val session = ContextSession(backgroundScope, source, store::load, store::save, labels)
        session.open(store.current!!) { error("Must retain original context") }; runCurrent()
        assertEquals("the bank", session.state.value.document!!.selectedText)
        session.analyze(ContextPart.GRAMMAR); runCurrent()
        source.replies.single().complete(ContextAnalysisResult(grammar = "结构")); runCurrent()
        assertEquals("bank", store.current!!.selectedText)
        assertEquals("the bank", ContextDocument.fromJson(store.current!!.contextJson)!!.selectedText)
        assertEquals(10L, store.current!!.id)
    }
}
