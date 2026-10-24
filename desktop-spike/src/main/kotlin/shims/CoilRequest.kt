@file:Suppress("PackageDirectoryMismatch", "UNUSED_PARAMETER")

package coil.request

import android.content.Context
import android.graphics.drawable.Drawable

class ImageRequest private constructor() {
    class Builder(context: Context) {
        fun data(data: Any?) = this
        fun size(size: Int) = this
        fun allowHardware(enable: Boolean) = this
        fun build() = ImageRequest()
    }
}

sealed class ImageResult {
    object Error : ImageResult()
}

class SuccessResult(val drawable: Drawable?) : ImageResult()
