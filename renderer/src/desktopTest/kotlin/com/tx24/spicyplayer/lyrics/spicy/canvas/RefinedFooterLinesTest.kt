package com.tx24.spicyplayer.lyrics.spicy.canvas

import com.tx24.spicyplayer.lyrics.spicy.models.FooterLine
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsProvenance
import org.junit.Assert.assertEquals
import org.junit.Test

class RefinedFooterLinesTest {
    @Test fun translationAndRomanizationGetARowEachOrShareOneRefined() {
        val footer = LyricsFooter(provenance = LyricsProvenance("LRCLIB"), translationCredit = "Genius + Google", romanizationCredit = "On-device")
        val lines = footer.lines()
        assertEquals(listOf("Provided by: LRCLIB", "Translation from: Genius + Google", "Romanization from: On-device"), lines.map { it.text })
        val refined = refinedFooterLines(lines)
        assertEquals(listOf("Provided by: LRCLIB", "Translation from: Genius + Google · Romanization from: On-device"), refined.map { it.text })
        // Pinned "No Writers" keeps them with the source.
        assertEquals(true, PinnedFooterMode.NoWriters.pins(FooterLine.Kind.TEXTS))
    }
}
