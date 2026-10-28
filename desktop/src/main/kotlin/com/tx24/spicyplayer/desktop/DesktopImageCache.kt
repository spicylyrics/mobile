package com.tx24.spicyplayer.desktop

import java.io.File
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Background pictures off the network (artist headers), kept on disk in [dir]. A copy is good for
 * [IMAGE_TTL_MS] since it was fetched or last shown; a picture that can't be fetched comes from an
 * older copy if there is one, else null. After each download the folder is trimmed to
 * [MAX_CACHE_BYTES] and [MAX_CACHE_FILES], least recently used first ([filesToPrune]).
 */
class DesktopImageCache(private val dir: File, private val client: OkHttpClient = OkHttpClient()) {
    /** The picture at [url]; blocks, so call it off the main thread. */
    fun bytes(url: String, now: Long = System.currentTimeMillis()): ByteArray? {
        val file = File(dir, imageCacheName(url))
        val cached = file.takeIf { it.isFile }
        if (cached != null && isFresh(cached.lastModified(), now)) {
            runCatching { cached.readBytes() }.getOrNull()?.let {
                cached.setLastModified(now)
                return it
            }
        }
        val fetched = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body.bytes() else null }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
        // Offline, the stale copy stands in. It isn't touched: that would make it fresh, and it
        // wouldn't be fetched again once back online.
        if (fetched == null) return cached?.let { runCatching { it.readBytes() }.getOrNull() }
        runCatching {
            dir.mkdirs()
            file.writeBytes(fetched)
            prune()
        }
        return fetched
    }

    private fun prune() {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".img") }.orEmpty()
        val doomed = filesToPrune(files.map { CacheEntry(it.name, it.length(), it.lastModified()) }).toSet()
        files.filter { it.name in doomed }.forEach { it.delete() }
    }
}

/** A month: header photos rarely change, and a stale one is still the right artist. */
const val IMAGE_TTL_MS = 30L * 24 * 60 * 60 * 1000

/** The most the background cache keeps on disk. */
const val MAX_CACHE_BYTES = 100L * 1024 * 1024
const val MAX_CACHE_FILES = 500

/** A cached picture's file name, size and when it was last fetched or shown. */
data class CacheEntry(val name: String, val sizeBytes: Long, val modifiedMs: Long)

/**
 * The files to delete so the rest fit in [maxBytes] and [maxFiles]: the least recently used ones,
 * oldest first, until both hold.
 */
fun filesToPrune(entries: List<CacheEntry>, maxBytes: Long = MAX_CACHE_BYTES, maxFiles: Int = MAX_CACHE_FILES): List<String> {
    var total = entries.sumOf { it.sizeBytes }
    var count = entries.size
    val doomed = mutableListOf<String>()
    for (entry in entries.sortedBy { it.modifiedMs }) {
        if (total <= maxBytes && count <= maxFiles) break
        doomed += entry.name
        total -= entry.sizeBytes
        count--
    }
    return doomed
}

/** The cache file for [url]: its SHA-1, so any URL makes a safe name. */
fun imageCacheName(url: String): String =
    MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".img"

/** Whether a copy fetched or shown at [modifiedMs] is still good at [nowMs]; one from the future isn't. */
fun isFresh(modifiedMs: Long, nowMs: Long, ttlMs: Long = IMAGE_TTL_MS): Boolean = nowMs - modifiedMs in 0 until ttlMs
