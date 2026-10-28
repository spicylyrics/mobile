package com.tx24.spicyplayer.desktop

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DesktopAvatarsTest {
    private val placeholder = runBlocking { PlaceholderAvatars.load("", 128) }

    @Test
    fun `a failed fetch gives the placeholder`() = runBlocking {
        assertSame(placeholder, DesktopAvatars(fetch = { null }).load("https://x/a.jpg", 128))
    }

    @Test
    fun `bytes that aren't a picture give the placeholder`() = runBlocking {
        assertSame(placeholder, DesktopAvatars(fetch = { byteArrayOf(1, 2, 3) }).load("https://x/a.jpg", 128))
    }

    @Test
    fun `a fetch that throws gives the placeholder`() = runBlocking {
        assertSame(placeholder, DesktopAvatars(fetch = { error("offline") }).load("https://x/a.jpg", 128))
    }

    @Test
    fun `a decoded picture is kept, so a redraw neither fetches nor decodes`() = runBlocking {
        var fetches = 0
        var decodes = 0
        val image = ImageBitmap(4, 4)
        val avatars = DesktopAvatars(fetch = { fetches++; byteArrayOf(1) }, decode = { _, _ -> decodes++; image })
        assertSame(image, avatars.load("https://x/a.jpg", 128))
        assertSame(image, avatars.load("https://x/a.jpg", 128))
        assertEquals(1, fetches)
        assertEquals(1, decodes)
    }

    @Test
    fun `a failure isn't kept, so it's tried again`() = runBlocking {
        var fetches = 0
        val avatars = DesktopAvatars(fetch = { fetches++; null })
        avatars.load("https://x/a.jpg", 128)
        avatars.load("https://x/a.jpg", 128)
        assertEquals(2, fetches)
    }

    @Test
    fun `only the last pictures are kept`() = runBlocking {
        var fetches = 0
        val avatars = DesktopAvatars(fetch = { fetches++; byteArrayOf(1) }, decode = { _, _ -> ImageBitmap(1, 1) })
        repeat(MAX_AVATARS + 1) { avatars.load("https://x/$it.jpg", 128) }
        avatars.load("https://x/0.jpg", 128)
        assertEquals(MAX_AVATARS + 2, fetches)
    }

    @Test
    fun `a real picture is shrunk toward the asked size`() = runBlocking {
        val png = java.io.ByteArrayOutputStream().also {
            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(512, 256, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        val image = DesktopAvatars(fetch = { png }).load("https://x/a.png", 128)!!
        assertEquals(128, image.width)
        assertEquals(64, image.height)
    }
}
