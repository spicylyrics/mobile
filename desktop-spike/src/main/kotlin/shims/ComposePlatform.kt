@file:Suppress("PackageDirectoryMismatch")

package androidx.compose.ui.platform

import android.content.Context
import android.view.Display
import android.view.View
import androidx.compose.runtime.staticCompositionLocalOf

val LocalContext = staticCompositionLocalOf { Context() }

/** The frame loop reads the display's refresh rate from here; the window provides the monitor's. */
val LocalView = staticCompositionLocalOf { View(Display(60f)) }
