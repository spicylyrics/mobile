package com.tx24.spicyplayer.ui.bigscreen

import com.tx24.spicyplayer.ui.nowplaying.NowPlayingState
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingTrack
import com.tx24.spicyplayer.ui.nowplaying.RepeatState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackTogglesTest {
    private val song = NowPlayingState(NowPlayingTrack("Song", "Artist"), player = "app")
    private val both = PlayerCapabilities(shuffle = true, repeat = true)

    @Test
    fun `nothing by default, so a platform without them is unchanged`() {
        val defaults = PlayerCapabilities()
        assertFalse(defaults.shuffle)
        assertFalse(defaults.repeat)
        assertEquals(PlaybackToggles(null, null), playbackToggles(defaults, song.copy(shuffle = true, repeat = RepeatState.All)))
    }

    @Test
    fun `the player's state shows where the platform can set it`() {
        assertEquals(PlaybackToggles(true, RepeatState.One), playbackToggles(both, song.copy(shuffle = true, repeat = RepeatState.One)))
        assertEquals(PlaybackToggles(false, RepeatState.Off), playbackToggles(both, song.copy(shuffle = false, repeat = RepeatState.Off)))
    }

    @Test
    fun `a player that doesn't say hides that toggle`() {
        assertEquals(PlaybackToggles(null, RepeatState.All), playbackToggles(both, song.copy(repeat = RepeatState.All)))
        assertEquals(PlaybackToggles(true, null), playbackToggles(both, song.copy(shuffle = true)))
    }

    @Test
    fun `each capability gates only its own toggle`() {
        val state = song.copy(shuffle = true, repeat = RepeatState.All)
        assertEquals(PlaybackToggles(true, null), playbackToggles(both.copy(repeat = false), state))
        assertEquals(PlaybackToggles(null, RepeatState.All), playbackToggles(both.copy(shuffle = false), state))
    }

    @Test
    fun `repeat cycles off, all, one, off`() {
        assertEquals(RepeatState.All, nextRepeat(RepeatState.Off))
        assertEquals(RepeatState.One, nextRepeat(RepeatState.All))
        assertEquals(RepeatState.Off, nextRepeat(RepeatState.One))
    }

    @Test
    fun `without repeat one, all goes back to off`() {
        assertEquals(RepeatState.All, nextRepeat(RepeatState.Off, oneSupported = false))
        assertEquals(RepeatState.Off, nextRepeat(RepeatState.All, oneSupported = false))
        // A player that somehow reports one still leaves it.
        assertEquals(RepeatState.Off, nextRepeat(RepeatState.One, oneSupported = false))
        assertEquals(RepeatState.One, nextRepeat(RepeatState.All, oneSupported = true))
    }
}
