package com.tx24.spicyplayer.desktop

import com.google.gson.Gson
import com.tx24.spicyplayer.lyrics.LyricsNotices
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.RemoteLyricsAdapter
import com.tx24.spicyplayer.network.data.LyricsLookupRequest
import com.tx24.spicyplayer.network.data.LyricsSource
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.ProviderResult
import com.tx24.spicyplayer.network.data.RemoteLyricsPolicy
import com.tx24.spicyplayer.network.data.SourcePair
import com.tx24.spicyplayer.network.data.SourceRanking
import com.tx24.spicyplayer.network.data.ProviderCooldownTracker
import com.tx24.spicyplayer.network.data.RemoteLyricsProvider
import com.tx24.spicyplayer.network.data.RemoteLyricsResolution
import com.tx24.spicyplayer.network.data.RemoteLyricsSource
import com.tx24.spicyplayer.network.data.providers.*
import com.tx24.spicyplayer.network.data.spotify.SharedSpotify
import com.tx24.spicyplayer.network.service.LyricsService
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** The Android backend's sources, minus its preferences and caches. */
class LyricsLookup(clientKey: String = spicyLyricsKey()) : SongLookup {
    private val providers = providers(clientKey)
    private val source = RemoteLyricsSource(providers, ProviderCooldownTracker())
    private val ranking = SourceRanking(providers.map { it.descriptor }, RemoteLyricsPolicy())

    override val sources: List<LyricsSourceDescriptor> = providers.map { it.descriptor }
        .filter { ranking.asked(it.id) }
        .sortedBy { ranking.bestPlace(it.id) ?: SourceRanking.AFTER_ALL }

    val keyHint = if (clientKey.isBlank()) "none" else "sl_pk_..." + clientKey.takeLast(4)

    /** The lyrics for [track] ready for the renderer, or the notice to show instead; with [romanize] off, only what the source romanized. */
    suspend fun find(track: Track, romanize: Boolean = true): Found = find(track, mutableMapOf(), null, romanize)

    /**
     * Like [find], keeping each source's answer in [known] (so a later call for the same song asks
     * no source twice), and with [sourceId] asking only that source.
     */
    override suspend fun find(track: Track, known: MutableMap<String, ProviderResult>, sourceId: String?): Found =
        find(track, known, sourceId, romanize = true)

    private suspend fun find(track: Track, known: MutableMap<String, ProviderResult>, sourceId: String?, romanize: Boolean): Found {
        val request = LyricsLookupRequest(track.artist, track.title, track.album, (track.durationMs / 1000).toInt())
        val policy = sourceId?.let(::only) ?: RemoteLyricsPolicy()
        val result = try {
            source.resolveLyrics(request, policy, known = known)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Found(LyricsNotices.lookupFailed(e), null)
        }
        if (result !is RemoteLyricsResolution.Found) {
            return Found(LyricsNotices.noLyrics(result.attempts) { id -> providers.firstOrNull { it.descriptor.id == id }?.descriptor?.displayName ?: id }, result)
        }
        val lyrics = runCatching { RemoteLyricsAdapter.render(result.selection, track.durationMs, romanize = romanize) }
            .getOrElse { LyricsNotices.renderFailed(result.selection.source.displayName, it) }
        return Found(lyrics, result)
    }

    /** Every source in its usual place, with only [sourceId]'s provider switched on. */
    private fun only(sourceId: String) = RemoteLyricsPolicy(
        sourceOrder = ranking.origins.map { it.id },
        pairs = ranking.origins.flatMap { origin ->
            origin.providerIds.map { SourcePair(origin.id, it).key to (it == sourceId) }
        }.toMap(),
    )

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

/** The publishable Spicy Lyrics key, from the checkout's `.env`, the environment, or the one built in. */
private fun spicyLyricsKey(): String = File(projectRoot, ".env").takeIf(File::exists)?.readLines()
    ?.firstOrNull { it.trim().startsWith("SPICY_LYRICS_CLIENT_KEY=") }?.substringAfter("=")?.trim()?.trim('"')
    ?.takeIf(String::isNotBlank)
    ?: System.getenv("SPICY_LYRICS_CLIENT_KEY")?.takeIf(String::isNotBlank)
    ?: LyricsLookup::class.java.getResource("/spicy-lyrics-key.txt")?.readText()?.trim().orEmpty()
