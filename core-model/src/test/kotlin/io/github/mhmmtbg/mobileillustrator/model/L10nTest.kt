package io.github.mhmmtbg.mobileillustrator.model

import kotlin.test.Test
import kotlin.test.assertEquals

class L10nTest {
    private fun inEnglish(block: () -> Unit) {
        L10n.english = true
        try { block() } finally { L10n.english = false }
    }

    @Test
    fun turkishIsTheSourceLanguage() {
        L10n.english = false
        assertEquals("Katmanlar", tr("Katmanlar"))
        assertEquals("Gopher görünürlüğü", tr("%s görünürlüğü", "Gopher"))
        assertEquals("Katman 2", trName("Katman 2"))
    }

    @Test
    fun englishTranslatesTextAndPlaceholders(): Unit = inEnglish {
        assertEquals("Layers", tr("Katmanlar"))
        assertEquals("Gopher visibility", tr("%s görünürlüğü", "Gopher"))
        // Tabloda olmayan metin olduğu gibi kalır; uygulama çökmez.
        assertEquals("bilinmeyen", tr("bilinmeyen"))
    }

    @Test
    fun defaultNamesAreTranslatedForDisplayOnly(): Unit = inEnglish {
        assertEquals("Layer 3", trName("Katman 3"))
        assertEquals("Untitled", trName("Adsız"))
        assertEquals("Path", trName("Yol"))
        assertEquals("Katmanım", trName("Katmanım"))
    }

    @Test
    fun everyTranslationKeepsItsPlaceholders(): Unit = inEnglish {
        for (key in L10n.keys) {
            val count = { s: String -> Regex("%s").findAll(s).count() }
            assertEquals(count(key), count(L10n.translate(key)), key)
        }
    }
}
