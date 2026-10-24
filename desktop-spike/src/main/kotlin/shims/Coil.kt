// No image loading in the spike: every request fails, so credits draw without avatars.
@file:Suppress("PackageDirectoryMismatch", "UNUSED_PARAMETER")

package coil

import android.content.Context
import coil.request.ImageRequest
import coil.request.ImageResult

object Coil {
    fun imageLoader(context: Context) = ImageLoader
}

object ImageLoader {
    suspend fun execute(request: ImageRequest): ImageResult = ImageResult.Error
}
