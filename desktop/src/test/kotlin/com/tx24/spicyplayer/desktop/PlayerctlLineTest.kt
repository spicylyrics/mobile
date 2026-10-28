package com.tx24.spicyplayer.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerctlLineTest {
    private fun line(vararg fields: String) = fields.joinToString("\u001f")

    private fun snapshot(raw: String) = (parsePlayerctlLine(raw, receivedWall = 1_000L) as MediaUpdate.Snapshot).snapshot

    @Test
    fun `the format has every field once`() {
        assertEquals(10, PLAYERCTL_FORMAT.split("\u001f").size)
        assertEquals(listOf("playerctl", "--follow", "metadata", "--format", PLAYERCTL_FORMAT), playerctlFollowArgs())
    }

    @Test
    fun `reads a playing spotify line`() {
        val s = snapshot(line("spotify", "Playing", "61234000", "215000000", "Song", "Band", "Record", "https://i.scdn.co/image/ab67616d0000b273abc"))
        assertEquals("spotify", s.app)
        assertTrue(s.current)
        assertEquals("Song", s.title)
        assertEquals("Band", s.artist)
        assertEquals("Record", s.album)
        assertEquals(61234L, s.positionMs)
        assertEquals(215000L, s.durationMs)
        assertEquals(1_000L, s.updatedAtWallMs)
        assertEquals(PlaybackStatus.Playing, s.status)
        assertNull(s.rate)
        assertNull(s.cover)
        assertEquals("https://i.scdn.co/image/ab67616d0000b273abc", s.artUrl)
    }

    @Test
    fun `local artwork becomes a file`() {
        val s = snapshot(line("vlc", "Paused", "5000000", "180000000", "Song", "Band", "", "file:///tmp/cover%20art.jpg") + "\n")
        assertEquals(PlaybackStatus.Paused, s.status)
        assertEquals("/tmp/cover art.jpg", s.cover?.path?.replace('\\', '/'))
        assertEquals("", s.album)
    }

    @Test
    fun `missing fields`() {
        // A player that knows no length, position or artwork.
        val s = snapshot(line("chromium.instance1234", "Stopped", "", "", "Video", "", "", ""))
        assertEquals(0L, s.positionMs)
        assertEquals(0L, s.durationMs)
        assertEquals(PlaybackStatus.Stopped, s.status)
        assertNull(s.artUrl)
        assertNull(s.cover)
    }

    @Test
    fun `malformed lines are skipped`() {
        assertNull(parsePlayerctlLine("", 0))
        assertNull(parsePlayerctlLine("No players found", 0))
        assertNull(parsePlayerctlLine(line("spotify", "Playing", "1"), 0))
        assertNull(parsePlayerctlLine(line("", "Playing", "1", "2", "a", "b", "c", "d"), 0))
    }

    @Test
    fun `a player switch is a new app`() {
        val a = snapshot(line("spotify", "Paused", "1000000", "200000000", "One", "A", "", ""))
        val b = snapshot(line("firefox.instance_1_42", "Playing", "0", "100000000", "Two", "B", "", ""))
        assertEquals("spotify", a.app)
        assertEquals("firefox.instance_1_42", b.app)
        assertTrue(b.current)
    }

    @Test
    fun `control arguments`() {
        val p = listOf("playerctl", "--player=spotify")
        assertEquals(p + "play", playerctlArgs("playerctl", "spotify", Transport.PLAY, 0))
        assertEquals(p + "pause", playerctlArgs("playerctl", "spotify", Transport.PAUSE, 0))
        assertEquals(p + "next", playerctlArgs("playerctl", "spotify", Transport.NEXT, 0))
        assertEquals(p + "previous", playerctlArgs("playerctl", "spotify", Transport.PREVIOUS, 0))
        assertEquals(p + listOf("position", "61.234"), playerctlArgs("playerctl", "spotify", Transport.SEEK, 61234))
        assertEquals(p + listOf("position", "0.000"), playerctlArgs("playerctl", "spotify", Transport.SEEK, -5))
    }

    @Test
    fun `finds a command on the path`() {
        val dir = createTempDirectory()
        val tool = File(dir, "playerctl").apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        assertTrue(onPath("playerctl", "/nowhere" + File.pathSeparator + dir.path))
        assertFalse(onPath("playerctl", "/nowhere"))
        assertFalse(onPath("playerctl", null))
        tool.delete()
        dir.delete()
    }

    private fun createTempDirectory(): File = kotlin.io.path.createTempDirectory("onpath").toFile()

    @Test
    fun `the last player quitting removes it`() {
        assertEquals(MediaUpdate.Removed("spotify"), parsePlayerctlLine("", 0, lastApp = "spotify"))
        assertEquals(MediaUpdate.Removed("vlc"), parsePlayerctlLine("No players found\n", 0, lastApp = "vlc"))
        assertNull(parsePlayerctlLine("", 0, lastApp = null))
    }

    @Test
    fun `shuffle and loop`() {
        val base = arrayOf("spotify", "Playing", "1000000", "200000000", "One", "A", "", "")
        val on = snapshot(line(*base, "true", "Playlist"))
        assertEquals(true, on.shuffle)
        assertEquals(RepeatMode.All, on.repeat)
        val off = snapshot(line(*base, "false", "None"))
        assertEquals(false, off.shuffle)
        assertEquals(RepeatMode.Off, off.repeat)
        assertEquals(RepeatMode.One, snapshot(line(*base, "", "Track")).repeat)
        // A player without them leaves the fields empty; the format before them has eight fields.
        val none = snapshot(line(*base, "", ""))
        assertNull(none.shuffle)
        assertNull(none.repeat)
        val older = snapshot(line(*base))
        assertNull(older.shuffle)
        assertNull(older.repeat)
        assertNull(parsePlayerctlLine(line(*base, "true"), 0))
    }

    @Test
    fun `shuffle and loop commands`() {
        assertEquals(listOf("playerctl", "--player=spotify", "shuffle", "On"), playerctlShuffleArgs("playerctl", "spotify", true))
        assertEquals(listOf("playerctl", "--player=spotify", "shuffle", "Off"), playerctlShuffleArgs("playerctl", "spotify", false))
        assertEquals(listOf("playerctl", "--player=vlc", "loop", "None"), playerctlLoopArgs("playerctl", "vlc", RepeatMode.Off))
        assertEquals(listOf("playerctl", "--player=vlc", "loop", "Track"), playerctlLoopArgs("playerctl", "vlc", RepeatMode.One))
        assertEquals(listOf("playerctl", "--player=vlc", "loop", "Playlist"), playerctlLoopArgs("playerctl", "vlc", RepeatMode.All))
    }

    @Test
    fun `mpris players can repeat one track`() {
        assertTrue(snapshot(line("spotify", "Playing", "1", "2", "One", "A", "", "", "true", "Track")).repeatOneSupported)
    }
}
