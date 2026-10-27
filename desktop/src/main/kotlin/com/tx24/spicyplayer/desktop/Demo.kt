package com.tx24.spicyplayer.desktop

import android.view.Display
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.canvas.LyricsView
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsCredit
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsFooter
import com.tx24.spicyplayer.lyrics.spicy.models.LyricsProvenance
import com.tx24.spicyplayer.lyrics.spicy.models.buildDisplayTimeline
import com.tx24.spicyplayer.lyrics.spicy.parser.TtmlLyricsParser
import java.awt.GraphicsEnvironment
import java.awt.Robot
import javax.imageio.ImageIO
import java.io.File
import kotlinx.coroutines.delay
import kotlin.system.exitProcess

/**
 * Usage: run --demo [--ttml file.ttml] [--seconds N] [--simple] [--apple] [--no-glow] [--maximized] [--no-vsync] [--no-kawarp]
 *   [--credits] [--size WxH] [--start-ms MS]
 *   [--snap file.png --snap-at S] [--record dir]
 *
 * Plays the lyrics on a fake clock from 0, looping, and prints frame-time stats every five
 * seconds. With --seconds it prints a summary and exits after that long; --snap saves what the
 * window shows S seconds in.
 */
fun runDemo(args: Array<String>) {
    fun flag(name: String) = name in args
    fun value(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }

    // Uncapped: frames as fast as the renderer can make them, to see its headroom.
    if (flag("--no-vsync")) System.setProperty("skiko.vsync.enabled", "false")
    val ttml = value("--ttml")?.let { File(it).readText() } ?: SyntheticSong.ttml()
    val parsed = TtmlLyricsParser.parse(ttml.byteInputStream())
    require(parsed.lines.isNotEmpty()) { "no lines parsed" }
    val type = parsed.type
    val lines = buildDisplayTimeline(parsed.lines, minimalMode = false)
    val songEndMs = lines.maxOf { it.endMs } + 4_000L
    val startOffsetMs = value("--start-ms")?.toLong() ?: 0L
    val (windowW, windowH) = value("--size")?.split("x")?.map(String::toInt) ?: listOf(1280, 800)
    // Community credits with names long enough to wrap in a narrow window.
    val footer = if (flag("--credits")) LyricsFooter(
        songwriters = listOf("Placeholder Writer", "Another Writer"),
        provenance = LyricsProvenance("Spicy Lyrics"),
        maker = LyricsCredit("averyverylongsyncmakername", "https://example.com/a", "https://example.com/a.png"),
        uploader = LyricsCredit("anotherlonguploadername", "https://example.com/b", "https://example.com/b.png"),
    ) else parsed.footer
    val runSeconds = value("--seconds")?.toInt()
    val config = RenderConfig(
        simpleLyricsMode = flag("--simple"),
        minimalLyricsMode = false,
        appleMusic = flag("--apple"),
        glowEnabled = !flag("--no-glow"),
    )
    val refreshRate = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.displayMode.refreshRate
        .takeIf { it > 0 }?.toFloat() ?: 60f
    println("lines=${lines.size} type=$type song=${songEndMs / 1000}s monitor=${refreshRate}Hz config=$config")

    val stats = FrameStats(refreshRate)
    val startNs = System.nanoTime()
    var lastReportNs = startNs
    val snap = value("--snap")?.let(::File)
    val snapAtMs = (value("--snap-at")?.toDouble() ?: 12.0).times(1000).toLong()

    application {
        val state = rememberWindowState(
            size = DpSize(windowW.dp, windowH.dp),
            placement = if (flag("--maximized")) WindowPlacement.Maximized else WindowPlacement.Floating,
        )
        var hud by remember { mutableStateOf("") }
        Window(onCloseRequest = { stats.printSummary(); exitApplication() }, state = state, title = "Spicy Lyrics renderer demo") {
            CompositionLocalProvider(LocalView provides View(Display(refreshRate))) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    if (flag("--no-kawarp")) Box(Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF3A1C5C), Color(0xFF0E2A47), Color(0xFF4A1530)))
                    )) else DesktopKawarp()
                    LyricsView(
                        lines = lines,
                        documentId = "spike",
                        currentTimeMs = { ((System.nanoTime() - startNs) / 1_000_000L + startOffsetMs) % songEndMs },
                        onSeekWord = {},
                        config = config,
                        lyricsType = type,
                        focusAnchorFraction = 0.5f,
                        restTopFraction = 0.25f,
                        footer = footer,
                        onFrameTick = { frameNs ->
                            stats.frame(frameNs)
                            val now = System.nanoTime()
                            if (now - lastReportNs >= 5_000_000_000L) {
                                lastReportNs = now
                                hud = stats.report(reset = true)
                                println(hud)
                            }
                        },
                    )
                    BasicText(hud, style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp),
                        modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
                }
            }
            if (snap != null) SnapAfter(window, snap, snapAtMs)
            value("--record")?.let { dir -> LaunchedEffect(Unit) {
                // Frames as PNGs every 50 ms, for finding what a single snap misses.
                window.isAlwaysOnTop = true
                val out = File(dir).apply { mkdirs() }
                val robot = Robot()
                var n = 0
                while (!window.contentPane.isShowing) delay(50)
                delay(1_000)
                while (true) {
                    val content = window.contentPane
                    val bounds = java.awt.Rectangle(content.locationOnScreen, content.size)
                    val t = (System.nanoTime() - startNs) / 1_000_000L + startOffsetMs
                    val image = robot.createScreenCapture(bounds)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { ImageIO.write(image, "png", File(out, "%05d_%06d.png".format(n++, t))) }
                    delay(50)
                }
            } }
            if (runSeconds != null) LaunchedEffect(Unit) {
                delay(runSeconds * 1000L)
                stats.printSummary()
                exitProcess(0)
            }
        }
    }
}

/** Saves what [window] shows [afterMs] in, for checking a change without touching the desktop. */
@androidx.compose.runtime.Composable
fun SnapAfter(window: java.awt.Window, file: File, afterMs: Long) = LaunchedEffect(Unit) {
    // The capture reads the screen, so nothing may cover the window.
    window.isAlwaysOnTop = true
    delay(afterMs)
    val content = (window as javax.swing.JFrame).contentPane
    val bounds = java.awt.Rectangle(content.locationOnScreen, content.size)
    ImageIO.write(Robot().createScreenCapture(bounds), "png", file)
    println("snap ${file.absolutePath} ${bounds.width}x${bounds.height}")
}
