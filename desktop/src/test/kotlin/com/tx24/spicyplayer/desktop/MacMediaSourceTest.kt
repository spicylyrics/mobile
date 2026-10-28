package com.tx24.spicyplayer.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacMediaSourceTest {
    private fun line(vararg fields: String) = fields.joinToString(MAC_SEP)

    @Test
    fun `reads a playing spotify line`() {
        val s = parseMacLine(line("Spotify", "playing", "61.234", "215000", "Song", "Band", "Record", "https://i.scdn.co/image/abc"), 1_000L)!!
        assertEquals("Spotify", s.app)
        assertFalse(s.current)
        assertEquals(PlaybackStatus.Playing, s.status)
        assertEquals(61234L, s.positionMs)
        assertEquals(215000L, s.durationMs)
        assertEquals("Song", s.title)
        assertEquals("Band", s.artist)
        assertEquals("Record", s.album)
        assertEquals(1_000L, s.updatedAtWallMs)
        assertEquals(1f, s.rate)
        assertEquals("https://i.scdn.co/image/abc", s.artUrl)
        assertNull(s.cover)
    }

    @Test
    fun `reads music, with seconds and a comma decimal`() {
        val s = parseMacLine(line("Music", "paused", "12,5", "245,333", "Track", "Singer", "Album", "") + "\n", 0L)!!
        assertEquals(PlaybackStatus.Paused, s.status)
        assertEquals(12500L, s.positionMs)
        assertEquals(245333L, s.durationMs)
        assertNull(s.artUrl)
    }

    @Test
    fun `a player with no track`() {
        val s = parseMacLine(line("Music", "stopped"), 0L)!!
        assertEquals(PlaybackStatus.Stopped, s.status)
        assertEquals("", s.title)
    }

    @Test
    fun `malformed lines are skipped`() {
        assertNull(parseMacLine("", 0))
        assertNull(parseMacLine("execution error: Not authorized to send Apple events to Spotify. (-1743)", 0))
        assertNull(parseMacLine(line("VLC", "playing", "1", "2", "a", "b", "c", ""), 0))
        assertNull(parseMacLine(line("Spotify", "playing", "1"), 0))
        assertNull(parseMacLine(line("Spotify", "playing", "soon", "2", "a", "b", "c", ""), 0))
        assertNull(parseMacLine(line("Spotify", "playing", "1", "2", "a", "b", "c", "missing value"), 0)?.artUrl)
    }

    @Test
    fun `a playing player is followed`() {
        fun snap(app: String, status: PlaybackStatus) = MediaSnapshot(app, false, "t", "a", "", 1, 0, 0, status, 1f)
        val spotifyPaused = snap("Spotify", PlaybackStatus.Paused)
        val musicPlaying = snap("Music", PlaybackStatus.Playing)
        val spotifyPlaying = snap("Spotify", PlaybackStatus.Playing)
        assertEquals("Music", pickMacCurrent(listOf(spotifyPaused, musicPlaying), "Spotify"))
        // Both playing: stay with the one followed.
        assertEquals("Spotify", pickMacCurrent(listOf(spotifyPlaying, musicPlaying), "Spotify"))
        // Nothing playing: stay while it's there.
        assertEquals("Music", pickMacCurrent(listOf(spotifyPaused, snap("Music", PlaybackStatus.Paused)), "Music"))
        assertEquals("Spotify", pickMacCurrent(listOf(spotifyPaused), "Music"))
        assertNull(pickMacCurrent(emptyList(), "Music"))
    }

    @Test
    fun `the poll script asks only installed players, without launching them`() {
        val script = macPollArgs(listOf(MacPlayer.MUSIC)).filterIndexed { i, _ -> i % 2 == 1 }
        assertTrue(macPollArgs(listOf(MacPlayer.MUSIC)).filterIndexed { i, _ -> i % 2 == 0 }.all { it == "-e" })
        assertTrue("if application \"Music\" is running then" in script)
        assertFalse(script.any { "Spotify" in it })
        assertFalse(script.any { "artwork url" in it })
        assertEquals("return out", script.last())
        assertTrue(macPollArgs(listOf(MacPlayer.SPOTIFY)).any { "artwork url of t" in it })
        assertEquals(listOf("-e", "set sep to character id 31", "-e", "set out to \"\"", "-e", "return out"), macPollArgs(emptyList()))
    }

    @Test
    fun `installed players`() {
        val apps = setOf("/System/Applications/Music.app", "/Users/me/Applications/Spotify.app")
        assertEquals(listOf(MacPlayer.SPOTIFY, MacPlayer.MUSIC), installedMacPlayers("/Users/me") { it in apps })
        assertEquals(emptyList<MacPlayer>(), installedMacPlayers("/Users/me") { false })
    }

    @Test
    fun `control scripts`() {
        assertEquals("tell application \"Spotify\" to play", macControlScript("Spotify", Transport.PLAY, 0))
        assertEquals("tell application \"Music\" to pause", macControlScript("Music", Transport.PAUSE, 0))
        assertEquals("tell application \"Spotify\" to next track", macControlScript("Spotify", Transport.NEXT, 0))
        assertEquals("tell application \"Music\" to previous track", macControlScript("Music", Transport.PREVIOUS, 0))
        assertEquals("tell application \"Spotify\" to set player position to 61.234", macControlScript("Spotify", Transport.SEEK, 61234))
        assertNull(macControlScript("Spotify\" to quit --", Transport.PLAY, 0))
    }

    @Test
    fun `needs osascript`() {
        val source = MacMediaSource(osascript = "/nowhere/osascript")
        assertNotNull(source.unavailableReason)
        assertEquals(Transport.entries.toSet(), source.capabilities)
    }
}
