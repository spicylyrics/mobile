package com.tx24.spicyplayer.ui.bigscreen

/**
 * Whether a window [widthDp] by [heightDp] gets the big-screen UI (the big-screen lyrics page and
 * the settings panel) rather than the phone's: at least [BIG_SCREEN_MIN_WIDTH_DP] wide (a tablet
 * held sideways, DeX, an unfolded foldable, a desktop window) and [BIG_SCREEN_MIN_HEIGHT_DP] tall,
 * so a phone in either orientation, a big phone on its side included, and a tablet held upright
 * keep the phone's screens.
 */
fun isBigScreenWindow(widthDp: Float, heightDp: Float): Boolean =
    widthDp >= BIG_SCREEN_MIN_WIDTH_DP && heightDp >= BIG_SCREEN_MIN_HEIGHT_DP

const val BIG_SCREEN_MIN_WIDTH_DP = 840f
const val BIG_SCREEN_MIN_HEIGHT_DP = 480f
