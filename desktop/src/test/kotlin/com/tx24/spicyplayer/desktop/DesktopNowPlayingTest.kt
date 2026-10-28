package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.bigscreen.PlayerCapabilities
import com.tx24.spicyplayer.ui.nowplaying.RepeatState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopNowPlayingTest {
    @Test
    fun `windows offers play, pause and seek but no skips yet`() {
        assertEquals(
            PlayerCapabilities(playPause = true, skipNext = false, skipPrevious = false, seek = true, fullscreen = true, pictureInPicture = true),
            capabilitiesOf(setOf(Transport.PLAY, Transport.PAUSE, Transport.SEEK)),
        )
    }

    @Test
    fun `play pause needs both commands`() {
        assertEquals(false, capabilitiesOf(setOf(Transport.PLAY)).playPause)
    }

    @Test
    fun `every command maps through`() {
        assertEquals(PlayerCapabilities(fullscreen = true, pictureInPicture = true), capabilitiesOf(Transport.entries.toSet()))
    }

    @Test
    fun `a source with no commands still has fullscreen`() {
        assertEquals(PlayerCapabilities(false, false, false, false, fullscreen = true, pictureInPicture = true), capabilitiesOf(emptySet()))
    }

    @Test
    fun `shuffle and repeat follow what the source can set`() {
        val caps = capabilitiesOf(Transport.entries.toSet(), canShuffle = true, canRepeat = false)
        assertEquals(true, caps.shuffle)
        assertEquals(false, caps.repeat)
        assertEquals(false, capabilitiesOf(Transport.entries.toSet()).shuffle)
    }

    @Test
    fun `the page's floating-window button is the mini lyrics window`() {
        assertEquals(true, capabilitiesOf(emptySet()).pictureInPicture)
    }

    @Test
    fun `repeat modes map both ways`() {
        assertEquals(null, repeatStateOf(null))
        for (mode in RepeatMode.entries) assertEquals(mode, repeatModeOf(repeatStateOf(mode)!!))
        assertEquals(RepeatState.One, repeatStateOf(RepeatMode.One))
        assertEquals(RepeatState.All, repeatStateOf(RepeatMode.All))
        assertEquals(RepeatState.Off, repeatStateOf(RepeatMode.Off))
    }

    @Test
    fun `the page sees the player's shuffle and repeat, and its presses reach the source`() {
        val calls = mutableListOf<String>()
        val source = object : MediaSource {
            override val name = "fake"
            override val unavailableReason: String? = null
            override val capabilities = emptySet<Transport>()
            override val canShuffle = true
            override val canRepeat = true
            override fun updates(): Flow<Received> = emptyFlow()
            override fun control(app: String, action: Transport, positionMs: Long) {}
            override fun setShuffle(app: String, on: Boolean) { calls += "shuffle $on" }
            override fun setRepeat(app: String, mode: RepeatMode) { calls += "repeat $mode" }
        }
        val session = MediaSession()
        val nowPlaying = DesktopNowPlaying(source, session)
        val wall = 1_761_600_000_000L
        session.feed(
            MediaSnapshot("Spotify.exe", true, "One", "Artist", "Album", 200_000, 0, wall, PlaybackStatus.Playing, 1f, shuffle = true, repeat = RepeatMode.All),
            50_000L, wall,
        )
        nowPlaying.refresh()
        assertEquals(true, nowPlaying.state.value.shuffle)
        assertEquals(RepeatState.All, nowPlaying.state.value.repeat)
        assertEquals(true, nowPlaying.state.value.repeatOneSupported)
        nowPlaying.setShuffle(false)
        nowPlaying.setRepeat(RepeatState.One)
        assertEquals(listOf("shuffle false", "repeat One"), calls)
        // A player that can't repeat one track says so, and the page hears it.
        session.feed(
            MediaSnapshot("Spotify.exe", true, "One", "Artist", "Album", 200_000, 250, wall + 250, PlaybackStatus.Playing, 1f, repeat = RepeatMode.All, repeatOneSupported = false),
            50_250L, wall + 250,
        )
        nowPlaying.refresh()
        assertEquals(false, nowPlaying.state.value.repeatOneSupported)
    }
}
