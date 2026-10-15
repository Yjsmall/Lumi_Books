package com.huangder.lumibooks.translation

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class TranslationSettingsTest {
    @Test fun missingOrInvalidConfigurationDefaultsToDisabled() {
        for (value in listOf(null, "", "{}", "not json")) assertFalse(TranslationSettings.fromJson(value).enabled)
        assertEquals("简体中文", TranslationSettings().targetLanguage)
        assertFalse(TranslationConfiguration(TranslationSettings(enabled = true), false).enabled)
    }

    @Test fun roundTripKeepsSettingsButContainsNoApiKey() {
        val settings = TranslationSettings(true, TranslationProvider.CUSTOM, "https://example.com/v1",
            TranslationAuth.API_KEY, "model", "日本語", false, "revision")
        assertEquals(settings, TranslationSettings.fromJson(settings.toJson()))
        assertFalse(settings.toJson().contains("token", ignoreCase = true))
        assertFalse(settings.toJson().contains("apiKey", ignoreCase = true))
    }

    @Test fun credentialsAreScopedToEndpointAndAuthentication() {
        val settings = TranslationSettings(provider = TranslationProvider.CUSTOM, baseUrl = "https://one.example/v1")
        assertNotEquals(settings.credentialScope, settings.copy(baseUrl = "https://two.example/v1").credentialScope)
        assertNotEquals(settings.credentialScope, settings.copy(auth = TranslationAuth.BEARER).credentialScope)
        assertEquals(settings.credentialScope, settings.copy(baseUrl = " https://one.example/v1/ ").credentialScope)
        assertEquals(TranslationAuth.API_KEY, TranslationSettings(provider = TranslationProvider.MIMO, auth = TranslationAuth.BEARER).normalized().auth)
    }

    @Test fun draftRoundTripPreservesTrailingSlashAndSpacesDuringTyping() {
        val draft = TranslationSettings(baseUrl = "https://example.com/", targetLanguage = "Brazilian ")
        assertEquals(draft, TranslationSettings.fromJson(draft.toJson()))
        assertEquals("Brazilian", draft.normalized().targetLanguage)
    }

    @Test fun contextOptionsRoundTripIndependently() {
        for ((grammar, vocabulary) in listOf(true to false, false to true, true to true)) {
            val raw = """{"contextGrammarEnabled":$grammar,"contextVocabularyEnabled":$vocabulary}"""
            val saved = JSONObject(TranslationSettings.fromJson(raw).toJson())
            assertEquals(grammar, saved.opt("contextGrammarEnabled"))
            assertEquals(vocabulary, saved.opt("contextVocabularyEnabled"))
        }
    }

    @Test fun legacyConfigurationDisablesContextOptionsWithoutLosingConnection() {
        val settings = TranslationSettings.fromJson("""{"enabled":true,"provider":"CUSTOM","baseUrl":"https://example.com/v1","auth":"BEARER","model":"existing-model","credentialVersion":"existing-key"}""")
        val saved = JSONObject(settings.toJson())
        assertEquals(false, saved.opt("contextGrammarEnabled"))
        assertEquals(false, saved.opt("contextVocabularyEnabled"))
        assertTrue(settings.enabled)
        assertEquals("existing-model", settings.model)
        assertEquals("existing-key", settings.credentialVersion)
        assertEquals("BEARER|https://example.com/v1", settings.credentialScope)
    }

    @Test fun contextOptionsDoNotChangeCredentialScope() {
        val original = TranslationSettings.fromJson("""{"provider":"CUSTOM","baseUrl":"https://example.com/v1","auth":"BEARER","credentialVersion":"existing-key"}""")
        val automatic = TranslationSettings.fromJson("""{"provider":"CUSTOM","baseUrl":"https://example.com/v1","auth":"BEARER","credentialVersion":"existing-key","contextGrammarEnabled":true,"contextVocabularyEnabled":true}""")
        assertEquals(original.credentialScope, automatic.credentialScope)
        assertEquals(original.credentialVersion, automatic.credentialVersion)
    }
}
