package com.tx24.spicyplayer.desktop

import androidx.compose.runtime.snapshotFlow
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.ProviderResult
import com.tx24.spicyplayer.network.data.RemoteLyricsQuality
import com.tx24.spicyplayer.network.data.RemoteLyricsResolution
import com.tx24.spicyplayer.network.data.measuredQuality
import com.tx24.spicyplayer.network.data.spotify.SpotifyLinks
import com.tx24.spicyplayer.translation.DiskTranslationCache
import com.tx24.spicyplayer.translation.GoogleTranslator
import com.tx24.spicyplayer.translation.SongLanguageDetector
import com.tx24.spicyplayer.translation.TranslationDocument
import com.tx24.spicyplayer.translation.TranslationEngine
import com.tx24.spicyplayer.translation.TranslationLanguages
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.translation.TranslationOutcome
import com.tx24.spicyplayer.translation.TranslationPreferences
import com.tx24.spicyplayer.translation.TranslationProvider
import com.tx24.spicyplayer.translation.TranslationResult
import com.tx24.spicyplayer.translation.languageCode
import com.tx24.spicyplayer.ui.bigscreen.LanguageChoice
import com.tx24.spicyplayer.ui.bigscreen.LyricsActions
import com.tx24.spicyplayer.ui.bigscreen.LyricsOptions
import com.tx24.spicyplayer.ui.bigscreen.LyricsSourceChoice
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.FileSettingsStore
import com.tx24.spicyplayer.ui.settings.SettingsStore
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** The song lookup the lyrics choices run on: [LyricsLookup], or a fake in tests. */
interface SongLookup {
    /** The sources asked, in rank order. */
    val sources: List<LyricsSourceDescriptor>

    /**
     * [track]'s lyrics, each source's answer kept in [known]; with [sourceId], from that source
     * alone; with [spotifyId], matched to that Spotify track.
     */
    suspend fun find(track: Track, known: MutableMap<String, ProviderResult>, sourceId: String?, spotifyId: String? = null): LyricsLookup.Found
}

/** Translates a song's lyrics into a language. */
fun interface SongTranslator {
    suspend fun translate(track: Track, lyrics: LyricsState.Ready, target: String): TranslationOutcome
}

/**
 * The playing song's lyrics and the More menu's choices for them: the source and the Spotify
 * track it is matched to (each kept per song in [store]), translation (on or off and the language, in [store]) and the display modes (in
 * [settings], the phone's keys).
 */
class DesktopLyrics(
    private val lookup: SongLookup,
    private val translator: SongTranslator,
    private val settings: CommonAppSettings,
    private val store: SettingsStore,
    private val scope: CoroutineScope,
) : LyricsActions {
    /** What the page shows: the song, its lyrics or notice, and their translation once it lands. */
    data class Shown(val track: Track, val lyrics: LyricsState, val translation: TranslationResult? = null)

    private val shownState = MutableStateFlow<Shown?>(null)
    val shown: StateFlow<Shown?> = shownState.asStateFlow()

    private val optionsState = MutableStateFlow(
        LyricsOptions(
            translating = store.getBoolean(KEY_TRANSLATE) ?: false,
            targetLanguage = store.getString(KEY_TARGET)?.let(::languageCode) ?: defaultTarget(),
            languages = LANGUAGES,
            translationMode = settings.translationMode,
            romanizationMode = settings.romanizationMode,
        ),
    )
    override val options: StateFlow<LyricsOptions> = optionsState.asStateFlow()

    private var track: Track? = null
    /** Each source's answer for [track], so switching sources asks none twice. */
    private var known: MutableMap<String, ProviderResult> = ConcurrentHashMap()
    private var shownSource: String? = null
    private var lookupJob: Job? = null
    private var translationJob: Job? = null

    /** Looks [track]'s lyrics up afresh, from the source picked for it if there is one. */
    fun load(track: Track) {
        this.track = track
        known = ConcurrentHashMap()
        shownSource = null
        optionsState.update {
            it.copy(pickedSource = store.getString(sourceKey(track)), spotifyTrackId = store.getString(spotifyKey(track)), message = null)
        }
        lookUp(track)
    }

    /** Nothing is playing. */
    fun clear() {
        lookupJob?.cancel()
        translationJob?.cancel()
        track = null
        shownSource = null
        shownState.value = null
        optionsState.update { it.copy(sources = emptyList(), pickedSource = null, spotifyTrackId = null, message = null) }
    }

    override fun setSpotifyLink(input: String): Boolean {
        val track = track ?: return false
        val id = SpotifyLinks.trackId(input)
        if (id == null) {
            optionsState.update { it.copy(message = "Enter a valid Spotify track ID or URL") }
            return false
        }
        store.putString(spotifyKey(track), id)
        rematch(track, id)
        return true
    }

    override fun clearSpotifyLink() {
        val track = track ?: return
        store.remove(spotifyKey(track))
        rematch(track, null)
    }

    /** The answers so far were for the old match, so every source is asked again. */
    private fun rematch(track: Track, spotifyId: String?) {
        known = ConcurrentHashMap()
        shownSource = null
        optionsState.update { it.copy(spotifyTrackId = spotifyId, message = null) }
        lookUp(track)
    }

    override fun useSource(id: String?) {
        val track = track ?: return
        if (id == null) store.remove(sourceKey(track)) else store.putString(sourceKey(track), id)
        optionsState.update { it.copy(pickedSource = id, message = null) }
        lookUp(track)
    }

    override fun setTranslating(on: Boolean) {
        store.putBoolean(KEY_TRANSLATE, on)
        optionsState.update { it.copy(translating = on, message = null) }
        if (on) translateShown() else {
            translationJob?.cancel()
            shownState.update { it?.copy(translation = null) }
        }
    }

    override fun setTargetLanguage(code: String) {
        val language = languageCode(code) ?: return
        store.putString(KEY_TARGET, language)
        optionsState.update { it.copy(targetLanguage = language, message = null) }
        if (optionsState.value.translating) translateShown()
    }

    override fun setTranslationMode(mode: TranslationMode) {
        settings.translationMode = mode
        optionsState.update { it.copy(translationMode = mode) }
    }

    override fun setRomanizationMode(mode: RomanizationMode) {
        settings.romanizationMode = mode
        optionsState.update { it.copy(romanizationMode = mode) }
    }

    /**
     * Keeps the menu's display modes in step with [settings] when something else changes them
     * (the settings window). Runs as long as [scope].
     */
    fun followSettings() {
        scope.launch {
            snapshotFlow { settings.translationMode to settings.romanizationMode }.collect { (translation, romanization) ->
                optionsState.update { it.copy(translationMode = translation, romanizationMode = romanization) }
            }
        }
    }

    /** Waits for the lookup and translation under way; for tests. */
    internal suspend fun settle() {
        lookupJob?.join()
        translationJob?.join()
    }

    private fun lookUp(track: Track) {
        lookupJob?.cancel()
        translationJob?.cancel()
        val known = known
        val spotifyId = optionsState.value.spotifyTrackId
        shownState.value = Shown(track, LyricsState.Loading)
        refreshSources()
        lookupJob = scope.launch {
            var message: String? = null
            var found: LyricsLookup.Found? = null
            optionsState.value.pickedSource?.let { picked ->
                val fromPicked = lookup.find(track, known, picked, spotifyId)
                if ((fromPicked.resolution as? RemoteLyricsResolution.Found)?.selection?.source?.id == picked) {
                    found = fromPicked
                } else {
                    val name = lookup.sources.firstOrNull { it.id == picked }?.displayName ?: picked
                    message = "$name has no lyrics for this song, so the usual pick is shown."
                }
            }
            val result = found ?: lookup.find(track, known, null, spotifyId)
            if (this@DesktopLyrics.track?.key != track.key) return@launch
            shownSource = (result.resolution as? RemoteLyricsResolution.Found)?.selection?.source?.id
            shownState.value = Shown(track, result.lyrics)
            optionsState.update { it.copy(message = message) }
            refreshSources()
            if (optionsState.value.translating) translateShown()
        }
    }

    private fun refreshSources() {
        optionsState.update { it.copy(sources = sourceChoices(lookup.sources, known, shownSource)) }
    }

    private fun translateShown() {
        val shown = shownState.value ?: return
        val lyrics = shown.lyrics as? LyricsState.Ready ?: return
        val target = optionsState.value.targetLanguage
        translationJob?.cancel()
        optionsState.update { it.copy(message = "Translating…") }
        translationJob = scope.launch {
            val outcome = try {
                translator.translate(shown.track, lyrics, target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                optionsState.update { it.copy(message = "Couldn't translate: ${e.message ?: "no answer"}") }
                return@launch
            }
            // Only onto the lyrics it was made for, still showing.
            if (shownState.value?.lyrics !== lyrics) return@launch
            when (outcome) {
                is TranslationOutcome.Translated -> {
                    shownState.update { it?.copy(translation = outcome.result) }
                    optionsState.update { it.copy(message = null) }
                }
                is TranslationOutcome.Skipped -> {
                    shownState.update { it?.copy(translation = null) }
                    val language = outcome.language?.let(TranslationLanguages::name)
                    optionsState.update { it.copy(message = if (language != null) "Already in $language." else "Nothing to translate.") }
                }
            }
        }
    }

    companion object {
        const val KEY_TRANSLATE = "translate"
        const val KEY_TARGET = "targetLanguage"
        private const val SOURCE_PREFIX = "source:"
        private const val SPOTIFY_PREFIX = "spotify:"

        /** Where the source picked for [track] is kept. */
        fun sourceKey(track: Track): String = SOURCE_PREFIX + track.key

        /** Where the Spotify track [track] is matched to by hand is kept. */
        fun spotifyKey(track: Track): String = SPOTIFY_PREFIX + track.key

        /** Every language Google can translate into, by name. */
        val LANGUAGES: List<LanguageChoice> = TranslationLanguages.common
            .filterKeys { TranslationLanguages.supports(TranslationProvider.Google, it) }
            .map { (code, name) -> LanguageChoice(code, name) }

        /** The system's language where it is listed, else English. */
        fun defaultTarget(locale: Locale = Locale.getDefault()): String =
            languageCode(locale.language)?.takeIf { code -> LANGUAGES.any { it.code == code } } ?: "en"

        private val sharedStore by lazy {
            FileSettingsStore(
                File(FileSettingsStore.appDataDir(System.getProperty("os.name").orEmpty(), System::getenv, System.getProperty("user.home")), "lyrics.properties"),
            )
        }

        /** The one store for the More menu's choices, beside the settings file; never build a second over it. */
        fun defaultStore(): FileSettingsStore = sharedStore
    }
}

/** The sources as the menu lists them, with what each had for the song in [known]. */
fun sourceChoices(sources: List<LyricsSourceDescriptor>, known: Map<String, ProviderResult>, shownId: String?): List<LyricsSourceChoice> =
    sources.map { source ->
        val result = known[source.id]
        val quality = (result as? ProviderResult.Hit)?.payload?.measuredQuality()
        LyricsSourceChoice(
            id = source.id,
            name = source.displayName,
            status = when (result) {
                is ProviderResult.Hit -> when (quality) {
                    RemoteLyricsQuality.WORD_SYNCED -> "Word-synced"
                    RemoteLyricsQuality.LINE_SYNCED -> "Line-synced"
                    RemoteLyricsQuality.PLAIN -> "Not synced"
                    else -> "No usable lyrics"
                }
                ProviderResult.Miss -> "No lyrics for this song"
                ProviderResult.NeedsMatch -> "Needs a Spotify link"
                null -> "Not asked yet"
                else -> "Unavailable right now"
            },
            available = result == null || (result is ProviderResult.Hit && quality != RemoteLyricsQuality.NONE),
            shown = source.id == shownId,
        )
    }

/** Google Translate through the shared engine, its results kept on disk in [cacheDir]. */
class GoogleSongTranslator(cacheDir: File) : SongTranslator {
    private val engine = TranslationEngine(SongLanguageDetector { null }, DiskTranslationCache(cacheDir))
    private val google = GoogleTranslator(OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build())

    override suspend fun translate(track: Track, lyrics: LyricsState.Ready, target: String): TranslationOutcome {
        val document = TranslationDocument.from("${track.artist} - ${track.title}", "${lyrics.provider}:${lyrics.source.orEmpty()}", lyrics.lines)
        val preferences = TranslationPreferences(targetLanguage = target, provider = TranslationProvider.Google, humanTranslations = false)
        engine.ownResult(document, preferences, null)?.let { return TranslationOutcome.Translated(it) }
        return engine.translate(document, preferences, null, google)
    }
}
