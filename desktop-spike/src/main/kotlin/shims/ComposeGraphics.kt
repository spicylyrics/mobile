@file:Suppress("PackageDirectoryMismatch", "UNUSED_PARAMETER")

package androidx.compose.ui.graphics

import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** The placeholder avatar: a flat orange square (the credits clip it round). */
fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap = ImageBitmap(128, 128).also { image ->
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), androidx.compose.ui.geometry.Size(128f, 128f)) {
        drawRect(Color(0xFFE08A3C))
    }
}
