package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.settings.InMemorySettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDelaysTest {
    @Test
    fun `saved per player under its key`() {
        val store = InMemorySettingsStore()
        val delays = SyncDelays(store)
        assertEquals("desktop.offset.spotify.exe", SyncDelays.key("Spotify.exe"))
        delays.set("Spotify.exe", 120)
        assertEquals(120L, store.getLong("desktop.offset.spotify.exe"))
        assertEquals(120L, SyncDelays(store).offsetFor("Spotify.exe"))
        assertEquals(0L, delays.offsetFor("chrome"))
        assertFalse(delays.flagged)
    }

    @Test
    fun `the flag wins`() {
        val store = InMemorySettingsStore(mapOf("desktop.offset.spotify.exe" to 120L))
        val delays = SyncDelays(store, flagMs = -40)
        assertTrue(delays.flagged)
        assertEquals(-40L, delays.offsetFor("Spotify.exe"))
        assertEquals(-40L, delays.offsetFor("chrome"))
    }

    @Test
    fun `the session takes each player's offset on a switch and a change live`() {
        val store = InMemorySettingsStore(mapOf("desktop.offset.spotify.exe" to 300L, "desktop.offset.chrome" to -200L))
        val delays = SyncDelays(store)
        val session = MediaSession(null, delays::offsetFor)
        val wall = 1_761_600_000_000L
        val mono = monoMs()
        fun snap(app: String, title: String) =
            MediaSnapshot(app, true, title, "Artist", "Album", 200_000, 10_123, wall, PlaybackStatus.Playing, 1f)
        session.feed(snap("Spotify.exe", "One"), mono, wall)
        assertEquals(300L, session.offsetMs)
        session.feed(snap("chrome", "Two"), mono, wall)
        assertEquals(-200L, session.offsetMs)

        delays.set("chrome", -100)
        val before = session.clock.positionAt(monoMs())
        session.offsetChanged()
        assertEquals(-100L, session.offsetMs)
        val moved = session.clock.positionAt(monoMs()) - before
        assertTrue("moved $moved", moved in 100L..150L)
    }
}
