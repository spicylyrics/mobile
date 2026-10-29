package com.tx24.spicyplayer.lyrics.spicy.canvas

import kotlin.time.TimeSource
import kotlinx.coroutines.yield

/**
 * Lets frames through during a long layout on the UI thread: once [sliceMs] of work has run since
 * the last pause, [pace] yields, so the frame waiting behind it draws before the layout goes on.
 */
internal class LayoutPacer(private val sliceMs: Long = SLICE_MS) {
    private var since = TimeSource.Monotonic.markNow()

    suspend fun pace() {
        if (since.elapsedNow().inWholeMilliseconds < sliceMs) return
        yield()
        since = TimeSource.Monotonic.markNow()
    }

    private companion object {
        /** About a 60 Hz frame: the new layout lands in a few frames, the window still drawing between them. */
        const val SLICE_MS = 16L
    }
}
