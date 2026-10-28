package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.bigscreen.PlayerCapabilities
import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopNowPlayingTest {
    @Test
    fun `windows offers play, pause and seek but no skips yet`() {
        assertEquals(
            PlayerCapabilities(playPause = true, skipNext = false, skipPrevious = false, seek = true, fullscreen = true),
            capabilitiesOf(setOf(Transport.PLAY, Transport.PAUSE, Transport.SEEK)),
        )
    }

    @Test
    fun `play pause needs both commands`() {
        assertEquals(false, capabilitiesOf(setOf(Transport.PLAY)).playPause)
    }

    @Test
    fun `every command maps through`() {
        assertEquals(PlayerCapabilities(fullscreen = true), capabilitiesOf(Transport.entries.toSet()))
    }

    @Test
    fun `a source with no commands still has fullscreen`() {
        assertEquals(PlayerCapabilities(false, false, false, false, fullscreen = true), capabilitiesOf(emptySet()))
    }
}
