package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.settings.InMemorySettingsStore
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniLyricsPlacementTest {
    private val primary = WindowBounds(0, 0, 1920, 1040)
    private val second = WindowBounds(1920, 0, 1280, 1024)

    @Test
    fun `the first time it opens in the primary screen's bottom right corner`() {
        val bounds = MiniLyricsPlacement.load(InMemorySettingsStore(), listOf(primary, second))
        assertEquals(
            WindowBounds(
                1920 - MiniLyricsPlacement.DEFAULT_WIDTH - MiniLyricsPlacement.MARGIN,
                1040 - MiniLyricsPlacement.DEFAULT_HEIGHT - MiniLyricsPlacement.MARGIN,
                MiniLyricsPlacement.DEFAULT_WIDTH,
                MiniLyricsPlacement.DEFAULT_HEIGHT,
            ),
            bounds,
        )
    }

    @Test
    fun `it comes back where it was left`() {
        val store = InMemorySettingsStore()
        val left = WindowBounds(2100, 300, 420, 240)
        MiniLyricsPlacement.save(store, left)
        assertEquals(420, store.getInt(MiniLyricsPlacement.KEY_WIDTH))
        assertEquals(left, MiniLyricsPlacement.load(store, listOf(primary, second)))
    }

    @Test
    fun `a screen that has gone sends it back to the primary one, whole`() {
        val store = InMemorySettingsStore()
        MiniLyricsPlacement.save(store, WindowBounds(2100, 300, 420, 240))
        assertEquals(WindowBounds(1920 - 420, 300, 420, 240), MiniLyricsPlacement.load(store, listOf(primary)))
    }

    @Test
    fun `hanging over an edge, it is pulled back on screen`() {
        assertEquals(WindowBounds(1920 - 360, 0, 360, 200), MiniLyricsPlacement.clamp(WindowBounds(1800, -50, 360, 200), listOf(primary)))
    }

    @Test
    fun `its size stays between the smallest and the screen`() {
        val tiny = MiniLyricsPlacement.clamp(WindowBounds(100, 100, 10, 10), listOf(primary))
        assertEquals(MiniLyricsPlacement.MIN_WIDTH, tiny.width)
        assertEquals(MiniLyricsPlacement.MIN_HEIGHT, tiny.height)
        val huge = MiniLyricsPlacement.clamp(WindowBounds(100, 100, 5000, 5000), listOf(primary))
        assertEquals(WindowBounds(0, 0, 1920, 1040), huge)
    }

    @Test
    fun `with no screens to go by, it stays as it was`() {
        val bounds = WindowBounds(5, 5, 300, 150)
        assertEquals(bounds, MiniLyricsPlacement.clamp(bounds, emptyList()))
    }

    @Test
    fun `the lyrics shrink with a small window as on the phone`() {
        assertEquals(1f, miniLyricsFontScale(360f, 200f, 100), 0.0001f)
        assertEquals(0.5f, miniLyricsFontScale(150f, 400f, 100), 0.0001f)
        assertEquals(0.4f, miniLyricsFontScale(60f, 40f, 100), 0.0001f)
        assertEquals(1.5f, miniLyricsFontScale(400f, 400f, 150), 0.0001f)
        assertEquals(1.2f, miniLyricsFontScale(400f, 400f, 100, staticBoost = 1.2f), 0.0001f)
    }

    @Test
    fun `it opens at the popup's size and shrinks no further than its smallest`() {
        assertEquals(390, MiniLyricsPlacement.DEFAULT_WIDTH)
        assertEquals(379, MiniLyricsPlacement.DEFAULT_HEIGHT)
        assertEquals(260, MiniLyricsPlacement.MIN_WIDTH)
        assertEquals(180, MiniLyricsPlacement.MIN_HEIGHT)
        // A size saved before the smallest grew comes back at the new smallest.
        val store = InMemorySettingsStore()
        MiniLyricsPlacement.save(store, WindowBounds(100, 100, 220, 110))
        assertEquals(WindowBounds(100, 100, 260, 180), MiniLyricsPlacement.load(store, listOf(primary)))
    }

    @Test
    fun `shuffle and repeat need width, the times need height`() {
        assertEquals(MiniChrome(modes = true, times = true), miniChrome(390f, 379f))
        assertEquals(MiniChrome(modes = true, times = true), miniChrome(280f, 230f))
        assertEquals(MiniChrome(modes = false, times = true), miniChrome(279f, 379f))
        assertEquals(MiniChrome(modes = true, times = false), miniChrome(390f, 229f))
        assertEquals(MiniChrome(modes = false, times = false), miniChrome(260f, 180f))
    }

    @Test
    fun `times read as minutes and seconds`() {
        assertEquals("0:00", miniTime(0))
        assertEquals("0:00", miniTime(-500))
        assertEquals("1:05", miniTime(65_999))
        assertEquals("12:34", miniTime((12 * 60 + 34) * 1000L))
        assertEquals("1:02:03", miniTime((3600 + 2 * 60 + 3) * 1000L))
    }
}
