package com.tx24.spicyplayer.desktop

import com.google.gson.JsonParser
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.network.data.RemoteLyricsResolution
import com.tx24.spicyplayer.platform.CoreLog
import java.io.File
import java.io.PrintWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

/**
 * Usage: `console [--app <id substring>] [--offset <ms>] [--log <file>] [--helper <gsmtc.exe>] [--replay <log>]`
 *
 * A measurement tool, not the app: follows Windows' current media session (or the first whose
 * app id contains `--app`), fetches its lyrics and prints the current line whenever it changes.
 * `--log` writes every raw helper line, stamped with when it arrived, for working out each
 * player's position error later. `--replay <log>` feeds such a log back through the clock at its
 * recorded times, without lyrics. `--offset` moves the lyrics that many ms earlier (later if negative).
 */
fun main(args: Array<String>) = runBlocking {
    // Windows' JVM writes stdout in the ANSI codepage, which has no CJK.
    System.setOut(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
    val opts = args.toList().zipWithNext().filter { it.first.startsWith("--") }.toMap()
    val helper = opts["--helper"]?.let(::File) ?: defaultHelper()
    val log = opts["--log"]?.let { PrintWriter(File(it).bufferedWriter(), true) }
    val replay = opts["--replay"]?.let(::File)
    CoreLog.sink = { level, tag, message -> if (level == CoreLog.Level.WARN) System.err.println("  [$tag] $message") }

    val lookup = LyricsLookup()
    val session = MediaSession(opts["--app"], opts["--offset"]?.toLongOrNull() ?: 0L)
    val clock = session.clock
    say("helper ${helper.path}, key ${lookup.keyHint}, log ${opts["--log"] ?: "off"}")

    val process = if (replay == null) ProcessBuilder(helper.path).redirectErrorStream(true).start() else null
    Runtime.getRuntime().addShutdownHook(Thread { process?.destroy(); log?.close() })

    var lines: List<TimedLine> = emptyList()
    var fetch: Job? = null
    var shownLine = -2

    // Prints the line under the clock whenever it changes.
    if (replay == null) launch(Dispatchers.Default) {
        while (isActive) {
            val pos = clock.positionAt(monoMs())
            val current = synchronized(clock) { lines }
            val lead = current.withIndex().filter { it.value.role == LineRole.LEAD }
            val at = lead.lastOrNull { it.value.startMs <= pos }
            val index = when {
                at == null -> -1
                pos > at.value.endMs && lead.none { it.index > at.index && it.value.startMs <= pos } -> -(at.index + 10)
                else -> at.index
            }
            if (current.isNotEmpty() && index != shownLine && clock.isPlaying) {
                shownLine = index
                val text = at?.takeIf { index >= 0 }?.value?.words?.joinToString("") { (if (it.attached) "" else " ") + it.text }?.trim()
                say("%8s  %s".format(fmt(pos), if (text != null) "L${at.index} @${fmt(at.value.startMs)}  $text" else "..."))
            }
            delay(10)
        }
    }

    withContext(Dispatchers.IO) {
        (replay?.bufferedReader() ?: process!!.inputStream.bufferedReader()).forEachLine { raw ->
            var receivedMono = monoMs()
            var receivedWall = System.currentTimeMillis()
            val line = if (replay != null) {
                val logged = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return@forEachLine
                receivedMono = logged["rxMono"].asLong
                receivedWall = logged["rx"].asLong
                logged["line"].asJsonObject
            } else {
                log?.println("""{"rx":$receivedWall,"rxMono":$receivedMono,"line":$raw}""")
                runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return@forEachLine
            }
            when (val event = session.feed(line, receivedMono, receivedWall)) {
                is SessionEvent.NewTrack -> {
                    val track = event.track
                    synchronized(clock) { lines = emptyList() }
                    shownLine = -2
                    say("== ${track.app}: ${track.artist} - ${track.title} (${fmt(track.durationMs)})")
                    fetch?.cancel()
                    if (replay != null) return@forEachLine
                    fetch = this@runBlocking.launch(Dispatchers.IO) {
                        val started = monoMs()
                        val (lyrics, resolution) = lookup.find(track, romanize = false)
                        if (lyrics is LyricsState.Ready && resolution is RemoteLyricsResolution.Found) {
                            synchronized(clock) { lines = lyrics.lines }
                            shownLine = -2
                            say("   lyrics: ${resolution.selection.source.displayName}, ${resolution.selection.quality}, ${lyrics.lines.size} lines, ${monoMs() - started} ms")
                        } else {
                            say("   no lyrics: " + ((lyrics as? LyricsState.Error)?.let { "${it.message} ${it.detail.orEmpty()}" } ?: lyrics))
                        }
                    }
                }
                is SessionEvent.Moved -> say(
                    "   ${event.event} ${event.status} pos=${fmt(event.positionMs)} age=${event.ageMs}ms " +
                        "clock=${fmt(event.clockBeforeMs)} drift=${event.outcome.driftMs} moved=${event.outcome.appliedMs}"
                )
                null -> Unit
            }
        }
    }
    say("helper exited (${process?.waitFor()})")
    coroutineContext.cancelChildren()
}

private fun fmt(ms: Long) = "%d:%02d.%03d".format(ms / 60_000, ms / 1000 % 60, ms % 1000)

private val stamp = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

private fun say(text: String) = synchronized(stamp) { println("${LocalTime.now().format(stamp)} $text") }
