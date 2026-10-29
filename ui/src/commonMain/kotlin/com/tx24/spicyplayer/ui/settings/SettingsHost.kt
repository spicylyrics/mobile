package com.tx24.spicyplayer.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** What the settings show of the song and the player, apart from the settings themselves. */
data class SettingsState(
    val songTitle: String = "",
    /** The song's lyrics in a few words, for the This song row. */
    val songSummary: String = "",
    /** The delay for every song on [outputLabel]. */
    val lyricDelayMs: Int = 0,
    /** Where the sound goes ("the phone speaker", a headset's name). */
    val outputLabel: String = "",
    /** This song's own delay, on top of [lyricDelayMs]. */
    val songDelayMs: Int = 0,
    /** A song is playing that can have a delay and source of its own. */
    val hasSongKey: Boolean = false,
    /** The Romanization & Translation row's summary. */
    val translationSummary: String = "",
    /** The Sources row's summary ("12 of 16 on"). */
    val sourcesSummary: String = "",
    /** Songs in the player's queue; 0 hides the queue in quick settings. */
    val queueSize: Int = 0,
    /** Quick settings' lyrics source row, or null without one. */
    val sourceSummary: String? = null,
    /** The lyrics on screen, to tell when they change; any value. */
    val lyrics: Any? = null,
)

/**
 * What the settings pages need from the platform around them: the player's delays, which pages and
 * rows exist here, and the parts that only one platform has (the phone's backups, app icon,
 * haptics, queue and diagnostics). Everything open does nothing, or shows nothing, until a
 * platform overrides it.
 */
abstract class SettingsHost {
    abstract fun setLyricDelay(ms: Int)
    abstract fun setSongDelay(ms: Int)

    /** Asks before the first Canvas is shown, then runs [onChosen]. */
    open fun chooseCanvas(onChosen: () -> Unit) = onChosen()

    /** A settings page was opened, for the usage report. */
    open fun countPage(page: SettingsPage) {}

    /** A quick settings screen was opened (the queue, the Lyrics Manager). */
    open fun quickScreenOpened(screen: QuickScreen) {}

    /**
     * Opens the Lyrics Manager as a page of its own instead of a screen of quick settings; false
     * where it's a quick settings screen ([LyricsManagerScreen]).
     */
    open fun openLyricsManagerPage(): Boolean = false

    /** Quick settings came up for these lyrics. */
    open fun quickSettingsShown() {}

    /** Whether [page] is one of the settings here. */
    open fun hasPage(page: SettingsPage): Boolean = true

    /** The lyrics can shrink into a floating window. */
    open val pictureInPicture: Boolean get() = true

    /** What the rows call the device ("your phone's font"), and its system ("a font Android can read"). */
    open val deviceName: String get() = "phone"
    open val systemName: String get() = "Android"

    /** The lyrics can show over the lock screen. */
    open val lockScreen: Boolean get() = true

    /** The rows only a phone or tablet has: refresh rate, lock screen, screen on, system bars, rotation. */
    open val phoneDevice: Boolean get() = true

    /** Each song can have a delay of its own, on top of the player's. */
    open val songDelay: Boolean get() = true

    /** The player can share its queue and pick a song's source, so their buttons can be shown. */
    open val queueAndSource: Boolean get() = true

    /** The launcher icon in a word, for the Theme row; null where there's no choice of icon. */
    open val appIconLabel: String? get() = null

    /** Opens a picker for a lyrics font file, imports it and makes it the Custom font; [onFailed] if it isn't a font. */
    @Composable
    open fun rememberLyricsFontPicker(onFailed: (Boolean) -> Unit): () -> Unit = {}

    @Composable
    open fun ThisSongPage(onOpenLyricsManager: () -> Unit, onOpenSpotifySearch: () -> Unit) {}

    @Composable
    open fun TranslationPage() {}

    @Composable
    open fun SourcesPage() {}

    @Composable
    open fun AdvancedPage() {}

    /** The card at the foot of the first page: version, updates, links. */
    @Composable
    open fun AboutCard() {}

    @Composable
    open fun AppIconRow() {}

    @Composable
    open fun HapticsSection() {}

    /** Finding the delay by tapping along, under the delay slider. */
    @Composable
    open fun DelayCalibrationRow() {}

    /** The lyrics' language, in quick settings. */
    @Composable
    open fun LyricsLanguageRow() {}

    @Composable
    open fun QueueScreen(onJumped: () -> Unit, modifier: Modifier) {}

    @Composable
    open fun SourceScreen(onChosen: () -> Unit, modifier: Modifier) {}

    @Composable
    open fun LyricsManagerScreen(uploading: Boolean, onUploadingChange: (Boolean) -> Unit, onDismiss: () -> Unit) {}
}
