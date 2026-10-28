package com.tx24.spicyplayer.desktop.update

import com.google.gson.Gson
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Looks for a newer desktop release on GitHub at most once a day, keeping the last answer in
 * [cacheDir] so a launch in between shows it without asking again. Offline or on any error it
 * shows what it last knew, or nothing.
 *
 * [fetch] returns the releases listing as JSON, or null when it can't be had; [now] is epoch ms.
 */
class UpdateCheck(
    private val running: String,
    private val cacheDir: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val fetch: () -> String? = ::fetchReleases,
) {
    private val found = MutableStateFlow<UpdateInfo?>(null)

    /** The release to offer, or null when the app is up to date (or can't tell). */
    val update: StateFlow<UpdateInfo?> = found.asStateFlow()

    /** Checks if the day's check is due, and publishes the answer to [update]. Blocks; call it off the UI thread. */
    fun run() {
        if (AppVersion.parse(running) == null) return
        val cache = File(cacheDir, CACHE_FILE)
        val cached = readCache(cache)
        var latest = cached?.latest
        if (checkDue(cached?.checkedAtMs, now())) {
            val json = runCatching(fetch).getOrNull()
            if (json != null) {
                latest = latestDesktopRelease(json)
                runCatching {
                    cacheDir.mkdirs()
                    cache.writeText(Gson().toJson(Cached(now(), latest)))
                }
            }
        }
        // A hand-edited cache can leave fields null behind Gson's back.
        found.value = runCatching { updateFor(running, latest) }.getOrNull()
    }

    private fun readCache(file: File): Cached? = runCatching { Gson().fromJson(file.readText(), Cached::class.java) }.getOrNull()

    /** What the cache file holds. */
    private data class Cached(val checkedAtMs: Long, val latest: UpdateInfo?)

    companion object {
        const val CACHE_FILE = "update-check.json"

        /** The check for the installed app: its version comes from the installer, so a run from the checkout is "dev". */
        fun forThisApp() = UpdateCheck(runningVersion(), configDir())
    }
}

const val DAY_MS = 24L * 60 * 60 * 1000

/** Whether a day has passed since [lastCheckedMs] (never checked, or a clock that went back, counts as due). */
fun checkDue(lastCheckedMs: Long?, nowMs: Long) = lastCheckedMs == null || nowMs - lastCheckedMs >= DAY_MS || nowMs < lastCheckedMs

/** The installed app's version, or "dev" when not run from an installed build. */
fun runningVersion(): String = System.getProperty("jpackage.app-version")?.takeIf(String::isNotBlank) ?: "dev"

/** Where the app keeps its settings: `%APPDATA%/Spicy Lyrics Desktop` on Windows, `~/.config/spicy-lyrics-desktop` elsewhere. */
fun configDir(
    osName: String = System.getProperty("os.name").orEmpty(),
    env: (String) -> String? = System::getenv,
    home: String = System.getProperty("user.home").orEmpty(),
): File {
    val appData = env("APPDATA")?.takeIf(String::isNotBlank)
    if (osName.startsWith("Windows", ignoreCase = true) && appData != null) return File(appData, "Spicy Lyrics Desktop")
    val config = env("XDG_CONFIG_HOME")?.takeIf(String::isNotBlank) ?: File(home, ".config").path
    return File(config, "spicy-lyrics-desktop")
}

const val RELEASES_URL = "https://api.github.com/repos/spicylyrics/mobile/releases?per_page=100"

/** The releases listing from GitHub, or null on any failure. */
fun fetchReleases(): String? = runCatching {
    val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    val request = Request.Builder().url(RELEASES_URL)
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "Spicy Lyrics Desktop")
        .build()
    client.newCall(request).execute().use { response -> if (response.isSuccessful) response.body.string() else null }
}.getOrNull()
