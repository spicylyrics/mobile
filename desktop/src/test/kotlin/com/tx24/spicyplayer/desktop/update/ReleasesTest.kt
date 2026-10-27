package com.tx24.spicyplayer.desktop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    private fun release(tag: String, draft: Boolean = false, prerelease: Boolean = true) =
        """{"tag_name":"$tag","html_url":"https://github.com/spicylyrics/mobile/releases/tag/$tag","published_at":"2026-10-27T12:00:00Z","draft":$draft,"prerelease":$prerelease}"""

    private fun listing(vararg releases: String) = releases.joinToString(",", "[", "]")

    @Test
    fun `versions compare as numbers`() {
        assertTrue(AppVersion.parse("0.1.10")!! > AppVersion.parse("0.1.9")!!)
        assertTrue(AppVersion.parse("1.0.0")!! > AppVersion.parse("0.99.99")!!)
        assertEquals(AppVersion(0, 2, 0), AppVersion.parse(" 0.2.0 "))
        assertNull(AppVersion.parse("dev"))
        assertNull(AppVersion.parse("1.2"))
        assertNull(AppVersion.parse("1.2.3-beta"))
    }

    @Test
    fun `only desktop tags count`() {
        assertEquals(AppVersion(0, 1, 10), AppVersion.fromTag("desktop-v0.1.10"))
        assertNull(AppVersion.fromTag("v0.16.0"))
        assertNull(AppVersion.fromTag("desktop-0.1.0"))
        assertNull(AppVersion.fromTag("desktop-v0.1"))
        assertNull(AppVersion.fromTag("desktop-v0.1.0-rc1"))
    }

    @Test
    fun `picks the highest desktop release`() {
        val json = listing(
            release("v0.16.0"),
            release("desktop-v0.1.9"),
            release("desktop-v0.1.10"),
            release("desktop-v0.2.0", draft = true),
            release("desktop-v0.1.x"),
            release("v1.0.0", prerelease = false),
        )
        val latest = latestDesktopRelease(json)!!
        assertEquals("0.1.10", latest.version)
        assertEquals("https://github.com/spicylyrics/mobile/releases/tag/desktop-v0.1.10", latest.url)
        assertEquals("2026-10-27T12:00:00Z", latest.published)
    }

    @Test
    fun `prereleases stop counting from 1_0_0`() {
        val json = listing(release("desktop-v0.9.0"), release("desktop-v1.1.0", prerelease = true), release("desktop-v1.0.0", prerelease = false))
        assertEquals("1.0.0", latestDesktopRelease(json)?.version)
    }

    @Test
    fun `unreadable listings give nothing`() {
        assertNull(latestDesktopRelease(""))
        assertNull(latestDesktopRelease("""{"message":"API rate limit exceeded"}"""))
        assertNull(latestDesktopRelease("[1, \"x\", null]"))
        assertNull(latestDesktopRelease(listing(release("v0.16.0"))))
        assertNull(latestDesktopRelease("<html>"))
    }

    @Test
    fun `an update only for an older numbered build`() {
        val latest = UpdateInfo("0.1.10", "https://example.invalid", "")
        assertEquals(latest, updateFor("0.1.9", latest))
        assertNull(updateFor("0.1.10", latest))
        assertNull(updateFor("0.2.0", latest))
        assertNull(updateFor("dev", latest))
        assertNull(updateFor("0.1.0", null))
    }
}
