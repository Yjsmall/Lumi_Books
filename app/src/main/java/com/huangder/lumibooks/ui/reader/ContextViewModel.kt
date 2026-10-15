package com.huangder.lumibooks.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.Note
import com.huangder.lumibooks.domain.repository.ReadingRepository
import com.huangder.lumibooks.translation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class ContextViewModel @Inject constructor(
    @ApplicationContext context: Context,
    source: TranslationRepository,
    private val readingRepository: ReadingRepository
) : ViewModel() {
    private val session = ContextSession(viewModelScope, source, ::findNote, readingRepository::saveContextNote,
        ContextLabels(context.getString(R.string.context_sentence), context.getString(R.string.context_meaning),
            context.getString(R.string.context_translation), context.getString(R.string.context_grammar), context.getString(R.string.context_vocabulary)))
    val state = session.state
    fun open(note: Note, loadContext: suspend () -> String?) = session.open(note, loadContext)
    fun analyze(part: ContextPart) = session.analyze(part)
    fun retry() = session.retryAnalysis()
    fun retrySave() = session.retrySave()
    fun stop() = session.stop()

    private suspend fun findNote(request: Note): Note? {
        val notes = readingRepository.getNotesByBookId(request.bookId).first()
        if (request.id > 0L) return notes.firstOrNull { it.id == request.id && it.syncId == request.syncId }
            ?: error("Context note was deleted")
        return notes.firstOrNull {
            it.contextJson != null && it.selectedText == request.selectedText && it.chapterIndex == request.chapterIndex &&
                if (request.startLocatorJson != null && it.startLocatorJson != null)
                    it.startLocatorJson == request.startLocatorJson && it.endLocatorJson == request.endLocatorJson
                else it.startPosition == request.startPosition && it.endPosition == request.endPosition
        }
    }
}
