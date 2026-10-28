package com.tx24.spicyplayer.ui.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.ui.bigscreen.BigScreenActions
import com.tx24.spicyplayer.ui.bigscreen.BigScreenPage
import com.tx24.spicyplayer.ui.bigscreen.BigScreenState
import com.tx24.spicyplayer.ui.bigscreen.LocalLyricsActions
import com.tx24.spicyplayer.ui.bigscreen.LyricsActions
import com.tx24.spicyplayer.ui.bigscreen.PlayerCapabilities

/**
 * What the big-screen page can offer on Android: the transport always goes to the player (it
 * says itself when it refuses a skip), seeking where the session allows it, the floating window
 * where the device has one, and no fullscreen, since the app already fills its window.
 */
fun androidCapabilities(canSeek: Boolean, pictureInPicture: Boolean): PlayerCapabilities = PlayerCapabilities(
    playPause = true,
    skipNext = true,
    skipPrevious = true,
    seek = canSeek,
    fullscreen = false,
    pictureInPicture = pictureInPicture,
)

/**
 * The big-screen page on a tablet, DeX or an unfolded foldable. The controls follow the phone's
 * own idle timer ([controlsShown]): every touch on the page counts ([onTouch]) without being
 * taken from what's under it. The romanize toggle writes to the setting ([onToggleRomanize]), so
 * [romanized] survives a fold or a resize. Back steps out of cinema before it leaves the app. The
 * More menu's lyrics choices go through [lyricsActions].
 */
@Composable
internal fun AndroidBigScreenPage(
    nowPlaying: NowPlaying,
    canSeek: Boolean,
    pictureInPicture: Boolean,
    lyrics: LyricsState,
    renderConfig: RenderConfig,
    noticeActions: NoticeActions?,
    controlsShown: Boolean,
    onTouch: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnterPip: () -> Unit,
    romanized: Boolean,
    onToggleRomanize: () -> Unit,
    background: @Composable () -> Unit,
    lyricsView: @Composable (modifier: Modifier, romanize: Boolean, activeLineTopPx: Float?, renderConfig: RenderConfig) -> Unit,
    lyricsActions: LyricsActions? = null,
) {
    var page by remember { mutableStateOf(BigScreenState()) }
    BackHandler(enabled = page.immersive) { page = page.back() }
    val currentRomanized by rememberUpdatedState(romanized)
    val toggleRomanize by rememberUpdatedState(onToggleRomanize)
    val openSettings by rememberUpdatedState(onOpenSettings)
    val enterPip by rememberUpdatedState(onEnterPip)
    val touch by rememberUpdatedState(onTouch)
    val actions = remember {
        BigScreenActions(
            onState = {
                if (it.romanized != currentRomanized) toggleRomanize()
                page = it
            },
            onOpenSettings = { openSettings() },
            onEnterPip = { enterPip() },
        )
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) touch()
                    }
                }
            },
    ) {
        CompositionLocalProvider(LocalLyricsActions provides lyricsActions) {
        BigScreenPage(
            state = page.copy(romanized = romanized),
            nowPlaying = nowPlaying,
            capabilities = androidCapabilities(canSeek, pictureInPicture),
            lyrics = lyrics,
            actions = actions,
            noticeActions = noticeActions,
            background = background,
            lyricsView = lyricsView,
            renderConfig = renderConfig,
            controlsShown = controlsShown,
        )
        }
    }
}
