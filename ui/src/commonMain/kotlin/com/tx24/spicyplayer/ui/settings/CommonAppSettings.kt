package com.tx24.spicyplayer.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tx24.spicyplayer.haptics.MusicHapticsStyle
import com.tx24.spicyplayer.lyrics.spicy.SyllableMerge
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.ui.background.LEGACY_BLUR_DP
import com.tx24.spicyplayer.ui.components.ChromeTheme
import com.tx24.spicyplayer.ui.nowplaying.HeaderSize
import com.tx24.spicyplayer.ui.nowplaying.StageArt
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * How the lyrics screen looks and behaves, saved in [store] (the `ui` preferences on Android).
 * Each setting reads as Compose state, so changing one redraws what uses it; writing one also
 * saves it.
 *
 * The app adds the settings whose types only it has (`AppSettings`). Lookup, source and sync
 * settings live in the view model instead (see `docs/settings.md`).
 *
 * @param pipButtonByDefault whether the floating-window button starts on.
 * @param prereleasesByDefault whether pre-releases are offered as updates until changed.
 */
open class CommonAppSettings(
    protected val store: SettingsStore,
    pipButtonByDefault: Boolean,
    prereleasesByDefault: Boolean,
) {
    /** Nothing was saved yet when this was made: a new install, or one whose data was cleared. */
    val startedEmpty = store.keys.isEmpty()

    /** Every setting below, so [reload] can re-read them; first, so it exists when they register. */
    private val all = mutableListOf<Setting<*>>()
    // `coverStage` and `bigCoverCanvas` are only read now, by [stageArt] and [bigCoverArt], but an
    // older backup still restores them.
    private val types = mutableMapOf<String, KClass<*>>("coverStage" to Boolean::class, "bigCoverCanvas" to Boolean::class)

    /** The type each setting is stored as, by key, so a restore can turn away the wrong one. */
    val storedTypes: Map<String, KClass<*>> get() = types

    /** Re-reads every setting from storage, after a restore wrote it behind their backs. */
    fun reload() = all.forEach { it.reload() }

    /** Romanized lyrics where the song has them; the floating button, not a settings row. */
    var romanize by boolean("romanize", false)
    var romanizationMode by enum("romanizationMode", RomanizationMode.Replace)
    var translationMode by enum("translationMode", TranslationMode.UnderLine)

    var lyricsStyle by enum("lyricsStyle", LyricsStyle.Spicy)
    var simpleLyricsMode by boolean("simpleLyricsMode", false)
    var refreshedSimpleLyrics by boolean("refreshedSimpleLyrics", false)
    var minimalLyricsMode by boolean("minimalLyricsMode", false)
    var originalWordMotion by boolean("originalWordMotion", false)
    /**
     * The lyric text size against the screen's default, in percent, held apart for portrait and
     * landscape. Both start from the size picked before they were split.
     */
    var lyricsSizePortrait by int("lyricsSizePortrait", legacyLyricsSize(), LYRICS_SIZE_RANGE)
    var lyricsSizeLandscape by int("lyricsSizeLandscape", legacyLyricsSize(), LYRICS_SIZE_RANGE)
    /** The lyrics' size as a scale, for the way the screen is turned. */
    fun lyricsScale(landscape: Boolean): Float = (if (landscape) lyricsSizeLandscape else lyricsSizePortrait) / 100f
    /** Sets both sizes to [preset]'s, as the intro's one choice does. */
    fun setLyricsSize(preset: LyricsSize) {
        lyricsSizePortrait = preset.percent
        lyricsSizeLandscape = preset.percent
    }
    private fun legacyLyricsSize(): Int =
        runCatching { LyricsSize.valueOf(store.getString("lyricsSize")!!).percent }.getOrDefault(100)
    /** Carries over the old "Use System Font" switch. */
    var lyricsFont by enum("lyricsFont", if (store.getBoolean("systemFont") == true) LyricsFont.System else LyricsFont.Default)
    /** The picked font's file in the app's storage (`LyricsFontFile`), and the name it came with. */
    var customFontFile by string("customFontFile", "")
    var customFontName by string("customFontName", "")
    var showScrollToActive by boolean("showScrollToActive", true)
    var pinnedFooter by enum("pinnedFooter", PinnedFooterMode.Off)
    /** A credit's profile opens in the browser (after asking) rather than in a pop-up. */
    var profilesInBrowser by boolean("profilesInBrowser", false)
    var duetLinePadding by boolean("duetLinePadding", true)
    /** A song ending on a duet line sung from the right has its credits there too. */
    var creditsFollowLastLine by boolean("creditsFollowLastLine", false)
    var refinedCredits by boolean("refinedCredits", true)
    /** "Translation from" and "Romanization from" in the credits. */
    var textCredits by boolean("textCredits", true)
    var backgroundPop by boolean("backgroundPop", false)
    var keepOverlappingLines by boolean("keepOverlappingLines", true)
    var syllableMerge by enum("syllableMerge", SyllableMerge.Off)

    var scrollLeadEnabled by boolean("scrollLeadEnabled", false)
    var scrollLeadMs by int("scrollLeadMs", 250, 0..800)
    var smoothScrolling by boolean("smoothScrolling", false)
    /** Unsynced lyrics scroll through at the song's pace, top to bottom over its length. */
    var staticAutoScroll by boolean("staticAutoScroll", true)
    var seekFadeCompensation by boolean("seekFadeCompensation", true)
    /** A mouse over a line lays a soft pill behind it; with a mouse only. */
    var lineHoverBackground by boolean("lineHoverBackground", true)

    var distanceBlur by boolean("distanceBlur", true)
    var glow by boolean("glow", true)
    /** Drops the costliest effects: the moving background, animated cover, glass blur, and lyric blur and glow. */
    var lowPerformance by boolean("lowPerformance", false)
    /** The playback buttons, the queue and pop-ups animate; off in [lowPerformance] too. */
    var uiAnimations by boolean("uiAnimations", true)
    /** Dragging a settings slider clears the rest of the settings away, so the change shows on the lyrics. */
    var peekWhileAdjusting by boolean("peekWhileAdjusting", true)

    /** Carries over the old Default/Legacy switch. */
    var backgroundType by enum(
        "backgroundType",
        if (store.getBoolean("legacyBackground") == true) BackgroundType.Legacy else BackgroundType.Default,
    )
    /** The still image backgrounds' blur, in dp. */
    var backgroundBlur by int("backgroundBlur", 0, 0..MAX_BACKGROUND_BLUR)
    /** The moving cover's (Default) blur strength, in percent. */
    var dynamicBackgroundBlur by int("dynamicBackgroundBlur", DEFAULT_DYNAMIC_BLUR, 0..100)
    /** The Legacy background's blur, in dp. */
    var legacyBackgroundBlur by int("legacyBackgroundBlur", LEGACY_BLUR_DP, 0..MAX_BACKGROUND_BLUR)

    /** The blur the session background takes for [type]: percent for Default, dp for the rest. */
    fun backgroundBlurFor(type: BackgroundType): Int = when (type) {
        BackgroundType.Default -> dynamicBackgroundBlur
        BackgroundType.Legacy -> legacyBackgroundBlur
        else -> backgroundBlur
    }
    /** The moving background speeds up and slows down with the song's tempo, loudness and beats. */
    var beatReactiveBackground by boolean("beatReactiveBackground", true)
    /** Holds the moving backgrounds (Default, Legacy) still. */
    var staticBackground by boolean("staticBackground", false)
    var releaseYearPosition by enum("releaseYearPosition", ReleaseYearPosition.Off)
    var expandWithoutLyrics by boolean("expandWithoutLyrics", false)
    /** Landscape: the timeline sits under the cover, above the song, rather than with the controls on it. */
    var timelineOutsideCover by boolean("timelineOutsideCover", true)
    /** Big screens: whether the page's view controls sit along its top or its bottom, 24 px in. */
    var viewControlsPosition by enum("viewControlsPosition", ViewControlsPosition.Top)
    /**
     * Big screens: a song without lyrics says why. Off, cinema and fullscreen leave just the song,
     * its cover in the middle. Errors still show their notice.
     */
    var showNoLyricsNotice by boolean("showNoLyricsNotice", true)
    /** The record's looping Apple Music cover in place of the still one, where it has one. */
    var animatedCover by boolean("animatedCover", true)
    /**
     * Landscape, beside the lyrics: what may fill its side of the screen, with the song docked
     * along its bottom. A song gets the first one it has; nothing picked is no stage. Unset, it
     * follows the `coverStage` switch it replaced, if that was ever flipped, else [StageArt.DEFAULT].
     */
    var stageArt by setting("stageArts", String::class, {
        store.getString("stageArts")?.let(StageArt::parse)
            ?: when (store.getBoolean("coverStage")) {
                true -> StageArt.COVER_STAGE_ON
                false -> emptySet()
                null -> StageArt.DEFAULT
            }
    }) { store.putString("stageArts", StageArt.store(it)) }
    /** Where the song has a Spotify Canvas, the background is that Canvas, blurred, over [backgroundType]. */
    var canvasBackground by boolean("canvasBackground", false)
    /**
     * Portrait, while the big cover is open: what takes its place. A Canvas fills the screen; the
     * animated or still cover spreads edge to edge and melts into the background. A song gets the
     * first one it has; nothing picked is the big cover as it is. Unset, it follows the
     * `bigCoverCanvas` switch it replaced, if that was ever flipped off, else [StageArt.DEFAULT].
     */
    var bigCoverArt by setting("bigCoverArts", String::class, {
        store.getString("bigCoverArts")?.let(StageArt::parse)
            ?: if (store.getBoolean("bigCoverCanvas") == false) emptySet() else StageArt.DEFAULT
    }) { store.putString("bigCoverArts", StageArt.store(it)) }
    /** While the stage or the big cover shows a Canvas, the background is that Canvas, blurred. */
    var canvasBackdrop by boolean("canvasBackdrop", true)
    /**
     * The loops that hold their frame while the music is paused, by [LOOP_KINDS] name; both kinds
     * by default. Unknown names are dropped; the app reads it as `AppSettings.pauseWithMusic`.
     */
    var pauseWithMusicKinds by setting("pauseWithMusic", String::class, {
        store.getString("pauseWithMusic")?.let(::parseLoopKinds) ?: LOOP_KINDS.map { it.name }.toSet()
    }) { kinds -> store.putString("pauseWithMusic", storeLoopKinds(kinds)) }
    /** Cover Art background: the record's looping Apple Music cover in place of the still one. */
    var animatedBackground by boolean("animatedBackground", false)
    /**
     * Background changes crossfade in place (the type too), image backgrounds keep their edges, and
     * Cover Colors uses the cover's two deepest shades.
     */
    var refinedBackground by boolean("refinedBackground", true)
    /** Default background: follows the record's animated cover while the header plays one ([animatedCover]). */
    var backgroundFollowsMotion by boolean("backgroundFollowsMotion", true)

    var keepScreenOn by boolean("keepScreenOn", true)
    /** The lyrics screen shows over the lock screen; anything opened over the lyrics asks to unlock first. */
    var showOverLockScreen by boolean("showOverLockScreen", false)
    /** Left untouched while the music plays, the lyrics screen dims to the lyrics alone on black. */
    var ambientMode by boolean("ambientMode", false)
    /** How long the lyrics screen waits after the last touch before [ambientMode] dims it. */
    var ambientDelay by enum("ambientDelay", AmbientDelay.Default)
    /** Which of the phone's bars stay on screen. */
    var systemBars by enum("systemBars", SystemBarsMode.HideBoth)
    /** Leaving the app with a song playing shrinks the lyrics into a small floating window. */
    var autoPip by boolean("autoPip", true)
    /** The floating window's shape; [pipCustomRatio] for [PipShape.Custom]. */
    var pipShape by enum("pipShape", PipShape.Wide)
    /** A custom window's width over its height, times 100 (Android allows 1:2.39 to 2.39:1). */
    var pipCustomRatio by int("pipCustomRatio", 100, PIP_RATIO_RANGE)
    /** The floating window shows the lyrics; off, it shows the song's cover, title and artist. */
    var pipShowLyrics by boolean("pipShowLyrics", true)
    /** The song bar (title, artist, timeline) along the bottom of a square or tall window. */
    var pipDock by boolean("pipDock", true)
    /** A small cover beside the title in a square or tall window's song bar. */
    var pipDockCover by boolean("pipDockCover", true)
    /** What the floating window shows for a song without lyrics. */
    var pipWithoutLyrics by enum("pipWithoutLyrics", PipWithoutLyrics.Message)
    /**
     * The floating window's lyric text size, in percent, on top of its own fit to the window. Held
     * apart from the full screen's, so big lyrics there don't leave one word per row in the window.
     */
    var pipLyricsSize by int("pipLyricsSize", 100, LYRICS_SIZE_RANGE)

    /** The floating window's width over its height. */
    val pipAspect: Float
        get() = if (pipShape == PipShape.Custom) pipCustomRatio / 100f else pipShape.width.toFloat() / pipShape.height

    /** The floating button that turns the screen to landscape, rotation lock or not. */
    var rotateButton by boolean("rotateButton", true)
    /**
     * A floating button that shrinks the lyrics into that window by hand. Off by default where
     * the system enters the window on its own; before that the button is the easy way in.
     */
    var pipButton by boolean("pipButton", pipButtonByDefault)
    /** No song header: the lyrics fill the page and scroll with the active line near the centre. */
    var hideHeader by boolean("hideHeader", false)
    var headerSize by enum("headerSize", HeaderSize.Large)
    /** Which side the now-playing panel sits on in landscape; the floating swap button, not a settings row. */
    var panelSide by enum("panelSide", PanelSide.Left)
    /** The screen's full refresh rate (90/120 Hz) instead of 60 Hz, which costs battery. */
    var highRefreshRate by boolean("highRefreshRate", false)
    var autoHideControls by boolean("autoHideControls", true)
    var controlsHideDelay by enum("controlsHideDelay", ControlsHideDelay.Default)
    /** The controls fade while paused too, not only while playing. */
    var hideControlsWhilePaused by boolean("hideControlsWhilePaused", false)
    /** Ticks and clicks on buttons, toggles, sliders and gestures (they also follow the system's touch-feedback setting). */
    var touchHaptics by boolean("touchHaptics", true)
    /** Vibrates along with the song's beats while the lyrics are on screen. */
    var musicHaptics by boolean("musicHaptics", false)
    /** How strong the music haptics are, in percent of their own strength (25..400; over 100 layers heavier vibrations on). */
    var musicHapticsStrength by int("musicHapticsStrength", 100, 25..200)
    var musicHapticsStyle by enum("musicHapticsStyle", MusicHapticsStyle.Drums)
    /** A floating button that opens the quick settings (the song's and the output's delay). */
    var quickSettingsButton by boolean("quickSettingsButton", true)
    /**
     * A floating button that opens the Lyrics Manager. Off by default: it's always in quick
     * settings and Settings → This song, and few open it often.
     */
    var lyricsManagerButton by boolean("lyricsManagerButton", false)
    /** The big-screen page's now-playing bar, open on the page; closed until the user opens it. */
    var nowBarOpen by boolean("nowBarOpen", false)
    /** The big-screen page's now-playing bar on the right of the lyrics instead of the left. */
    var nowBarOnRight by boolean("nowBarOnRight", false)
    /** The big-screen page's compact button: following the window, or held compact or side by side. */
    var compactView by enum("compactView", CompactView.Auto)
    /** A floating button that opens the queue (it's always in quick settings); shown while the player shares one. */
    var queueButton by boolean("queueButton", false)
    /** A floating button that opens the song's lyrics source choice (it's always in quick settings). */
    var sourceButton by boolean("sourceButton", false)
    /** The player's own extra actions (like, shuffle modes, …) as floating buttons. */
    var playerButtons by boolean("playerButtons", false)
    var romanizeButton by boolean("romanizeButton", true)
    var translateButton by boolean("translateButton", true)
    var resyncButton by boolean("resyncButton", true)
    var expandButton by boolean("expandButton", true)
    /** How the Lyrics Manager's last upload was applied; the upload screen reopens on it. */
    var ttmlUploadSaves by boolean("ttmlUploadSaves", true)

    /** How Settings and the pop-ups are dressed. */
    var settingsTheme by enum("settingsTheme", ChromeTheme.Purple)
    var popupTheme by enum("popupTheme", ChromeTheme.Purple)

    /** The launcher icon, by [APP_ICONS] name; the app reads it as `AppSettings.appIcon`. */
    var appIconName by setting("appIcon", String::class, {
        store.getString("appIcon")?.takeIf { name -> APP_ICONS.any { it.name == name } } ?: APP_ICONS.first().name
    }) { store.putString("appIcon", it) }

    /** Anonymous usage stats; nothing is sent until the notice has been answered. */
    var usageStats by boolean(KEY_USAGE_STATS, true)
    var usageStatsAsked by boolean(KEY_USAGE_STATS_ASKED, false)
    /** The first-run introduction has been gone through (it only shows on a new install). */
    var introDone by boolean("introDone", false)
    /** Asked for again from Settings, on an install that wouldn't show it by itself. Not saved. */
    var introReplay by mutableStateOf(false)

    /** Pre-releases offered as updates. */
    var includePrereleases by boolean("includePrereleases", prereleasesByDefault)

    /** The word motion used when [originalWordMotion] is off (`RenderConfig.wordMotionBoost`). */
    val wordMotionBoost get() = if (originalWordMotion) 1f else WORD_MOTION_BOOST

    private fun boolean(key: String, default: Boolean) =
        setting(key, Boolean::class, { store.getBoolean(key) ?: default }) { store.putBoolean(key, it) }

    private fun string(key: String, default: String) =
        setting(key, String::class, { store.getString(key) ?: default }) { store.putString(key, it) }

    private fun int(key: String, default: Int, range: IntRange) =
        setting(key, Int::class, { (store.getInt(key) ?: default).coerceIn(range) }) { store.putInt(key, it) }

    private inline fun <reified E : Enum<E>> enum(key: String, default: E) =
        setting(key, String::class, { store.getString(key)?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default }) {
            store.putString(key, it.name)
        }

    /** A saved setting under [key], stored as [type], read by [load] and written by [save]. */
    protected fun <T> setting(key: String, type: KClass<*>, load: () -> T, save: (T) -> Unit): ReadWriteProperty<Any?, T> =
        Setting(load, save).also { all += it; types[key] = type }

    private class Setting<T>(private val load: () -> T, private val save: (T) -> Unit) : ReadWriteProperty<Any?, T> {
        private var value by mutableStateOf(load())
        fun reload() { value = load() }
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.value = value
            save(value)
        }
    }

    companion object {
        /** Sung words grow and lift this much more than the desktop amount, which reads better on a phone. */
        const val WORD_MOTION_BOOST = 1.25f
        const val KEY_USAGE_STATS = "usageStats"
        const val KEY_USAGE_STATS_ASKED = "usageStatsAsked"
    }
}

/**
 * What fills the screen behind the lyrics: the moving cover ([Default], [Legacy]), a still image
 * ([Auto] and [ArtistHeader]: the artist's Spotify header, else the cover; [CoverArt]), or the
 * cover's colours ([Color]).
 */
enum class BackgroundType(val label: String) {
    Default("Default"),
    Legacy("Legacy"),
    Auto("Auto"),
    ArtistHeader("Artist Header"),
    CoverArt("Cover Art"),
    Color("Color");

    /** A still picture, which the blur setting softens. */
    val image get() = this == Auto || this == ArtistHeader || this == CoverArt
    val usesArtistHeader get() = this == Auto || this == ArtistHeader
    /** Moves on its own (and can be held still). */
    val moving get() = this == Default || this == Legacy
}

/** The strongest [CommonAppSettings.backgroundBlur]. */
const val MAX_BACKGROUND_BLUR = 67

/** The moving cover's blur by default: the reference's. */
const val DEFAULT_DYNAMIC_BLUR = 60

/** The floating window's shapes, as width:height; [Custom] takes `AppSettings.pipCustomRatio`. */
enum class PipShape(val label: String, val width: Int, val height: Int) {
    Wide("Wide (16:9)", 16, 9),
    Classic("Classic (4:3)", 4, 3),
    Square("Square (1:1)", 1, 1),
    Tall("Tall (3:4)", 3, 4),
    Custom("Custom", 1, 1),
}

/** What the floating window shows for a song without lyrics: the notice, or the song's card. */
enum class PipWithoutLyrics(val label: String) {
    Message("Message"),
    SongCard("Song card"),
}

/** `AppSettings.pipCustomRatio`'s range: Android's 1:2.39 to 2.39:1, times 100. */
val PIP_RATIO_RANGE = 42..239

/** How long the controls stay after the last touch before they fade. */
enum class ControlsHideDelay(val label: String, val millis: Long) {
    Short("2 seconds", 2_000L),
    Default("3 seconds", 3_000L),
    Long("5 seconds", 5_000L),
    Longer("10 seconds", 10_000L),
}

enum class AmbientDelay(val label: String, val millis: Long) {
    Shortest("15 seconds", 15_000L),
    Default("30 seconds", 30_000L),
    Minute("1 minute", 60_000L),
    TwoMinutes("2 minutes", 120_000L),
    FiveMinutes("5 minutes", 300_000L),
}

/** Where the big-screen page's view controls sit. */
enum class ViewControlsPosition(val label: String) { Top("Top"), Bottom("Bottom") }

/** Which side the now-playing panel sits on in landscape. */
enum class PanelSide { Left, Right }

/** The big-screen page's compact choice: [Auto] follows the window; [override] is the page's own form of it. */
enum class CompactView(val override: Boolean?) {
    Auto(null), Compact(true), Side(false);

    companion object {
        fun of(override: Boolean?): CompactView = entries.first { it.override == override }
    }
}

/** Where the song's release year shows beside the artists, if at all. */
enum class ReleaseYearPosition(val label: String) { Off("Off"), Left("Left"), Right("Right") }

/** How the lyrics look and move: Spicy Lyrics' own, or like Apple Music's. */
enum class LyricsStyle(val label: String) {
    Spicy("Spicy Lyrics"),
    AppleMusic("Apple Music"),
}

/** Lyric text sizes to pick from in one step, against the default, which follows the screen width. */
enum class LyricsSize(val percent: Int, val label: String) {
    Small(85, "Small"),
    Default(100, "Default"),
    Large(115, "Large"),
    Larger(130, "Larger"),
    Huge(160, "Huge"),
    ;

    companion object {
        /** The one nearest [percent]. */
        fun nearest(percent: Int): LyricsSize = entries.minBy { kotlin.math.abs(it.percent - percent) }
    }
}

/** How far the lyrics' size goes either way, in percent of the default. */
val LYRICS_SIZE_RANGE = 60..250

/** A choice the shared settings store by [name] while its type lives in the app, with its [label]. */
class NamedChoice(val name: String, val label: String)

/** The kinds of looping cover art, in the app's `LoopKind` order. */
val LOOP_KINDS = listOf(NamedChoice("AnimatedCover", "Animated covers"), NamedChoice("Canvas", "Spotify Canvas"))

/** The launcher icons, in the app's `AppIcon` order; the first is the default. */
val APP_ICONS = listOf(NamedChoice("Purple", "Purple"), NamedChoice("Pride", "Pride"))

/** Loop kinds stored as names joined by commas; an unknown name is dropped. */
internal fun parseLoopKinds(stored: String): Set<String> =
    stored.split(',').map { it.trim() }.filter { name -> LOOP_KINDS.any { it.name == name } }.toSet()

internal fun storeLoopKinds(kinds: Set<String>): String = LOOP_KINDS.map { it.name }.filter { it in kinds }.joinToString(",")

/** The Lyrics Font setting. */
enum class LyricsFont(val label: String) {
    Default("Default"),
    System("System"),
    Custom("Custom"),
}
