package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HiBrowserToolsTest {
    @Test
    fun translateUrlEncodesPageAddress() {
        val url = HiBrowserTools.translateUrl("https://example.com/a?b=c&d=e", "ur")
        assertTrue(url.startsWith("https://translate.google.com/translate?sl=auto&tl=ur&hl=ur&u="))
        assertTrue(url.endsWith("https%3A%2F%2Fexample.com%2Fa%3Fb%3Dc%26d%3De"))
    }

    @Test
    fun translateLanguageMapsLegacyAndChineseCodes() {
        assertEquals("zh-TW", HiBrowserTools.translateTargetLanguage(Locale("zh", "TW")))
        assertEquals("zh-CN", HiBrowserTools.translateTargetLanguage(Locale("zh", "CN")))
        assertEquals("id", HiBrowserTools.translateTargetLanguage(Locale("in", "ID")))
        assertEquals("ar", HiBrowserTools.translateTargetLanguage(Locale("ar", "SA")))
    }
}