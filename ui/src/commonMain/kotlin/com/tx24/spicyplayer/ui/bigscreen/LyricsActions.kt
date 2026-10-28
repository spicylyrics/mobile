package com.tx24.spicyplayer.ui.bigscreen

import androidx.compose.runtime.staticCompositionLocalOf
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.translation.TranslationMode
import kotlinx.coroutines.flow.StateFlow

/** One place the song's lyrics can come from, as the More menu lists it. */
data class LyricsSourceChoice(
    val id: String,
    val name: String,
    /** What it had for this song: "Word-synced", "No lyrics for this song", "Not asked yet". */
    val status: String,
    /** It has lyrics for the song, or hasn't been asked yet; false greys it out. */
    val available: Boolean,
    /** Its lyrics are the ones on screen. */
    val shown: Boolean,
)

/** A language a translation can go into. */
data class LanguageChoice(val code: String, val name: String)

/**
 * A question to answer before lyrics go somewhere new, such as a translation service: [id] is the
 * consent asked for (`TranslationConsent.id`), [title] and [description] the disclosure's words.
 */
data class ConsentRequest(val id: String, val title: String, val description: String)

/** What the More menu shows: the song's sources, and how its lyrics are translated and romanized. */
data class LyricsOptions(
    /** Every source asked for lyrics, in rank order. Empty while nothing is playing. */
    val sources: List<LyricsSourceChoice> = emptyList(),
    /** The source picked for this song, or null for the automatic pick. */
    val pickedSource: String? = null,
    /** Songs are translated into [targetLanguage]. */
    val translating: Boolean = false,
    val targetLanguage: String = "en",
    val languages: List<LanguageChoice> = emptyList(),
    val translationMode: TranslationMode = TranslationMode.UnderLine,
    val romanizationMode: RomanizationMode = RomanizationMode.Replace,
    /** How the last choice went, when it needs saying ("Translating…", a source with nothing). */
    val message: String? = null,
    /** Waiting on the user's OK before lyrics are sent ([LyricsActions.answerConsent]). */
    val pendingConsent: ConsentRequest? = null,
)

/**
 * The lyrics choices behind the More menu: which source the song's lyrics come from, translation,
 * and how romanization shows. The desktop drives it from its lookup; phones and tablets can adapt
 * their view model to it.
 */
interface LyricsActions {
    val options: StateFlow<LyricsOptions>

    /** Shows [id]'s lyrics for this song from now on, or the automatic pick again for null. */
    fun useSource(id: String?)

    fun setTranslating(on: Boolean)
    fun setTargetLanguage(code: String)
    fun setTranslationMode(mode: TranslationMode)
    fun setRomanizationMode(mode: RomanizationMode)

    /** The answer to [LyricsOptions.pendingConsent] [id]: allowed, the waiting choice goes ahead and is remembered. */
    fun answerConsent(id: String, allowed: Boolean)
}

/** The page's lyrics choices; none leaves the More menu's entries unavailable. */
val LocalLyricsActions = staticCompositionLocalOf<LyricsActions?> { null }

/** A row of the More menu's source list. */
data class SourceRow(val id: String?, val title: String, val detail: String, val selected: Boolean, val enabled: Boolean)

/** The source list: the automatic pick first, then each source with what it had. */
fun sourceRows(options: LyricsOptions): List<SourceRow> {
    val shown = options.sources.firstOrNull { it.shown }
    val automatic = SourceRow(
        id = null,
        title = "Automatic",
        detail = if (options.pickedSource == null && shown != null) "Showing ${shown.name}" else "The best match",
        selected = options.pickedSource == null,
        enabled = true,
    )
    return listOf(automatic) + options.sources.map { source ->
        SourceRow(
            id = source.id,
            title = source.name,
            detail = if (source.shown && options.pickedSource == null) "${source.status} · showing now" else source.status,
            selected = options.pickedSource == source.id,
            enabled = source.available || options.pickedSource == source.id,
        )
    }
}

/** The More menu's one-line summary of each entry. */
fun sourceSummary(options: LyricsOptions): String =
    options.pickedSource?.let { id -> options.sources.firstOrNull { it.id == id }?.name ?: id }
        ?: options.sources.firstOrNull { it.shown }?.let { "Automatic · ${it.name}" }
        ?: "Automatic"

fun translationSummary(options: LyricsOptions): String {
    if (!options.translating) return "Off"
    val language = options.languages.firstOrNull { it.code == options.targetLanguage }?.name ?: options.targetLanguage
    return "$language · ${options.translationMode.label}"
}

fun romanizationSummary(options: LyricsOptions): String = options.romanizationMode.label
