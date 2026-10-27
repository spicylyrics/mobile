package com.tx24.spicyplayer.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GsmtcLineTest {
    private val spotify = """{"t":1761600000123,"mono":1520.5,"event":"timeline","app":"Spotify.exe","current":true,""" +
        """"title":"Song","artist":"Band","album":"Record","durationMs":215000,"positionMs":61234,""" +
        """"lastUpdatedMs":1761600000100,"status":"Playing","rate":1.0,"cover":"C:\\Temp\\spicy-lyrics-covers\\0123456789abcdef.img"}"""

    private fun snapshot(raw: String) = (parseGsmtcLine(raw) as MediaUpdate.Snapshot).snapshot

    @Test
    fun `reads a session report`() {
        val s = snapshot(spotify)
        assertEquals("Spotify.exe", s.app)
        assertTrue(s.current)
        assertEquals("Song", s.title)
        assertEquals("Band", s.artist)
        assertEquals("Record", s.album)
        assertEquals(215000L, s.durationMs)
        assertEquals(61234L, s.positionMs)
        assertEquals(1761600000100L, s.updatedAtWallMs)
        assertEquals(PlaybackStatus.Playing, s.status)
        assertEquals(1f, s.rate)
        assertEquals(File("C:\\Temp\\spicy-lyrics-covers\\0123456789abcdef.img"), s.cover)
        assertEquals("timeline", s.event)
    }

    @Test
    fun `missing or null artwork and rate are null`() {
        val noCover = snapshot(spotify.replace(Regex(""","cover":"[^"]*""""), ""))
        assertNull(noCover.cover)
        val nullCover = snapshot(spotify.replace(Regex(""""cover":"[^"]*""""), "\"cover\":null").replace("\"rate\":1.0", "\"rate\":null"))
        assertNull(nullCover.cover)
        assertNull(nullCover.rate)
    }

    @Test
    fun `malformed lines are skipped`() {
        assertNull(parseGsmtcLine(""))
        assertNull(parseGsmtcLine("thread 'main' panicked at src/main.rs"))
        assertNull(parseGsmtcLine("""{"app":"Spotify.exe","status":"Playing""""))
        assertNull(parseGsmtcLine("[1,2,3]"))
        // A report without its timeline.
        assertNull(parseGsmtcLine(spotify.replace(""""lastUpdatedMs":1761600000100,""", "")))
        assertNull(parseGsmtcLine(spotify.replace(""""positionMs":61234""", """"positionMs":"soon"""")))
    }

    @Test
    fun `a removal and the ctl reply`() {
        assertEquals(MediaUpdate.Removed("Spotify.exe"), parseGsmtcLine("""{"t":1761600000123,"event":"removed","app":"Spotify.exe"}"""))
        assertNull(parseGsmtcLine("""{"t":1761600000123,"ctl":"seek","app":"Spotify.exe","ok":true}"""))
    }

    @Test
    fun `apple music's artist and album come apart`() {
        val s = snapshot(spotify.replace("\"artist\":\"Band\"", "\"artist\":\"Band — Record Two\"").replace("\"album\":\"Record\"", "\"album\":\"\""))
        assertEquals("Band", s.artist)
        assertEquals("Record Two", s.album)
        // With an album of its own, a dash in the artist stays.
        val kept = snapshot(spotify.replace("\"artist\":\"Band\"", "\"artist\":\"Band — Live\""))
        assertEquals("Band — Live", kept.artist)
        assertEquals("Record", kept.album)
    }

    @Test
    fun `statuses map by name`() {
        assertEquals(PlaybackStatus.Paused, snapshot(spotify.replace("\"Playing\"", "\"Paused\"")).status)
        assertEquals(PlaybackStatus.Changing, snapshot(spotify.replace("\"Playing\"", "\"Changing\"")).status)
        assertEquals(PlaybackStatus.Unknown, snapshot(spotify.replace("\"Playing\"", "\"Rewinding\"")).status)
    }

    @Test
    fun `a player switch moves current`() {
        val chrome = """{"t":1761600005000,"event":"sessions","app":"chrome","current":true,"title":"Other","artist":"Someone",""" +
            """"album":"","durationMs":180000,"positionMs":0,"lastUpdatedMs":1761600004990,"status":"Playing","rate":1.0,"cover":null}"""
        val spotifyNow = snapshot(spotify.replace("\"current\":true", "\"current\":false"))
        assertFalse(spotifyNow.current)
        assertTrue(snapshot(chrome).current)
        assertEquals("chrome", snapshot(chrome).app)
    }

    @Test
    fun `ctl arguments`() {
        assertEquals(listOf("ctl", "Spotify", "play"), gsmtcCtlArgs("Spotify", Transport.PLAY, 0))
        assertEquals(listOf("ctl", "Spotify", "pause"), gsmtcCtlArgs("Spotify", Transport.PAUSE, 0))
        assertEquals(listOf("ctl", "Spotify", "seek", "61234"), gsmtcCtlArgs("Spotify", Transport.SEEK, 61234))
        assertEquals(listOf("ctl", "Spotify", "seek", "0"), gsmtcCtlArgs("Spotify", Transport.SEEK, -50))
        assertNull(gsmtcCtlArgs("Spotify", Transport.NEXT, 0))
    }
}
