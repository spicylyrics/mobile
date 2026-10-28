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

    @Test fun fileNamesKeepOnlySafeCharacters() {
        assertEquals("AC_DC - Back in Black", sanitizeFilename("AC/DC - Back in Black"))
        assertEquals("____", sanitizeFilename("君と歩く"))
        assertEquals(100, sanitizeFilename("x".repeat(300)).length)
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
