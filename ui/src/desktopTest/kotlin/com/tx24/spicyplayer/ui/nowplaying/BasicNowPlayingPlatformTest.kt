package com.tx24.spicyplayer.ui.nowplaying

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class BasicNowPlayingPlatformTest {
    @Test
    fun `without a platform of its own, nothing is forgotten and layers blur`() {
        val platform = BasicNowPlayingPlatform()
        runBlocking { platform.forgetMotionCover(MotionCoverQuery("Artist", "Album", "Title")) }
        assertTrue(platform.blursLayers)
    }
}
