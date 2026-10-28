package com.tx24.spicyplayer.desktop

import java.io.File
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Background pictures off the network (artist headers), kept on disk in [dir] for [IMAGE_TTL_MS].
 * A picture that can't be fetched comes from an older copy if there is one, else null.
 */
class DesktopImageCache(private val dir: File, private val client: OkHttpClient = OkHttpClient()) {
    /** The picture at [url]; blocks, so call it off the main thread. */
    fun bytes(url: String, now: Long = System.currentTimeMillis()): ByteArray? {
        val file = File(dir, imageCacheName(url))
        val cached = file.takeIf { it.isFile }
        if (cached != null && isFresh(cached.lastModified(), now)) runCatching { cached.readBytes() }.getOrNull()?.let { return it }
        val fetched = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body.bytes() else null }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        if (fetched == null) return cached?.let { runCatching { it.readBytes() }.getOrNull() }
        runCatching {
            dir.mkdirs()
            file.writeBytes(fetched)
        }
        return fetched
    }
}

/** A month: header photos rarely change, and a stale one is still the right artist. */
const val IMAGE_TTL_MS = 30L * 24 * 60 * 60 * 1000

/** The cache file for [url]: its SHA-1, so any URL makes a safe name. */
fun imageCacheName(url: String): String =
    MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".img"

/** Whether a copy written at [modifiedMs] is still good at [nowMs]; one from the future isn't. */
fun isFresh(modifiedMs: Long, nowMs: Long, ttlMs: Long = IMAGE_TTL_MS): Boolean = nowMs - modifiedMs in 0 until ttlMs
