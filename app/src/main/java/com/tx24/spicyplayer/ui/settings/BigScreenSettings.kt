package com.tx24.spicyplayer.ui.settings

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.PictureInPicture
import com.tx24.spicyplayer.haptics.HapticPlayer
import com.tx24.spicyplayer.haptics.MusicHapticsStyle
import com.tx24.spicyplayer.ui.nowplaying.StageArt
import com.tx24.spicyplayer.ui.settings.panel.PanelActions
import com.tx24.spicyplayer.ui.settings.panel.PanelGroup
import com.tx24.spicyplayer.ui.settings.panel.PanelRow
import com.tx24.spicyplayer.ui.settings.panel.SettingsPanel
import com.tx24.spicyplayer.ui.settings.panel.SliderSpec
import com.tx24.spicyplayer.ui.settings.panel.settingsPanelSections
import com.tx24.spicyplayer.ui.bigscreen.isBigScreenWindow
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.update.UpdateViewModel
import dev.chrisbanes.haze.HazeState

/**
 * Settings for the window's width: in a wide window (a tablet in landscape, an unfolded
 * foldable, a DeX window; not a phone on its side) the big-screen [SettingsPanel], else
 * [SettingsScreen] as on a phone.
 * Read again whenever the window is resized. The panel's Device tab opens [SettingsScreen] too,
 * for what only its pages hold (sources, delays, translation).
 */
@Composable
internal fun AdaptiveSettings(
    state: PlayerUiState,
    viewModel: ExternalPlaybackViewModel,
    updater: UpdateViewModel,
    settings: AppSettings,
    backdrop: HazeState?,
    contentPadding: PaddingValues,
    onClosed: () -> Unit,
    onOpenLyricsManager: () -> Unit = {},
    onOpenSpotifySearch: () -> Unit = {},
    onPeekChange: (Boolean) -> Unit = {},
    restorePages: List<SettingsPage> = emptyList(),
    onPagesChange: (List<SettingsPage>) -> Unit = {},
) {
    var allPages by remember { mutableStateOf(false) }
    val window = LocalConfiguration.current
    if (!allPages && isBigScreenWindow(window.screenWidthDp.toFloat(), window.screenHeightDp.toFloat())) {
        BigScreenSettings(settings, contentPadding, onClosed, onOpenAllPages = { allPages = true })
    } else {
        SettingsScreen(
            state = state,
            viewModel = viewModel,
            updater = updater,
            settings = settings,
            backdrop = backdrop,
            contentPadding = contentPadding,
            onClosed = onClosed,
            onOpenLyricsManager = onOpenLyricsManager,
            onOpenSpotifySearch = onOpenSpotifySearch,
            onPeekChange = onPeekChange,
            restorePages = restorePages,
            onPagesChange = onPagesChange,
        )
    }
}

@Composable
private fun BigScreenSettings(settings: AppSettings, contentPadding: PaddingValues, onClosed: () -> Unit, onOpenAllPages: () -> Unit) {
    val context = LocalContext.current
    val platform = rememberAndroidPlatformActions()
    val sections = remember(settings) {
        val device = bigScreenDeviceGroups(
            settings,
            pipSupported = PictureInPicture.supported(context),
            canPlayMusicHaptics = HapticPlayer(context).canPlayMusic,
            lockScreen = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1,
            onOpenAllPages = onOpenAllPages,
        )
        val actions = PanelActions(
            version = BuildConfig.VERSION_NAME,
            links = listOf("GitHub" to "https://github.com/spicylyrics/mobile"),
        )
        // The interface scale is the desktop window's; the tablet keeps the system's size.
        settingsPanelSections(settings, actions, device, hiddenRows = setOf("interfaceScale"))
    }
    CompositionLocalProvider(LocalPlatformActions provides platform) {
        // Back clears a search, then leaves a section for the list, and only then closes.
        SettingsPanel(
            sections, settings.settingsTheme, onClose = onClosed, modifier = Modifier.fillMaxSize().padding(contentPadding),
            systemBack = { BackHandler(onBack = it) },
            // A focused field raises the keyboard over half the panel; tap the search to type.
            autoFocusSearch = false,
        )
    }
}

/**
 * The panel's Device tab: what the phone's own pages hold that the shared tabs don't (the screen,
 * the floating window, haptics, floating buttons, moving covers), bound to the same settings.
 */
internal fun bigScreenDeviceGroups(
    s: AppSettings,
    pipSupported: Boolean,
    canPlayMusicHaptics: Boolean,
    lockScreen: Boolean,
    onOpenAllPages: () -> Unit,
): List<PanelGroup> {
    fun toggle(key: String, label: String, description: String, get: () -> Boolean, set: (Boolean) -> Unit, disabled: () -> String? = { null }) =
        PanelRow.Toggle(key, label, description, disabled = disabled, checked = get, onChange = set)

    val noPip: () -> String? = { if (pipSupported) null else "Not available on this device." }
    val pipLyrics: () -> String? = { noPip() ?: if (s.pipShowLyrics) null else "Turn on Show Lyrics to change this." }
    val noMusicHaptics: () -> String? = {
        if (!canPlayMusicHaptics) "Needs a vibration motor and Android 8 or newer."
        else if (!s.musicHaptics) "Turn on Haptics to the Music to change this."
        else null
    }
    return listOf(
        PanelGroup("Screen", listOfNotNull(
            toggle("highRefreshRate", "Smoother Motion", "Draw at your screen's full refresh rate instead of 60 Hz. Uses slightly more battery.",
                { s.highRefreshRate }, { s.highRefreshRate = it }),
            toggle("keepScreenOn", "Keep the Screen On", "Stop the screen from turning off while music plays.",
                { s.keepScreenOn }, { s.keepScreenOn = it }),
            if (lockScreen) toggle("showOverLockScreen", "Show Over Lock Screen",
                "The power button brings the lyrics back without unlocking. Settings still ask to unlock.",
                { s.showOverLockScreen }, { s.showOverLockScreen = it }) else null,
            PanelRow.Select(
                "systemBars", "System Bars", "Whether the status and navigation bars stay on screen over the lyrics.",
                options = SystemBarsMode.entries.map { it.name }, labels = SystemBarsMode.entries.map { it.label },
                value = { s.systemBars.name }, onChange = { s.systemBars = SystemBarsMode.valueOf(it) },
            ),
            toggle("ambientMode", "Ambient Mode", "Left untouched while music plays, dim to the lyrics and the song on black.",
                { s.ambientMode }, { s.ambientMode = it }),
            PanelRow.Select(
                "ambientDelay", "Dim After", "How long ambient mode waits after the last touch.",
                disabled = { if (s.ambientMode) null else "Turn on Ambient Mode to change this." },
                options = AmbientDelay.entries.map { it.name }, labels = AmbientDelay.entries.map { it.label },
                value = { s.ambientDelay.name }, onChange = { s.ambientDelay = AmbientDelay.valueOf(it) },
            ),
            PanelRow.Slider(
                "lyricsSizePortrait", "Lyrics Size in Portrait", "With the screen upright, against its default. Lyrics Size sets it on its side.",
                spec = SliderSpec(LYRICS_SIZE_RANGE.first, LYRICS_SIZE_RANGE.last, 5, default = 100, unit = "%"),
                value = { s.lyricsSizePortrait }, onChange = { s.lyricsSizePortrait = it },
            ),
        )),
        PanelGroup("Floating Lyrics", listOf(
            toggle("autoPip", "Floating Lyrics", "Leave the app while a song plays and the lyrics shrink into a small floating window.",
                { s.autoPip }, { s.autoPip = it }, noPip),
            PanelRow.Select(
                "pipShape", "Shape", "The floating window's width to height.",
                disabled = noPip,
                options = PipShape.entries.map { it.name }, labels = PipShape.entries.map { it.label },
                value = { s.pipShape.name }, onChange = { s.pipShape = PipShape.valueOf(it) },
            ),
            toggle("pipShowLyrics", "Show Lyrics", "Off shows the song instead: cover, title and artist.",
                { s.pipShowLyrics }, { s.pipShowLyrics = it }, noPip),
            PanelRow.Slider(
                "pipLyricsSize", "Floating Lyrics Size", "The floating window's own text size, apart from the full screen's.",
                disabled = pipLyrics,
                spec = SliderSpec(LYRICS_SIZE_RANGE.first, LYRICS_SIZE_RANGE.last, 5, default = 100, unit = "%"),
                value = { s.pipLyricsSize }, onChange = { s.pipLyricsSize = it },
            ),
            toggle("pipDock", "Song Bar", "Square and tall windows show the title, artist and timeline under the lyrics.",
                { s.pipDock }, { s.pipDock = it }, pipLyrics),
            toggle("pipDockCover", "Cover in the Song Bar", "A small cover beside the title and artist.",
                { s.pipDockCover }, { s.pipDockCover = it },
                { pipLyrics() ?: if (s.pipDock) null else "Turn on Song Bar to change this." }),
            PanelRow.Select(
                "pipWithoutLyrics", "Without Lyrics, Show", "What the window shows for a song that has no lyrics.",
                disabled = pipLyrics,
                options = PipWithoutLyrics.entries.map { it.name }, labels = PipWithoutLyrics.entries.map { it.label },
                value = { s.pipWithoutLyrics.name }, onChange = { s.pipWithoutLyrics = PipWithoutLyrics.valueOf(it) },
            ),
        )),
        PanelGroup("Haptics", listOf(
            toggle("touchHaptics", "Touch Haptics", "Tick on buttons, switches, sliders and gestures. Your device's touch feedback setting applies too.",
                { s.touchHaptics }, { s.touchHaptics = it }),
            toggle("musicHaptics", "Haptics to the Music",
                "Feel the song while the lyrics are on screen, with a swell into every drop. Works for songs found on Spotify.",
                { s.musicHaptics && canPlayMusicHaptics }, { s.musicHaptics = it },
                { if (canPlayMusicHaptics) null else "Needs a vibration motor and Android 8 or newer." }),
            PanelRow.Select(
                "musicHapticsStyle", "Music Haptics Follow",
                "The drums: kicks deep, snares crisp, and the notes where there are no drums. The beat: a steady pulse, firmer on each bar.",
                disabled = noMusicHaptics,
                options = MusicHapticsStyle.entries.map { it.name }, labels = MusicHapticsStyle.entries.map { it.label },
                value = { s.musicHapticsStyle.name }, onChange = { s.musicHapticsStyle = MusicHapticsStyle.valueOf(it) },
            ),
            PanelRow.Slider(
                "musicHapticsStrength", "Music Haptics Strength",
                "How strong the vibrations to the music are. Android's media vibration setting can also turn them off.",
                disabled = noMusicHaptics,
                spec = SliderSpec(25, 200, 25, default = 100, unit = "%"),
                value = { s.musicHapticsStrength }, onChange = { s.musicHapticsStrength = it },
            ),
        )),
        PanelGroup("Floating Buttons", listOf(
            toggle("playerButtons", "Player Buttons", "Show the extra buttons the player offers, like Like or Save. Shuffle and repeat always stay.",
                { s.playerButtons }, { s.playerButtons = it }),
            toggle("pipButton", "Floating Lyrics Button", "Show the button that shrinks the lyrics into a small floating window, over other apps.",
                { s.pipButton }, { s.pipButton = it }, noPip),
            toggle("lyricsManagerButton", "Lyrics Manager Button", "Show its own button for the Lyrics Manager. It's always in quick settings and This song.",
                { s.lyricsManagerButton }, { s.lyricsManagerButton = it }),
            toggle("queueButton", "Queue Button", "Show its own button for the player's queue, while it shares one. It's always in quick settings.",
                { s.queueButton }, { s.queueButton = it }),
            toggle("resyncButton", "Resync Button", "Show the button that snaps the lyrics back in time with the player.",
                { s.resyncButton }, { s.resyncButton = it }),
            toggle("expandButton", "Expand Button", "Show the button that opens the big cover. It stays while the cover is open, to get back.",
                { s.expandButton }, { s.expandButton = it }),
            toggle("quickSettingsButton", "Quick Settings Button", "Show the button that opens the delays, the Lyrics Manager and the queue without leaving the lyrics.",
                { s.quickSettingsButton }, { s.quickSettingsButton = it }),
            toggle("rotateButton", "Rotate Button", "Show the button that turns the lyrics to landscape and back, even with rotation locked.",
                { s.rotateButton }, { s.rotateButton = it }),
            toggle("sourceButton", "Lyrics Source Button", "Show its own button for choosing this song's lyrics source. It's always in quick settings.",
                { s.sourceButton }, { s.sourceButton = it }),
            toggle("romanizeButton", "Romanize Button", "Show the button that turns romanized lyrics on and off, where the song has them.",
                { s.romanizeButton }, { s.romanizeButton = it }),
            toggle("translateButton", "Translate Button", "Show the button that turns translated lyrics on and off.",
                { s.translateButton }, { s.translateButton = it }),
        )),
        PanelGroup("Moving Covers", listOf(
            toggle("animatedCover", "Animated Cover", "Play the album's animated cover from Apple Music in the song header, where it has one.",
                { s.animatedCover }, { s.animatedCover = it }),
            toggle("animatedBackground", "Animated Background", "Play the album's animated cover from Apple Music behind the lyrics, where it has one.",
                { s.animatedBackground }, { s.animatedBackground = it }),
            toggle("backgroundFollowsMotion", "Follow the Animated Cover", "The moving background follows the animated cover while the header plays one.",
                { s.backgroundFollowsMotion }, { s.backgroundFollowsMotion = it },
                { if (s.animatedCover) null else "Turn on Animated Cover to change this." }),
            toggle("canvasBackground", "Show Canvas Where Available",
                "Where the song has a Spotify Canvas, the background is that Canvas, blurred. Other songs keep your background.",
                { s.canvasBackground }, { s.canvasBackground = it }),
            toggle("bigCoverArts", "Canvas on the Big Cover", "In portrait, the song's Spotify Canvas fills the screen while the big cover is open.",
                { StageArt.Canvas in s.bigCoverArt }, { s.bigCoverArt = if (it) s.bigCoverArt + StageArt.Canvas else s.bigCoverArt - StageArt.Canvas }),
            toggle("canvasBackdrop", "Blurred Canvas Behind", "While the stage or the big cover shows a Canvas, the background is that Canvas, blurred.",
                { s.canvasBackdrop }, { s.canvasBackdrop = it }),
        )),
        PanelGroup("App", listOf(
            PanelRow.Select(
                "appIcon", "App Icon", "Choose the icon on your home screen. It changes once you leave the app.",
                options = AppIcon.entries.map { it.name }, labels = AppIcon.entries.map { it.label },
                value = { s.appIcon.name }, onChange = { s.appIcon = AppIcon.valueOf(it) },
            ),
            PanelRow.Button(
                "allSettingsPages", "All Settings",
                "Sources, delays, translation and the rest, in the full settings pages.",
                keywords = "sources delay translation romanization lyrics manager queue updates",
                text = "Open", onClick = onOpenAllPages,
            ),
        )),
    )
}
