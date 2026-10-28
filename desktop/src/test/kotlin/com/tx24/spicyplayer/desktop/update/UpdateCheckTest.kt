package com.tx24.spicyplayer.desktop.update

import java.io.File
import kotlin.io.path.createTempDirectory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    private val dir = createTempDirectory("update-check").toFile()
    private var nowMs = 1_800_000_000_000L
    private var fetches = 0
    private var answer: String? = """[{"tag_name":"desktop-v0.2.0","html_url":"https://github.com/spicylyrics/mobile/releases/tag/desktop-v0.2.0","published_at":"2026-10-27T12:00:00Z","draft":false,"prerelease":true}]"""

    private fun check(running: String = "0.1.0") = UpdateCheck(running, dir, now = { nowMs }, fetch = { fetches++; answer })

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `the daily gate`() {
        assertTrue(checkDue(null, 0))
        assertFalse(checkDue(1_000, 1_000 + DAY_MS - 1))
        assertTrue(checkDue(1_000, 1_000 + DAY_MS))
        // A clock set back doesn't hold the check off for days.
        assertTrue(checkDue(1_000 + DAY_MS, 1_000))
    }

    @Test
    fun `finds an update and asks at most once a day`() {
        val first = check().apply { run() }
        assertEquals("0.2.0", first.update.value?.version)
        assertEquals(1, fetches)

        nowMs += DAY_MS - 1
        val second = check().apply { run() }
        assertEquals(1, fetches)
        assertEquals("0.2.0", second.update.value?.version)

        nowMs += 1
        check().run()
        assertEquals(2, fetches)
    }

    @Test
    fun `up to date or dev shows nothing`() {
        assertNull(check("0.2.0").apply { run() }.update.value)
        assertNull(check("dev").apply { run() }.update.value)
    }

    @Test
    fun `dev never asks`() {
        check("dev").run()
        assertEquals(0, fetches)
    }

    @Test
    fun `offline shows nothing and asks again next launch`() {
        answer = null
        assertNull(check().apply { run() }.update.value)
        check().run()
        assertEquals(2, fetches)
    }

    @Test
    fun `offline keeps the last answer`() {
        check().run()
        nowMs += DAY_MS
        answer = null
        assertEquals("0.2.0", check().apply { run() }.update.value?.version)
    }

    @Test
    fun `a broken cache counts as never checked`() {
        dir.mkdirs()
        File(dir, UpdateCheck.CACHE_FILE).writeText("{not json")
        assertEquals("0.2.0", check().apply { run() }.update.value?.version)
        assertEquals(1, fetches)
    }

    @Test
    fun `config folders`() {
        val windows = configDir("Windows 11", env = { if (it == "APPDATA") "C:\\Users\\me\\AppData\\Roaming" else null }, home = "C:\\Users\\me")
        assertEquals(File("C:\\Users\\me\\AppData\\Roaming", "Spicy Lyrics Desktop"), windows)
        assertEquals(File("/home/me/.config", "spicy-lyrics-desktop"), configDir("Linux", env = { null }, home = "/home/me"))
        assertEquals(File("/xdg", "spicy-lyrics-desktop"), configDir("Linux", env = { if (it == "XDG_CONFIG_HOME") "/xdg" else null }, home = "/home/me"))
    }
}
