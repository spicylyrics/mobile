package com.tx24.spicyplayer.desktop

import com.google.gson.Gson
import com.tx24.spicyplayer.lyrics.LyricsNotices
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.RemoteLyricsAdapter
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.LyricsSource
import com.tx24.spicyplayer.network.data.ProviderCooldownTracker
import com.tx24.spicyplayer.network.data.RemoteLyricsProvider
import com.tx24.spicyplayer.network.data.RemoteLyricsResolution
import com.tx24.spicyplayer.network.data.RemoteLyricsSource
import com.tx24.spicyplayer.network.data.providers.*
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.network.service.LyricsService
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** The Android backend's sources, minus its preferences and caches. */
class LyricsLookup(clientKey: String = spicyLyricsKey()) {
    private val providers = providers(clientKey)
    private val source = RemoteLyricsSource(providers, ProviderCooldownTracker())

    val keyHint = if (clientKey.isBlank()) "none" else "sl_pk_..." + clientKey.takeLast(4)

    /** The lyrics for [track] ready for the renderer, or the notice to show instead; with [romanize] off, only what the source romanized. */
    suspend fun find(track: Track, romanize: Boolean = true): Found {
        val request = LyricsLookupRequest(track.artist, track.title, track.album, (track.durationMs / 1000).toInt())
        val result = runCatching { source.resolveLyrics(request) }.getOrElse { return Found(LyricsNotices.lookupFailed(it), null) }
        if (result !is RemoteLyricsResolution.Found) {
            return Found(LyricsNotices.noLyrics(result.attempts) { id -> providers.firstOrNull { it.descriptor.id == id }?.descriptor?.displayName ?: id }, result)
        }
        val lyrics = runCatching { RemoteLyricsAdapter.render(result.selection, track.durationMs, romanize = romanize) }
            .getOrElse { LyricsNotices.renderFailed(result.selection.source.displayName, it) }
        return Found(lyrics, result)
    }

    data class Found(val lyrics: LyricsState, val resolution: RemoteLyricsResolution?)
}

private fun providers(clientKey: String): Set<RemoteLyricsProvider> {
    val client = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()
    val gson = Gson()
    val resolver = SharedSpotify.resolver
    val genius = GeniusSongPages(client, gson)
    val lrclib = Retrofit.Builder()
        .baseUrl(LyricsService.BASE_URL)
        .client(client.newBuilder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "Spicy Lyrics Desktop").build())
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

/** The publishable Spicy Lyrics key, from the checkout's `.env` or the environment. */
private fun spicyLyricsKey(): String = File(projectRoot, ".env").takeIf(File::exists)?.readLines()
    ?.firstOrNull { it.trim().startsWith("SPICY_LYRICS_CLIENT_KEY=") }?.substringAfter("=")?.trim()?.trim('"')
    ?.takeIf(String::isNotBlank)
    ?: System.getenv("SPICY_LYRICS_CLIENT_KEY").orEmpty()
