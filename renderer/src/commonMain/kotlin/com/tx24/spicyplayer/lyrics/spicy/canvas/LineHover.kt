package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.geometry.Rect

/**
 * The line under the mouse, and the ones it has just left, each easing in or out on its own:
 * - the line itself brightens to full opacity with no blur over [LINE_S]
 *   (`cubic-bezier(.61, 1, .88, 1)`);
 * - the pill behind it fades in over [PILL_FADE_S] (`ease`) and grows from [PILL_REST_SCALE] to
 *   [PILL_HOVER_SCALE] over [PILL_SCALE_S] on a springy curve.
 * Leaving runs the same way back. Only a hovering pointer drives it, so on a touch screen it stays
 * at rest and nothing is drawn.
 */
internal class LineHover {
    /** The line the pointer is over, or null. */
    var target: Int? = null
        private set

    private class Entry(val index: Int) {
        var line = 0f
        var pillAlpha = 0f
        var pillScale = 0f
    }

    private val entries = ArrayList<Entry>(2)

    /** Whether anything is still easing, or showing at all. */
    val active: Boolean get() = entries.isNotEmpty()

    /** The pointer moved over line [index], or off every line (null). */
    fun hover(index: Int?) {
        if (index == target) return
        target = index
        if (index != null && entries.none { it.index == index }) entries.add(Entry(index))
    }

    /** Steps every line [dt] seconds toward hovered or not; true while any of them moved. */
    fun step(dt: Float): Boolean {
        var moved = false
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val goal = if (entry.index == target) 1f else 0f
            val line = approach(entry.line, goal, dt / LINE_S)
            val alpha = approach(entry.pillAlpha, goal, dt / PILL_FADE_S)
            val scale = approach(entry.pillScale, goal, dt / PILL_SCALE_S)
            if (line != entry.line || alpha != entry.pillAlpha || scale != entry.pillScale) moved = true
            entry.line = line
            entry.pillAlpha = alpha
            entry.pillScale = scale
            if (goal == 0f && line == 0f && alpha == 0f && scale == 0f) iterator.remove()
        }
        return moved
    }

    /** How hovered line [index] looks now, or null for one at rest (nearly every line). */
    fun look(index: Int): HoverLook? {
        for (i in entries.indices) {
            val entry = entries[i]
            if (entry.index != index) continue
            return HoverLook(
                line = LINE_EASING.transform(entry.line),
                pillAlpha = PILL_FADE_EASING.transform(entry.pillAlpha),
                pillScale = PILL_REST_SCALE + (PILL_HOVER_SCALE - PILL_REST_SCALE) * springyEase(entry.pillScale),
            )
        }
        return null
    }

    /** Lets go of every line at once (new lyrics). */
    fun reset() {
        target = null
        entries.clear()
    }

    companion object {
        const val LINE_S = 0.2f
        const val PILL_FADE_S = 0.25f
        const val PILL_SCALE_S = 0.4f
        const val PILL_REST_SCALE = 0.9f
        const val PILL_HOVER_SCALE = 1.05f
        private val LINE_EASING = CubicBezierEasing(0.61f, 1f, 0.88f, 1f)
        /** CSS `ease`. */
        private val PILL_FADE_EASING = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

        /** The pill's growth curve, a CSS `linear()` easing: (progress, value) stops. */
        private val SPRINGY_STOPS = floatArrayOf(
            0f, 0f, 0.01f, 0.013f, 0.022f, 0.051f, 0.098f, 0.404f, 0.126f, 0.51f, 0.155f, 0.602f,
            0.187f, 0.683f, 0.222f, 0.754f, 0.26f, 0.813f, 0.302f, 0.861f, 0.348f, 0.9f, 0.4f, 0.931f,
            0.527f, 0.972f, 0.702f, 0.992f, 1f, 1f,
        )

        internal fun springyEase(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            var i = 2
            while (i < SPRINGY_STOPS.size - 2 && SPRINGY_STOPS[i] < x) i += 2
            val x0 = SPRINGY_STOPS[i - 2]
            val y0 = SPRINGY_STOPS[i - 1]
            val x1 = SPRINGY_STOPS[i]
            val y1 = SPRINGY_STOPS[i + 1]
            return if (x1 <= x0) y1 else y0 + (y1 - y0) * (x - x0) / (x1 - x0)
        }

        private fun approach(value: Float, goal: Float, step: Float): Float =
            if (value < goal) minOf(goal, value + step) else maxOf(goal, value - step)
    }
}

/**
 * A hovered line's look: [line] how far it has brightened (0 as it is, 1 full opacity and no
 * blur), and its pill's opacity and scale.
 */
internal data class HoverLook(val line: Float, val pillAlpha: Float, val pillScale: Float)

/**
 * The pill behind a hovered line, before its scale: the line's row from [lineLeftPx] for
 * [lineWidthPx], reaching .5% of the lyrics' width further left and .85% above and below, as
 * `.line::before` does (`translate(-.5cqw, -50%)`, `width: calc(100% + .5cqw)`,
 * `height: calc(100% + 1.7cqw)`).
 */
internal fun hoverPillRect(lineLeftPx: Float, lineWidthPx: Float, lineTopPx: Float, lineHeightPx: Float, viewWidthPx: Float): Rect {
    val cqw = viewWidthPx / 100f
    return Rect(
        left = lineLeftPx - 0.5f * cqw,
        top = lineTopPx - 0.85f * cqw,
        right = lineLeftPx + lineWidthPx,
        bottom = lineTopPx + lineHeightPx + 0.85f * cqw,
    )
}

/** The pill's corner, 16px against 56px lyrics, scaled with the lyrics like the other effects. */
internal fun hoverPillRadiusPx(pxPerReferencePx: Float): Float = 16f * pxPerReferencePx

/** The pill's colour, white at this alpha. */
internal const val HOVER_PILL_ALPHA = 0.1f
