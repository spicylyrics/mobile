package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.LyricsDisplay
import com.tx24.spicyplayer.ui.settings.lyricsDisplay
import kotlin.math.roundToInt

/**
 * What the settings ask of the lyrics window for [lyrics] of a song [durationMs] long, in a
 * [widthPx] by [heightPx] window.
 * The settings are Compose state, so a composable reading this follows the settings window live.
 */
fun CommonAppSettings.desktopLyrics(lyrics: LyricsState, durationMs: Long, widthPx: Float, heightPx: Float): LyricsDisplay {
    val ready = lyrics as? LyricsState.Ready
    return lyricsDisplay(
        songDurationMs = durationMs,
        landscape = widthPx > heightPx,
        romanizationAvailable = ready?.lines?.any { line -> line.words.any { it.romanized != null } } == true,
    )
}

/**
 * The background settings the desktop's Kawarp honours.
 *
 * @param animate it moves; off with Static Background or low performance mode.
 * @param beatReactive its speed follows the song's audio analysis.
 * @param blurPasses Kawarp's blur passes for the Default background's blur setting.
 */
data class DesktopBackground(val animate: Boolean, val beatReactive: Boolean, val blurPasses: Int)

fun CommonAppSettings.desktopBackground() = DesktopBackground(
    animate = !staticBackground && !lowPerformance,
    beatReactive = beatReactiveBackground && !staticBackground && !lowPerformance,
    blurPasses = kawarpBlurPasses(dynamicBackgroundBlur),
)

/**
 * The blur setting (0-100%) as Kawarp's blur passes: 60%, the default, is Kawarp's own 8 passes,
 * as on the phone.
 */
fun kawarpBlurPasses(intensity: Int): Int = (intensity.coerceIn(0, 100) * 8f / 60f).roundToInt().coerceIn(1, 40)
