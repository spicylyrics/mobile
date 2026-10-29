package com.tx24.spicyplayer.haptics

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** [onClick], after [type]'s feedback. */
@Composable
fun withHaptic(type: HapticFeedbackType, onClick: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return { haptics.performHapticFeedback(type); onClick() }
}
