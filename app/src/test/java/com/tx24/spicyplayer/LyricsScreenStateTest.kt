package com.tx24.spicyplayer

import com.tx24.spicyplayer.playback.PlayerLimit
import com.tx24.spicyplayer.playback.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsScreenStateTest {
    @Test
    fun `the player's state reaches the shared screen as it is`() {
        val state = PlayerUiState(
            accessGranted = true,
            sourcePackage = "com.spotify.music",
            title = "Song",
            artist = "Artist",
            album = "Album",
            durationMs = 200_000L,
            isPlaying = true,
            canSeek = true,
            canvasUrl = "https://example.com/canvas.mp4",
            canvasPending = false,
            releaseYear = "2020",
            localLyricsKey = "artist|song",
            translationEnabled = true,
            translating = true,
            humanRomanizedLines = 3,
            limitNotice = PlayerLimit.Seek,
        ).lyricsScreenState(artwork = null)

        assertEquals("Song", state.nowPlaying.track.title)
        assertEquals("Artist", state.nowPlaying.track.artists)
        assertEquals(200_000L, state.durationMs)
        assertTrue(state.isPlaying)
        assertTrue(state.hasSession)
        assertTrue(state.nowPlaying.canSeek)
        assertTrue(state.accessGranted)
        assertEquals("https://example.com/canvas.mp4", state.canvasUrl)
        assertFalse(state.canvasPending)
        assertEquals("2020", state.releaseYear)
        assertTrue(state.hasSongKey)
        assertFalse(state.hasQueue)
        assertTrue(state.translationEnabled)
        assertTrue(state.translating)
        assertEquals(3, state.humanRomanizedLines)
        assertTrue(state.playerNotice)
    }

    @Test
    fun `nothing playing has no session, no song key and no notice`() {
        val state = PlayerUiState().lyricsScreenState(artwork = null)

        assertFalse(state.hasSession)
        assertFalse(state.hasSongKey)
        assertFalse(state.playerNotice)
        assertFalse(state.accessGranted)
        assertTrue(state.canvasPending)
        assertNull(state.nowPlaying.artwork)
    }
}
