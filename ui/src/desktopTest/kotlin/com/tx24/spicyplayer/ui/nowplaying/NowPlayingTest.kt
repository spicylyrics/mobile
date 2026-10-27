package com.tx24.spicyplayer.ui.nowplaying

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTest {
    @Test
    fun `the header's info carries the track, the cover and the way the player moved`() {
        val state = NowPlayingState(
            track = NowPlayingTrack("Title", "A, B", album = "Album", durationMs = 200_000L),
            player = "com.spotify.music",
            artworkUri = "content://cover",
            direction = TrackDirection.Backward,
        )
        assertEquals(
            NowPlayingInfo("Title", "A, B", "Album", null, "content://cover", TrackDirection.Backward, hasSession = true),
            state.info,
        )
    }

    @Test
    fun `no player means no session, so the logo shows at once`() {
        val state = NowPlayingState(NowPlayingTrack("Nothing playing", ""))
        assertFalse(state.hasSession)
        assertFalse(state.info.hasSession)
    }

    @Test
    fun `a state that keeps its song keeps an equal info`() {
        val state = NowPlayingState(NowPlayingTrack("Title", "Artist"), player = "app")
        assertEquals(state.info, state.copy(isPlaying = true).info)
    }

    @Test
    fun `a cover swipe skips the way it went`() {
        val player = FakeNowPlaying()
        player.skip(TrackDirection.Forward)
        player.skip(TrackDirection.Backward)
        assertEquals(listOf("next", "previous"), player.commands)
        assertEquals(TrackDirection.Backward, player.state.value.direction)
    }

    @Test
    fun `the fake player follows its own transport`() {
        val player = FakeNowPlaying(NowPlayingState(NowPlayingTrack("Song", "Artist"), canSeek = true))
        player.playPause()
        assertTrue(player.state.value.isPlaying)
        player.seekTo(42_000L)
        assertEquals(42_000L, player.positionMs())
        player.sendCustomAction("shuffle")
        assertEquals(listOf("playPause", "seek:42000", "action:shuffle"), player.commands)
    }
}
