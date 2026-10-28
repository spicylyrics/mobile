package com.tx24.spicyplayer.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSourceTest {
    @Test
    fun `platforms by os name`() {
        assertEquals(Platform.WINDOWS, platformOf("Windows 11"))
        assertEquals(Platform.WINDOWS, platformOf("Windows Server 2022"))
        assertEquals(Platform.LINUX, platformOf("Linux"))
        assertEquals(Platform.LINUX, platformOf("FreeBSD"))
        assertEquals(Platform.MAC, platformOf("Mac OS X"))
        assertEquals(Platform.MAC, platformOf("Darwin"))
        assertEquals(Platform.OTHER, platformOf("SunOS"))
        assertEquals(Platform.OTHER, platformOf(""))
    }

    @Test
    fun `the factory picks each platform's source`() {
        val helper = File("nowhere/gsmtc.exe")
        val windows = mediaSource(helper, osName = "Windows 11")
        assertTrue(windows is GsmtcMediaSource)
        assertEquals(helper, (windows as GsmtcMediaSource).helper)
        assertNotNull(windows.unavailableReason)
        assertTrue(mediaSource(helper, osName = "Linux") is MprisMediaSource)
        assertTrue(mediaSource(helper, osName = "Mac OS X") is MacMediaSource)
        assertNotNull(mediaSource(helper, osName = "SunOS").unavailableReason)
    }

    @Test
    fun `both sources take every command`() {
        assertEquals(Transport.entries.toSet(), GsmtcMediaSource(File("x")).capabilities)
        assertEquals(Transport.entries.toSet(), MprisMediaSource(path = null).capabilities)
        assertNotNull(MprisMediaSource(path = null).unavailableReason)
    }

    @Test
    fun `retries back off to 30 seconds`() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), (1..7).map(::retryDelayMs))
        assertEquals(1_000L, retryDelayMs(0))
    }
}
