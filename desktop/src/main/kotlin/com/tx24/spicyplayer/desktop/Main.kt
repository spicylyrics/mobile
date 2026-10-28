package com.tx24.spicyplayer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.canvas.LocalDisplayRefreshRate
import com.tx24.spicyplayer.lyrics.spicy.canvas.LocalLyricsImageLoader
import com.tx24.spicyplayer.lyrics.spicy.canvas.CjkFonts
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsView
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsCredit
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsProvenance
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.models.buildDisplayTimeline
import com.tx24.spicyplayer.lyrics.spicy.canvas.kawarp.BackgroundSpeed
import com.tx24.spicyplayer.network.data.spotify.AudioAnalysis
import com.tx24.spicyplayer.network.data.spotify.LocalTrackMetadata
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.platform.CoreLog
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.Image
import kotlinx.coroutines.launch

/** The background speeds up and slows down with the song's tempo, loudness and beats. */
private const val BEAT_REACTIVE_BACKGROUND = true

/** Where Spotify's audio analyses are kept between runs. */
private val cacheDir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "Spicy Lyrics/cache")

/** What the window shows: nothing playing, a track being looked up, or its lyrics or notice. */
private sealed interface Screen {
    /** Nothing to show; [problem] says why, when the player can't be followed. */
    data class Waiting(val problem: String? = null) : Screen
    data class Track(val track: com.tx24.spicyplayer.desktop.Track, val lyrics: LyricsState) : Screen
}

/**
 * Usage: `run [--app <id substring>] [--offset <ms>] [--helper <gsmtc.exe on Windows>] [--snap file.png --snap-at S]`, or `run --demo ...` for the
 * renderer on a made-up song (see [runDemo]).
 *
 * Shows the lyrics of Windows' current media session (or the first whose app id contains
 * `--app`), word-synced on the session's position, `--offset` ms earlier.
 */
fun main(args: Array<String>) {
    if ("--demo" in args) return runDemo(args)
    val opts = args.toList().zipWithNext().filter { it.first.startsWith("--") }.toMap()
    val source = mediaSource(opts["--helper"]?.let(::File))
    CoreLog.sink = { level, tag, message -> if (level >= CoreLog.Level.WARN) System.err.println("[$tag] $message") }

    val session = MediaSession(opts["--app"], opts["--offset"]?.toLongOrNull() ?: 0L)
    val lookup = LyricsLookup()
    val screen = MutableStateFlow<Screen>(Screen.Waiting())
    val playing = MutableStateFlow(false)
    val cover = MutableStateFlow<File?>(null)
    // The playing song's audio analysis, which the background's speed follows.
    val analysis = MutableStateFlow<AudioAnalysis?>(null)
    val spotify = SharedSpotify.extras(cacheDir)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var fetch: Job? = null
    var beats: Job? = null

    // Cancel first, so the collector doesn't start the source again once it's closed.
    fun stop() {
        scope.cancel()
        source.close()
    }
    Runtime.getRuntime().addShutdownHook(Thread(::stop))

    fun onSnapshot(update: MediaUpdate.Snapshot, received: Received) {
        val event = session.feed(update.snapshot, received.receivedMono, received.receivedWall)
        playing.value = session.clock.isPlaying
        cover.value = session.cover
        if (event is SessionEvent.NewTrack) {
            val track = event.track
            screen.value = Screen.Track(track, LyricsState.Loading)
            fetch?.cancel()
            fetch = scope.launch {
                val found = lookup.find(track).lyrics
                if (session.track?.key == track.key) screen.value = Screen.Track(track, found)
            }
            analysis.value = null
            beats?.cancel()
            if (BEAT_REACTIVE_BACKGROUND) beats = scope.launch {
                val local = LocalTrackMetadata(track.title, track.artist, track.album, track.durationMs)
                val found = spotify.trackIds(local, null).firstOrNull()?.let { spotify.audioAnalysis(it) }
                if (session.track?.key == track.key) analysis.value = found
            }
        }
    }

    /** The player is gone: back to waiting, with [problem] when it's the source that failed. */
    fun forgetTrack(problem: String? = null) {
        session.clear()
        fetch?.cancel()
        beats?.cancel()
        analysis.value = null
        playing.value = false
        cover.value = null
        screen.value = Screen.Waiting(problem)
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
        Window(
            onCloseRequest = { stop(); exitApplication() },
            state = rememberWindowState(size = DpSize(1100.dp, 760.dp)),
            title = "Spicy Lyrics",
            icon = remember { BitmapPainter(Image.makeFromEncoded(object {}.javaClass.getResource("/icon.png")!!.readBytes()).toComposeImageBitmap()) },
        ) {
            // The screen the window is on, again whenever it moves: the primary one may run at another rate.
            var refreshRate by remember { mutableStateOf(refreshRateOf(window)) }
            DisposableEffect(window) {
                val listener = object : ComponentAdapter() {
                    override fun componentMoved(e: ComponentEvent?) { refreshRate = refreshRateOf(window) }
                }
                window.addComponentListener(listener)
                onDispose { window.removeComponentListener(listener) }
            }
            CompositionLocalProvider(LocalDisplayRefreshRate provides refreshRate, LocalLyricsImageLoader provides PlaceholderAvatars) {
                val shown by screen.collectAsState()
                val isPlaying by playing.collectAsState()
                val coverFile by cover.collectAsState()
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    DesktopKawarp(coverFile, isPlaying, speed = {
                        analysis.value?.let { BackgroundSpeed.at(session.clock.positionAt(monoMs()) / 1000f, it) }
                    })
                    when (val s = shown) {
                        is Screen.Waiting -> Notice(
                            "Waiting for a song",
                            s.problem ?: "Play something in Spotify or another player that shows in Windows' media controls.",
                        )
                        is Screen.Track -> {
                            when (val lyrics = s.lyrics) {
                                is LyricsState.Ready -> Lyrics(
                                    lyrics = lyrics,
                                    trackKey = s.track.key,
                                    currentTimeMs = { session.clock.positionAt(monoMs()) },
                                    onSeek = { session.seek(source, it) },
                                    isPlaying = isPlaying,
                                )
                                is LyricsState.Error -> Notice(lyrics.message, listOfNotNull(lyrics.body, lyrics.detail).joinToString("\n\n"))
                                else -> Unit
                            }
                            NowPlaying(s.track, Modifier.align(Alignment.TopStart))
                        }
                    }
                }
            }
            opts["--snap"]?.let { SnapAfter(window, File(it), ((opts["--snap-at"]?.toDouble() ?: 8.0) * 1000).toLong()) }
        }
    }
}

/** The refresh rate of the screen [window] is on, or 60 Hz when it doesn't say. */
private fun refreshRateOf(window: java.awt.Window): Float =
    window.graphicsConfiguration?.device?.displayMode?.refreshRate?.takeIf { it > 0 }?.toFloat() ?: 60f

@Composable
private fun Lyrics(lyrics: LyricsState.Ready, trackKey: String, currentTimeMs: () -> Long, onSeek: (Long) -> Unit, isPlaying: Boolean) {
    val config = RenderConfig.FULL
    val lines = remember(lyrics.lines) {
        val original = lyrics.lines.map { line ->
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
        buildDisplayTimeline(original, minimalMode = config.isMinimal, holdThroughShortGaps = when (lyrics.lyricsType) {
            LyricsType.Syllable -> config.isMinimal
            LyricsType.Line -> config.isSimple
            else -> false
        }).also(CjkFonts::useFor)
    }
    LyricsView(
        lines = lines,
        documentId = remember(trackKey, lyrics.lines) { "$trackKey:${lyrics.lines.hashCode()}" },
        currentTimeMs = currentTimeMs,
        onSeekWord = onSeek,
        isPlaying = isPlaying,
        focusAnchorFraction = 0.5f,
        restTopFraction = 0.25f,
        lyricsType = lyrics.lyricsType,
        config = config,
        footer = remember(lyrics) {
            LyricsFooter(
                songwriters = lyrics.songwriters,
                provenance = LyricsProvenance(lyrics.source ?: lyrics.provider),
                maker = lyrics.maker?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
                uploader = lyrics.uploader?.let { LyricsCredit(it.username, it.profileUrl, it.avatarUrl) },
            )
        },
    )
}

/** Title and artist, small, in the corner: the lyrics are the point. */
@Composable
private fun NowPlaying(track: Track, modifier: Modifier) {
    Column(modifier.padding(20.dp).widthIn(max = 420.dp)) {
        BasicText(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
        BasicText(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp))
    }
}

@Composable
private fun Notice(title: String, body: String) {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText(title, style = TextStyle(color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            if (body.isNotBlank()) BasicText(body, modifier = Modifier.padding(top = 12.dp),
                style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 15.sp, textAlign = TextAlign.Center))
        }
    }
}
