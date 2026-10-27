package com.tx24.spicyplayer.desktop.page

/** How the lyrics page fills the window. */
enum class ViewMode {
    /** The page in the window, with the now-playing bar optional. */
    Page,

    /** The page on its own across the window, the now-playing bar always open and its cover holding the controls. */
    Cinema,

    /** Cinema across the whole screen. */
    Fullscreen,
}

/**
 * The page's view state, changed only through the functions here so every way in (the view
 * controls, F11, Escape) moves it the same way.
 *
 * Below [COMPACT_BELOW_DP] wide, cinema and fullscreen turn compact: the song moves to a bar
 * across the top. The compact button overrides that until the page is left.
 */
data class PageState(
    val mode: ViewMode = ViewMode.Page,
    /** Whether the now-playing bar is open on the page; cinema and fullscreen always show it. */
    val nowBarWanted: Boolean = true,
    val nowBarOnRight: Boolean = false,
    /** Compact mode as the compact button set it, or null to follow the window's width. */
    val compactOverride: Boolean? = null,
    val romanized: Boolean = false,
) {
    val immersive: Boolean get() = mode != ViewMode.Page
    val nowBarOpen: Boolean get() = immersive || nowBarWanted

    fun isCompact(widthDp: Float): Boolean = immersive && (compactOverride ?: (widthDp <= COMPACT_BELOW_DP))

    /** The cinema button on the page; the fullscreen button's cinema face. */
    fun toggleCinema(): PageState = if (immersive) close() else copy(mode = ViewMode.Cinema)

    /** F11 and the fullscreen button: fullscreen and cinema swap, and the page goes fullscreen. */
    fun toggleFullscreen(): PageState = copy(mode = if (mode == ViewMode.Fullscreen) ViewMode.Cinema else ViewMode.Fullscreen)

    /** Escape: fullscreen drops to cinema, cinema to the page. */
    fun back(): PageState = when (mode) {
        ViewMode.Fullscreen -> copy(mode = ViewMode.Cinema)
        ViewMode.Cinema -> close()
        ViewMode.Page -> this
    }

    /** The close button: straight back to the page. */
    fun close(): PageState = copy(mode = ViewMode.Page, compactOverride = null)

    fun toggleNowBar(): PageState = copy(nowBarWanted = !nowBarWanted)
    fun swapSides(): PageState = copy(nowBarOnRight = !nowBarOnRight)
    fun toggleCompact(widthDp: Float): PageState = if (immersive) copy(compactOverride = !isCompact(widthDp)) else this
    fun toggleRomanization(): PageState = copy(romanized = !romanized)

    /** What [control] does when clicked, for a page [widthDp] wide; [ViewControl.More] and [ViewControl.Settings] open menus instead. */
    fun click(control: ViewControl, widthDp: Float): PageState = when (control) {
        ViewControl.CinemaView -> toggleCinema()
        ViewControl.CompactMode -> toggleCompact(widthDp)
        ViewControl.Romanization -> toggleRomanization()
        ViewControl.NowBar -> toggleNowBar()
        ViewControl.NowBarSide -> swapSides()
        ViewControl.Fullscreen -> toggleFullscreen()
        ViewControl.Close -> close()
        ViewControl.More, ViewControl.Settings -> this
    }

    companion object {
        /** `max-width: 70.812rem`. */
        const val COMPACT_BELOW_DP = 1133f
    }
}

/** The round buttons along the page's view-control row, in the order they appear. */
enum class ViewControl {
    CinemaView, CompactMode, Romanization, NowBar, NowBarSide, Fullscreen, More, Settings, Close,
}

/**
 * The view controls [state] shows: the cinema button only on the page and the compact one only
 * off it, the now-playing toggle on the page, the side swap whenever the bar is open, and the
 * fullscreen button in cinema and fullscreen. Close only shows off the page; on the page the
 * window's own close does that. [ViewControl.More] holds what the mobile app adds.
 */
fun viewControls(state: PageState): List<ViewControl> = buildList {
    if (!state.immersive) add(ViewControl.CinemaView) else add(ViewControl.CompactMode)
    add(ViewControl.Romanization)
    if (!state.immersive) add(ViewControl.NowBar)
    if (state.nowBarOpen) add(ViewControl.NowBarSide)
    if (state.immersive) add(ViewControl.Fullscreen)
    add(ViewControl.More)
    add(ViewControl.Settings)
    if (state.immersive) add(ViewControl.Close)
}

/** The tooltip over [control] in [state] at [widthDp]. */
fun tooltip(control: ViewControl, state: PageState, widthDp: Float): String = when (control) {
    ViewControl.CinemaView -> "Cinema View"
    ViewControl.CompactMode -> if (state.isCompact(widthDp)) "Disable Compact Mode" else "Enable Compact Mode"
    ViewControl.Romanization -> if (state.romanized) "Disable Romanization" else "Enable Romanization"
    ViewControl.NowBar -> "NowBar"
    ViewControl.NowBarSide -> "Swap NowBar Side"
    ViewControl.Fullscreen -> if (state.mode == ViewMode.Cinema) "Fullscreen" else "Cinema View"
    ViewControl.More -> "More"
    ViewControl.Settings -> "Spicy Lyrics Settings"
    ViewControl.Close -> "Close Page"
}

/** Which key presses move the page: F11 and Escape. */
enum class PageKey { F11, Escape }

fun PageState.onKey(key: PageKey): PageState = when (key) {
    PageKey.F11 -> toggleFullscreen()
    PageKey.Escape -> back()
}
