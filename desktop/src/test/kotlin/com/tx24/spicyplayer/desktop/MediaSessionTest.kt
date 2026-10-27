package com.tx24.spicyplayer.desktop

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionTest {
    private val wall = 1_761_600_000_000L
    private val mono = 50_000L

    private fun snap(app: String, title: String, current: Boolean = true, positionMs: Long = 10_123, stamp: Long = wall, status: PlaybackStatus = PlaybackStatus.Playing) =
        MediaSnapshot(app, current, title, "Artist", "Album", 200_000, positionMs, stamp, status, 1f)

    @Test
    fun `follows the current player across a switch`() {
        val session = MediaSession()
        val first = session.feed(snap("Spotify.exe", "One"), mono, wall)
        assertEquals(SessionEvent.NewTrack(Track("Spotify.exe", "One", "Artist", "Album", 200_000)), first)
        // Another player reporting while not current is ignored.
        assertNull(session.feed(snap("chrome", "Two", current = false), mono + 100, wall + 100))
        assertEquals("One", session.track?.title)
        // Once it becomes current, its track takes over.
        val second = session.feed(snap("chrome", "Two", stamp = wall + 200), mono + 200, wall + 200)
        assertTrue(second is SessionEvent.NewTrack)
        assertEquals("chrome", session.track?.app)
    }

    @Test
    fun `an app filter ignores current`() {
        val session = MediaSession(appFilter = "spotify")
        assertNull(session.feed(snap("chrome", "Two"), mono, wall))
        assertTrue(session.feed(snap("Spotify.exe", "One", current = false), mono, wall) is SessionEvent.NewTrack)
    }

    @Test
    fun `a copy of the last report is dropped`() {
        val session = MediaSession()
        session.feed(snap("Spotify.exe", "One"), mono, wall)
        assertNull(session.feed(snap("Spotify.exe", "One"), mono + 250, wall + 250))
    }

    @Test
    fun `the clock follows the report and the offset`() {
        val session = MediaSession(offsetMs = 300)
        session.feed(snap("Spotify.exe", "One", positionMs = 10_123), mono, wall)
        assertEquals(10_423L, session.clock.positionAt(mono))
        assertTrue(session.clock.isPlaying)
    }

    @Test
    fun `a seek goes only to a source that takes it`() {
        val seen = mutableListOf<Pair<Transport, Long>>()
        class Fake(override val capabilities: Set<Transport>) : MediaSource {
            override val name = "fake"
            override val unavailableReason: String? = null
            override fun updates(): Flow<Received> = emptyFlow()
            override fun control(app: String, action: Transport, positionMs: Long) { seen += action to positionMs }
        }
        val session = MediaSession(offsetMs = 300)
        session.feed(snap("Spotify.exe", "One"), mono, wall)
        session.seek(Fake(emptySet()), 50_000)
        assertTrue(seen.isEmpty())
        session.seek(Fake(setOf(Transport.SEEK)), 50_000)
        assertEquals(listOf(Transport.SEEK to 49_700L), seen)
    }
}
