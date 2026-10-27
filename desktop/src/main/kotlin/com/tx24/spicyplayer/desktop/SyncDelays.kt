package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.ui.settings.SettingsStore

/**
 * The saved lyric offset per player, in ms (positive shows the lyrics earlier), under
 * `desktop.offset.<app id>`. A [flagMs] from `--offset` wins for every player while given.
 * Unsaved players start at 0: the per-player leads measured so far are corrected in [MediaSession].
 */
class SyncDelays(private val store: SettingsStore, private val flagMs: Long? = null) {
    /** Whether `--offset` overrides the saved values. */
    val flagged get() = flagMs != null

    fun offsetFor(app: String): Long = flagMs ?: store.getLong(key(app)) ?: 0L

    fun set(app: String, offsetMs: Long) = store.putLong(key(app), offsetMs)

    companion object {
        fun key(app: String) = "desktop.offset.${app.trim().lowercase()}"
    }
}
