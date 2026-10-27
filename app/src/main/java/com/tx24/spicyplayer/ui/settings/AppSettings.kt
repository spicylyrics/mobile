package com.tx24.spicyplayer.ui.settings

import android.content.SharedPreferences
import android.os.Build
import com.tx24.spicyplayer.BuildConfig
import com.tx24.spicyplayer.ui.nowplaying.LoopKind

/**
 * The phone's settings: the shared ones ([CommonAppSettings]) in the `ui` preferences, plus typed
 * views of the two stored by name there because their types live in the app.
 */
class AppSettings(prefs: SharedPreferences) : CommonAppSettings(
    store = SharedPreferencesStore(prefs),
    pipButtonByDefault = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
    // On by default while the app itself is a pre-release (0.x).
    prereleasesByDefault = BuildConfig.VERSION_NAME.startsWith("0."),
) {
    /** The loops that hold their frame while the music is paused ([pauseWithMusicKinds]). */
    var pauseWithMusic: Set<LoopKind>
        get() = LoopKind.entries.filter { it.name in pauseWithMusicKinds }.toSet()
        set(value) { pauseWithMusicKinds = value.mapTo(mutableSetOf()) { it.name } }

    /** Which launcher icon shows (`AppIcon.apply`), from [appIconName]. */
    var appIcon: AppIcon
        get() = AppIcon.entries.firstOrNull { it.name == appIconName } ?: AppIcon.Purple
        set(value) { appIconName = value.name }

    companion object {
        /** Sung words grow and lift this much more than the desktop amount, which reads better on a phone. */
        const val WORD_MOTION_BOOST = CommonAppSettings.WORD_MOTION_BOOST
    }
}
