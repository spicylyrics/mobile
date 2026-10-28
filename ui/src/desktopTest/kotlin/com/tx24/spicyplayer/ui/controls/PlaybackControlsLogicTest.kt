package com.tx24.spicyplayer.ui.controls

import com.tx24.spicyplayer.ui.nowplaying.FakeNowPlaying
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingState
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingTrack
import com.tx24.spicyplayer.ui.nowplaying.SessionCustomAction
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackControlsLogicTest {
    private val shuffle = SessionCustomAction("com.spotify.shuffle", "Shuffle", null)
    private val repeat = SessionCustomAction("com.spotify.repeat", "Repeat one", null)
    private val loop = SessionCustomAction("loop_mode", "Mode", null)
    private val like = SessionCustomAction("com.spotify.like", "Add to Liked Songs", null)

    @Test
    fun `actions sort by their name and id`() {
        assertEquals(ActionKind.Shuffle, shuffle.kind)
        assertEquals(ActionKind.Repeat, repeat.kind)
        assertEquals(ActionKind.Repeat, loop.kind)
        assertEquals(ActionKind.Other, like.kind)
    }

    @Test
    fun `without the player's buttons only shuffle and repeat stay`() {
        val all = listOf(shuffle, like, repeat)
        assertEquals(all, shownCustomActions(all, playerButtons = true))
        assertEquals(listOf(shuffle, repeat), shownCustomActions(all, playerButtons = false))
    }

    @Test
    fun `clock reads minutes and seconds, and hours past an hour`() {
        assertEquals("0:00", formatClock(0L))
        assertEquals("0:00", formatClock(-5_000L))
        assertEquals("2:33", formatClock(153_999L))
        assertEquals("10:05", formatClock(605_000L))
        assertEquals("1:02:05", formatClock(3_725_000L))
    }

    @Test
    fun `controls built over a fake player drive it`() {
        val player = FakeNowPlaying(NowPlayingState(NowPlayingTrack("Song", "Artist", durationMs = 200_000L), canSeek = true, customActions = listOf(shuffle, like)))
        val state = player.state.value
        val controls = PlaybackControlsState(
            isPlaying = state.isPlaying,
            canSeek = state.canSeek,
            durationMs = state.track.durationMs,
            positionMs = player::positionMs,
            onPlayPause = player::playPause,
            onPrevious = player::skipPrevious,
            onNext = player::skipNext,
            onSeek = player::seekTo,
            customActions = shownCustomActions(state.customActions, playerButtons = false),
            onCustomAction = player::sendCustomAction,
        )
        controls.onSeek(30_000L)
        assertEquals(30_000L, controls.positionMs())
        controls.onCustomAction(controls.customActions.single().action)
        controls.onNext()
        assertEquals(listOf("seek:30000", "action:com.spotify.shuffle", "next"), player.commands)
    }
}
