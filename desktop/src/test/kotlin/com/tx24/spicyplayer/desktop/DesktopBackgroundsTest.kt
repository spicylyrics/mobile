package com.tx24.spicyplayer.desktop

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopBackgroundsTest {
    @Test
    fun `cover crop fills a wide box from the middle of a square`() {
        assertArrayEquals(intArrayOf(0, 160, 640, 320), coverCrop(640, 640, 200f, 100f))
    }

    @Test
    fun `cover crop fills a tall box from the middle of a wide picture`() {
        assertArrayEquals(intArrayOf(300, 0, 200, 400), coverCrop(800, 400, 50f, 100f))
    }

    @Test
    fun `cache names are stable and file safe`() {
        val name = imageCacheName("https://i.scdn.co/image/abc?x=1&y=/2")
        assertEquals(name, imageCacheName("https://i.scdn.co/image/abc?x=1&y=/2"))
        assertTrue(Regex("[0-9a-f]{40}\\.img").matches(name))
        assertFalse(name == imageCacheName("https://i.scdn.co/image/abd"))
    }

    @Test
    fun `cached copies stay good for a month`() {
        val now = 100L * IMAGE_TTL_MS
        assertTrue(isFresh(now - 1, now))
        assertFalse(isFresh(now - IMAGE_TTL_MS, now))
        assertFalse(isFresh(now + 1, now))
    }

    @Test
    fun `a fresh copy is read without the network`() {
        val dir = Files.createTempDirectory("bg").toFile()
        val url = "http://127.0.0.1:1/fresh"
        File(dir, imageCacheName(url)).writeBytes(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), DesktopImageCache(dir).bytes(url))
    }

    @Test
    fun `offline, a stale copy beats nothing`() {
        val dir = Files.createTempDirectory("bg").toFile()
        val url = "http://127.0.0.1:1/stale"
        File(dir, imageCacheName(url)).apply { writeBytes(byteArrayOf(4, 5)); setLastModified(0L) }
        assertArrayEquals(byteArrayOf(4, 5), DesktopImageCache(dir).bytes(url))
    }

    @Test
    fun `offline with nothing cached gives null`() {
        val dir = Files.createTempDirectory("bg").toFile()
        assertNull(DesktopImageCache(dir).bytes("http://127.0.0.1:1/none"))
    }

    @Test
    fun `under both caps nothing is pruned`() {
        val entries = listOf(CacheEntry("a", 10, 1), CacheEntry("b", 10, 2))
        assertEquals(emptyList<String>(), filesToPrune(entries, maxBytes = 20, maxFiles = 2))
    }

    @Test
    fun `over the size cap the least recently used go first`() {
        val entries = listOf(CacheEntry("new", 40, 300), CacheEntry("old", 40, 100), CacheEntry("mid", 40, 200))
        assertEquals(listOf("old"), filesToPrune(entries, maxBytes = 80, maxFiles = 10))
        assertEquals(listOf("old", "mid"), filesToPrune(entries, maxBytes = 50, maxFiles = 10))
    }

    @Test
    fun `over the file cap the oldest go until it holds`() {
        val entries = (1..5).map { CacheEntry("f$it", 1, it.toLong()) }
        assertEquals(listOf("f1", "f2"), filesToPrune(entries, maxBytes = 1_000, maxFiles = 3))
    }

    @Test
    fun `a fresh hit counts as use`() {
        val dir = Files.createTempDirectory("bg").toFile()
        val url = "http://127.0.0.1:1/used"
        val file = File(dir, imageCacheName(url)).apply { writeBytes(byteArrayOf(7)); setLastModified(1_000_000L) }
        DesktopImageCache(dir).bytes(url, now = 2_000_000L)
        assertEquals(2_000_000L, file.lastModified())
    }

    @Test
    fun `an offline stale copy stays stale`() {
        val dir = Files.createTempDirectory("bg").toFile()
        val url = "http://127.0.0.1:1/old"
        val file = File(dir, imageCacheName(url)).apply { writeBytes(byteArrayOf(8)); setLastModified(0L) }
        DesktopImageCache(dir).bytes(url)
        assertEquals(0L, file.lastModified())
    }

    @Test
    fun `a cache write replaces the old copy and leaves no temporary file`() {
        val dir = Files.createTempDirectory("bg").toFile()
        val file = File(dir, "a.img").apply { writeBytes(byteArrayOf(1, 1, 1, 1)) }
        writeAtomically(file, byteArrayOf(9, 9))
        assertArrayEquals(byteArrayOf(9, 9), file.readBytes())
        assertEquals(listOf("a.img"), dir.list()!!.toList())
    }
}
