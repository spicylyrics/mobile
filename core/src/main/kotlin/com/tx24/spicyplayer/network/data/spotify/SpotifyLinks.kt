package com.tx24.spicyplayer.network.data.spotify

/** Spotify track links as people paste them. */
object SpotifyLinks {
    /**
     * The track ID in [input]: a `spotify:track:` URI, an `open.spotify.com/track/` link (with or
     * without its `?si=` tail or a locale path before it), or the bare 22-character ID. Null for
     * anything else.
     */
    fun trackId(input: String?): String? {
        val value = input?.trim().orEmpty()
        val candidate = when {
            value.startsWith("spotify:track:") -> value.substringAfterLast(':')
            "/track/" in value -> value.substringAfter("/track/").substringBefore('?').substringBefore('/')
            else -> value
        }
        return candidate.takeIf { it.length == 22 && it.all(Char::isLetterOrDigit) }
    }
}
