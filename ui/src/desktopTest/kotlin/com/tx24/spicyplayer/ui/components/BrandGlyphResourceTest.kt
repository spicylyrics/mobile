package com.tx24.spicyplayer.ui.components

import com.tx24.spicyplayer.ui.resources.Res
import com.tx24.spicyplayer.ui.resources.app_glyph
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The brand glyph is drawn from Compose resources here and from Android's `R.drawable` by the
 * intro screen, so the two files must stay the same vector.
 */
@OptIn(ExperimentalResourceApi::class)
class BrandGlyphResourceTest {
    @Test
    fun `the glyph resource is packaged and has its five shapes`() {
        assertNotNull(Res.drawable.app_glyph)
        val xml = runBlocking { Res.readBytes("drawable/app_glyph.xml") }.decodeToString()
        assertEquals(5, Regex("<path ").findAll(xml).count())
    }

    @Test
    fun `the glyph matches the Android drawable`() {
        val shared = File("src/commonMain/composeResources/drawable/app_glyph.xml")
        val android = File("../app/src/main/res/drawable/app_glyph.xml")
        assumeTrue("run from the ui module", shared.isFile && android.isFile)
        assertEquals(android.readText().replace("\r\n", "\n"), shared.readText().replace("\r\n", "\n"))
    }
}
