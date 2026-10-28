package com.tx24.spicyplayer.ui.bigscreen

import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.CompactView

/** How the big-screen lyrics page fills its window. */
enum class ViewMode {
    /** The page in the window, with the now-playing bar optional. */
    Page,

    /** The page on its own across the window, the now-playing bar always open and its cover holding the controls. */
    Cinema,

    /** Cinema across the whole screen, where the platform can do that. */
    Fullscreen,
}

/** Where the song goes: beside the lyrics, or as a bar across their top. */
enum class BigScreenLayout { Side, Compact }

/**
 * The page's view state, changed only through the functions here so every way in (the view
 * controls, F11, Escape) moves it the same way.
 *
 * The layout follows the window ([layoutFor]): narrower than [SIDE_MIN_WIDTH_DP] or taller than
 * wide (a tablet held upright, a thin window) the song moves to a bar across the top, and in
 * cinema and fullscreen so it does up to [COMPACT_BELOW_DP] wide. The compact button overrides
 * that off the page, and the choice holds the next time cinema or fullscreen opens.
 */
data class BigScreenState(
    val mode: ViewMode = ViewMode.Page,
    /** Whether the now-playing bar is open on the page; cinema and fullscreen always show it. */
    val nowBarWanted: Boolean = true,
    val nowBarOnRight: Boolean = false,
    /** Compact mode as the compact button set it, or null to follow the window. */
    val compactOverride: Boolean? = null,
    val romanized: Boolean = false,
) {
    val immersive: Boolean get() = mode != ViewMode.Page
    val nowBarOpen: Boolean get() = immersive || nowBarWanted

    fun isCompact(widthDp: Float, heightDp: Float): Boolean {
        if (immersive) compactOverride?.let { return it }
        return when {
            widthDp < SIDE_MIN_WIDTH_DP || widthDp < heightDp -> true
            immersive -> widthDp <= COMPACT_BELOW_DP
            else -> false
        }
    }

    fun layoutFor(widthDp: Float, heightDp: Float): BigScreenLayout =
        if (isCompact(widthDp, heightDp)) BigScreenLayout.Compact else BigScreenLayout.Side

    /** The cinema button on the page; closes back to the page from cinema or fullscreen. */
    fun toggleCinema(): BigScreenState = if (immersive) close() else copy(mode = ViewMode.Cinema)

    /**
     * F11 and the fullscreen button: fullscreen and cinema swap, and the page goes fullscreen.
     * Nothing where the platform has no fullscreen.
     */
    fun toggleFullscreen(capabilities: PlayerCapabilities): BigScreenState = when {
        !capabilities.fullscreen -> this
        mode == ViewMode.Fullscreen -> copy(mode = ViewMode.Cinema)
        else -> copy(mode = ViewMode.Fullscreen)
    }

    /** Escape or back: fullscreen drops to cinema, cinema to the page. */
    fun back(): BigScreenState = when (mode) {
        ViewMode.Fullscreen -> copy(mode = ViewMode.Cinema)
        ViewMode.Cinema -> close()
        ViewMode.Page -> this
    }

    /** The close button: straight back to the page. */
    fun close(): BigScreenState = copy(mode = ViewMode.Page)

    fun toggleNowBar(): BigScreenState = copy(nowBarWanted = !nowBarWanted)
    fun swapSides(): BigScreenState = copy(nowBarOnRight = !nowBarOnRight)
    fun toggleCompact(widthDp: Float, heightDp: Float): BigScreenState =
        if (immersive) copy(compactOverride = !isCompact(widthDp, heightDp)) else this
    fun toggleRomanization(): BigScreenState = copy(romanized = !romanized)

    /** What [control] does when clicked; [ViewControl.More] and [ViewControl.Settings] open menus instead. */
    fun click(control: ViewControl, widthDp: Float, heightDp: Float, capabilities: PlayerCapabilities): BigScreenState =
        when (control) {
            ViewControl.CinemaView -> toggleCinema()
            ViewControl.CompactMode -> toggleCompact(widthDp, heightDp)
            ViewControl.Romanization -> toggleRomanization()
            ViewControl.NowBar -> toggleNowBar()
            ViewControl.NowBarSide -> swapSides()
            ViewControl.Fullscreen -> toggleFullscreen(capabilities)
            ViewControl.Close -> close()
            ViewControl.More, ViewControl.Settings, ViewControl.LyricsManager -> this
        }

    fun onKey(key: PageKey, capabilities: PlayerCapabilities): BigScreenState = when (key) {
        PageKey.F11 -> toggleFullscreen(capabilities)
        PageKey.Escape -> back()
    }

    companion object {
        /** `max-width: 70.812rem`: below it cinema and fullscreen go compact. */
        const val COMPACT_BELOW_DP = 1133f

        /** Below this width even the page can't fit the cover beside readable lyrics. */
        const val SIDE_MIN_WIDTH_DP = 700f
    }
}

/** The round buttons along the page's view-control row, in the order they appear. */
enum class ViewControl {
    CinemaView, CompactMode, Romanization, NowBar, NowBarSide, Fullscreen, More, LyricsManager, Settings, Close,
}

/**
 * The view controls [state] shows: the cinema button only on the page and the compact one only
 * off it, the now-playing toggle on the page, the side swap whenever the bar is open, and the
 * fullscreen button in cinema and fullscreen where the platform has fullscreen. Close only shows
 * off the page; on the page the window's own close does that. [ViewControl.More] holds the
 * mobile app's extras, and the Lyrics Manager button shows where the platform has one and the
 * user turned it on ([PlayerCapabilities.lyricsManager]).
 */
fun viewControls(state: BigScreenState, capabilities: PlayerCapabilities): List<ViewControl> = buildList {
    if (!state.immersive) add(ViewControl.CinemaView) else add(ViewControl.CompactMode)
    add(ViewControl.Romanization)
    if (!state.immersive) add(ViewControl.NowBar)
    if (state.nowBarOpen) add(ViewControl.NowBarSide)
    if (state.immersive && capabilities.fullscreen) add(ViewControl.Fullscreen)
    add(ViewControl.More)
    if (capabilities.lyricsManager) add(ViewControl.LyricsManager)
    add(ViewControl.Settings)
    if (state.immersive) add(ViewControl.Close)
}

/** The tooltip over [control] in [state]. */
fun tooltip(control: ViewControl, state: BigScreenState, widthDp: Float, heightDp: Float): String = when (control) {
    ViewControl.CinemaView -> "Cinema View"
    ViewControl.CompactMode -> if (state.isCompact(widthDp, heightDp)) "Disable Compact Mode" else "Enable Compact Mode"
    ViewControl.Romanization -> if (state.romanized) "Disable Romanization" else "Enable Romanization"
    ViewControl.NowBar -> "NowBar"
    ViewControl.NowBarSide -> "Swap NowBar Side"
    ViewControl.Fullscreen -> if (state.mode == ViewMode.Cinema) "Fullscreen" else "Cinema View"
    ViewControl.More -> "More"
    ViewControl.LyricsManager -> "Lyrics Manager"
    ViewControl.Settings -> "Spicy Lyrics Settings"
    ViewControl.Close -> "Close Page"
}

/** [this] with the view kept in [settings] from the last run: the now-playing bar and the compact choice. */
fun BigScreenState.withSavedView(settings: CommonAppSettings): BigScreenState = copy(
    nowBarWanted = settings.nowBarOpen,
    nowBarOnRight = settings.nowBarOnRight,
    compactOverride = settings.compactView.override,
)

/** Keeps [state]'s view in [this] for the next run; a value that didn't change isn't written. */
fun CommonAppSettings.saveView(state: BigScreenState) {
    if (nowBarOpen != state.nowBarWanted) nowBarOpen = state.nowBarWanted
    if (nowBarOnRight != state.nowBarOnRight) nowBarOnRight = state.nowBarOnRight
    CompactView.of(state.compactOverride).let { if (compactView != it) compactView = it }
}

/** The keys that move the page: F11, and Escape (or a tablet's back). */
enum class PageKey { F11, Escape }
