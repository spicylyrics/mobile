package com.tx24.spicyplayer.ui.settings

import com.tx24.spicyplayer.lyrics.spicy.RenderConfig
import com.tx24.spicyplayer.lyrics.spicy.ScrollConfig
import com.tx24.spicyplayer.lyrics.spicy.canvas.PinnedFooterMode
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.translation.TranslationMode

/**
 * The renderer's config from the lyrics settings. [songDurationMs] is the playing song's length,
 * over which static lyrics scroll when Static Auto-Scroll is on.
 *
 * Built fresh, not copied: the mode-dependent defaults are worked out in the constructor.
 * Simple and Minimal are Spicy Lyrics' modes; the Apple Music style has its own look, so they and
 * Background Vocal Pop-Out are off under it. Low performance mode drops the distance blur and
 * the glow.
 */
fun CommonAppSettings.renderConfig(songDurationMs: Long): RenderConfig {
    val appleMusicStyle = lyricsStyle == LyricsStyle.AppleMusic
    return RenderConfig(
        simpleLyricsMode = simpleLyricsMode && !appleMusicStyle,
        minimalLyricsMode = minimalLyricsMode && !appleMusicStyle,
        appleMusic = appleMusicStyle,
        refreshedSimple = refreshedSimpleLyrics && simpleLyricsMode && !appleMusicStyle,
        wordMotionBoost = wordMotionBoost,
        wideDuetPadding = duetLinePadding,
        creditsFollowLastLine = creditsFollowLastLine,
        refinedCredits = refinedCredits,
        backgroundPop = backgroundPop && !appleMusicStyle,
        keepOverlappingLines = keepOverlappingLines,
        syllableMerge = syllableMerge,
        distanceBlurEnabled = distanceBlur && !lowPerformance,
        glowEnabled = glow && !lowPerformance,
        scroll = ScrollConfig(
            leadMs = if (scrollLeadEnabled) scrollLeadMs.coerceAtLeast(0).toLong() else 0L,
            smooth = smoothScrolling,
            seekFadeCompensation = seekFadeCompensation,
            staticSongMs = if (staticAutoScroll) songDurationMs else 0L,
        ),
    )
}

/** Whether the lyrics show their romanization: asked for, and the lyrics on screen have one ([available]). */
fun CommonAppSettings.romanizes(available: Boolean): Boolean = romanize && available

/**
 * Everything the settings decide about how a lyrics view draws, beside the [config]: for a host
 * that shows the lyrics without the phone's screen around them.
 *
 * @param fontSizeScale the Lyrics Size setting for the orientation, 1 at 100%.
 * @param romanize whether romanized lines show (see [romanizes]).
 * @param pinnedFooter where the credits sit; the host may still move them back under the lyrics
 * while its own controls cover the bottom.
 */
data class LyricsDisplay(
    val config: RenderConfig,
    val fontSizeScale: Float,
    val romanize: Boolean,
    val romanizationMode: RomanizationMode,
    val translationMode: TranslationMode,
    val textCredits: Boolean,
    val pinnedFooter: PinnedFooterMode,
)

/** [LyricsDisplay] for a view [landscape] (wider than tall) showing lyrics that have romanization or not. */
fun CommonAppSettings.lyricsDisplay(songDurationMs: Long, landscape: Boolean, romanizationAvailable: Boolean) = LyricsDisplay(
    config = renderConfig(songDurationMs),
    fontSizeScale = lyricsScale(landscape),
    romanize = romanizes(romanizationAvailable),
    romanizationMode = romanizationMode,
    translationMode = translationMode,
    textCredits = textCredits,
    pinnedFooter = pinnedFooter,
)
