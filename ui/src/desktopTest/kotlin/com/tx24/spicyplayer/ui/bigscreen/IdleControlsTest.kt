package com.tx24.spicyplayer.ui.bigscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleControlsTest {
    @Test
    fun `controls fade after the set delay while playing`() {
        assertEquals(3_000L, idleAfterMs(IdleSettings(), playing = true))
        assertEquals(10_000L, idleAfterMs(IdleSettings(delayMs = 10_000L), playing = true))
    }

    @Test
    fun `with hiding off they never fade`() {
        assertNull(idleAfterMs(IdleSettings(autoHide = false), playing = true))
        assertNull(idleAfterMs(IdleSettings(autoHide = false, hideWhilePaused = true), playing = false))
    }

    @Test
    fun `paused, they fade only with hide while paused`() {
        assertNull(idleAfterMs(IdleSettings(), playing = false))
        assertEquals(3_000L, idleAfterMs(IdleSettings(hideWhilePaused = true), playing = false))
    }

    @Test
    fun `the pointer on the page brings them up faintly until it rests`() {
        assertEquals(ControlsLevel.Page, controlsLevel(onPage = true, onCover = false, revealed = false, idle = false, held = false))
        assertEquals(ControlsLevel.Hidden, controlsLevel(onPage = true, onCover = false, revealed = false, idle = true, held = false))
        assertEquals(ControlsLevel.Hidden, controlsLevel(onPage = false, onCover = false, revealed = false, idle = false, held = false))
    }

    @Test
    fun `on the cover they stay fully up however long the pointer rests`() {
        assertEquals(ControlsLevel.Cover, controlsLevel(onPage = true, onCover = true, revealed = false, idle = false, held = false))
        assertEquals(ControlsLevel.Cover, controlsLevel(onPage = true, onCover = true, revealed = false, idle = true, held = false))
    }

    @Test
    fun `a drag or an open menu holds them where they were, wherever the pointer goes`() {
        assertEquals(
            ControlsLevel.Cover,
            controlsLevel(onPage = false, onCover = false, revealed = false, idle = true, held = true, previous = ControlsLevel.Cover),
        )
        assertEquals(
            ControlsLevel.Page,
            controlsLevel(onPage = false, onCover = false, revealed = false, idle = true, held = true, previous = ControlsLevel.Hidden),
        )
    }

    @Test
    fun `a tap reveals hidden controls and puts shown ones away`() {
        assertTrue(revealedAfterTap(ControlsLevel.Hidden))
        assertFalse(revealedAfterTap(ControlsLevel.Page))
        assertFalse(revealedAfterTap(ControlsLevel.Cover))
        // Revealed by touch, they fade with the idle timer like a resting pointer.
        assertEquals(ControlsLevel.Page, controlsLevel(onPage = false, onCover = false, revealed = true, idle = false, held = false))
        assertEquals(ControlsLevel.Hidden, controlsLevel(onPage = false, onCover = false, revealed = true, idle = true, held = false))
    }

    @Test
    fun `levels dim the cover more the more the controls show`() {
        assertEquals(0f, ControlsLevel.Hidden.controlsAlpha)
        assertEquals(1f, ControlsLevel.Hidden.artworkBrightness)
        assertEquals(0.65f, ControlsLevel.Page.controlsAlpha)
        assertEquals(0.78f, ControlsLevel.Page.artworkBrightness)
        assertEquals(0.985f, ControlsLevel.Cover.controlsAlpha)
        assertEquals(0.55f, ControlsLevel.Cover.artworkBrightness)
    }

    @Test
    fun `the page's view controls go 0, 0_7, 1`() {
        assertEquals(0f, viewControlsAlpha(onPage = false, onRow = false, revealed = false, held = false))
        assertEquals(0.7f, viewControlsAlpha(onPage = true, onRow = false, revealed = false, held = false))
        assertEquals(1f, viewControlsAlpha(onPage = true, onRow = true, revealed = false, held = false))
        assertEquals(1f, viewControlsAlpha(onPage = false, onRow = false, revealed = true, held = false))
        // The More menu open: the pointer is on the menu, not the row, and the row stays.
        assertEquals(1f, viewControlsAlpha(onPage = false, onRow = false, revealed = false, held = true))
    }
}
