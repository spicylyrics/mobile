package com.tx24.spicyplayer.desktop

import android.view.Display
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
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
import com.google.gson.JsonParser
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsView
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsCredit
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsProvenance
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsType
import com.tx24.spicyplayer.lyrics.spicy.models.Word
import com.tx24.spicyplayer.lyrics.spicy.models.buildDisplayTimeline
import com.tx24.spicyplayer.platform.CoreLog
import java.awt.GraphicsEnvironment
import java.io.File
import kotlin.concurrent.thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** What the window shows: nothing playing, a track being looked up, or its lyrics or notice. */
private sealed interface Screen {
    data object Waiting : Screen
    data class Track(val track: com.tx24.spicyplayer.desktop.Track, val lyrics: LyricsState) : Screen
}

/**
 * Usage: `run [--app <id substring>] [--helper <gsmtc.exe>] [--snap file.png --snap-at S]`, or `run --demo ...` for the
 * renderer on a made-up song (see [runDemo]).
 *
 * Shows the lyrics of Windows' current media session (or the first whose app id contains
 * `--app`), word-synced on the session's position.
 */
fun main(args: Array<String>) {
    if ("--demo" in args) return runDemo(args)
    val opts = args.toList().zipWithNext().filter { it.first.startsWith("--") }.toMap()
    val helper = opts["--helper"]?.let(::File) ?: defaultHelper()
    require(helper.exists()) { "No media session helper at $helper; build it with `cargo build --release` in desktop/gsmtc" }
    CoreLog.sink = { level, tag, message -> if (level >= CoreLog.Level.WARN) System.err.println("[$tag] $message") }

    val session = MediaSession(opts["--app"])
    val lookup = LyricsLookup()
    val screen = MutableStateFlow<Screen>(Screen.Waiting)
    val playing = MutableStateFlow(false)
    val cover = MutableStateFlow<File?>(null)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var fetch: Job? = null

    val process = ProcessBuilder(helper.path).redirectErrorStream(true).start()
    Runtime.getRuntime().addShutdownHook(Thread { process.destroy() })
    thread(name = "media-session", isDaemon = true) {
        process.inputStream.bufferedReader().forEachLine { raw ->
            val receivedMono = monoMs()
            val receivedWall = System.currentTimeMillis()
            val line = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return@forEachLine
            val event = session.feed(line, receivedMono, receivedWall)
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
            }
        }
        screen.value = Screen.Waiting
        System.err.println("media session helper exited (${process.waitFor()})")
    }

    val refreshRate = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.refreshRate
        .takeIf { it > 0 }?.toFloat() ?: 60f
    application {
        Window(
            onCloseRequest = { process.destroy(); exitApplication() },
            state = rememberWindowState(size = DpSize(1100.dp, 760.dp)),
            title = "Spicy Lyrics",
        ) {
            CompositionLocalProvider(LocalView provides View(Display(refreshRate))) {
                val shown by screen.collectAsState()
                val isPlaying by playing.collectAsState()
                val coverFile by cover.collectAsState()
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    DesktopKawarp(coverFile, isPlaying)
                    when (val s = shown) {
                        Screen.Waiting -> Notice("Waiting for a song", "Play something in Spotify or another player that shows in Windows' media controls.")
                        is Screen.Track -> {
                            when (val lyrics = s.lyrics) {
                                is LyricsState.Ready -> Lyrics(
                                    lyrics = lyrics,
                                    trackKey = s.track.key,
                                    currentTimeMs = { session.clock.positionAt(monoMs()) },
                                    onSeek = { session.seek(helper, it) },
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
