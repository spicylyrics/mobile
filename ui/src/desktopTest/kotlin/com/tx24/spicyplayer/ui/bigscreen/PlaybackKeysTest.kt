package com.tx24.spicyplayer.ui.bigscreen

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackKeysTest {
    private val all = PlayerCapabilities()

    @Test
    fun `space plays and pauses, arrows seek, shift arrows skip`() {
        assertEquals(PlaybackKey.PlayPause, playbackKeyOf(Key.Spacebar, shift = false, otherModifier = false))
        assertEquals(PlaybackKey.SeekBack, playbackKeyOf(Key.DirectionLeft, shift = false, otherModifier = false))
        assertEquals(PlaybackKey.SeekForward, playbackKeyOf(Key.DirectionRight, shift = false, otherModifier = false))
        assertEquals(PlaybackKey.Previous, playbackKeyOf(Key.DirectionLeft, shift = true, otherModifier = false))
        assertEquals(PlaybackKey.Next, playbackKeyOf(Key.DirectionRight, shift = true, otherModifier = false))
    }

    @Test
    fun `other keys and the app's own shortcuts are left alone`() {
        assertNull(playbackKeyOf(Key.A, shift = false, otherModifier = false))
        assertNull(playbackKeyOf(Key.DirectionUp, shift = false, otherModifier = false))
        assertNull(playbackKeyOf(Key.Spacebar, shift = true, otherModifier = false))
        assertNull(playbackKeyOf(Key.DirectionRight, shift = false, otherModifier = true))
        assertNull(playbackKeyOf(Key.Spacebar, shift = false, otherModifier = true))
    }

    @Test
    fun `seeks move five seconds and stay inside the song`() {
        assertEquals(PlaybackCommand.Seek(65_000), playbackCommand(PlaybackKey.SeekForward, 60_000, 200_000, all, canSeek = true))
        assertEquals(PlaybackCommand.Seek(55_000), playbackCommand(PlaybackKey.SeekBack, 60_000, 200_000, all, canSeek = true))
        assertEquals(PlaybackCommand.Seek(0), playbackCommand(PlaybackKey.SeekBack, 2_000, 200_000, all, canSeek = true))
        assertEquals(PlaybackCommand.Seek(200_000), playbackCommand(PlaybackKey.SeekForward, 198_000, 200_000, all, canSeek = true))
        // An unknown length only keeps it above zero.
        assertEquals(PlaybackCommand.Seek(9_000), playbackCommand(PlaybackKey.SeekForward, 4_000, 0, all, canSeek = true))
    }

    @Test
    fun `no seek where the platform or the player can't`() {
        assertNull(playbackCommand(PlaybackKey.SeekForward, 0, 100_000, all.copy(seek = false), canSeek = true))
        assertNull(playbackCommand(PlaybackKey.SeekBack, 50_000, 100_000, all, canSeek = false))
    }

    @Test
    fun `play pause and skips follow the capabilities`() {
        assertEquals(PlaybackCommand.PlayPause, playbackCommand(PlaybackKey.PlayPause, 0, 0, all, canSeek = false))
        assertEquals(PlaybackCommand.Next, playbackCommand(PlaybackKey.Next, 0, 0, all, canSeek = false))
        assertEquals(PlaybackCommand.Previous, playbackCommand(PlaybackKey.Previous, 0, 0, all, canSeek = false))
        val windows = PlayerCapabilities(skipNext = false, skipPrevious = false, fullscreen = true)
        assertNull(playbackCommand(PlaybackKey.Next, 0, 0, windows, canSeek = true))
        assertNull(playbackCommand(PlaybackKey.Previous, 0, 0, windows, canSeek = true))
        assertNull(playbackCommand(PlaybackKey.PlayPause, 0, 0, all.copy(playPause = false), canSeek = true))
    }
}
