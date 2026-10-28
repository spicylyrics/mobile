package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.settings.SettingsStore

/** A window's place and size, in the screen's logical pixels (Compose's dp on the desktop). */
data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int) {
    val centreX get() = x + width / 2
    val centreY get() = y + height / 2

    operator fun contains(other: WindowBounds) = other.centreX in x until x + width && other.centreY in y until y + height
}

/**
 * Where the mini lyrics window opens: where it was left, saved in the app's settings store, kept
 * whole on a screen that still exists; the first time, the primary screen's bottom right corner.
 */
object MiniLyricsPlacement {
    const val MIN_WIDTH = 260
    const val MIN_HEIGHT = 180
    const val DEFAULT_WIDTH = 390
    const val DEFAULT_HEIGHT = 379
    /** The first place's distance from the screen's edges. */
    const val MARGIN = 24

    const val KEY_X = "miniLyricsX"
    const val KEY_Y = "miniLyricsY"
    const val KEY_WIDTH = "miniLyricsWidth"
    const val KEY_HEIGHT = "miniLyricsHeight"

    /** The saved place on [screens] (usable areas, the primary first), or the first place. */
    fun load(store: SettingsStore, screens: List<WindowBounds>): WindowBounds {
        val primary = screens.firstOrNull() ?: WindowBounds(0, 0, 1280, 720)
        val x = store.getInt(KEY_X)
        val y = store.getInt(KEY_Y)
        val width = store.getInt(KEY_WIDTH) ?: DEFAULT_WIDTH
        val height = store.getInt(KEY_HEIGHT) ?: DEFAULT_HEIGHT
        val wanted = if (x != null && y != null) {
            WindowBounds(x, y, width, height)
        } else {
            WindowBounds(primary.x + primary.width - width - MARGIN, primary.y + primary.height - height - MARGIN, width, height)
        }
        return clamp(wanted, screens)
    }

    fun save(store: SettingsStore, bounds: WindowBounds) {
        store.putInt(KEY_X, bounds.x)
        store.putInt(KEY_Y, bounds.y)
        store.putInt(KEY_WIDTH, bounds.width)
        store.putInt(KEY_HEIGHT, bounds.height)
    }

    /**
     * [bounds] at least the smallest size and no bigger than its screen, moved so all of it shows
     * on the screen its centre is on, or on the primary screen when that one has gone.
     */
    fun clamp(bounds: WindowBounds, screens: List<WindowBounds>): WindowBounds {
        val screen = screens.firstOrNull { bounds in it } ?: screens.firstOrNull() ?: return bounds
        val width = bounds.width.coerceIn(MIN_WIDTH, maxOf(MIN_WIDTH, screen.width))
        val height = bounds.height.coerceIn(MIN_HEIGHT, maxOf(MIN_HEIGHT, screen.height))
        val x = bounds.x.coerceIn(screen.x, maxOf(screen.x, screen.x + screen.width - width))
        val y = bounds.y.coerceIn(screen.y, maxOf(screen.y, screen.y + screen.height - height))
        return WindowBounds(x, y, width, height)
    }
}

/**
 * The lyrics' size in a [widthDp] by [heightDp] mini window, as in the phone's floating window:
 * full size at 300x190 or more, shrinking with a smaller window down to 40%, times the floating
 * window's Lyrics Size ([sizePercent]) and [staticBoost] (unsynced lyrics drawn at the synced size).
 */
fun miniLyricsFontScale(widthDp: Float, heightDp: Float, sizePercent: Int, staticBoost: Float = 1f): Float =
    minOf(widthDp / 300f, heightDp / 190f).coerceIn(0.4f, 1f) * (sizePercent / 100f) * staticBoost
