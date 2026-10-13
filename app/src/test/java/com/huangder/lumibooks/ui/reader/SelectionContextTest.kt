package com.huangder.lumibooks.ui.reader

import org.junit.Assert.*
import org.junit.Test

class SelectionContextTest {
    @Test fun extractsOnlyTheSentenceAtTheSelectedOccurrence() {
        val text = "First word here. Second word in context. Last sentence."
        val start = text.indexOf("word", text.indexOf("Second"))
        assertEquals("Second word in context.", extractSelectionContext(text, start, start + 4))
    }

    @Test fun retainsAllSentencesTouchedBySelection() {
        assertEquals("One word.\r\nAnother word.", extractSelectionContext("One word.\r\nAnother word.\r\nEnd", 4, 23))
    }

    @Test fun chineseSentenceKeepsCommaAndSemicolonClauses() {
        assertContext("上一句。这个单词很重要，值得记住；以后会用到！下一句。", "单词",
            "这个单词很重要，值得记住；以后会用到！")
    }

    @Test fun closingQuotesAndRepeatedPunctuationStayWithSentence() {
        assertContext("他说：“这个词是什么？！”然后离开。", "是什么", "他说：“这个词是什么？！”")
        assertContext("Before. She asked, \"Which word?!\" After.", "word", "She asked, \"Which word?!\"")
    }

    @Test fun lowerCaseEnglishAndExactSelectionBoundaries() {
        assertContext("first sentence. another word appears. last sentence.", "word", "another word appears.")
        assertContext("First. Second. Third.", "Second.", "Second.")
        assertContext("First. Second. Third.", "First.", "First.")
    }

    @Test fun decimalsAndCommonTitlesDoNotEndTheSentence() {
        assertContext("Before. Dr. Smith paid 3.14 dollars. After.", "dollars", "Dr. Smith paid 3.14 dollars.")
        assertContext("Before. Open example.com today. After.", "today", "Open example.com today.")
    }

    @Test fun ellipsesAndParagraphEndAreBoundaries() {
        assertContext("前一句……这个单词在这里。后一句。", "单词", "这个单词在这里。")
        assertContext("Before... This word matters... After.", "word", "This word matters...")
        assertContext("Previous\nA word without a full stop\nNext", "word", "A word without a full stop")
    }

    @Test fun surroundingSelectedWhitespaceDoesNotIncludeAdjacentSentences() {
        val text = "First.  Second sentence.  Third."
        assertEquals("Second sentence.", extractSelectionContext(text, 6, 25))
        assertNull(extractSelectionContext("  \n ", 0, 4))
    }

    private fun assertContext(text: String, selected: String, expected: String) {
        val start = text.indexOf(selected)
        assertEquals(expected, extractSelectionContext(text, start, start + selected.length))
    }

    @Test fun respectsUnicodeParagraphBoundariesAndChapterEdges() {
        assertEquals("开头词语。", extractSelectionContext("开头词语。\u2029下一段", 2, 4))
        assertEquals("Last word", extractSelectionContext("First\nLast word", 11, 15))
    }

    @Test fun invalidOrEmptySelectionDoesNotFabricateContext() {
        assertNull(extractSelectionContext("Hello", -1, 2))
        assertNull(extractSelectionContext("Hello", 2, 2))
        assertNull(extractSelectionContext("Hello", 0, 10))
        assertNull(extractSelectionContext("", 0, 0))
    }
}
