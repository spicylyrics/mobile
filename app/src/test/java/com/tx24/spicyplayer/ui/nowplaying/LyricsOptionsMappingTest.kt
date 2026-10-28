package com.tx24.spicyplayer.ui.nowplaying

import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.playback.SourceChoice
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.translation.TranslationPreferences
import com.tx24.spicyplayer.translation.TranslationProvider
import com.tx24.spicyplayer.ui.bigscreen.LyricsSourceChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsOptionsMappingTest {
    private val choices = listOf(
        SourceChoice("spicy", "Spicy Lyrics", "Word-synced", available = true, shown = true),
        SourceChoice("lrclib", "LRCLIB", "No lyrics for this song", available = false, shown = false, canModelSync = true),
        SourceChoice("musicxmatch", "Other", "Not asked yet", available = true, shown = false),
    )

    private fun map(state: PlayerUiState = PlayerUiState()) =
        lyricsOptions(state, choices, TranslationMode.Replace, RomanizationMode.UnderLine)

    @Test
    fun `the sources come over as the view model ranks them`() {
        val options = map()
        assertEquals(
            listOf(
                LyricsSourceChoice("spicy", "Spicy Lyrics", "Word-synced", available = true, shown = true),
                LyricsSourceChoice("lrclib", "LRCLIB", "No lyrics for this song", available = false, shown = false),
                LyricsSourceChoice("musicxmatch", "Other", "Not asked yet", available = true, shown = false),
            ),
            options.sources,
        )
        assertNull(options.pickedSource)
    }

    @Test
    fun `a source picked for the song is the picked one`() {
        assertEquals("lrclib", map(PlayerUiState(sourceOverride = "lrclib")).pickedSource)
    }

    @Test
    fun `translation reads as the phone's button does`() {
        assertFalse(map().translating)
        assertTrue(map(PlayerUiState(translationEnabled = true)).translating)
        // A failed translation shows off, with a note on how to try again.
        val failed = map(PlayerUiState(translationEnabled = true, translationFailed = true))
        assertFalse(failed.translating)
        assertTrue(failed.message!!.startsWith("Couldn't translate"))
        assertEquals("Translating…", map(PlayerUiState(translationEnabled = true, translating = true)).message)
        assertNull(map(PlayerUiState(translationEnabled = true)).message)
    }

    @Test
    fun `the target and its languages come from the translation preferences`() {
        val options = map(PlayerUiState(translationPreferences = TranslationPreferences(targetLanguage = "de", provider = TranslationProvider.Google)))
        assertEquals("de", options.targetLanguage)
        assertTrue(options.languages.any { it.code == "de" })
        assertTrue(options.languages.isNotEmpty())
    }

    @Test
    fun `the display modes are the settings'`() {
        val options = map()
        assertEquals(TranslationMode.Replace, options.translationMode)
        assertEquals(RomanizationMode.UnderLine, options.romanizationMode)
    }
}
