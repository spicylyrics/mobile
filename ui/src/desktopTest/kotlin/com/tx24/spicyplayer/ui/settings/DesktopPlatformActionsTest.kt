package com.tx24.spicyplayer.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopPlatformActionsTest {
    @Test fun knownTypesBecomeExtensions() {
        assertEquals(listOf("ttf", "otf"), DesktopPlatformActions.extensionsFor(listOf("font/ttf", "font/otf")))
        assertEquals(listOf("json"), DesktopPlatformActions.extensionsFor(listOf("APPLICATION/JSON")))
        assertEquals(listOf("xml", "ttml"), DesktopPlatformActions.extensionsFor(listOf("text/xml", "application/xml")))
    }

    @Test fun anyOrUnknownTypeOffersEveryFile() {
        assertEquals(emptyList<String>(), DesktopPlatformActions.extensionsFor(emptyList()))
        assertEquals(emptyList<String>(), DesktopPlatformActions.extensionsFor(listOf("*/*")))
        assertEquals(emptyList<String>(), DesktopPlatformActions.extensionsFor(listOf("font/ttf", "application/octet-stream")))
    }

    @Test fun noPlatformActionsSayNo() {
        var picked: String? = "unset"
        NoPlatformActions.pickFile(listOf("font/ttf")) { picked = it }
        assertEquals(null, picked)
        assertEquals(false, NoPlatformActions.openUrl("https://example.com"))
    }
}
