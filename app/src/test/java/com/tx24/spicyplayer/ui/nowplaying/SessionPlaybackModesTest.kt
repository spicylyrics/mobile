package com.tx24.spicyplayer.ui.nowplaying

import android.support.v4.media.session.PlaybackStateCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPlaybackModesTest {
    private val both = PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or PlaybackStateCompat.ACTION_SET_REPEAT_MODE

    @Test
    fun `repeat modes map to the page's states`() {
        assertEquals(RepeatState.Off, repeatOf(PlaybackStateCompat.REPEAT_MODE_NONE))
        assertEquals(RepeatState.One, repeatOf(PlaybackStateCompat.REPEAT_MODE_ONE))
        assertEquals(RepeatState.All, repeatOf(PlaybackStateCompat.REPEAT_MODE_ALL))
        assertEquals(RepeatState.All, repeatOf(PlaybackStateCompat.REPEAT_MODE_GROUP))
        assertNull(repeatOf(PlaybackStateCompat.REPEAT_MODE_INVALID))
    }

    @Test
    fun `shuffle modes map to on and off`() {
        assertEquals(false, shuffleOf(PlaybackStateCompat.SHUFFLE_MODE_NONE))
        assertEquals(true, shuffleOf(PlaybackStateCompat.SHUFFLE_MODE_ALL))
        assertEquals(true, shuffleOf(PlaybackStateCompat.SHUFFLE_MODE_GROUP))
        assertNull(shuffleOf(PlaybackStateCompat.SHUFFLE_MODE_INVALID))
    }

    @Test
    fun `requests round-trip through the session's modes`() {
        for (state in RepeatState.values()) assertEquals(state, repeatOf(repeatModeOf(state)))
        assertEquals(true, shuffleOf(shuffleModeOf(true)))
        assertEquals(false, shuffleOf(shuffleModeOf(false)))
    }

    @Test
    fun `modes count only where the session takes the set action`() {
        val none = playbackModesOf(0L, PlaybackStateCompat.SHUFFLE_MODE_NONE, PlaybackStateCompat.REPEAT_MODE_NONE)
        assertEquals(PlaybackModes(), none)

        val shuffleOnly = playbackModesOf(
            PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE,
            PlaybackStateCompat.SHUFFLE_MODE_ALL,
            PlaybackStateCompat.REPEAT_MODE_ONE,
        )
        assertEquals(PlaybackModes(shuffle = true, repeat = null), shuffleOnly)

        val all = playbackModesOf(both, PlaybackStateCompat.SHUFFLE_MODE_NONE, PlaybackStateCompat.REPEAT_MODE_ONE)
        assertEquals(PlaybackModes(shuffle = false, repeat = RepeatState.One), all)
    }

    @Test
    fun `an unconnected session reports nothing even with the actions`() {
        val modes = playbackModesOf(both, PlaybackStateCompat.SHUFFLE_MODE_INVALID, PlaybackStateCompat.REPEAT_MODE_INVALID)
        assertEquals(PlaybackModes(), modes)
    }

    @Test
    fun `capabilities follow the known modes`() {
        val off = androidCapabilities(canSeek = true, pictureInPicture = false)
        assertFalse(off.shuffle)
        assertFalse(off.repeat)

        val on = androidCapabilities(canSeek = true, pictureInPicture = false, PlaybackModes(shuffle = false, repeat = RepeatState.Off))
        assertTrue(on.shuffle)
        assertTrue(on.repeat)
    }
}
