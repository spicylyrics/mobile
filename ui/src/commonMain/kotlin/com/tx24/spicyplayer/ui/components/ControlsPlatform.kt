package com.tx24.spicyplayer.ui.components

import androidx.compose.runtime.Composable

/** A clock that only moves forward, in milliseconds, for spacing taps apart. */
internal expect fun uptimeMillis(): Long

/** [text] in Unicode NFD: each accented letter split into the letter and its combining marks. */
internal expect fun decomposeAccents(text: String): String

/** The window's height in pixels, keyboard included, for placing pop-up menus. */
@Composable
internal expect fun windowHeightPx(): Int
