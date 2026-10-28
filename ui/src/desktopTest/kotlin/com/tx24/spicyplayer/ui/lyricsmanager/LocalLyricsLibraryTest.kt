package com.tx24.spicyplayer.ui.lyricsmanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLyricsLibraryTest {
    private val hello = LibraryEntry("a1b2", "Hello", "Ada Lovelace", "First", 3)
    private val night = LibraryEntry("c3d4", "Night Drive", "Grace", "Second", 2)
    private val rain = LibraryEntry("e5f6", "Rain", "Ada Lovelace", "Third", 1)
    private val all = listOf(hello, night, rain)

    @Test fun searchMatchesTitleArtistAndId() {
        assertEquals(all, filterLibrary(all, ""))
        assertEquals(all, filterLibrary(all, "   "))
        assertEquals(listOf(night), filterLibrary(all, "drive"))
        assertEquals(listOf(hello, rain), filterLibrary(all, "LOVELACE"))
        assertEquals(listOf(rain), filterLibrary(all, "e5f"))
        assertEquals(listOf(hello), filterLibrary(all, "  hel "))
        assertTrue(filterLibrary(all, "nothing").isEmpty())
    }

    @Test fun thePlayingSongGoesFirst() {
        assertEquals(listOf(rain, hello, night), pinCurrent(all, "e5f6"))
        assertEquals(all, pinCurrent(all, "a1b2"))
        assertEquals(all, pinCurrent(all, null))
        assertEquals(all, pinCurrent(all, "not saved"))
    }

    @Test fun fileNamesDropOnlyWhatFileSystemsRefuse() {
        assertEquals("AC_DC - Back in Black", sanitizeFilename("AC/DC - Back in Black"))
        assertEquals("君と歩く", sanitizeFilename("君と歩く"))
        assertEquals("Café Ünïcode", sanitizeFilename("Café Ünïcode"))
        assertEquals("What_ _Live_ _1_", sanitizeFilename("What? \"Live\" <1>"))
        assertEquals("a_b_c", sanitizeFilename("a\tb\u0000c"))
        assertEquals("Hello", sanitizeFilename("Hello. . "))
        assertEquals("lyrics", sanitizeFilename("..."))
        assertEquals(100, sanitizeFilename("x".repeat(300)).length)
        // A cut through an emoji drops the half that's left.
        assertEquals("x".repeat(99), sanitizeFilename("x".repeat(99) + "🎵"))
    }

    @Test fun onlyLyricsFilesAreTaken() {
        assertTrue(acceptsFile("song.ttml"))
        assertTrue(acceptsFile("SONG.LRC"))
        assertTrue(acceptsFile("song.xml"))
        assertTrue(acceptsFile("lyrics"))
        assertFalse(acceptsFile("cover.png"))
        assertFalse(acceptsFile("song.txt"))
    }
}
