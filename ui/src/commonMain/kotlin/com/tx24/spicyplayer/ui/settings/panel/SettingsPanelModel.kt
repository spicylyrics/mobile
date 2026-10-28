package com.tx24.spicyplayer.ui.settings.panel

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** The settings panel's tabs, in sidebar order. */
enum class PanelSectionId { Lyrics, Background, Playback, Interface, Appearance, Device, Experiments, Advanced, About }

/**
 * One tab of the panel. [secondary] tabs sit under the sidebar's divider. [intro] is a short line
 * under the pane title; [emptyText] shows when no group has a row.
 */
class PanelSection(
    val id: PanelSectionId,
    val title: String,
    val icon: ImageVector,
    val groups: List<PanelGroup> = emptyList(),
    val secondary: Boolean = false,
    val intro: String? = null,
    val emptyText: String = "Nothing here yet.",
) {
    val rows: List<PanelRow> get() = groups.flatMap { it.rows }
}

/** A captioned card of rows. */
class PanelGroup(val title: String?, val rows: List<PanelRow>)

/**
 * One row: its [label] and [description] are both what it shows and what search reads. [key] is
 * the setting it changes (null for an action). [disabled] says why the row can't be changed right
 * now, [note] why it may not take effect; both null when there's nothing to say.
 */
sealed class PanelRow(
    val id: String,
    val label: String,
    val description: String?,
    val keywords: String?,
    val key: String?,
    val disabled: () -> String?,
    val note: () -> String?,
) {
    /** The control drops onto its own full-width line under the label. */
    open val stacked: Boolean get() = false

    class Toggle(
        id: String,
        label: String,
        description: String?,
        key: String = id,
        keywords: String? = null,
        disabled: () -> String? = { null },
        note: () -> String? = { null },
        val checked: () -> Boolean,
        val onChange: (Boolean) -> Unit,
    ) : PanelRow(id, label, description, keywords, key, disabled, note)

    /** A pick from [options] (stored values), shown as [labels]. */
    class Select(
        id: String,
        label: String,
        description: String?,
        key: String = id,
        keywords: String? = null,
        disabled: () -> String? = { null },
        note: () -> String? = { null },
        val options: List<String>,
        val labels: List<String> = options,
        val value: () -> String,
        val onChange: (String) -> Unit,
    ) : PanelRow(id, label, description, keywords, key, disabled, note) {
        init {
            require(options.size == labels.size) { "$id: ${options.size} options, ${labels.size} labels" }
        }
    }

    class Slider(
        id: String,
        label: String,
        description: String?,
        key: String = id,
        keywords: String? = null,
        disabled: () -> String? = { null },
        note: () -> String? = { null },
        val spec: SliderSpec,
        val value: () -> Int,
        val onChange: (Int) -> Unit,
    ) : PanelRow(id, label, description, keywords, key, disabled, note) {
        override val stacked get() = true
    }

    /** An action, like clearing a cache; [text] is the button's. */
    class Button(
        id: String,
        label: String,
        description: String?,
        keywords: String? = null,
        val text: String,
        val onClick: () -> Unit,
    ) : PanelRow(id, label, description, keywords, null, { null }, { null })
}

/**
 * A slider's range. A range across zero is bipolar: its fill grows out of a centre tick and a
 * positive value reads with a plus sign. [default] shows a Reset once the value leaves it.
 */
data class SliderSpec(val min: Int, val max: Int, val step: Int = 1, val default: Int? = null, val unit: String? = null) {
    init {
        require(min < max && step > 0) { "bad slider range $min..$max step $step" }
    }

    val bipolar: Boolean get() = min < 0 && max > 0

    /** [value] held to the range and snapped to the nearest step from [min]. */
    fun clamp(value: Int): Int {
        val inRange = value.coerceIn(min, max)
        val snapped = min + ((inRange - min).toFloat() / step).roundToInt() * step
        return snapped.coerceIn(min, max)
    }

    /** Where [value] sits along the track, 0 at [min] to 1 at [max]. */
    fun fraction(value: Int): Float = (clamp(value) - min).toFloat() / (max - min)

    /** The value at [fraction] along the track, snapped. */
    fun valueAt(fraction: Float): Int = clamp(min + (fraction.coerceIn(0f, 1f) * (max - min)).roundToInt())

    /** Where the fill starts: the centre for a bipolar range, else the left end. */
    val zeroFraction: Float get() = if (bipolar) -min.toFloat() / (max - min) else 0f

    /** "+250 ms", "-40 ms", "12 dp". */
    fun format(value: Int): String {
        val v = clamp(value)
        val sign = if (bipolar && v > 0) "+" else ""
        return sign + v + (unit?.let { " $it" } ?: "")
    }
}

/** The query's words, lowercased. */
fun searchTerms(query: String): List<String> = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

/**
 * Every term must be in the row's own text. Section and group titles are left out: "lyrics" would
 * otherwise match every row in the Lyrics tab, and the sidebar already covers browsing by section.
 */
fun matchesRow(query: String, row: PanelRow): Boolean {
    val terms = searchTerms(query)
    if (terms.isEmpty()) return true
    val haystack = listOfNotNull(row.label, row.description, row.keywords).joinToString(" ").lowercase()
    return terms.all { it in haystack }
}

/** Every term is in the label itself; those hits are listed first. */
fun matchesLabel(query: String, row: PanelRow): Boolean {
    val label = row.label.lowercase()
    return searchTerms(query).all { it in label }
}

/** A tab's search hits: label matches first, then description-only ones, each in the tab's order. */
class SearchHit(val section: PanelSection, val rows: List<PanelRow>)

fun searchPanel(query: String, sections: List<PanelSection>): List<SearchHit> = sections.mapNotNull { section ->
    val matched = section.rows.filter { matchesRow(query, it) }
    val (byLabel, byText) = matched.partition { matchesLabel(query, it) }
    (byLabel + byText).takeIf { it.isNotEmpty() }?.let { SearchHit(section, it) }
}

/** Where each search term occurs in [text], merged and in order, for highlighting. */
fun highlightRanges(text: String, query: String): List<IntRange> {
    val terms = searchTerms(query)
    if (terms.isEmpty()) return emptyList()
    val pattern = Regex(terms.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
    val ranges = pattern.findAll(text).map { it.range }.sortedBy { it.first }.toList()
    return ranges.fold(mutableListOf()) { merged, r ->
        val last = merged.lastOrNull()
        if (last != null && r.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, r.last)
        else merged += r
        merged
    }
}

/**
 * How the panel lays out for its width: [Wide], the tab rail beside the pane; [Compact], the same
 * with a narrower rail and gutters, as on a tablet in portrait; [SinglePane], one page at a time,
 * the section list first, as on a phone or a small window.
 */
enum class PanelLayout(val railWidth: Dp, val gutter: Dp) {
    Wide(216.dp, 32.dp),
    Compact(184.dp, 22.dp),
    SinglePane(0.dp, 16.dp),
}

/** Below this, one page at a time. */
val SINGLE_PANE_BELOW = 600.dp

/** Below this, the narrower rail and gutters. */
val COMPACT_BELOW = 900.dp

fun panelLayoutFor(width: Dp): PanelLayout = when {
    width < SINGLE_PANE_BELOW -> PanelLayout.SinglePane
    width < COMPACT_BELOW -> PanelLayout.Compact
    else -> PanelLayout.Wide
}
