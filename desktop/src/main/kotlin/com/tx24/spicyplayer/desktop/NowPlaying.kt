package com.tx24.spicyplayer.desktop

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tx24.spicyplayer.lyrics.RemoteLyricsAdapter
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.spicy.models.LineRole
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.LyricsSource
import com.tx24.spicyplayer.network.data.ProviderCooldownTracker
import com.tx24.spicyplayer.network.data.RemoteLyricsResolution
import com.tx24.spicyplayer.network.data.RemoteLyricsSource
import com.tx24.spicyplayer.network.data.providers.*
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.network.service.LyricsService
import com.tx24.spicyplayer.platform.CoreLog
import com.tx24.spicyplayer.playback.LyricClock
import java.io.File
import java.io.PrintWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Usage: `desktop [--app <id substring>] [--log <file>] [--helper <gsmtc.exe>]`
 *
 * Follows Windows' current media session (or the first whose app id contains `--app`), fetches its
 * lyrics through :core and prints the current line whenever it changes. `--log` writes every raw
 * helper line, stamped with when it arrived, for working out each player's position error later.
 * `--replay <log>` feeds such a log back through the clock at its recorded times, without lyrics.
 */
fun main(args: Array<String>) = runBlocking {
    // Windows' JVM writes stdout in the ANSI codepage, which has no CJK.
    System.setOut(java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"))
    val opts = args.toList().zipWithNext().filter { it.first.startsWith("--") }.toMap()
    val root = generateSequence(File("").absoluteFile) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").exists() }
        ?: File("").absoluteFile
    val helper = File(opts["--helper"] ?: File(root, "desktop/gsmtc/target/release/gsmtc.exe").path)
    val log = opts["--log"]?.let { PrintWriter(File(it).bufferedWriter(), true) }
    val appFilter = opts["--app"]
    val replay = opts["--replay"]?.let(::File)
    CoreLog.sink = { level, tag, message -> if (level == CoreLog.Level.WARN) System.err.println("  [$tag] $message") }

    val key = envValue(File(root, ".env"), "SPICY_LYRICS_CLIENT_KEY") ?: System.getenv("SPICY_LYRICS_CLIENT_KEY").orEmpty()
    val source = RemoteLyricsSource(providers(key), ProviderCooldownTracker())
    say("helper ${helper.path}, key ${if (key.isBlank()) "none" else "sl_pk_..." + key.takeLast(4)}, log ${opts["--log"] ?: "off"}")

    val process = if (replay == null) ProcessBuilder(helper.path).redirectErrorStream(true).start() else null
    Runtime.getRuntime().addShutdownHook(Thread { process?.destroy(); log?.close() })

    val clock = LyricClock(mono())
    var trackKey: String? = null
    var lines: List<TimedLine> = emptyList()
    var fetch: Job? = null
    var shownLine = -2
    var lastStampMs = 0L
    var lastStatus = ""
    /** Whether the status changed since the stamp did: every later report with that stamp is stale. */
    var stampOutlived = false

    // Prints the line under the clock whenever it changes.
    if (replay == null) launch(Dispatchers.Default) {
        while (isActive) {
            val now = mono()
            val pos = clock.positionAt(now)
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
            var receivedMono = mono()
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
            if (!line.has("status")) return@forEachLine
            val app = line.str("app")
            val picked = if (appFilter != null) app.contains(appFilter, ignoreCase = true) else line["current"]?.asBoolean == true
            if (!picked) return@forEachLine

            val title = line.str("title")
            // Apple Music for Windows sends "artist — album" as the artist and no album.
            val (artist, album) = line.str("artist").let { raw ->
                if (line.str("album").isBlank() && " — " in raw) raw.substringBeforeLast(" — ") to raw.substringAfterLast(" — ")
                else raw to line.str("album")
            }
            val status = line.str("status")
            val playing = status == "Playing"
            // GSMTC stamps the position in wall time; the clock runs on the monotonic one.
            var updatedAtMono = receivedMono - (receivedWall - line["lastUpdatedMs"].asLong)
            var positionMs = line["positionMs"].asLong
            // A status change can arrive a few ms before its timeline, carrying the old stamp: a
            // resume would count on through the whole pause, a pause would stop at the last
            // report. Until the timeline comes, a resume starts from where the clock stopped and
            // a pause stops the clock where it is.
            // Chrome resumes under its pause's stamp too, so once the status has changed under a
            // stamp, nothing more with that stamp is trusted.
            val stamp = line["lastUpdatedMs"].asLong
            stampOutlived = stamp == lastStampMs && (stampOutlived || status != lastStatus)
            val staleStamp = stampOutlived
            lastStampMs = stamp
            lastStatus = status
            if (staleStamp && trackKey == "$title\u001f$artist") {
                positionMs = clock.positionAt(receivedMono)
                updatedAtMono = receivedMono
            }
            val report = LyricClock.Report(
                state = when (status) { "Playing" -> 3; "Paused" -> 2; "Stopped" -> 1; else -> 0 },
                playing = playing,
                positionMs = positionMs,
                updatedAtMs = updatedAtMono,
                speed = if (playing) (line["rate"]?.takeUnless { it.isJsonNull }?.asFloat ?: 1f) else 0f,
            )
            val newTrack = "$title\u001f$artist"
            if (newTrack != trackKey && title.isNotBlank()) {
                trackKey = newTrack
                synchronized(clock) { lines = emptyList() }
                shownLine = -2
                clock.startTrack(receivedMono, report.speed, playing)
                clock.onReport(report, receivedMono, snap = true)
                say("== $app: $artist - $title (${fmt(line["durationMs"].asLong)})")
                fetch?.cancel()
                if (replay != null) return@forEachLine
                val request = LyricsLookupRequest(artist, title, album, (line["durationMs"].asLong / 1000).toInt())
                val durationMs = line["durationMs"].asLong
                fetch = this@runBlocking.launch(Dispatchers.IO) {
                    val started = mono()
                    val result = runCatching { source.resolveLyrics(request) }.getOrElse { say("   lookup failed: $it"); return@launch }
                    if (result is RemoteLyricsResolution.Found) {
                        val ready = RemoteLyricsAdapter.render(result.selection, durationMs, romanize = false)
                        synchronized(clock) { lines = ready.lines }
                        shownLine = -2
                        say("   lyrics: ${result.selection.source.displayName}, ${result.selection.quality}, ${ready.lines.size} lines, ${mono() - started} ms")
                    } else {
                        say("   no lyrics (${result::class.simpleName}): " + result.attempts.joinToString { "${it.sourceId}=${it.outcome}" })
                    }
                }
                return@forEachLine
            }
            if (!clock.isFresh(report)) return@forEachLine
            val before = clock.positionAt(receivedMono)
            val outcome = clock.onReport(report, receivedMono)
            val age = receivedWall - line["lastUpdatedMs"].asLong
            say("   ${line.str("event")} $status pos=${fmt(report.positionMs)} age=${age}ms clock=${fmt(before)} drift=${outcome.driftMs} moved=${outcome.appliedMs}")
        }
    }
    say("helper exited (${process?.waitFor()})")
    coroutineContext.cancelChildren()
}

/** The providers the Android backend builds, minus its preferences and caches. */
private fun providers(clientKey: String): Set<com.tx24.spicyplayer.network.data.RemoteLyricsProvider> {
    val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    val gson = Gson()
    val resolver = SharedSpotify.resolver
    val genius = GeniusSongPages(client, gson)
    val lrclib = Retrofit.Builder()
        .baseUrl(LyricsService.BASE_URL)
        .client(client.newBuilder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Spicy Lyrics Desktop test").build())
        }.build())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(LyricsService::class.java)
    return setOf(
        SpicyLyricsProvider(client, gson, resolver, clientKey),
        AmllLyricsProvider(client, gson),
        UnisonLyricsProvider(client, gson),
        SpicyUnisonLyricsProvider(client, gson, resolver),
        RmmRevivalLyricsProvider(client, gson),
        LrcRedLyricsProvider(client, gson, java.util.concurrent.ConcurrentHashMap()),
        KugouLyricsProvider(client, gson),
        QqMusicLyricsProvider(client, gson),
        KuwoLyricsProvider(client),
        NetEaseLyricsProvider(client, gson),
        LyricsSource(lrclib),
        LrcMuxLyricsProvider(client, gson),
        GeniusLyricsProvider(genius),
        YouTubeTranscriptLyricsProvider(client, gson),
    )
}

private fun envValue(file: File, name: String): String? = file.takeIf(File::exists)?.readLines()
    ?.firstOrNull { it.trim().startsWith("$name=") }?.substringAfter("=")?.trim()?.trim('"')?.takeIf(String::isNotBlank)

private fun JsonObject.str(name: String) = get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

private fun mono() = System.nanoTime() / 1_000_000

private fun fmt(ms: Long) = "%d:%02d.%03d".format(ms / 60_000, ms / 1000 % 60, ms % 1000)

private val stamp = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

private fun say(text: String) = synchronized(stamp) { println("${LocalTime.now().format(stamp)} $text") }
