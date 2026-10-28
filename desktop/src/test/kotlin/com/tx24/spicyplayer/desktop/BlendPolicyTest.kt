package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.network.data.LyricsCapability
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.RemoteLyricsPolicy
import com.tx24.spicyplayer.network.data.SourceRanking
import org.junit.Assert.assertEquals
import org.junit.Test

class BlendPolicyTest {
    private val descriptors = listOf("netease", "qq_music", "kugou").map {
        LyricsSourceDescriptor(it, it, 0, setOf(LyricsCapability.WORD_SYNC, LyricsCapability.LINE_SYNC))
    }
    private val plain = SourceRanking(descriptors, RemoteLyricsPolicy())

    @Test fun noBlendIsBuiltUntilSwitchedOn() {
        assertEquals(emptyList<String>(), plain.liveBlends.map { it.id })
        assertEquals(emptyList<String>(), SourceRanking(descriptors, blendPolicy(plain, emptySet())).liveBlends.map { it.id })
    }

    @Test fun onlyTheBlendsAskedForAreBuilt() {
        val policy = blendPolicy(plain, setOf("blend_netease_qq", "blend_kugou", "netease", "no_such_blend"))
        val ranking = SourceRanking(descriptors, policy)
        assertEquals(setOf("blend_netease_qq", "blend_kugou"), ranking.liveBlends.map { it.id }.toSet())
        // Only blend switches are touched: the providers keep their usual ones.
        assertEquals(2, policy.pairs.size)
        assertEquals(setOf(true), policy.pairs.values.toSet())
        assertEquals(true, ranking.asked("netease"))
    }

    @Test fun aBlendWhoseDonorIsMissingIsNotOffered() {
        val neteaseOnly = SourceRanking(descriptors.take(1), RemoteLyricsPolicy())
        val ranking = SourceRanking(descriptors.take(1), blendPolicy(neteaseOnly, setOf("blend_netease_qq", "blend_netease")))
        assertEquals(listOf("blend_netease"), ranking.liveBlends.map { it.id })
    }
}
