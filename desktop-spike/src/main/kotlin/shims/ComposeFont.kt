// The Android Font(resId) overloads, reading the app's font files from the classpath. Without
// default arguments these win over Compose Desktop's own Font(resId), which can't load a file.
// Variable fonts draw at their default instance here: the spike's lyrics are Latin.
@file:Suppress("PackageDirectoryMismatch", "UNUSED_PARAMETER")

package androidx.compose.ui.text.font

import com.tx24.spicyplayer.R

fun Font(resId: Int, weight: FontWeight): Font {
    val name = R.font.names.getValue(resId)
    val bytes = R::class.java.getResourceAsStream("/fonts/$name.ttf")!!.use { it.readBytes() }
    return androidx.compose.ui.text.platform.Font("$name-${weight.weight}", bytes, weight)
}

fun Font(resId: Int, weight: FontWeight, variationSettings: FontVariation.Settings): Font = Font(resId, weight)
