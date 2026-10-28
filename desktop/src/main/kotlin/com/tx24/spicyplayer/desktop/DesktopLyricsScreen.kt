package com.tx24.spicyplayer.desktop

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

    // The phone's quick settings sheet comes to the desktop with the settings pages; until then
    // its button, and the queue and source buttons that open its screens, stay hidden.
    override val quickSettings: Boolean get() = false

    override val pictureInPicture: Boolean get() = true
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

/**
 * The screen's pop-ups on the desktop: settings and the Lyrics Manager are the window's own pages.
 * Quick settings has no desktop version yet ([DesktopLyricsHost.quickSettings] hides its buttons).
 */
class DesktopOverlays(private val nav: DesktopNav) : LyricsOverlays() {
    override fun openSettings() = nav.open(Destination.Settings)
    override fun openLyricsManager() = nav.open(Destination.LyricsManager)
    override fun openQuickSettings(start: QuickScreen) {
        if (start == QuickScreen.LyricsManager) nav.open(Destination.LyricsManager)
    }
    override fun openSpotifySearch() = Unit
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
const val SCALE_SETTLE_MS = 250L

/**
 * Composes [content] once, out of sight, a few seconds after the window opens, then lets it go.
 * The first time the window turns between landscape and portrait, the screen composes the other
 * layout's controls and stage for the first time in the run: classes load and the code runs
 * uncompiled, which held the window still for a couple of seconds. Composed here first, at the
 * window's size turned the other way, that later turn finds it warm. It's measured at that size,
 * takes no room, sits outside the window (so no pointer reaches it) and is hidden from
 * accessibility.
 */
@Composable
fun WarmOtherLayout(widthDp: Dp, heightDp: Dp, content: @Composable () -> Unit) {
    var composing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(WARM_UP_AFTER_MS)
        composing = true
        delay(WARM_UP_FOR_MS)
        composing = false
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

/** When the other layout is composed out of sight: after startup's own work has settled. */
const val WARM_UP_AFTER_MS = 3_000L

/** How long it stays composed: long enough for its lyrics to lay out and a few frames to draw. */
const val WARM_UP_FOR_MS = 2_500L
