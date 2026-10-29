package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.components.DesktopBack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tx24.spicyplayer.ui.bigscreen.BigScreenActions
import com.tx24.spicyplayer.ui.bigscreen.BigScreenPage
import com.tx24.spicyplayer.ui.bigscreen.BigScreenState
import com.tx24.spicyplayer.ui.bigscreen.LocalLyricsActions
import com.tx24.spicyplayer.ui.bigscreen.LocalTooltipHost
import com.tx24.spicyplayer.ui.bigscreen.ScrollToActiveButton
import com.tx24.spicyplayer.ui.bigscreen.ViewMode
import com.tx24.spicyplayer.ui.bigscreen.onPlaybackKey
import com.tx24.spicyplayer.ui.lyrics.LyricsOverlays
import com.tx24.spicyplayer.ui.nowplaying.LocalNowPlayingPlatform
import com.tx24.spicyplayer.ui.lyrics.LyricsScreen
import com.tx24.spicyplayer.ui.lyrics.LyricsScreenState
import com.tx24.spicyplayer.ui.bigscreen.bigScreenOptions
import com.tx24.spicyplayer.ui.bigscreen.pageKeyOf
import com.tx24.spicyplayer.ui.bigscreen.saveView
import com.tx24.spicyplayer.ui.bigscreen.withSavedView
import com.tx24.spicyplayer.ui.components.LocalPopupPalette
import com.tx24.spicyplayer.ui.components.LocalUiAnimations
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.canvas.LocalDisplayRefreshRate
import com.tx24.spicyplayer.lyrics.spicy.canvas.LocalLyricsImageLoader
import com.tx24.spicyplayer.lyrics.spicy.canvas.CjkFonts
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsLayoutCalculator
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsView
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsViewState
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedLyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsCredit
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsProvenance
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.models.buildDisplayTimeline
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.BackgroundSpeed
import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.platform.CoreLog
import com.tx24.spicyplayer.desktop.update.UpdateBanner
import com.tx24.spicyplayer.desktop.update.UpdateCheck

import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.translation.TranslationPresentation
import com.tx24.spicyplayer.translation.TranslationResult
import java.awt.GraphicsEnvironment
import com.tx24.spicyplayer.ui.settings.FileSettingsStore
import com.tx24.spicyplayer.ui.settings.InstanceLock
import com.tx24.spicyplayer.translation.romanizationCredit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import javax.swing.JOptionPane
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.skia.Image
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where Spotify's audio analyses are kept between runs. */
private val cacheDir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "Spicy Lyrics/cache")

/** Held from start to exit, so a second copy never writes the settings over this one's. */
private var instanceLock: InstanceLock? = null

/** Takes the settings folder's [InstanceLock], or tells the user a copy is already running and exits. */
private fun holdInstanceLock() {
    val dir = FileSettingsStore.appDataDir(System.getProperty("os.name").orEmpty(), System::getenv, System.getProperty("user.home"))
    when (val lock = InstanceLock.acquire(dir)) {
        InstanceLock.TakenByAnother -> {
            val message = "Spicy Lyrics is already running."
            System.err.println(message)
            if (!GraphicsEnvironment.isHeadless()) JOptionPane.showMessageDialog(null, message, "Spicy Lyrics", JOptionPane.INFORMATION_MESSAGE)
            exitProcess(0)
        }
        else -> instanceLock = lock
    }
}

/** What the window shows: nothing playing, a track being looked up, or its lyrics or notice. */
private sealed interface Screen {
    /** Nothing to show; [problem] says why, when the player can't be followed. */
    data class Waiting(val problem: String? = null) : Screen
    data class Track(val track: com.tx24.spicyplayer.desktop.Track, val lyrics: LyricsState) : Screen
}

/**
 * Usage: `run [--app <id substring>] [--offset <ms>] [--helper <gsmtc.exe on Windows>] [--page settings] [--snap file.png --snap-at S]`, or `run --demo ...` for the
 * renderer on a made-up song (see [runDemo]).
 *
 * Shows the lyrics of Windows' current media session (or the first whose app id contains
 * `--app`), word-synced on the session's position, `--offset` ms earlier.
 */
fun main(args: Array<String>) {
    if ("--demo" in args) return runDemo(args)
    holdInstanceLock()
    val opts = args.toList().zipWithNext().filter { it.first.startsWith("--") }.toMap()
    val source = mediaSource(opts["--helper"]?.let(::File))
    CoreLog.sink = { level, tag, message -> if (level >= CoreLog.Level.WARN) System.err.println("[$tag] $message") }

    val delays = SyncDelays(SyncDelays.defaultStore(), opts["--offset"]?.toLongOrNull())
    val session = MediaSession(opts["--app"], delays::offsetFor)
    val lyricsLibrary = DesktopLyricsLibrary(coverOf = { session.cover })
    val lookup = LyricsLookup(local = lyricsLibrary)
    // Settings → Sources: read before the lyrics start, and followed as the settings page changes it.
    val sourceSettings = DesktopSourceSettings(DesktopLyrics.defaultStore(), lookup.descriptors, onChange = lookup::useSources)
    lookup.useSources(sourceSettings.policy())
    val screen = MutableStateFlow<Screen>(Screen.Waiting())
    // Short notices for the lyrics screen's toast, such as the Lyrics Manager's.
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val playing = MutableStateFlow(false)
    val cover = MutableStateFlow<File?>(null)
    // The playing song's audio analysis, which the background's speed follows.
    val analysis = MutableStateFlow<AudioAnalysis?>(null)
    val spotify = SharedSpotify.extras(cacheDir)
    val backgroundImages = DesktopImageCache(File(cacheDir, "backgrounds"))
    // A folder of its own, so a pile of small pictures never pushes the backgrounds out.
    val avatars = DesktopAvatars(DesktopImageCache(File(cacheDir, "avatars"))::bytes)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var beats: Job? = null

    /** The background follows [track]'s beats: its audio analysis, where Spotify has one. */
    fun fetchBeats(track: Track) {
        beats?.cancel()
        beats = scope.launch {
            val local = LocalTrackMetadata(track.title, track.artist, track.album, track.durationMs)
            val found = spotify.trackIds(local, null).firstOrNull()?.let { spotify.audioAnalysis(it) }
            if (session.track?.key == track.key) analysis.value = found
        }
    }
    // Move with the Music turned on mid-song: the song playing gets its beats now, not the next one.
    scope.launch {
        snapshotFlow { desktopSettings.desktopBackground().beatReactive }.drop(1).collect { on ->
            if (on && analysis.value == null) session.track?.let(::fetchBeats)
        }
    }
    val nowPlaying = DesktopNowPlaying(source, session)
    // The menu drives the lyrics from the UI thread, so the media updates hand over to it too.
    val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val songLyrics = DesktopLyrics(lookup, GoogleSongTranslator(File(cacheDir, "translations")), desktopSettings, DesktopLyrics.defaultStore(), uiScope)
        .also { it.followSettings() }
    scope.launch {
        songLyrics.shown.collect { shown -> if (shown != null) screen.value = Screen.Track(shown.track, shown.lyrics) }
    }
    val capabilities = capabilitiesOf(source.capabilities, source.canShuffle, source.canRepeat)

    suspend fun lookUp(track: Track) = withContext(Dispatchers.Main) { songLyrics.load(track) }

    val updates = UpdateCheck.forThisApp()
    scope.launch { updates.run() }

    // Cancel first, so the collector doesn't start the source again once it's closed.
    fun stop() {
        scope.cancel()
        source.close()
    }
    Runtime.getRuntime().addShutdownHook(Thread(::stop))

    suspend fun onSnapshot(update: MediaUpdate.Snapshot, received: Received) {
        val event = session.feed(update.snapshot, received.receivedMono, received.receivedWall)
        playing.value = session.clock.isPlaying
        cover.value = session.cover
        nowPlaying.refresh()
        if (event is SessionEvent.NewTrack) {
            val track = event.track
            lookUp(track)
            analysis.value = null
            beats?.cancel()
            if (desktopSettings.desktopBackground().beatReactive) fetchBeats(track)
        }
    }

    /** The player is gone: back to waiting, with [problem] when it's the source that failed. */
    suspend fun forgetTrack(problem: String? = null) {
        session.clear()
        beats?.cancel()
        analysis.value = null
        playing.value = false
        cover.value = null
        withContext(Dispatchers.Main) { songLyrics.clear() }
        screen.value = Screen.Waiting(problem)
        nowPlaying.clear()
    }

    scope.launch {
        // A source that can't start or stops (the helper crashed, playerctl went away) is started
        // again, sooner after a run that worked.
        var failures = 0
        while (true) {
            val problem = source.unavailableReason ?: try {
                source.updates().collect { received ->
                    // Working again: the failure no longer applies.
                    if (failures > 0 && screen.value is Screen.Waiting) screen.value = Screen.Waiting()
                    failures = 0
                    when (val update = received.update) {
                        is MediaUpdate.Removed -> if (update.app == session.track?.app) forgetTrack()
                        is MediaUpdate.Snapshot -> onSnapshot(update, received)
                    }
                }
                "The ${source.name} media source stopped"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "The ${source.name} media source failed: ${e.message ?: e.javaClass.simpleName}"
            }
            System.err.println(problem)
            failures++
            forgetTrack(problem)
            delay(retryDelayMs(failures))
        }
    }

    application {
        // The screen the main window is on, again whenever it moves: the primary one may run at another rate.
        var refreshRate by remember { mutableStateOf(60f) }
        val windowState = rememberWindowState(size = DpSize(1100.dp, 760.dp))
        val mainLyricsView = remember { LyricsViewState() }
        val miniLyricsView = remember { LyricsViewState() }
        // Settings open over the lyrics, as on the phone; `--page settings` starts with them open.
        val overlays = remember { DesktopOverlays().also { it.settings = opts["--page"] == "settings" } }
        // A key on the lyrics shows their controls, as a touch does on the phone.
        val desktopKeys = remember { DesktopKeys() }
        var page by remember { mutableStateOf(BigScreenState(romanized = desktopSettings.romanize).withSavedView(desktopSettings)) }
        // The now-playing bar and the compact choice come back as they were left.
        LaunchedEffect(page.nowBarWanted, page.nowBarOnRight, page.compactOverride) { desktopSettings.saveView(page) }
        val lyricsFont = remember(desktopSettings.lyricsFont, desktopSettings.customFontFile) {
            desktopLyricsFont(desktopSettings.lyricsFont, desktopSettings.customFontFile)
        }
        SideEffect { LyricsLayoutCalculator.setFont(lyricsFont.family, lyricsFont.key) }
        val popupPalette = desktopSettings.popupTheme.palette
        // The page's romanize button and the Romanize setting are one switch.
        LaunchedEffect(page.romanized) { if (desktopSettings.romanize != page.romanized) desktopSettings.romanize = page.romanized }
        LaunchedEffect(desktopSettings.romanize) { if (page.romanized != desktopSettings.romanize) page = page.copy(romanized = desktopSettings.romanize) }
        LaunchedEffect(page.mode) {
            windowState.placement = if (page.mode == ViewMode.Fullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
        }
        Window(
            onCloseRequest = { stop(); exitApplication() },
            onPreviewKeyEvent = overlays::onShortcut,
            state = windowState,
            title = "Spicy Lyrics",
            icon = remember { BitmapPainter(Image.makeFromEncoded(object {}.javaClass.getResource("/icon.png")!!.readBytes()).toComposeImageBitmap()) },
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && !overlays.open) desktopKeys.press()
                MiniLyrics.onKey(event) || overlayKey(event, overlays) ||
                    pageKeyOf(event)?.let { page = page.onKey(it, capabilities); true } ?: onPlaybackKey(event, nowPlaying, capabilities)
            },
        ) {
            // Back from another window, the lyrics jump to the line being sung, wherever they were scrolled.
            OnWindowFocus(window) { mainLyricsView.scrollToActive() }
            LaunchedEffect(window) { DebugControl.startIfAsked(window) }
            DisposableEffect(window) {
                refreshRate = refreshRateOf(window)
                val listener = object : ComponentAdapter() {
                    override fun componentMoved(e: ComponentEvent?) { refreshRate = refreshRateOf(window) }
                }
                window.addComponentListener(listener)
                onDispose { window.removeComponentListener(listener) }
            }
            CompositionLocalProvider(
                LocalDisplayRefreshRate provides refreshRate,
                LocalLyricsImageLoader provides avatars,
                LocalTooltipHost provides DesktopTooltips,
                LocalLyricsActions provides songLyrics,
                LocalPopupPalette provides popupPalette,
                LocalUiAnimations provides (desktopSettings.uiAnimations && !desktopSettings.lowPerformance),
                LocalNowPlayingPlatform provides desktopNowPlayingPlatform,
            ) {
                val shown by screen.collectAsState()
                val translated by songLyrics.shown.collectAsState()
                val track = shown as? Screen.Track
                val lyricsState = track?.lyrics ?: LyricsState.Idle
                val noticeActions = remember {
                    desktopNoticeActions(
                        track = { session.track },
                        onRetry = { session.track?.let { songLyrics.load(it) } },
                        report = { session.track?.let { "Track: ${it.title} by ${it.artist}\nPlayer: ${it.app}" }.orEmpty() },
                    )
                }
                val settingsHost = remember(window) {
                    DesktopSettingsHost(
                        window, session, delays, sourceSettings, lyricsLibrary,
                        reloadLyrics = { songLyrics.shown.value?.track?.let(songLyrics::load) },
                        say = { toasts.tryEmit(it) },
                    )
                }
                fun desktopHost(drawsOverlays: Boolean) =
                    DesktopLyricsHost(
                        transport = nowPlaying,
                        lyricsActions = songLyrics,
                        overlays = overlays,
                        keys = desktopKeys,
                        positionMs = { session.clock.positionAt(monoMs()) },
                        seek = { session.seek(source, it) },
                        onResync = { session.resync(source) },
                        openMiniWindow = { MiniLyrics.open = true },
                        background = background@{ animate, modifier ->
                            if (!DesktopSwitches.background) {
                                Box(modifier.background(Color(0xFF121212)))
                                return@background
                            }
                            val type = desktopSettings.backgroundType
                            val backgroundPlaying by playing.collectAsState()
                            val backgroundCover by cover.collectAsState()
                            val backgroundTrack = (screen.collectAsState().value as? Screen.Track)?.track
                            val moving = desktopSettings.desktopBackground()
                            Box(modifier) {
                                DesktopBackgrounds(
                                    type = type,
                                    blurDp = desktopSettings.backgroundBlurFor(type),
                                    animate = moving.animate && animate,
                                    cover = backgroundCover,
                                    trackKey = backgroundTrack?.key,
                                    findArtistHeader = { session.track?.let { artistHeaderUrl(spotify, it) } },
                                    images = backgroundImages,
                                ) {
                                    DesktopKawarp(
                                        backgroundCover,
                                        backgroundPlaying,
                                        speed = {
                                            analysis.value?.let { BackgroundSpeed.at(session.clock.positionAt(monoMs()) / 1000f, it) }
                                        }.takeIf { moving.beatReactive },
                                        animate = moving.animate && animate,
                                        blurPasses = moving.blurPasses,
                                    )
                                }
                            }
                        },
                        settingsHost = settingsHost,
                        drawsOverlays = drawsOverlays,
                    )
                val host = remember { desktopHost(drawsOverlays = true) }
                val warmHost = remember { desktopHost(drawsOverlays = false) }
                val nowPlayingState by nowPlaying.state.collectAsState()
                val readyLyrics = lyricsState as? LyricsState.Ready
                val translation = translated?.takeIf { readyLyrics != null && it.lyrics === readyLyrics }?.translation
                val options by songLyrics.options.collectAsState()
                // Moving the mouse over the lyrics shows their controls, as a touch does on the phone.
                BoxWithConstraints(Modifier.fillMaxSize().pointerInput(desktopKeys) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type == PointerEventType.Move && !overlays.open) desktopKeys.moved()
                        }
                    }
                }) {
                    // Drawn at the size a phone held sideways would show it, whatever the window's size
                    // and the system's scaling, then sized by the Interface Scale setting.
                    val system = LocalDensity.current
                    SideEffect { settingsHost.density = Density(system.density * desktopSettings.interfaceScale / 100f, system.fontScale) }
                    val targetScale = lyricsScreenScale(maxWidth.value, maxHeight.value, desktopSettings.interfaceScale)
                    var scale by remember { mutableStateOf(targetScale) }
                    LaunchedEffect(targetScale) {
                        if (targetScale == scale) return@LaunchedEffect
                        delay(SCALE_SETTLE_MS)
                        scale = targetScale
                    }
                    // Until it settles, a new scale shows at once as a zoom of the current layout.
                    ScaledToWindow(targetScale / scale) {
                    CompositionLocalProvider(LocalDensity provides Density(system.density * scale, system.fontScale)) {
                    // The screen picks portrait or landscape from the window's size, as the phone does from its screen's.
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                    MaterialTheme(colorScheme = darkColorScheme()) {
                    val screenState = LyricsScreenState(
                        nowPlaying = nowPlayingState,
                        lyrics = lyricsState,
                        hasSongKey = track != null,
                        translation = translation,
                        translationEnabled = options.translating,
                        translating = options.translating && readyLyrics != null && translation == null,
                        translationHelps = readyLyrics != null,
                        waitingDetail = (shown as? Screen.Waiting)?.problem,
                    )
                    LyricsScreen(
                        state = screenState,
                        settings = desktopSettings,
                        host = host,
                        overlays = overlays,
                        noticeActions = noticeActions,
                        screenWidthDp = maxWidth.value.toInt(),
                        screenHeightDp = maxHeight.value.toInt(),
                        messages = toasts,
                        lyricsViewState = mainLyricsView,
                    )
                    // The other orientation's layout, composed once out of sight so the first turn isn't
                    // cold: while no lyrics are sweeping yet (still being looked up, or none), or paused.
                    WarmOtherLayout(maxWidth, maxHeight, quiet = readyLyrics == null || !nowPlayingState.isPlaying) {
                        LyricsScreen(
                            state = screenState,
                            settings = desktopSettings,
                            host = warmHost,
                            overlays = remember { LyricsOverlays() },
                            noticeActions = noticeActions,
                            screenWidthDp = maxHeight.value.toInt(),
                            screenHeightDp = maxWidth.value.toInt(),
                            messages = remember { emptyFlow<String>() },
                        )
                    }
                    }
                    }
                    }
                    }
                    UpdateBanner(updates.update, Modifier.align(Alignment.BottomCenter))
                }
            }
            opts["--snap"]?.let { SnapAfter(window, File(it), ((opts["--snap-at"]?.toDouble() ?: 8.0) * 1000).toLong()) }
        }

        // Ctrl+M: the lyrics in a small window that stays on top.
        val miniShown by screen.collectAsState()
        val miniTranslated by songLyrics.shown.collectAsState()
        val miniPlaying by playing.collectAsState()
        val miniTrack = miniShown as? Screen.Track
        CompositionLocalProvider(LocalPopupPalette provides popupPalette) {
        MiniLyricsWindow(
            nowPlaying,
            capabilities,
            miniTrack?.lyrics ?: LyricsState.Idle,
            onFocus = { miniLyricsView.scrollToActive() },
        ) { modifier, fontSizeScale ->
            val ready = miniTrack?.lyrics as? LyricsState.Ready
            if (miniTrack != null && ready != null) {
                val display = desktopSettings.desktopLyrics(ready, miniTrack.track.durationMs)
                // A window of its own: it doesn't see the main window's locals.
                CompositionLocalProvider(LocalDisplayRefreshRate provides refreshRate, LocalLyricsImageLoader provides avatars) {
                Lyrics(
                    lyrics = ready,
                    trackKey = miniTrack.track.key,
                    currentTimeMs = { session.clock.positionAt(monoMs()) },
                    onSeek = {},
                    isPlaying = miniPlaying,
                    modifier = modifier,
                    romanize = display.romanize,
                    activeLineTopPx = null,
                    config = display.config,
                    translation = miniTranslated?.takeIf { it.lyrics === ready }?.translation,
                    translationMode = desktopSettings.translationMode,
                    fontSizeScale = fontSizeScale,
                    romanizationMode = display.romanizationMode,
                    viewState = miniLyricsView,
                )
                }
            }
        }
        }
    }
}

/** The refresh rate of the screen [window] is on, or 60 Hz when it doesn't say. */
private fun refreshRateOf(window: java.awt.Window): Float =
    window.graphicsConfiguration?.device?.displayMode?.refreshRate?.takeIf { it > 0 }?.toFloat() ?: 60f

@Composable
private fun Lyrics(
    lyrics: LyricsState.Ready,
    trackKey: String,
    currentTimeMs: () -> Long,
    onSeek: (Long) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier,
    romanize: Boolean,
    activeLineTopPx: Float?,
    config: RenderConfig,
    translation: TranslationResult?,
    translationMode: TranslationMode,
    fontSizeScale: Float,
    romanizationMode: RomanizationMode,
    viewState: LyricsViewState? = null,
    /** Credits held at the bottom of the lyrics instead of scrolling after them. */
    pinnedFooter: PinnedFooterMode = PinnedFooterMode.Off,
    /** The button back to the active line while the lyrics are scrolled away (Scroll to Active Button). */
    scrollToActiveButton: Boolean = false,
) {
    val original = remember(lyrics.lines) {
        lyrics.lines.map { line ->
            Line(
                words = line.words.map { Word(it.text, it.startMs, it.endMs, isPartOfWord = it.attached, romanizedText = it.romanized) },
                startMs = line.startMs,
                endMs = line.endMs,
                agent = line.agent,
                role = line.role,
                groupId = line.groupId,
                oppositeAligned = line.oppositeAligned,
                lineTimed = line.lineTimed,
                untimed = line.untimed,
            )
        }
    }
    val lines = remember(original, config) {
        buildDisplayTimeline(original, minimalMode = config.isMinimal, holdThroughShortGaps = when (lyrics.lyricsType) {
            LyricsType.Syllable -> config.isMinimal
            LyricsType.Line -> config.isSimple
            else -> false
        }).also(CjkFonts::useFor)
    }
    val presentation = remember(original, lines, translation, translationMode) {
        translation?.let { TranslationPresentation.forTimeline(original, lines, it, translationMode) }
    }
    val viewState = remember { LyricsViewState() }
    var pinnedHeightPx by remember { mutableIntStateOf(0) }
    val pinnedGapPx = with(LocalDensity.current) { PINNED_FOOTER_GAP.toPx() }
    Box(modifier) {
    LyricsView(
        lines = lines,
        documentId = remember(trackKey, lyrics.lines) { "$trackKey:${lyrics.lines.hashCode()}" },
        currentTimeMs = currentTimeMs,
        onSeekWord = onSeek,
        modifier = Modifier.fillMaxSize(),
        viewState = viewState,
        pinnedFooter = pinnedFooter,
        // The lyrics fade out above the pinned credits.
        maskBottomPx = { if (pinnedHeightPx > 0) pinnedHeightPx + 2f * pinnedGapPx else 0f },
        romanize = romanize,
        romanizationMode = romanizationMode,
        translation = presentation,
        activeLineTopPx = activeLineTopPx,
        isPlaying = isPlaying,
        focusAnchorFraction = 0.5f,
        restTopFraction = 0.25f,
        lyricsType = lyrics.lyricsType,
        config = config,
        fontSizeScale = fontSizeScale,
        footer = run {
            // Who translated and romanized what's on screen, while the Text Credits setting asks.
            val textCredits = desktopSettings.textCredits
            // The desktop has no human (Genius) romanization: it is the lyrics' own or on-device.
            val romanizedFrom = remember(lyrics, romanize, textCredits) { romanizationCredit(lyrics, 0, romanize, textCredits) }
            remember(lyrics, translation, textCredits, romanizedFrom) {
                LyricsFooter(
                    songwriters = lyrics.songwriters,
                    translationCredit = translation?.credit()?.takeIf { textCredits },
                    romanizationCredit = romanizedFrom,
                    provenance = LyricsProvenance(lyrics.source ?: lyrics.provider),
                    maker = lyrics.maker?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                    uploader = lyrics.uploader?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                )
            }
        },
    )
    if (pinnedFooter != PinnedFooterMode.Off) PinnedLyricsFooter(
        state = viewState,
        mode = pinnedFooter,
        fontSizeScale = fontSizeScale,
        tappable = true,
        onHeight = { pinnedHeightPx = it },
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = PINNED_FOOTER_GAP),
    )
    ScrollToActiveButton(
        viewState, scrollToActiveButton,
        bottomPx = { if (pinnedHeightPx > 0) pinnedHeightPx + pinnedGapPx else 0f },
    )
    }
}

/** Between the pinned credits and the bottom of the lyrics. */
private val PINNED_FOOTER_GAP = 20.dp

/**
 * While settings or quick settings are over the lyrics, Esc steps back through them ([DesktopBack])
 * and every other key stops here, so the lyrics' keys don't act behind them.
 */
private fun overlayKey(event: KeyEvent, overlays: DesktopOverlays): Boolean {
    if (!overlays.open) return false
    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) DesktopBack.dispatch()
    return true
}
