package com.tx24.spicyplayer.ui.bigscreen

import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.translation.TranslationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsActionsTest {
    private val sources = listOf(
        LyricsSourceChoice("spicy", "Spicy Lyrics", "Word-synced", available = true, shown = true),
        LyricsSourceChoice("lrclib", "LRCLIB", "Line-synced", available = true, shown = false),
        LyricsSourceChoice("genius", "Genius", "No lyrics for this song", available = false, shown = false),
    )

    @Test fun automaticComesFirstAndSaysWhatItShows() {
        val rows = sourceRows(LyricsOptions(sources = sources))
        assertEquals(listOf(null, "spicy", "lrclib", "genius"), rows.map { it.id })
        assertTrue(rows[0].selected)
        assertEquals("Showing Spicy Lyrics", rows[0].detail)
        assertEquals("Word-synced · showing now", rows[1].detail)
        assertFalse(rows[3].enabled)
    }

    @Test fun aPickedSourceIsSelectedEvenWithNothing() {
        val rows = sourceRows(LyricsOptions(sources = sources, pickedSource = "genius"))
        assertFalse(rows[0].selected)
        assertEquals("The best match", rows[0].detail)
        assertTrue(rows[3].selected)
        // Picked, it stays tappable so it can be picked again after the song changes.
        assertTrue(rows[3].enabled)
        assertEquals("Word-synced", rows[1].detail)
    }

    @Test fun summaries() {
        assertEquals("Automatic", sourceSummary(LyricsOptions()))
        assertEquals("Automatic · Spicy Lyrics", sourceSummary(LyricsOptions(sources = sources)))
        assertEquals("LRCLIB", sourceSummary(LyricsOptions(sources = sources, pickedSource = "lrclib")))
        assertEquals("gone", sourceSummary(LyricsOptions(sources = sources, pickedSource = "gone")))

        assertEquals("Off", translationSummary(LyricsOptions(translating = false)))
        val french = LyricsOptions(translating = true, targetLanguage = "fr", languages = listOf(LanguageChoice("fr", "French")), translationMode = TranslationMode.Replace)
        assertEquals("French · Replace", translationSummary(french))
        assertEquals("xx · Under each line", translationSummary(LyricsOptions(translating = true, targetLanguage = "xx")))

        assertEquals("Under each line", romanizationSummary(LyricsOptions(romanizationMode = RomanizationMode.UnderLine)))
    }
}
