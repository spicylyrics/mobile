package com.tx24.spicyplayer.desktop

import com.google.gson.Gson
import com.tx24.spicyplayer.lyrics.LyricsNotices
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.RemoteLyricsAdapter
import com.tx24.spicyplayer.network.data.LyricsBlendDefinition
import com.tx24.spicyplayer.network.data.LyricsBlends
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
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** The Android backend's sources, minus its preferences and caches. */
class LyricsLookup(clientKey: String = spicyLyricsKey()) : SongLookup {
    private val providers = providers(clientKey)
    private val source = RemoteLyricsSource(providers, ProviderCooldownTracker())
    private val descriptors = providers.map { it.descriptor }

    @Volatile private var policy = RemoteLyricsPolicy()
    @Volatile private var ranking = SourceRanking(descriptors, policy)

    override val sources: List<LyricsSourceDescriptor>
        get() {
            val ranking = ranking
            val blends = ranking.liveBlends.map(LyricsBlendDefinition::descriptor)
            return (descriptors.filter { ranking.asked(it.id) } + blends)
                .sortedBy { ranking.bestPlace(it.id) ?: SourceRanking.AFTER_ALL }
        }

    override val blends: List<LyricsBlendDefinition> = LyricsBlends.ALL.filter { blend ->
        ranking.origins.any { blend.id in it.providerIds }
    }

    override fun useBlends(ids: Set<String>) {
        policy = blendPolicy(SourceRanking(descriptors, RemoteLyricsPolicy()), ids)
        ranking = SourceRanking(descriptors, policy)
    }

    val keyHint = if (clientKey.isBlank()) "none" else "sl_pk_..." + clientKey.takeLast(4)

    /** The lyrics for [track] ready for the renderer, or the notice to show instead; with [romanize] off, only what the source romanized. */
    suspend fun find(track: Track, romanize: Boolean = true): Found = find(track, mutableMapOf(), null, romanize)

    /**
     * Like [find], keeping each source's answer in [known] (so a later call for the same song asks
     * no source twice), with [sourceId] asking only that source, and with [spotifyId] matched to
     * that Spotify track instead of searching for one.
     */
    override suspend fun find(track: Track, known: MutableMap<String, ProviderResult>, sourceId: String?, spotifyId: String?): Found =
        find(track, known, sourceId, romanize = true, spotifyId = spotifyId)

    private suspend fun find(track: Track, known: MutableMap<String, ProviderResult>, sourceId: String?, romanize: Boolean, spotifyId: String? = null): Found {
        val request = LyricsLookupRequest(track.artist, track.title, track.album, (track.durationMs / 1000).toInt(), spotifyTrackId = spotifyId)
        val policy = sourceId?.let(::only) ?: this.policy
        val result = runCatching { source.resolveLyrics(request, policy, known = known) }.getOrElse { return Found(LyricsNotices.lookupFailed(it), null) }
        if (result !is RemoteLyricsResolution.Found) {
            return Found(LyricsNotices.noLyrics(result.attempts) { id -> providers.firstOrNull { it.descriptor.id == id }?.descriptor?.displayName ?: id }, result)
        }
        val lyrics = runCatching { RemoteLyricsAdapter.render(result.selection, track.durationMs, romanize = romanize) }
            .getOrElse { LyricsNotices.renderFailed(result.selection.source.displayName, it) }
        return Found(lyrics, result)
    }

    /**
     * Every source in its usual place, with only [sourceId]'s provider switched on; for a blend,
     * its donors too, since it is built from their answers.
     */
    private fun only(sourceId: String): RemoteLyricsPolicy {
        val ranking = ranking
        val on = setOf(sourceId) + LyricsBlends.byId(sourceId)?.donorIds.orEmpty()
        return RemoteLyricsPolicy(
            sourceOrder = ranking.origins.map { it.id },
            pairs = ranking.origins.flatMap { origin ->
                origin.providerIds.map { SourcePair(origin.id, it).key to (it in on) }
            }.toMap(),
        )
    }

    data class Found(val lyrics: LyricsState, val resolution: RemoteLyricsResolution?)
}

/**
 * The usual policy with the blends in [ids] switched on wherever [ranking] lists them (in the
 * services they draw on, just above their donors); every other blend stays off.
 */
fun blendPolicy(ranking: SourceRanking, ids: Set<String>): RemoteLyricsPolicy = RemoteLyricsPolicy(
    pairs = ranking.origins.flatMap { origin ->
        origin.providerIds.filter { it in ids && LyricsBlends.byId(it) != null }.map { SourcePair(origin.id, it).key to true }
    }.toMap(),
)

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
