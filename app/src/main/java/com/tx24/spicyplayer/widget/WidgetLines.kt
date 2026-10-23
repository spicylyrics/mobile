package com.tx24.spicyplayer.widget

import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole

/**
 * What the home screen widget shows at a moment: the line being sung and the one after it, and
 * when that next changes ([changesAtMs], a lyric position; null when it won't).
 *
 * Plain Kotlin, no Android.
 */
internal data class WidgetLines(val current: String, val next: String, val changesAtMs: Long?) {
    /** One lead line as the widget writes it. */
    data class Lead(val startMs: Long, val endMs: Long, val text: String)

    companion object {
        /** Between two lines at least this far apart the widget shows [BREAK] instead of the line just sung. */
        const val BREAK_GAP_MS = 4_000L
        const val BREAK = "♪"

        /**
         * The lead lines of [lines] as text: background vocals and interludes left out, and the
         * words joined as they're spaced (each word carries its own trailing space).
         */
        fun leads(lines: List<TimedLine>): List<Lead> = lines
            .filter { it.role == LineRole.LEAD }
            .map { line -> Lead(line.startMs, line.endMs, line.words.joinToString("") { it.text }.replace(WHITESPACE, " ").trim()) }
            .filter { it.text.isNotEmpty() }

        /** The first two lines and no change: lyrics without timing. */
        fun unsynced(leads: List<Lead>): WidgetLines =
            WidgetLines(leads.getOrNull(0)?.text.orEmpty(), leads.getOrNull(1)?.text.orEmpty(), null)

        /** What shows at [positionMs] of synced [leads] (in time order). */
        fun at(leads: List<Lead>, positionMs: Long): WidgetLines {
            if (leads.isEmpty()) return WidgetLines("", "", null)
            val index = leads.indexOfLast { it.startMs <= positionMs }
            if (index < 0) return WidgetLines(BREAK, leads[0].text, leads[0].startMs)
            val line = leads[index]
            val following = leads.getOrNull(index + 1)
                ?: return WidgetLines(line.text, "", null)
            val breakAhead = following.startMs - line.endMs >= BREAK_GAP_MS
            return when {
                breakAhead && positionMs >= line.endMs -> WidgetLines(BREAK, following.text, following.startMs)
                breakAhead -> WidgetLines(line.text, following.text, line.endMs)
                else -> WidgetLines(line.text, following.text, following.startMs)
            }
        }

        private val WHITESPACE = Regex("\\s+")
    }
}
