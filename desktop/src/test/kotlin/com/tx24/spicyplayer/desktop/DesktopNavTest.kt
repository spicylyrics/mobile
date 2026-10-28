package com.tx24.spicyplayer.desktop

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopNavTest {
    @Test
    fun `each shortcut opens its page, and only with Ctrl or Cmd`() {
        assertEquals(Destination.Settings, shortcutDestination(Key.Comma, command = true))
        assertEquals(Destination.LyricsManager, shortcutDestination(Key.L, command = true))
        assertEquals(Destination.SyncDelay, shortcutDestination(Key.D, command = true))
        assertNull(shortcutDestination(Key.D, command = false))
        assertNull(shortcutDestination(Key.M, command = true))
    }

    @Test
    fun `back from any page is the lyrics`() {
        val nav = DesktopNav()
        assertEquals(Destination.Lyrics, nav.at)
        nav.open(Destination.SyncDelay)
        nav.open(Destination.Settings)
        assertEquals(Destination.Settings, nav.at)
        nav.back()
        assertEquals(Destination.Lyrics, nav.at)
    }
}
