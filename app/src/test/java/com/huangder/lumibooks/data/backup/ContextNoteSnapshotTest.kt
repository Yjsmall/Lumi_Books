package com.huangder.lumibooks.data.backup

import com.huangder.lumibooks.data.local.entity.NoteEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ContextNoteSnapshotTest {
    @Test fun portableWebdavStateRoundTripPreservesContextAndAnnotationFields() {
        val note = NoteEntity(bookId = "book", chapterIndex = 1, startPosition = 2, endPosition = 8,
            startLocatorJson = "{\"position\":2}", endLocatorJson = "{\"position\":8}", selectedText = "Source sentence.",
            note = "Readable analysis", color = "#ffee00", createdAt = 100, syncId = "stable-context-note",
            tagsJson = "[\"Review\"]", contextJson = "{\"meaning\":\"in context\"}")
        val original = snapshot(note)

        val json = original.toJson()

        assertEquals("{\"meaning\":\"in context\"}", JSONObject(json).getJSONArray("notes").getJSONObject(0).getString("contextJson"))
        assertEquals(note, PortableSnapshot.fromJson(json).notes.single())
    }

    @Test fun oldAndExplicitNullSnapshotsLoadAsOrdinaryNotes() {
        val legacyJson = """{"schemaVersion":1,"notes":[{"bookId":"book","chapterIndex":1,"startPosition":2,"endPosition":8,"selectedText":"Source sentence.","note":"Old note","color":"yellow","createdAt":100,"syncId":"stable-old-note"}]}"""
        val legacy = PortableSnapshot.fromJson(legacyJson).notes.single()
        assertNull(legacy.contextJson)
        assertEquals("Old note", legacy.note)
        assertEquals("stable-old-note", legacy.syncId)

        val explicitNull = JSONObject(legacyJson).apply { getJSONArray("notes").getJSONObject(0).put("contextJson", JSONObject.NULL) }
        assertEquals(legacy, PortableSnapshot.fromJson(explicitNull.toString()).notes.single())
        assertEquals(legacy, PortableSnapshot.fromJson(snapshot(legacy).toJson()).notes.single())
    }

    private fun snapshot(note: NoteEntity) = PortableSnapshot(createdAt = 100, sourceDeviceId = "device",
        preferences = emptyList(), books = emptyList(), folders = emptyList(), bookFolderLinks = emptyList(),
        tags = emptyList(), bookTagLinks = emptyList(), readingRecords = emptyList(), bookmarks = emptyList(),
        notes = listOf(note), tombstones = emptyList(), assets = emptyList())
}
