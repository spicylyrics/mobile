package com.tx24.spicyplayer.lyrics.spicy.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineHoverTest {
    private val hover = LineHover()

    private fun run(seconds: Float) {
        repeat((seconds * 60).toInt()) { hover.step(1f / 60f) }
    }

    @Test fun nothingIsHoveredUntilThePointerComes() {
        assertFalse(hover.active)
        assertNull(hover.look(0))
        assertFalse(hover.step(1f / 60f))
    }

    @Test fun aHoveredLineBrightensAndItsPillFadesInAndGrows() {
        hover.hover(3)
        assertTrue(hover.step(0.1f))
        val partway = hover.look(3)!!
        assertTrue(partway.pillAlpha in 0.01f..0.99f)
        assertTrue(partway.pillScale > LineHover.PILL_REST_SCALE && partway.pillScale < LineHover.PILL_HOVER_SCALE)
        run(0.5f)
        val there = hover.look(3)!!
        assertEquals(1f, there.line, 0.0001f)
        assertEquals(1f, there.pillAlpha, 0.0001f)
        assertEquals(LineHover.PILL_HOVER_SCALE, there.pillScale, 0.0001f)
        // Settled, it stops asking for frames.
        assertFalse(hover.step(1f / 60f))
        assertNull(hover.look(2))
    }

    @Test fun theFadeTakesAQuarterSecondAndTheGrowthTwoFifths() {
        hover.hover(0)
        hover.step(LineHover.PILL_FADE_S)
        assertEquals(1f, hover.look(0)!!.pillAlpha, 0.0001f)
        assertTrue(hover.look(0)!!.pillScale < LineHover.PILL_HOVER_SCALE)
        hover.step(LineHover.PILL_SCALE_S - LineHover.PILL_FADE_S)
        assertEquals(LineHover.PILL_HOVER_SCALE, hover.look(0)!!.pillScale, 0.0001f)
    }

    @Test fun movingToAnotherLineFadesTheFirstOutAsTheNextComesIn() {
        hover.hover(1)
        run(0.5f)
        hover.hover(2)
        hover.step(0.1f)
        val leaving = hover.look(1)!!
        val coming = hover.look(2)!!
        assertTrue(leaving.pillAlpha < 1f && leaving.pillAlpha > 0f)
        assertTrue(coming.pillAlpha > 0f && coming.pillAlpha < 1f)
        run(0.5f)
        assertNull(hover.look(1))
        assertEquals(1f, hover.look(2)!!.pillAlpha, 0.0001f)
    }

    @Test fun leavingEveryLineLetsThemAllGo() {
        hover.hover(4)
        run(0.5f)
        hover.hover(null)
        run(0.5f)
        assertNull(hover.look(4))
        assertFalse(hover.active)
    }

    @Test fun thePillGrowsOnItsSpringyCurve() {
        assertEquals(0f, LineHover.springyEase(0f), 0.0001f)
        assertEquals(0.404f, LineHover.springyEase(0.098f), 0.0001f)
        assertEquals(0.931f, LineHover.springyEase(0.4f), 0.0001f)
        assertEquals(1f, LineHover.springyEase(1f), 0.0001f)
        // Between stops, a straight line.
        assertEquals((0.972f + 0.992f) / 2f, LineHover.springyEase((0.527f + 0.702f) / 2f), 0.0001f)
    }

    @Test fun thePillCoversTheLineRowWithItsMargins() {
        val rect = hoverPillRect(lineLeftPx = 50f, lineWidthPx = 900f, lineTopPx = 200f, lineHeightPx = 60f, viewWidthPx = 1000f)
        assertEquals(45f, rect.left, 0.001f)
        assertEquals(950f, rect.right, 0.001f)
        assertEquals(191.5f, rect.top, 0.001f)
        assertEquals(268.5f, rect.bottom, 0.001f)
        // 16px against 56px lyrics, scaling with them.
        assertEquals(16f, hoverPillRadiusPx(1f), 0.001f)
        assertEquals(8f, hoverPillRadiusPx(0.5f), 0.001f)
    }
}
