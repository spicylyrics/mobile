package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.nowplaying.TrackDirection
import com.tx24.spicyplayer.ui.nowplaying.skip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopNowPlayingTest {
    private val sent = mutableListOf<String>()
    private var clock = 0L
    private val player = DesktopNowPlaying({ clock }) { app, command, at -> sent += "$app $command $at" }

    @Test
    fun `a report becomes the header's song`() {
        player.update("Spotify.exe", "Title", "Artist", "Album", 180_000L, playing = true)
        val state = player.state.value
        assertEquals("Title", state.info.title)
        assertEquals(180_000L, state.track.durationMs)
        assertTrue(state.hasSession)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `nothing playing has no session and takes no commands`() {
        player.update(null, "", "", "", 0L, playing = false)
        assertFalse(player.state.value.hasSession)
        player.playPause()
        player.skipNext()
        player.seekTo(1_000L)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `play and pause go to the followed app and flip the button at once`() {
        player.update("Spotify.exe", "Title", "Artist", "Album", 180_000L, playing = true)
        player.playPause()
        assertFalse(player.state.value.isPlaying)
        player.playPause()
        assertEquals(listOf("Spotify.exe PAUSE 0", "Spotify.exe PLAY 0"), sent)
    }

    @Test
    fun `the next song comes in from the side the skip went`() {
        player.update("app", "One", "Artist", "", 1L, playing = true)
        player.skip(TrackDirection.Backward)
        player.update("app", "Zero", "Artist", "", 1L, playing = true)
        assertEquals(TrackDirection.Backward, player.state.value.direction)
        // A song changing on its own moves on.
        player.update("app", "One", "Artist", "", 1L, playing = true)
        assertEquals(TrackDirection.Forward, player.state.value.direction)
        assertEquals(listOf("app PREVIOUS 0"), sent)
    }

    @Test
    fun `seeks go out only where the player can seek, and never before the start`() {
        player.update("app", "Song", "Artist", "", 1L, playing = true, canSeek = false)
        player.seekTo(5_000L)
        player.update("app", "Song", "Artist", "", 1L, playing = true, canSeek = true)
        player.seekTo(-20L)
        assertEquals(listOf("app SEEK 0"), sent)
    }

    @Test
    fun `the position is the clock's`() {
        clock = 42_000L
        assertEquals(42_000L, player.positionMs())
    }
}
