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

    @Test
    fun `shuffle and repeat, and the poll lines from before them`() {
        val spotify = parseMacLine(line("Spotify", "playing", "1", "2000", "Song", "Band", "Record", "", "true", "true"), 0)!!
        assertEquals(true, spotify.shuffle)
        assertEquals(RepeatMode.All, spotify.repeat)
        assertEquals("Song", spotify.title)
        val off = parseMacLine(line("Spotify", "paused", "1", "2000", "Song", "Band", "Record", "", "false", "false"), 0)!!
        assertEquals(false, off.shuffle)
        assertEquals(RepeatMode.Off, off.repeat)
        val music = parseMacLine(line("Music", "playing", "1", "2", "Track", "Singer", "Album", "", "true", "one"), 0)!!
        assertEquals(true, music.shuffle)
        assertEquals(RepeatMode.One, music.repeat)
        assertEquals(RepeatMode.All, parseMacLine(line("Music", "playing", "1", "2", "T", "S", "A", "", "false", "all"), 0)!!.repeat)
        assertEquals(RepeatMode.Off, parseMacLine(line("Music", "playing", "1", "2", "T", "S", "A", "", "false", "off"), 0)!!.repeat)
        // A player with no track still says.
        val idle = parseMacLine(line("Music", "stopped", "false", "all"), 0)!!
        assertEquals(false, idle.shuffle)
        assertEquals(RepeatMode.All, idle.repeat)
        // Couldn't read them, or something else came back: unknown.
        val unread = parseMacLine(line("Spotify", "playing", "1", "2000", "Song", "Band", "Record", "", "", "missing value"), 0)!!
        assertNull(unread.shuffle)
        assertNull(unread.repeat)
        assertNull(parseMacLine(line("Music", "playing", "1", "2", "T", "S", "A", "", "yes", "sometimes"), 0)!!.repeat)
        // The lines from before shuffle and repeat.
        val old = parseMacLine(line("Spotify", "playing", "1", "2000", "Song", "Band", "Record", ""), 0)!!
        assertEquals("Song", old.title)
        assertNull(old.shuffle)
        assertNull(old.repeat)
        assertNull(parseMacLine(line("Music", "stopped"), 0)!!.shuffle)
        // Odd field counts are still rejected.
        assertNull(parseMacLine(line("Music", "stopped", "true"), 0))
        assertNull(parseMacLine(line("Spotify", "playing", "1", "2000", "Song", "Band", "Record", "", "true"), 0))
    }

    @Test
    fun `the poll reads shuffle and repeat apart from the track`() {
        val spotify = macPollArgs(listOf(MacPlayer.SPOTIFY)).filterIndexed { i, _ -> i % 2 == 1 }
        assertTrue("set sh to (shuffling as string)" in spotify)
        assertTrue("set rp to (repeating as string)" in spotify)
        val music = macPollArgs(listOf(MacPlayer.MUSIC)).filterIndexed { i, _ -> i % 2 == 1 }
        assertTrue("set sh to (shuffle enabled as string)" in music)
        assertTrue("set rp to (song repeat as string)" in music)
        // Each read is in its own try, ahead of the track's, so a failed one can't hide the track.
        assertTrue(music.indexOf("set rp to (song repeat as string)") < music.indexOf("set t to current track"))
        assertEquals("try", music[music.indexOf("set sh to (shuffle enabled as string)") - 1])
        assertEquals("end try", music[music.indexOf("set rp to (song repeat as string)") + 1])
    }

    @Test
    fun `shuffle and repeat scripts`() {
        assertEquals("tell application \"Spotify\" to set shuffling to true", macShuffleScript("Spotify", true))
        assertEquals("tell application \"Music\" to set shuffle enabled to false", macShuffleScript("Music", false))
        assertEquals("tell application \"Spotify\" to set repeating to false", macRepeatScript("Spotify", RepeatMode.Off))
        // Spotify can't repeat one track: One turns on its only repeat.
        assertEquals("tell application \"Spotify\" to set repeating to true", macRepeatScript("Spotify", RepeatMode.One))
        assertEquals("tell application \"Spotify\" to set repeating to true", macRepeatScript("Spotify", RepeatMode.All))
        assertEquals("tell application \"Music\" to set song repeat to off", macRepeatScript("Music", RepeatMode.Off))
        assertEquals("tell application \"Music\" to set song repeat to one", macRepeatScript("Music", RepeatMode.One))
        assertEquals("tell application \"Music\" to set song repeat to all", macRepeatScript("Music", RepeatMode.All))
        assertNull(macShuffleScript("VLC", true))
        assertNull(macRepeatScript("Spotify\" to quit --", RepeatMode.All))
    }

    @Test
    fun `spotify can't repeat one track`() {
        val spotify = parseMacLine(line("Spotify", "playing", "1", "2000", "Song", "Band", "Record", "", "false", "true"), 0)!!
        assertFalse(spotify.repeatOneSupported)
        assertFalse(parseMacLine(line("Spotify", "stopped", "false", "false"), 0)!!.repeatOneSupported)
        assertFalse(parseMacLine(line("Spotify", "playing", "1", "2000", "Song", "Band", "Record", ""), 0)!!.repeatOneSupported)
        assertTrue(parseMacLine(line("Music", "playing", "1", "2", "T", "S", "A", "", "false", "one"), 0)!!.repeatOneSupported)
        assertTrue(parseMacLine(line("Music", "stopped"), 0)!!.repeatOneSupported)
    }
}
