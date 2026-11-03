package com.manish.vahakvigil.preferences

import com.manish.vahakvigil.data.preferences.LanguageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageManagerTest {
    @Test fun `english and hindi are available and tags remain extensible`() {
        assertEquals(listOf("en", "hi"), LanguageManager.supportedLanguages.map { it.tag })
        assertTrue(LanguageManager.supportedLanguages.all { it.label.isNotBlank() })
    }
}
