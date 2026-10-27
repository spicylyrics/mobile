// No image loading yet: every avatar comes back as the same placeholder disc.
@file:Suppress("PackageDirectoryMismatch", "UNUSED_PARAMETER")

package coil

import android.content.Context
import coil.request.ImageRequest
import coil.request.ImageResult

object Coil {
    fun imageLoader(context: Context) = ImageLoader
}

object ImageLoader {
    suspend fun execute(request: ImageRequest): ImageResult = coil.request.SuccessResult(android.graphics.drawable.Drawable())
}
