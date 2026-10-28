package com.tx24.spicyplayer.ui.background

import com.tx24.spicyplayer.ui.settings.BackgroundType

/** Which renderer a [BackgroundType] needs. */
enum class BackgroundKind { Kawarp, Legacy, Color, Still }

fun kindOf(type: BackgroundType): BackgroundKind = when (type) {
    BackgroundType.Default -> BackgroundKind.Kawarp
    BackgroundType.Legacy -> BackgroundKind.Legacy
    BackgroundType.Color -> BackgroundKind.Color
    BackgroundType.Auto, BackgroundType.ArtistHeader, BackgroundType.CoverArt -> BackgroundKind.Still
}

/** How far the artist header has got for the song on screen. */
enum class HeaderLoad { NotYet, Loaded, Failed }

/** What a still background shows: nothing new yet (the last picture stays), the artist header, or the cover. */
enum class StillPick { Wait, Header, Cover }

/**
 * The still background's picture. Cover Art is the cover. Artist Header and Auto wait while the
 * header is looked up ([headerPending]) and while a found header ([headerUrl]) downloads, then show
 * it; with no header, or one that failed to load, they show the cover.
 */
fun pickStill(type: BackgroundType, headerUrl: String?, headerPending: Boolean, header: HeaderLoad): StillPick = when {
    !type.usesArtistHeader -> StillPick.Cover
    headerPending -> StillPick.Wait
    headerUrl == null -> StillPick.Cover
    header == HeaderLoad.NotYet -> StillPick.Wait
    header == HeaderLoad.Loaded -> StillPick.Header
    else -> StillPick.Cover
}

/**
 * One pixel (ARGB) through the still background's `brightness(0.55) contrast(1.05) saturate(1.7)`,
 * each step clamped to 0..1 as the browser's filter chain does. Alpha comes out opaque.
 */
fun gradeStillPixel(argb: Int): Int {
    var r = ((argb shr 16) and 0xFF) / 255f
    var g = ((argb shr 8) and 0xFF) / 255f
    var b = (argb and 0xFF) / 255f
    r = (r * 0.55f).coerceIn(0f, 1f); g = (g * 0.55f).coerceIn(0f, 1f); b = (b * 0.55f).coerceIn(0f, 1f)
    r = ((r - 0.5f) * 1.05f + 0.5f).coerceIn(0f, 1f)
    g = ((g - 0.5f) * 1.05f + 0.5f).coerceIn(0f, 1f)
    b = ((b - 0.5f) * 1.05f + 0.5f).coerceIn(0f, 1f)
    // The filter spec's saturate matrix.
    val s = 1.7f
    val nr = (0.213f + 0.787f * s) * r + (0.715f - 0.715f * s) * g + (0.072f - 0.072f * s) * b
    val ng = (0.213f - 0.213f * s) * r + (0.715f + 0.285f * s) * g + (0.072f - 0.072f * s) * b
    val nb = (0.213f - 0.213f * s) * r + (0.715f - 0.715f * s) * g + (0.072f + 0.928f * s) * b
    return (0xFF shl 24) or
        ((nr.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 16) or
        ((ng.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 8) or
        (nb.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
}

/** The still background is graded and blurred on a copy no longer than this on its long side. */
const val STILL_MAX_PX = 1280

/**
 * One frame of the Legacy background's 45 s loop (`@keyframes bgAnim`): translate3d(-8%, 6%) and
 * scale 1.02 → 1.1 at the half, back again by the end. [progress] is 0..1 through the loop.
 */
data class LegacyDrift(val translateX: Float, val translateY: Float, val scale: Float)

fun legacyDrift(progress: Float): LegacyDrift {
    val p = if (progress < 0.5f) progress * 2f else 2f - progress * 2f
    return LegacyDrift(translateX = -0.08f * p, translateY = 0.06f * p, scale = 1.02f + 0.08f * p)
}

const val LEGACY_DRIFT_LOOP_MS = 45_000L

/**
 * The Legacy layers' boxes, back to front, as fractions of the page (left, top, width, height):
 * BackCenter (300% × 100%, right −50%, top −20%), Back (200% × 100%, bottom-left), Front (200% ×
 * 100%, top-right). Each is a pill-shaped, blurred copy of the cover.
 */
val LegacyLayerBoxes: List<FloatArray> = listOf(
    floatArrayOf(-1.5f, -0.2f, 3f, 1f),
    floatArrayOf(0f, 0f, 2f, 1f),
    floatArrayOf(-1f, 0f, 2f, 1f),
)

/** CSS blur lengths are Gaussian sigmas; Compose's blur radius maps to sigma = radius × 0.57735 + 0.5. */
fun cssBlurSigmaToRadius(sigmaPx: Float): Float = ((sigmaPx - 0.5f) / 0.57735f).coerceAtLeast(0.1f)
