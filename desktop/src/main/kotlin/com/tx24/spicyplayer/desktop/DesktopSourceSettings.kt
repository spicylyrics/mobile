package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.network.data.LyricsOrigin
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.LyricsSourceSettings
import com.tx24.spicyplayer.network.data.RemoteLyricsPolicy
import com.tx24.spicyplayer.network.data.RemoteLyricsQuality
import com.tx24.spicyplayer.network.data.SourceDisclosure
import com.tx24.spicyplayer.network.data.SourceDisclosures
import com.tx24.spicyplayer.network.data.SourcePair
import com.tx24.spicyplayer.network.data.SourceRanking
import com.tx24.spicyplayer.ui.settings.SettingsStore

/**
 * The phone's Settings → Sources choices (the sources' order and switches, and the sync types
 * each may give) in [store], under the phone's own keys, for the [providers] there are.
 * [onChange] hears each new policy, for the lookup to use.
 */
class DesktopSourceSettings(
    private val store: SettingsStore,
    private val providers: Collection<LyricsSourceDescriptor>,
    var onChange: (RemoteLyricsPolicy) -> Unit = {},
) {
    /** The stored policy with every source in its place and every switch spelled out; the defaults when nothing is stored. */
    fun policy(): RemoteLyricsPolicy {
        val order = store.getString(SOURCE_ORDER) ?: return LyricsSourceSettings.normalized(RemoteLyricsPolicy(), providers)
        val pairs = store.getStringSet(PAIRS_OFF).orEmpty().associateWith { false } + store.getStringSet(PAIRS_ON).orEmpty().associateWith { true }
        val syncOff = store.getStringSet(SYNC_TYPES_OFF).orEmpty().mapNotNull { entry ->
            val pair = SourcePair.parse(entry) ?: return@mapNotNull null
            runCatching { pair.originId to RemoteLyricsQuality.valueOf(pair.providerId) }.getOrNull()
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
        return LyricsSourceSettings.normalized(
            RemoteLyricsPolicy(
                sourceOrder = order.split(',').filter(String::isNotBlank),
                disabledSourceIds = store.getStringSet(SOURCES_OFF).orEmpty(),
                pairs = pairs,
                syncTypesOff = syncOff,
            ),
            providers,
        )
    }

    /** The sources, in their order. */
    fun origins(): List<LyricsOrigin> = SourceRanking(providers, policy()).origins

    /** Whether source [originId] is on: switched on, with a provider in it switched on. */
    fun isOn(originId: String): Boolean = LyricsSourceSettings.isOn(SourceRanking(providers, policy()), originId)

    /**
     * What switching source [originId] on sends where, when it hasn't been agreed to yet; null
     * when it can go on straight away.
     */
    fun disclosureFor(originId: String): SourceDisclosure? {
        val disclosure = SourceDisclosures.forId(originId)
            ?: LyricsSourceSettings.providerToSwitchOn(SourceRanking(providers, policy()), originId)
                ?.let { id -> providers.firstOrNull { it.id == id } }?.let(SourceDisclosures::forSource)
        return disclosure?.takeIf { it.id !in disclosed() }
    }

    /** Remembers that [disclosure] was shown and agreed to, as the phone does. */
    fun agree(disclosure: SourceDisclosure) {
        store.putString(KEY_DISCLOSED, (disclosed() + disclosure.id).sorted().joinToString(","))
    }

    /** Kept as one comma-separated string, the way [DesktopLyrics] keeps the translation consents beside them. */
    private fun disclosed(): Set<String> = store.getString(KEY_DISCLOSED)?.split(',')?.filter(String::isNotBlank)?.toSet().orEmpty()

    fun setSourceEnabled(originId: String, enabled: Boolean) = save(LyricsSourceSettings.setSourceEnabled(policy(), providers, originId, enabled))

    private fun save(policy: RemoteLyricsPolicy) {
        val normalized = LyricsSourceSettings.normalized(policy, providers)
        store.putString(SOURCE_ORDER, normalized.sourceOrder.joinToString(","))
        store.putStringSet(SOURCES_OFF, normalized.disabledSourceIds)
        store.putStringSet(PAIRS_ON, normalized.pairs.filterValues { it }.keys)
        store.putStringSet(PAIRS_OFF, normalized.pairs.filterValues { !it }.keys)
        store.putStringSet(SYNC_TYPES_OFF, normalized.syncTypesOff.flatMapTo(mutableSetOf()) { (origin, types) -> types.map { "$origin/${it.name}" } })
        onChange(normalized)
    }

    companion object {
        // The phone's keys for the same choices.
        const val SOURCE_ORDER = "sourceOrder"
        const val SOURCES_OFF = "sourcesOff"
        const val PAIRS_ON = "pairsOn"
        const val PAIRS_OFF = "pairsOff"
        const val SYNC_TYPES_OFF = "syncTypesOff"
        /** The disclosures agreed to; the translation consents share it. */
        const val KEY_DISCLOSED = DesktopLyrics.KEY_DISCLOSED
    }
}
