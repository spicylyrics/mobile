package com.tx24.spicyplayer.ui.components

import androidx.compose.runtime.compositionLocalOf

/** Whether the interface animates (playback buttons, lists, pop-up screens): the setting, and not low performance mode. */
val LocalUiAnimations = compositionLocalOf { true }
