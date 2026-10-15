package com.huangder.lumibooks.translation

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ContextAnalysisTest {
    @Test fun documentRoundTripKeepsWordSentenceAndEachAnalysisSeparate() {
        val document = ContextDocument("bank", "He sat on the bank.", "河岸", "他坐在河岸上。", "主谓结构", "sit on：坐在…上", "简体中文")
        val restored = ContextDocument.fromJson(document.toJson())!!
        assertEquals("bank", restored.selectedText)
        assertEquals("He sat on the bank.", restored.sentence)
        assertEquals("河岸", restored.meaning)
        assertEquals("他坐在河岸上。", restored.translation)
        assertEquals("主谓结构", restored.grammar)
        assertEquals("sit on：坐在…上", restored.vocabulary)
        assertNull(ContextDocument.fromJson("{}"))
        assertNull(ContextDocument.fromJson("not a document"))
    }

    @Test fun savedSourcePreservesSelectionWhitespaceAndSentenceLineBreaks() {
        val document = ContextDocument(" bank \n", " He sat on the bank.\n", meaning = "河岸", translation = "他坐在河岸上。")
        val restored = ContextDocument.fromJson(document.toJson())!!
        assertEquals(" bank \n", restored.selectedText)
        assertEquals(" He sat on the bank.\n", restored.sentence)
    }

    @Test fun contextRequestSendsWordAndSentenceAndOnlyMergesRequestedSections() = runBlocking {
        MockWebServer().use { server ->
            val answer = JSONObject().put("meaning", "河岸").put("translation", "他坐在河岸上。")
                .put("grammar", "unrequested")
            server.enqueue(MockResponse().setBody(JSONObject().put("choices", JSONArray().put(
                JSONObject().put("message", JSONObject().put("content", answer.toString())))).toString()))
            val settings = TranslationSettings(provider = TranslationProvider.CUSTOM, baseUrl = server.url("/v1").toString(),
                model = "test", auth = TranslationAuth.BEARER, allowHttp = true)
            val request = ContextAnalysisRequest("bank", "He sat on the bank.", setOf(ContextPart.BASIC))
            val result = TranslationHttpClient().analyzeContext(settings, "test-key", request)
            val sent = server.takeRequest(3, TimeUnit.SECONDS)!!
            val data = JSONObject(JSONObject(sent.body.readUtf8()).getJSONArray("messages").getJSONObject(1).getString("content"))
            assertEquals("bank", data.getString("selectedText"))
            assertEquals("He sat on the bank.", data.getString("sentence"))
            assertEquals("Bearer test-key", sent.getHeader("Authorization"))
            val merged = ContextDocument("bank", "He sat on the bank.").merge(result, request.parts, "简体中文")
            assertEquals("河岸", merged.meaning)
            assertEquals("他坐在河岸上。", merged.translation)
            assertNull(merged.grammar)
        }
    }

    @Test fun partialOrWronglyTypedAnalysisIsRejectedInsteadOfSavingIncompleteResults() {
        val request = setOf(ContextPart.BASIC)
        for (response in listOf("{}", "{\"meaning\":\"河岸\"}", "{\"meaning\":[],\"translation\":\"译文\"}", "plain text")) {
            val failure = runCatching { parseContextAnalysis(response, request) }.exceptionOrNull()
            assertEquals(TranslationError.EMPTY_RESPONSE, (failure as TranslationException).error)
        }
        assertEquals("结构", parseContextAnalysis("```json\n{\"grammar\":\"结构\"}\n```", setOf(ContextPart.GRAMMAR)).grammar)
    }
}
