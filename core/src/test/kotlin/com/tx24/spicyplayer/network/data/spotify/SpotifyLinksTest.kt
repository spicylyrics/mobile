package com.tx24.spicyplayer.network.data.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyLinksTest {
    private val id = "4uLU6hMCjMI75M1A2tKUQC"

    @Test fun readsEveryFormPeoplePaste() {
        assertEquals(id, SpotifyLinks.trackId(id))
        assertEquals(id, SpotifyLinks.trackId("  $id\n"))
        assertEquals(id, SpotifyLinks.trackId("spotify:track:$id"))
        assertEquals(id, SpotifyLinks.trackId("https://open.spotify.com/track/$id"))
        assertEquals(id, SpotifyLinks.trackId("https://open.spotify.com/track/$id?si=abc123"))
        assertEquals(id, SpotifyLinks.trackId("https://open.spotify.com/intl-de/track/$id?si=abc"))
    }

    @Test fun refusesAnythingElse() {
        assertNull(SpotifyLinks.trackId(null))
        assertNull(SpotifyLinks.trackId(""))
        assertNull(SpotifyLinks.trackId("Never Gonna Give You Up"))
        assertNull(SpotifyLinks.trackId("https://open.spotify.com/album/$id"))
        assertNull(SpotifyLinks.trackId("spotify:track:short"))
        assertNull(SpotifyLinks.trackId(id.dropLast(1) + "-"))
    }
}
