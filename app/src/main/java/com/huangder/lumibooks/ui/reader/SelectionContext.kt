package com.huangder.lumibooks.ui.reader

/** Expands an exact selection to its sentence, retaining source offsets across visual pages. */
internal fun extractSelectionContext(text: CharSequence, start: Int, end: Int): String? {
    if (start < 0 || end > text.length || start >= end) return null
    var first = start
    var last = end
    // Selection handles can include spaces or a trailing newline, not an adjacent sentence.
    while (first < last && text[first].isWhitespace()) first++
    while (last > first && text[last - 1].isWhitespace()) last--
    if (first == last) return null

    var sentenceStart = 0
    var index = 0
    while (index < text.length) {
        val char = text[index]
        val paragraphEnd = char == '\n' || char == '\r' || char == '\u2029' || char == '\u2028'
        val sentenceEnd = char in CONTEXT_SENTENCE_ENDINGS && (char != '.' || text.isSentencePeriod(index))
        if (paragraphEnd || sentenceEnd) {
            var boundary = index + 1
            if (sentenceEnd) {
                // Keep runs such as ?!", ？）。 and …… with the sentence they close.
                while (boundary < text.length &&
                    (text[boundary] in CONTEXT_SENTENCE_ENDINGS || text[boundary] in CONTEXT_SENTENCE_CLOSERS)) boundary++
            }
            if (boundary <= first) {
                sentenceStart = boundary
            } else if (boundary >= last) {
                return text.subSequence(sentenceStart, boundary).toString().trim()
            }
            index = boundary
        } else {
            index++
        }
    }
    return text.subSequence(sentenceStart, text.length).toString().trim()
}

private fun CharSequence.isSentencePeriod(index: Int): Boolean {
    // Internal dots in decimals, domains and abbreviations are not sentence endings.
    if (getOrNull(index + 1)?.isLetterOrDigit() == true) return false
    if (getOrNull(index + 1) == '.') return true
    var wordStart = index
    while (wordStart > 0 && (this[wordStart - 1].isLetter() || this[wordStart - 1] == '.')) wordStart--
    val word = subSequence(wordStart, index).toString().lowercase(java.util.Locale.ROOT)
    return word !in CONTEXT_NON_TERMINAL_ABBREVIATIONS
}

private const val CONTEXT_SENTENCE_ENDINGS = ".!?。！？…"
private const val CONTEXT_SENTENCE_CLOSERS = "\"'”’」』》〉】〕）)]}"
private val CONTEXT_NON_TERMINAL_ABBREVIATIONS = setOf("mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "e.g", "i.e")
