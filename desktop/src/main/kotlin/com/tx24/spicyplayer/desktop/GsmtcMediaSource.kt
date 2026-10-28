package com.tx24.spicyplayer.desktop

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Windows' media sessions (GSMTC), read through the gsmtc helper in `desktop/gsmtc`: it writes
 * one JSON line per session per change, and `gsmtc ctl <app> play|pause|next|previous|seek <ms>` drives one.
 */
class GsmtcMediaSource(val helper: File) : MediaSource {
    override val name = "gsmtc"
    override val unavailableReason
        get() = if (helper.exists()) null else "No media session helper at $helper; build it with `cargo build --release` in desktop/gsmtc"

    override val capabilities = Transport.entries.toSet()

    private val running = AtomicReference<Process?>()

    override fun updates(): Flow<Received> = flow {
        val process = ProcessBuilder(helper.path).redirectErrorStream(true).start()
        running.getAndSet(process)?.destroy()
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val receivedMono = monoMs()
                    val receivedWall = System.currentTimeMillis()
                    val update = parseGsmtcLine(raw) ?: continue
                    emit(Received(update, receivedMono, receivedWall, raw))
                }
            }
            System.err.println("media session helper exited (${process.waitFor()})")
        } finally {
            process.destroy()
            running.compareAndSet(process, null)
        }
    }.flowOn(Dispatchers.IO)

    override fun control(app: String, action: Transport, positionMs: Long) {
        ProcessBuilder(listOf(helper.path) + gsmtcCtlArgs(app, action, positionMs)).redirectErrorStream(true).start()
    }

    override fun close() {
        running.getAndSet(null)?.destroy()
    }
}

/** The helper's arguments for [action] on the session whose app id contains [app]. */
fun gsmtcCtlArgs(app: String, action: Transport, positionMs: Long): List<String> = when (action) {
    Transport.PLAY -> listOf("ctl", app, "play")
    Transport.PAUSE -> listOf("ctl", app, "pause")
    Transport.SEEK -> listOf("ctl", app, "seek", positionMs.coerceAtLeast(0L).toString())
    Transport.NEXT -> listOf("ctl", app, "next")
    Transport.PREVIOUS -> listOf("ctl", app, "previous")
}

/** One line of the helper's output, or null for one that isn't a session report (or isn't JSON). */
fun parseGsmtcLine(raw: String): MediaUpdate? =
    runCatching { JsonParser.parseString(raw) }.getOrNull()?.takeIf(JsonElement::isJsonObject)?.let { parseGsmtcJson(it.asJsonObject) }

/** [parseGsmtcLine] for a line already parsed, as Console's logs keep it. */
fun parseGsmtcJson(line: JsonObject): MediaUpdate? {
    val app = line.str("app")
    if (!line.has("status")) {
        return if (line.str("event") == "removed" && app.isNotEmpty()) MediaUpdate.Removed(app) else null
    }
    val durationMs = line.long("durationMs") ?: return null
    val positionMs = line.long("positionMs") ?: return null
    val stamp = line.long("lastUpdatedMs") ?: return null
    // Apple Music for Windows sends "artist — album" as the artist and no album.
    val (artist, album) = line.str("artist").let { raw ->
        if (line.str("album").isBlank() && " — " in raw) raw.substringBeforeLast(" — ") to raw.substringAfterLast(" — ")
        else raw to line.str("album")
    }
    val status = line.str("status")
    return MediaUpdate.Snapshot(
        MediaSnapshot(
            app = app,
            current = line.get("current")?.takeIf { it.isJsonPrimitive }?.asBoolean == true,
            title = line.str("title"),
            artist = artist,
            album = album,
            durationMs = durationMs,
            positionMs = positionMs,
            updatedAtWallMs = stamp,
            status = PlaybackStatus.entries.firstOrNull { it.name == status } ?: PlaybackStatus.Unknown,
            rate = line.get("rate")?.takeUnless { it.isJsonNull }?.let { runCatching { it.asFloat }.getOrNull() },
            cover = line.str("cover").takeIf(String::isNotBlank)?.let(::File),
            event = line.str("event"),
        ),
    )
}

/** The helper bundled with the installed app, or the checkout's cargo build under `:desktop:run`. */
fun defaultHelper(): File = System.getProperty("compose.application.resources.dir")?.let { File(it, "gsmtc.exe") }
    ?.takeIf(File::exists) ?: File(projectRoot, "desktop/gsmtc/target/release/gsmtc.exe")

internal fun JsonObject.str(name: String) = get(name)?.takeUnless { it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }.orEmpty()

private fun JsonObject.long(name: String) = get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }
