package com.tx24.spicyplayer.ui.settings

import com.tx24.spicyplayer.ui.nowplaying.StageArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonAppSettingsTest {
    private fun settings(vararg values: Pair<String, Any>, pipButton: Boolean = false, prereleases: Boolean = true) =
        InMemorySettingsStore(mapOf(*values)).let { it to CommonAppSettings(it, pipButton, prereleases) }

    /**
     * Writes every setting back as it reads on a fresh install, then checks what landed in the
     * store: a renamed key or a changed default breaks this, and either would cost users their
     * saved settings or change what a new install looks like.
     */
    @Test fun everyKeyAndDefaultIsPinned() {
        val (store, s) = settings()
        s.romanize = s.romanize
        s.romanizationMode = s.romanizationMode
        s.translationMode = s.translationMode
        s.lyricsStyle = s.lyricsStyle
        s.simpleLyricsMode = s.simpleLyricsMode
        s.refreshedSimpleLyrics = s.refreshedSimpleLyrics
        s.minimalLyricsMode = s.minimalLyricsMode
        s.originalWordMotion = s.originalWordMotion
        s.lyricsSizePortrait = s.lyricsSizePortrait
        s.lyricsSizeLandscape = s.lyricsSizeLandscape
        s.lyricsFont = s.lyricsFont
        s.customFontFile = s.customFontFile
        s.customFontName = s.customFontName
        s.showScrollToActive = s.showScrollToActive
        s.pinnedFooter = s.pinnedFooter
        s.profilesInBrowser = s.profilesInBrowser
        s.duetLinePadding = s.duetLinePadding
        s.creditsFollowLastLine = s.creditsFollowLastLine
        s.refinedCredits = s.refinedCredits
        s.textCredits = s.textCredits
        s.backgroundPop = s.backgroundPop
        s.keepOverlappingLines = s.keepOverlappingLines
        s.syllableMerge = s.syllableMerge
        s.scrollLeadEnabled = s.scrollLeadEnabled
        s.scrollLeadMs = s.scrollLeadMs
        s.smoothScrolling = s.smoothScrolling
        s.staticAutoScroll = s.staticAutoScroll
        s.seekFadeCompensation = s.seekFadeCompensation
        s.distanceBlur = s.distanceBlur
        s.glow = s.glow
        s.lowPerformance = s.lowPerformance
        s.uiAnimations = s.uiAnimations
        s.peekWhileAdjusting = s.peekWhileAdjusting
        s.backgroundType = s.backgroundType
        s.backgroundBlur = s.backgroundBlur
        s.dynamicBackgroundBlur = s.dynamicBackgroundBlur
        s.legacyBackgroundBlur = s.legacyBackgroundBlur
        s.beatReactiveBackground = s.beatReactiveBackground
        s.staticBackground = s.staticBackground
        s.releaseYearPosition = s.releaseYearPosition
        s.expandWithoutLyrics = s.expandWithoutLyrics
        s.timelineOutsideCover = s.timelineOutsideCover
        s.viewControlsPosition = s.viewControlsPosition
        s.showNoLyricsNotice = s.showNoLyricsNotice
        s.animatedCover = s.animatedCover
        s.stageArt = s.stageArt
        s.canvasBackground = s.canvasBackground
        s.bigCoverCanvas = s.bigCoverCanvas
        s.canvasBackdrop = s.canvasBackdrop
        s.pauseWithMusicKinds = s.pauseWithMusicKinds
        s.animatedBackground = s.animatedBackground
        s.refinedBackground = s.refinedBackground
        s.backgroundFollowsMotion = s.backgroundFollowsMotion
        s.keepScreenOn = s.keepScreenOn
        s.showOverLockScreen = s.showOverLockScreen
        s.ambientMode = s.ambientMode
        s.ambientDelay = s.ambientDelay
        s.systemBars = s.systemBars
        s.autoPip = s.autoPip
        s.pipShape = s.pipShape
        s.pipCustomRatio = s.pipCustomRatio
        s.pipShowLyrics = s.pipShowLyrics
        s.pipDock = s.pipDock
        s.pipDockCover = s.pipDockCover
        s.pipWithoutLyrics = s.pipWithoutLyrics
        s.pipLyricsSize = s.pipLyricsSize
        s.rotateButton = s.rotateButton
        s.pipButton = s.pipButton
        s.hideHeader = s.hideHeader
        s.headerSize = s.headerSize
        s.panelSide = s.panelSide
        s.highRefreshRate = s.highRefreshRate
        s.autoHideControls = s.autoHideControls
        s.controlsHideDelay = s.controlsHideDelay
        s.hideControlsWhilePaused = s.hideControlsWhilePaused
        s.touchHaptics = s.touchHaptics
        s.musicHaptics = s.musicHaptics
        s.musicHapticsStrength = s.musicHapticsStrength
        s.musicHapticsStyle = s.musicHapticsStyle
        s.quickSettingsButton = s.quickSettingsButton
        s.lyricsManagerButton = s.lyricsManagerButton
        s.nowBarOpen = s.nowBarOpen
        s.nowBarOnRight = s.nowBarOnRight
        s.compactView = s.compactView
        s.queueButton = s.queueButton
        s.sourceButton = s.sourceButton
        s.playerButtons = s.playerButtons
        s.romanizeButton = s.romanizeButton
        s.translateButton = s.translateButton
        s.resyncButton = s.resyncButton
        s.expandButton = s.expandButton
        s.ttmlUploadSaves = s.ttmlUploadSaves
        s.settingsTheme = s.settingsTheme
        s.popupTheme = s.popupTheme
        s.appIconName = s.appIconName
        s.usageStats = s.usageStats
        s.usageStatsAsked = s.usageStatsAsked
        s.introDone = s.introDone
        s.includePrereleases = s.includePrereleases

        val expected = mapOf<String, Any>(
            "romanize" to false,
            "romanizationMode" to "Replace",
            "translationMode" to "UnderLine",
            "lyricsStyle" to "Spicy",
            "simpleLyricsMode" to false,
            "refreshedSimpleLyrics" to false,
            "minimalLyricsMode" to false,
            "originalWordMotion" to false,
            "lyricsSizePortrait" to 100,
            "lyricsSizeLandscape" to 100,
            "lyricsFont" to "Default",
            "customFontFile" to "",
            "customFontName" to "",
            "showScrollToActive" to true,
            "pinnedFooter" to "Off",
            "profilesInBrowser" to false,
            "duetLinePadding" to true,
            "creditsFollowLastLine" to false,
            "refinedCredits" to true,
            "textCredits" to true,
            "backgroundPop" to false,
            "keepOverlappingLines" to true,
            "syllableMerge" to "Off",
            "scrollLeadEnabled" to false,
            "scrollLeadMs" to 250,
            "smoothScrolling" to false,
            "staticAutoScroll" to true,
            "seekFadeCompensation" to true,
            "distanceBlur" to true,
            "glow" to true,
            "lowPerformance" to false,
            "uiAnimations" to true,
            "peekWhileAdjusting" to true,
            "backgroundType" to "Default",
            "backgroundBlur" to 0,
            "dynamicBackgroundBlur" to 60,
            "legacyBackgroundBlur" to 40,
            "beatReactiveBackground" to true,
            "staticBackground" to false,
            "releaseYearPosition" to "Off",
            "expandWithoutLyrics" to false,
            "timelineOutsideCover" to true,
            "viewControlsPosition" to "Top",
            "showNoLyricsNotice" to true,
            "animatedCover" to true,
            "stageArts" to StageArt.store(StageArt.DEFAULT),
            "canvasBackground" to false,
            "bigCoverCanvas" to true,
            "canvasBackdrop" to true,
            "pauseWithMusic" to "AnimatedCover,Canvas",
            "animatedBackground" to false,
            "refinedBackground" to true,
            "backgroundFollowsMotion" to true,
            "keepScreenOn" to true,
            "showOverLockScreen" to false,
            "ambientMode" to false,
            "ambientDelay" to "Default",
            "systemBars" to "HideBoth",
            "autoPip" to true,
            "pipShape" to "Wide",
            "pipCustomRatio" to 100,
            "pipShowLyrics" to true,
            "pipDock" to true,
            "pipDockCover" to true,
            "pipWithoutLyrics" to "Message",
            "pipLyricsSize" to 100,
            "rotateButton" to true,
            "pipButton" to false,
            "hideHeader" to false,
            "headerSize" to "Large",
            "panelSide" to "Left",
            "highRefreshRate" to false,
            "autoHideControls" to true,
            "controlsHideDelay" to "Default",
            "hideControlsWhilePaused" to false,
            "touchHaptics" to true,
            "musicHaptics" to false,
            "musicHapticsStrength" to 100,
            "musicHapticsStyle" to "Drums",
            "quickSettingsButton" to true,
            "lyricsManagerButton" to false,
            "nowBarOpen" to false,
            "nowBarOnRight" to false,
            "compactView" to "Auto",
            "queueButton" to false,
            "sourceButton" to false,
            "playerButtons" to false,
            "romanizeButton" to true,
            "translateButton" to true,
            "resyncButton" to true,
            "expandButton" to true,
            "ttmlUploadSaves" to true,
            "settingsTheme" to "Purple",
            "popupTheme" to "Purple",
            "appIcon" to "Purple",
            "usageStats" to true,
            "usageStatsAsked" to false,
            "introDone" to false,
            "includePrereleases" to true,
        )
        assertEquals(expected.keys, store.keys)
        expected.forEach { (key, value) ->
            val stored: Any? = when (value) {
                is Boolean -> store.getBoolean(key)
                is Int -> store.getInt(key)
                else -> store.getString(key)
            }
            assertEquals(key, value, stored)
        }
        // The types a restore checks against are the ones written, plus the switch stageArts replaced.
        assertEquals(expected.keys + "coverStage", s.storedTypes.keys)
    }

    @Test fun namedChoicesReadOnlyKnownNames() {
        assertEquals(setOf("Canvas"), settings("pauseWithMusic" to "Canvas, Hologram").second.pauseWithMusicKinds)
        assertEquals(emptySet<String>(), settings("pauseWithMusic" to "").second.pauseWithMusicKinds)
        assertEquals(setOf("AnimatedCover", "Canvas"), settings("pauseWithMusic" to 7).second.pauseWithMusicKinds)
        assertEquals("Pride", settings("appIcon" to "Pride").second.appIconName)
        assertEquals("Purple", settings("appIcon" to "Hologram").second.appIconName)

        val (store, s) = settings()
        s.pauseWithMusicKinds = setOf("Canvas", "AnimatedCover", "Hologram")
        assertEquals("AnimatedCover,Canvas", store.getString("pauseWithMusic"))
    }

    @Test fun thePlatformDefaultsComeFromTheCaller() {
        assertTrue(settings(pipButton = true).second.pipButton)
        assertFalse(settings(prereleases = false).second.includePrereleases)
    }

    @Test fun wrongTypesReadAsDefaults() {
        val (_, s) = settings("lowPerformance" to "yes", "backgroundBlur" to 999, "scrollLeadMs" to "fast", "stageArts" to 3, "lyricsStyle" to "Karaoke")
        assertFalse(s.lowPerformance)
        assertEquals(MAX_BACKGROUND_BLUR, s.backgroundBlur)
        assertEquals(250, s.scrollLeadMs)
        assertEquals(StageArt.DEFAULT, s.stageArt)
        assertEquals(LyricsStyle.Spicy, s.lyricsStyle)
    }

    @Test fun oldSwitchesCarryOver() {
        assertEquals(LyricsFont.System, settings("systemFont" to true).second.lyricsFont)
        assertEquals(BackgroundType.Legacy, settings("legacyBackground" to true).second.backgroundType)
        assertEquals(StageArt.COVER_STAGE_ON, settings("coverStage" to true).second.stageArt)
        assertEquals(emptySet<StageArt>(), settings("coverStage" to false).second.stageArt)
        assertEquals(StageArt.DEFAULT, settings("coverStage" to "yes").second.stageArt)
        assertEquals(130, settings("lyricsSize" to "Larger").second.lyricsSizeLandscape)
    }

    @Test fun startedEmptyOnlyOnANewInstall() {
        assertTrue(settings().second.startedEmpty)
        assertFalse(settings("introDone" to true).second.startedEmpty)
    }

    @Test fun reloadPicksUpWritesBehindItsBack() {
        val (store, s) = settings()
        store.putBoolean("glow", false)
        assertTrue(s.glow)
        s.reload()
        assertFalse(s.glow)
    }

    @Test fun writesGoThroughToTheStore() {
        val (store, s) = settings()
        s.backgroundType = BackgroundType.CoverArt
        s.stageArt = setOf(StageArt.CoverArt)
        assertEquals("CoverArt", store.getString("backgroundType"))
        assertEquals("CoverArt", store.getString("stageArts"))
        assertEquals(BackgroundType.CoverArt, CommonAppSettings(store, false, true).backgroundType)
    }
}
