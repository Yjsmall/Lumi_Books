package com.huangder.lumibooks.data.repository

import android.app.Application
import androidx.room.Room
import com.huangder.lumibooks.data.local.database.AppDatabase
import com.huangder.lumibooks.data.sync.SyncIdentityStore
import com.huangder.lumibooks.domain.model.BookFormat
import com.huangder.lumibooks.domain.model.HighlightRule
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.repository.BookRepository
import com.huangder.lumibooks.highlight.HighlightRuleMaterializer
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ContextNotePersistenceTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ReadingRepositoryImpl

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java).build()
        repository = ReadingRepositoryImpl(database.readingRecordDao(), database.bookmarkDao(), database.noteDao(),
            database.syncStateDao(), SyncIdentityStore(database.syncStateDao()), database)
    }

    @After fun tearDown() { database.close() }

    @Test fun initialSaveReturnsDatabaseIdentityAndDraftRetryUpdatesTheSameNote() = runBlocking {
        val draft = draft()
        val initial = repository.saveContextNote(draft)
        val supplemented = repository.saveContextNote(draft.copy(note = "Meaning and grammar", contextJson = "{\"grammar\":\"clause\"}"))

        assertTrue(initial.id > 0)
        assertEquals(initial.id, supplemented.id)
        assertEquals("context-draft", supplemented.syncId)
        assertEquals("Meaning and grammar", supplemented.note)
        assertEquals("{\"grammar\":\"clause\"}", supplemented.contextJson)
        assertTrue(supplemented.isNote)
        assertEquals(listOf(supplemented), repository.getNotesByBookId("book").first())
        assertTrue(supplemented.updatedAt > draft.updatedAt)
    }

    @Test fun concurrentDraftSavesCannotInsertDuplicates() = runBlocking {
        val saved = (1..4).map { index ->
            async(Dispatchers.IO) { repository.saveContextNote(draft().copy(note = "Result $index")) }
        }.awaitAll()

        assertEquals(1, saved.map { it.id }.distinct().size)
        assertEquals(1, repository.getNotesByBookId("book").first().size)
    }

    @Test fun supplementAdvancesTheLatestTimestampWhenDeviceClockIsEarlier() = runBlocking {
        val initial = repository.saveContextNote(draft().copy(updatedAt = 4_102_444_800_000L))
        val supplemented = repository.saveContextNote(initial.copy(note = "Supplement"))

        assertTrue(supplemented.updatedAt > initial.updatedAt)
    }

    @Test fun supplementPreservesLatestTagsAnchorsStyleAndIdentityFromAnotherEditor() = runBlocking {
        val initial = repository.saveContextNote(draft())
        val latest = initial.copy(chapterIndex = 4, startPosition = 18, endPosition = 24,
            startLocatorJson = "{\"position\":18}", endLocatorJson = "{\"position\":24}",
            selectedText = "Current source", color = "#112233", type = "underline",
            tags = listOf("Review"), origin = Note.ORIGIN_HIGHLIGHT_RULE, sourceRuleId = "rule",
            sourceMatchKey = "match", styleSnapshotJson = "{\"width\":2}")
        repository.updateNote(latest)
        val result = repository.saveContextNote(initial.copy(note = "New analysis", contextJson = "{\"meaning\":\"new\"}"))

        assertEquals(latest.copy(note = "New analysis", contextJson = "{\"meaning\":\"new\"}", updatedAt = result.updatedAt), result)
        assertEquals(result, repository.getNotesByBookId("book").first().single())
    }

    @Test fun deletedExistingNoteCannotBeRecreatedByAnInFlightSupplement() = runBlocking {
        val initial = repository.saveContextNote(draft())
        repository.deleteNote(initial)

        val failure = runCatching { repository.saveContextNote(initial.copy(note = "Late result")) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(repository.getNotesByBookId("book").first().isEmpty())
    }

    @Test fun mismatchedExistingIdentityCannotOverwriteAnotherNote() = runBlocking {
        val initial = repository.saveContextNote(draft())
        val failure = runCatching { repository.saveContextNote(initial.copy(syncId = "another-note", note = "Wrong result")) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("Initial analysis", repository.getNotesByBookId("book").first().single().note)
    }

    @Test fun ordinaryInsertAndUpdateAlsoRoundTripContextMetadata() = runBlocking {
        repository.insertNote(draft())
        val initial = repository.getNotesByBookId("book").first().single()
        assertEquals("{\"meaning\":\"initial\"}", initial.contextJson)

        repository.updateNote(initial.copy(contextJson = "{\"translation\":\"translated\"}"))
        assertEquals("{\"translation\":\"translated\"}", repository.getNotesByBookId("book").first().single().contextJson)
    }

    @Test fun ruleRescanKeepsContextMetadataAlongsideTheExistingNoteBody() = runBlocking {
        val identity = SyncIdentityStore(database.syncStateDao())
        val rules = HighlightRuleRepositoryImpl(database.highlightRuleDao(), database.noteDao(), database, identity)
        val unusedBooks = Proxy.newProxyInstance(BookRepository::class.java.classLoader, arrayOf(BookRepository::class.java)) { _, _, _ ->
            error("Chapter materialization must not read a book file")
        } as BookRepository
        val materializer = HighlightRuleMaterializer(RuntimeEnvironment.getApplication(), unusedBooks, rules, database, identity)
        val rule = HighlightRule("rule", "Rule", "Source", underlineMode = 3)
        rules.saveRule(rule)
        rules.setMaterializeNotes("book", true)
        materializer.materializeChapter("book", BookFormat.TXT, 0, "Source sentence.")
        val generated = repository.getNotesByBookId("book").first().single()
        repository.saveContextNote(generated.copy(note = "Analysis", contextJson = "{\"meaning\":\"saved\"}"))

        rules.saveRule(rule.copy(underlineMode = 4))
        materializer.materializeChapter("book", BookFormat.TXT, 0, "Source sentence.")

        val rescanned = repository.getNotesByBookId("book").first().single()
        assertEquals(generated.id, rescanned.id)
        assertEquals("Analysis", rescanned.note)
        assertEquals("{\"meaning\":\"saved\"}", rescanned.contextJson)
    }

    private fun draft() = Note(bookId = "book", chapterIndex = 1, startPosition = 2, endPosition = 8,
        startLocatorJson = "{\"position\":2}", endLocatorJson = "{\"position\":8}", selectedText = "Source sentence.",
        note = "Initial analysis", color = "#ffee00", createdAt = 100, syncId = "context-draft", isNote = true,
        contextJson = "{\"meaning\":\"initial\"}")
}
