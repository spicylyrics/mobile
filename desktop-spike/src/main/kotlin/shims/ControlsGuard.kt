@file:Suppress("PackageDirectoryMismatch", "UNUSED_PARAMETER")

package com.tx24.spicyplayer.ui.controls

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/** No controls to keep hidden on the spike's window. */
fun Modifier.keepsControlsHidden(tapsOnly: Boolean = false, hit: (Offset) -> Boolean = { true }): Modifier = this
