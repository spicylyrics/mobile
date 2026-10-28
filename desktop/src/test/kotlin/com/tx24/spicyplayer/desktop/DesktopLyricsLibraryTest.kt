package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.lyricsmanager.AddResult
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLyricsLibraryTest {
    private val dir: File = Files.createTempDirectory("lyrics-library").toFile()
    private val track = Track("spotify", "Hello", "Ada", "First", 6_000L)
    private val ttml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div>""" +
        """<p begin="0s" end="2s"><span begin="0s" end="1s">Hello</span> <span begin="1s" end="2s">world</span></p>""" +
        """</div></body></tt>"""

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test fun savedSongsSurviveARestartAndCanBeRemoved() = runBlocking {
        val cover = File(dir, "playing.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val library = DesktopLyricsLibrary(File(dir, "lyrics")) { cover }
        assertEquals(AddResult.Added(saved = true), library.add(track.asLibraryTrack(), ttml, "song.ttml", save = true))

        val reopened = DesktopLyricsLibrary(File(dir, "lyrics"))
        val entry = reopened.entries().single()
        assertEquals("Hello", entry.title)
        assertEquals("Ada", entry.artist)
        assertEquals("First", entry.album)
        assertEquals(ttml, reopened.raw(entry.id))
        assertArrayEquals(byteArrayOf(1, 2, 3), reopened.cover(entry.id))

        reopened.remove(entry.id)
        assertTrue(reopened.entries().isEmpty())
        assertNull(reopened.raw(entry.id))
    }

    @Test fun thePlayingSongIsFoundByItsId() = runBlocking {
        val library = DesktopLyricsLibrary(File(dir, "lyrics"))
        library.add(track.asLibraryTrack(), ttml, "song.ttml", save = true)
        val id = library.entries().single().id
        assertEquals(id, library.idOf(track.asLibraryTrack()))
        // The player's casing and spacing don't make it another song.
        assertEquals(id, library.idOf(track.copy(title = " hello ", artist = "ADA").asLibraryTrack()))
        assertTrue(id != library.idOf(track.copy(title = "Goodbye").asLibraryTrack()))
    }

    @Test fun filesThatAreNotLyricsAreRefused() = runBlocking {
        val library = DesktopLyricsLibrary(File(dir, "lyrics"))
        val notLyrics = library.add(track.asLibraryTrack(), "just some words", "notes.ttml", save = true)
        assertEquals(AddResult.Refused("Failed to parse TTML."), notLyrics)
        val untimed = library.add(track.asLibraryTrack(), "[ti:Only tags]\n[00:01.00]", "song.lrc", save = true)
        assertEquals(AddResult.Refused("That LRC file has no timed lines."), untimed)
        assertTrue(library.entries().isEmpty())
    }

    @Test fun lrcIsSavedAsTtml() = runBlocking {
        val library = DesktopLyricsLibrary(File(dir, "lyrics"))
        val result = library.add(track.asLibraryTrack(), "[00:01.00]First line\n[00:03.00]Second line", "song.lrc", save = true)
        assertEquals(AddResult.Added(saved = true), result)
        assertTrue(library.raw(library.entries().single().id)!!.contains("<tt"))
    }

    @Test fun lookupPrefersJustOnceThenSavedLyrics() = runBlocking {
        val library = DesktopLyricsLibrary(File(dir, "lyrics"))
        assertNull(library.resolutionFor(track))
        library.add(track.asLibraryTrack(), ttml, "song.ttml", save = true)
        assertEquals(LOCAL_SOURCE, library.resolutionFor(track)!!.selection.source)

        library.add(track.asLibraryTrack(), ttml, "song.ttml", save = false)
        assertEquals(UPLOADED_SOURCE, library.resolutionFor(track)!!.selection.source)
        // Just once isn't saved, and goes on reset.
        assertEquals(1, library.entries().size)
        library.clearOnce()
        assertEquals(LOCAL_SOURCE, library.resolutionFor(track)!!.selection.source)
        assertNull(library.resolutionFor(track.copy(title = "Another")))
    }

    @Test fun theDataFolderSitsBesideTheSettings() {
        assertEquals("lyrics", DesktopLyricsLibrary.defaultDir().name)
    }
}
