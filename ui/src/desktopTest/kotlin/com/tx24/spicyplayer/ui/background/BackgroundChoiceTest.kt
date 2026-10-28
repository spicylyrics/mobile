package com.tx24.spicyplayer.ui.background

import com.tx24.spicyplayer.ui.settings.BackgroundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundChoiceTest {
    @Test
    fun `each type has its renderer`() {
        assertEquals(BackgroundKind.Kawarp, kindOf(BackgroundType.Default))
        assertEquals(BackgroundKind.Legacy, kindOf(BackgroundType.Legacy))
        assertEquals(BackgroundKind.Color, kindOf(BackgroundType.Color))
        for (type in listOf(BackgroundType.Auto, BackgroundType.ArtistHeader, BackgroundType.CoverArt)) {
            assertEquals(BackgroundKind.Still, kindOf(type))
        }
    }

    @Test
    fun `cover art is always the cover`() {
        assertEquals(StillPick.Cover, pickStill(BackgroundType.CoverArt, "https://x", headerPending = true, header = HeaderLoad.Loaded))
    }

    @Test
    fun `artist header waits for its lookup and download`() {
        assertEquals(StillPick.Wait, pickStill(BackgroundType.ArtistHeader, null, headerPending = true, header = HeaderLoad.NotYet))
        assertEquals(StillPick.Wait, pickStill(BackgroundType.ArtistHeader, "https://x", headerPending = false, header = HeaderLoad.NotYet))
        assertEquals(StillPick.Header, pickStill(BackgroundType.Auto, "https://x", headerPending = false, header = HeaderLoad.Loaded))
    }

    @Test
    fun `no header or a failed one shows the cover`() {
        assertEquals(StillPick.Cover, pickStill(BackgroundType.ArtistHeader, null, headerPending = false, header = HeaderLoad.NotYet))
        assertEquals(StillPick.Cover, pickStill(BackgroundType.Auto, "https://x", headerPending = false, header = HeaderLoad.Failed))
    }

    @Test
    fun `the still grade darkens and saturates`() {
        assertEquals(0xFF000000.toInt(), gradeStillPixel(0xFF000000.toInt()))
        // White: 0.55, then contrast 1.05 around half: 0.5525, grey stays grey through saturate.
        val white = gradeStillPixel(0xFFFFFFFF.toInt())
        val r = (white shr 16) and 0xFF
        assertEquals(141, r)
        assertEquals(r, (white shr 8) and 0xFF)
        assertEquals(r, white and 0xFF)
        // A red stays red, and gets redder than its grey.
        val red = gradeStillPixel(0xFFC04040.toInt())
        assertTrue(((red shr 16) and 0xFF) > ((red shr 8) and 0xFF))
        assertEquals(0xFF, red ushr 24)
    }

    @Test
    fun `the legacy loop goes out and back`() {
        val start = legacyDrift(0f)
        assertEquals(0f, start.translateX, 1e-6f)
        assertEquals(0f, start.translateY, 1e-6f)
        assertEquals(1.02f, start.scale, 1e-6f)
        val half = legacyDrift(0.5f)
        assertEquals(-0.08f, half.translateX, 1e-6f)
        assertEquals(0.06f, half.translateY, 1e-6f)
        assertEquals(1.1f, half.scale, 1e-6f)
        assertEquals(legacyDrift(0.25f).scale, legacyDrift(0.75f).scale, 1e-6f)
    }

    @Test
    fun `css blur converts to the compose radius`() {
        assertEquals((40f - 0.5f) / 0.57735f, cssBlurSigmaToRadius(40f), 1e-3f)
        assertEquals(0.1f, cssBlurSigmaToRadius(0f), 1e-6f)
    }
}
