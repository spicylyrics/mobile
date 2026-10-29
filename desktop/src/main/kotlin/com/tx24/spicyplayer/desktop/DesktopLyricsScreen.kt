package com.tx24.spicyplayer.desktop

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.CompositionLocalProvider
import com.tx24.spicyplayer.ui.settings.QuickSettingsModal
import com.tx24.spicyplayer.ui.settings.SettingsScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalUriHandler
import com.tx24.spicyplayer.ui.bigscreen.LyricsActions
import com.tx24.spicyplayer.ui.bigscreen.ConsentPrompt
import com.tx24.spicyplayer.ui.theme.SpicyColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.tx24.spicyplayer.ui.lyrics.LyricsOverlays
import com.tx24.spicyplayer.ui.lyrics.LyricsScreenContext
import com.tx24.spicyplayer.ui.lyrics.LyricsScreenHost
import com.tx24.spicyplayer.ui.lyrics.ScreenKeys
import com.tx24.spicyplayer.ui.nowplaying.BasicNowPlayingPlatform
import com.tx24.spicyplayer.ui.nowplaying.NowPlayingPlatform
import com.tx24.spicyplayer.ui.nowplaying.PlaybackTransport
import com.tx24.spicyplayer.ui.settings.QuickScreen
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The desktop's side of the shared lyrics screen: the session's lyric clock and resync, the
 * lookup's translation, the desktop background, and the mini window in place of the phone's
 * floating one.
 */
class DesktopLyricsHost(
    override val transport: PlaybackTransport,
    private val lyricsActions: LyricsActions,
    private val overlays: LyricsOverlays,
    override val keys: DesktopKeys,
    private val positionMs: () -> Long,
    private val seek: (Long) -> Unit,
    private val onResync: () -> Unit,
    private val openMiniWindow: () -> Unit,
    private val background: @Composable (animate: Boolean, modifier: Modifier) -> Unit,
    /** The settings pages' desktop side, which settings and quick settings open over the lyrics with. */
    private val settingsHost: DesktopSettingsHost,
    /** False for a copy of the screen composed out of sight ([WarmOtherLayout]): no pop-ups of its own. */
    private val drawsOverlays: Boolean = true,
) : LyricsScreenHost() {
    override fun lyricPositionMs(): Long = positionMs()
    override fun seekToLyric(positionMs: Long) { seek(positionMs) }
    override fun resync() { onResync() }

    /** The translate button: on asks first the first time ([Overlays] shows the question). */
    override fun toggleTranslation() {
        lyricsActions.setTranslating(!lyricsActions.options.value.translating)
    }

    override val pictureInPicture: Boolean get() = true
    // Until the phone's Lyrics Manager is shared.
    override val lyricsManager: Boolean get() = false
    override fun enterPip() { openMiniWindow() }

    @Composable
    override fun Background(
        animate: Boolean,
        beatReactive: Boolean,
        followMotionUrl: String?,
        onFollowMotionFailed: () -> Unit,
        motionCoverUrl: String?,
        onMotionCoverFailed: () -> Unit,
        modifier: Modifier,
    ) = background(animate, modifier)

    /**
     * The question a first translation waits on, in a card over the lyrics. A credit's profile
     * opens in the browser; there's no pop-up to ask first.
     */
    @Composable
    override fun Overlays(context: LyricsScreenContext) {
        if (!drawsOverlays) return
        // Settings and quick settings over the lyrics, as on the phone. Esc is their back ([DesktopBack]).
        if (overlays.settings || overlays.quickSettings) CompositionLocalProvider(
            LocalDensity provides (settingsHost.density ?: LocalDensity.current),
        ) {
            val state = settingsHost.rememberState()
            if (overlays.settings) SettingsScreen(
                state = state,
                host = settingsHost,
                settings = desktopSettings,
                backdrop = context.backdrop,
                contentPadding = context.padding,
                onClosed = { overlays.settings = false },
                onOpenLyricsManager = overlays::openLyricsManager,
                onPeekChange = { overlays.settingsPeeking = it },
            )
            QuickSettingsModal(
                visible = overlays.quickSettings,
                state = state,
                host = settingsHost,
                backdrop = context.backdrop,
                startOn = overlays.quickStart,
                onDismiss = { overlays.quickSettings = false },
            )
        }
        val options by lyricsActions.options.collectAsState()
        options.pendingConsent?.let { request ->
            Popup(
                alignment = Alignment.Center,
                onDismissRequest = { lyricsActions.answerConsent(request.id, false) },
                properties = PopupProperties(focusable = true),
            ) {
                Box(
                    Modifier.width(340.dp).shadow(24.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))
                        .background(SpicyColors.BgElevated).padding(vertical = 6.dp),
                ) {
                    ConsentPrompt(request, onAnswer = { allowed -> lyricsActions.answerConsent(request.id, allowed) })
                }
            }
        }

        val request = context.viewState.profileRequest
        val uriHandler = LocalUriHandler.current
        LaunchedEffect(request) {
            if (request == null) return@LaunchedEffect
            request.profileUrl?.let { url -> runCatching { uriHandler.openUri(url) } }
            context.viewState.dismissProfile()
        }
    }
}

/** Key presses in the window, which show the controls as a touch does. */
class DesktopKeys : ScreenKeys {
    override val presses = MutableStateFlow(0L)
    override var controlsShown: () -> Boolean = { true }

    fun press() {
        presses.value = monoMs()
    }
}

/** The screen's pop-ups on the desktop: settings and quick settings open over the lyrics as on the phone ([DesktopLyricsHost.Overlays]). */
class DesktopOverlays : LyricsOverlays() {
    override fun openSpotifySearch() = Unit

    /** Settings or quick settings are up, which take the keys from the lyrics behind them. */
    val open: Boolean get() = settings || quickSettings

    /**
     * For the window's `onPreviewKeyEvent`, so focused fields don't swallow them: Ctrl+, opens
     * settings and Ctrl+D quick settings, where the delay is (Cmd on a Mac).
     */
    fun onShortcut(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown || !(event.isCtrlPressed || event.isMetaPressed)) return false
        when (event.key) {
            Key.Comma -> openSettings()
            Key.D -> openQuickSettings(QuickScreen.Main)
            else -> return false
        }
        return true
    }
}

/**
 * How much bigger than the system's density the lyrics screen is drawn in a window [widthDp] by
 * [heightDp] (at the system's density): a small window grows until its short side is where a
 * phone's is held sideways ([PHONE_SHORT_SIDE_DP]), up to a large tablet's size
 * ([MAX_AUTO_SCALE]); a bigger window gets more room rather than bigger controls. Then times
 * [percent], the Interface Scale setting, which is the way to go bigger. In 10% steps.
 */
fun lyricsScreenScale(widthDp: Float, heightDp: Float, percent: Int): Float {
    val fit = (minOf(widthDp, heightDp) / PHONE_SHORT_SIDE_DP).coerceIn(1f, MAX_AUTO_SCALE)
    return ((fit * percent / 100f) * 10f).roundToInt().coerceAtLeast(1) / 10f
}

/** A phone's short side in dp, held sideways. */
const val PHONE_SHORT_SIDE_DP = 420f

/** The most the window's size alone scales the screen by: about a large tablet's. */
const val MAX_AUTO_SCALE = 1.5f

/**
 * How long a new scale waits for the window to stop changing size: every change of scale lays
 * every piece of text out again, so a drag keeps the scale it started with until it settles.
 */
const val SCALE_SETTLE_MS = 120L

/**
 * Shows [content] [ratio] times its size, filling the box it's in: laid out in a box [ratio] times
 * smaller, then drawn (and hit-tested) enlarged from the top left. While the window is dragged, a
 * new interface scale shows at once this way, the layout as it is; when the scale settles the
 * density takes it over and [ratio] is 1 again, the same size in pixels, so nothing jumps.
 */
@Composable
fun ScaledToWindow(ratio: Float, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().layout { measurable, constraints ->
            val width = (constraints.maxWidth / ratio).roundToInt()
            val height = (constraints.maxHeight / ratio).roundToInt()
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(constraints.maxWidth, constraints.maxHeight) {
                if (ratio == 1f) placeable.place(0, 0) else placeable.placeWithLayer(0, 0) {
                    scaleX = ratio
                    scaleY = ratio
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        },
    ) { content() }
}

/**
 * Composes [content] once per run, out of sight, then lets it go. The first time the window turns
 * between landscape and portrait, the screen composes the other layout's controls and stage for
 * the first time in the run: classes load and the code runs uncompiled, which held the window
 * still for a couple of seconds. Composed here first, at the window's size turned the other way,
 * that later turn finds it warm.
 *
 * That cost lands where it can't be seen: the first time the screen is [quiet] after its first
 * frame, which is while the first song's lyrics are still being looked up (or nothing is
 * playing), or failing that, the first pause. Never on a timer during playback.
 *
 * It's measured at that size, takes no room, sits outside the window (so no pointer reaches it)
 * and is hidden from accessibility.
 */
@Composable
fun WarmOtherLayout(widthDp: Dp, heightDp: Dp, quiet: Boolean, content: @Composable () -> Unit) {
    if (!DesktopSwitches.warmUp) return
    var composing by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(quiet, done) {
        if (!quiet || done || composing) return@LaunchedEffect
        // After the first frame is up, so the window shows before this work starts.
        withFrameNanos { }
        composing = true
    }
    // Once started it runs its course, even if the lyrics land meanwhile: the cost is already paid.
    LaunchedEffect(composing) {
        if (!composing) return@LaunchedEffect
        delay(WARM_UP_FOR_MS)
        composing = false
        done = true
    }
    if (!composing) return
    Box(
        Modifier
            .clearAndSetSemantics {}
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints.fixed(heightDp.roundToPx(), widthDp.roundToPx()))
                layout(0, 0) { placeable.place(-placeable.width - 1, 0) }
            },
    ) { content() }
}

/** How long it stays composed: long enough for its lyrics to lay out and a few frames to draw. */
const val WARM_UP_FOR_MS = 2_500L

/**
 * Switches for comparing on the installed app without a rebuild, set as JVM system properties (a
 * `java-options=-D...` line in the launcher's `.cfg`). The renderer has its own (`spicy.textBlur`,
 * `spicy.textMasks`).
 * - `spicy.warmUp=off`: no out-of-sight composition of the other layout.
 * - `spicy.glass=off`: the glass and pop-ups don't blur the page behind them.
 */
object DesktopSwitches {
    val warmUp: Boolean = System.getProperty("spicy.warmUp") != "off"
    val glass: Boolean = System.getProperty("spicy.glass") != "off"
    /** `spicy.progressiveBlur=on`: the controls' shade as Haze's progressive blur, as on the phone. */
    val progressiveBlur: Boolean = System.getProperty("spicy.progressiveBlur") == "on"
    /** `spicy.background=off`: a plain dark page instead of the chosen background. */
    val background: Boolean = System.getProperty("spicy.background") != "off"
}

/** The now-playing services on the desktop: the basic ones, without blur where [DesktopSwitches.glass] is off. */
val desktopNowPlayingPlatform: NowPlayingPlatform = object : BasicNowPlayingPlatform() {
    override val blursLayers: Boolean get() = DesktopSwitches.glass
    // Skia's own blur faded through a mask: Haze's progressive blur is a looped runtime shader
    // that Direct3D compiles for seconds on the CPU, the first time the shade shows (a portrait window).
    override val progressiveBlur: Boolean get() = DesktopSwitches.progressiveBlur
}
