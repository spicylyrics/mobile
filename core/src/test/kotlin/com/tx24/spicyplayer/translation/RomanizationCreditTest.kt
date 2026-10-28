package com.tx24.spicyplayer.translation

import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RomanizationCreditTest {
    private fun line(romanized: String?) = TimedLine(0, 1000, listOf(TimedWord("歌", 0, 1000, false, romanized)))

    private fun lyrics(vararg romanized: String?, sourceRomanized: Boolean = false) =
        LyricsState.Ready(romanized.map(::line), source = null, maker = null, uploader = null, songwriters = emptyList(), sourceRomanized = sourceRomanized)

    @Test fun `it names where the romanization comes from`() {
        assertEquals("Lyrics", romanizationCredit(lyrics("uta", sourceRomanized = true), 0, romanize = true, shown = true))
        assertEquals("On-device", romanizationCredit(lyrics("uta", "uta"), 0, romanize = true, shown = true))
        assertEquals("Genius", romanizationCredit(lyrics("uta", "uta"), 2, romanize = true, shown = true))
        assertEquals("Genius + On-device", romanizationCredit(lyrics("uta", "uta"), 1, romanize = true, shown = true))
    }

    @Test fun `nothing while romanization is off, the credit is off, or there is none`() {
        assertNull(romanizationCredit(lyrics("uta"), 0, romanize = false, shown = true))
        assertNull(romanizationCredit(lyrics("uta"), 0, romanize = true, shown = false))
        assertNull(romanizationCredit(lyrics(null, null), 0, romanize = true, shown = true))
        assertNull(romanizationCredit(LyricsState.Idle, 0, romanize = true, shown = true))
        assertNull(romanizationCredit(null, 0, romanize = true, shown = true))
    }
}
