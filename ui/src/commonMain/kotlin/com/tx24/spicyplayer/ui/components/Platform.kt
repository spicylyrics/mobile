package com.tx24.spicyplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

/**
 * Back while [enabled]: the system's back on Android, Esc on the desktop. Of several enabled at
 * once, the one declared last is asked.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/** How far a back gesture has gone (0 → 1), and whether it started at the left edge. */
class BackProgress(val progress: Float, val fromLeft: Boolean)

/**
 * Back that can follow a gesture: [onBack] collects its progress and finishes once the gesture is
 * let go, or is cancelled if it's called off. Where there's no gesture (Esc), the flow ends at once.
 */
@Composable
expect fun PlatformPredictiveBackHandler(enabled: Boolean = true, onBack: suspend (Flow<BackProgress>) -> Unit)

/** A pop-up plate's soft drop shadow, drawn behind it. */
expect fun Modifier.modalShadow(): Modifier

/** The height of the window's root, in px: where a menu has to stop. */
@Composable
expect fun rootHeightPx(): Int

/** [text] in Unicode's canonical decomposition (NFD): accents split off their letters. */
expect fun decomposed(text: String): String
