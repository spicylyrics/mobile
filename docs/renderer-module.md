# The `:renderer` module

The word-synced lyrics renderer and the Kawarp background engine, shared by the Android app (`:app`) and the desktop app (`:desktop`). It is a Kotlin Multiplatform + Compose Multiplatform library with two targets, `android` (AGP 9's `com.android.kotlin.multiplatform.library`) and `jvm("desktop")`. Before it, the desktop build copied these sources out of `:app` and faked the Android APIs they touched with stub classes.

Files keep the app's package names (as `:core` does), so moving one doesn't change its imports.

The modules stay flat beside each other: `:core` (JVM), `:renderer` (multiplatform), `:ui` (multiplatform, the shared chrome and, later, the shared screens), and the two apps `:app` and `:desktop` on top. Dependencies run one way, `:core` ← `:renderer` ← `:ui` ← apps. A `:shared:*` group would only add a folder level and longer project paths in every build file and CI command; the module names already say what is shared.

## Layout

```
renderer/
  src/commonMain/kotlin/com/tx24/spicyplayer/
    lyrics/spicy/RenderConfig.kt
    lyrics/spicy/Platform.kt                  expect: clock, identity hash, code points, rounding
    lyrics/spicy/parser/LetterSynthesizer.kt
    lyrics/spicy/animation/**                 springs, splines, LyricsAnimator, motion styles
    lyrics/spicy/canvas/*.kt                  LyricsView, layout, wrapping, scroll, drawing, credits
    lyrics/spicy/canvas/LyricFonts.kt         expect: the bundled lyric font families
    lyrics/spicy/canvas/LyricsImageLoader.kt  the credits' avatar loader, injected by the host
    lyrics/spicy/canvas/RefreshRate.kt        expect: the display's refresh rate, for the frame loop
    lyrics/spicy/canvas/kawarp/KawarpEngine.kt, KawarpBlurCore.kt, KawarpShader.kt
    ui/controls/ControlsRevealGuard.kt        touches that leave the playback controls hidden
  src/androidMain/kotlin/...                  actuals
  src/androidMain/res/font/*.ttf              the lyric fonts (also the desktop's classpath resources)
  src/desktopMain/kotlin/...                  actuals, LocalDisplayRefreshRate
  src/desktopTest/kotlin/...                  the renderer's unit tests (plain JVM JUnit4)
```

Not moved: the Kawarp hosts, which are platform code through and through. `KawarpBackground.kt` and `KawarpSnapshot.kt` stay in `:app` (Android `RuntimeShader`, `Bitmap`, video frames, `BuildConfig`); `DesktopKawarp.kt` stays in `:desktop` (Skia `RuntimeEffect`). Both read the engine, the blur and the shader from `:renderer`. The old bitmap background (`DynamicBackground*`, `StackBlur`), the widget painter and `MotionFrames` stay in `:app` too.

## Platform seams

| Seam | Common side | Android | Desktop |
|---|---|---|---|
| Lyric fonts | `internal expect object LyricFonts` (`bundled`, `vazirmatn`, `georgian` font families) | `Font(R.font.x, weight, variationSettings)` from the module's `res/font`, as before | the same `.ttf` files read from the classpath (`/font/x.ttf`) |
| Script fonts | `LyricFonts.scriptFamily(text, weight)`, asked first for each line | null: Android's font fallback already picks well | `CjkFonts`: Windows' Chinese, Japanese or Korean font for a CJK line, the Han font chosen per song (`CjkFonts.useFor(lines)`) |
| Display refresh rate | `@Composable internal expect fun rememberRefreshRate(): () -> Float?`, read by the frame loop | `LocalView.current.display?.refreshRate` | `LocalDisplayRefreshRate`, which the window provides |
| Credits avatars | `fun interface LyricsImageLoader`, `LocalLyricsImageLoader` (null: no avatars) | `:app` provides `CoilLyricsImageLoader` (Coil 2, the lyrics screen's context) at the screen's root | `:desktop` provides `PlaceholderAvatars`, the flat square it drew before |
| Monotonic clock | `internal expect fun monotonicNanos(): Long` | `System.nanoTime()` | `System.nanoTime()` |
| Identity hash | `internal expect fun identityHash(any: Any): Int` (the per-word letter-spacing nudge that keeps text layouts distinct) | `System.identityHashCode` | `System.identityHashCode` |
| Code points, rounding | `internal expect` `codePointAtIndex`, `codePointBeforeIndex`, `charCount`, `roundHalfUp` | `java.lang` | `java.lang` |
| Haptics | `LocalHapticFeedback`, already common | Android's | a no-op |

The JVM actuals are written twice (Android and desktop), a few lines each, rather than through a custom `jvmShared` source set, which would switch off Kotlin's default source-set hierarchy.

`commonMain` holds no `java.*` or `android.*` reference. With only JVM-flavoured targets the compiler doesn't enforce that (common code compiles against the JVM on each target), so `CommonMainPlatformApiTest` scans the sources for it.

Fonts are not in Compose Multiplatform resources, on purpose. The home screen widget's layouts (`widget_song_row.xml`, `styles.xml`) name `@font/lyrics_*`, and a widget can only use Android font resources, so the files must stay Android resources; CMP resources would package a second copy as assets. CMP's `Font(Res.font.x)` is also `@Composable`, while the renderer builds its font families once, outside composition. So the files live once in `src/androidMain/res/font`, and other modules reuse the families through `LyricsLayoutCalculator.spicyFontFamily` (which also honours the Lyrics Font setting). The renderer has no strings or images of its own. An iOS target needs the fonts as CMP resources (iOS has no Android resources and no classpath): either a copy for the iOS source set only, or CMP resources pointed at the same `res/font` directory for that source set, read in its `LyricFonts` actual.

## The shader

The fused Kawarp shader is one string, `KAWARP_FUSED_AGSL` in `kawarp/KawarpShader.kt`. The same source is valid AGSL and SkSL, so nothing is converted: Android compiles it with `RuntimeShader`, the desktop with Skia's `RuntimeEffect.makeForShader`.

## What happened to each desktop shim

| Shim | Replaced by |
|---|---|
| `R.kt`, `ComposeFont.kt` | `LyricFonts` desktop actual |
| `AndroidView.kt`, `ComposePlatform.kt` (`LocalView`, `LocalContext`) | `LocalDisplayRefreshRate` + `rememberRefreshRate` |
| `Coil.kt`, `CoilRequest.kt`, `CoreKtx.kt`, `AndroidDrawable.kt`, `AndroidGraphics.kt`, `AndroidStubs.kt`, `ComposeGraphics.kt` | `LyricsImageLoader` |
| `ControlsGuard.kt` | the real `ControlsRevealGuard`, now in the module; with no guard provided it is a no-op |

`desktop/build.gradle.kts` loses its `rendererSources`, `kawarpShader` and `rendererFonts` tasks and depends on `project(":renderer")`.

## Dependencies

`:renderer` depends on `:core` in `commonMain`: the renderer draws `:core`'s lyric models (`Line`, `Word`, `LyricsType`, `FooterLine`, ...), its translation presentation and its script detectors. That works because `:core` is a JVM library and both targets are JVM-flavoured. It is the one thing that blocks an iOS target: `:core` (or at least its models, parsers' detectors and translation presentation) has to become multiplatform first. The rest of `commonMain` is ready for it.

Compose comes from Compose Multiplatform 1.12.2, which resolves to the androidx Compose artifacts on Android; the app's own Compose BOM still decides the versions it ships.

## Tests

`./gradlew :renderer:desktopTest` runs the renderer's tests (animation, layout, scroll, blur, letter synthesis) on the JVM, and `./gradlew :ui:desktopTest` the shared UI's (header and landscape metrics, marquee, stage art, cover colours). Each module has its own `CommonMainPlatformApiTest`. The full check is

```
.\gradlew.bat assembleDebug testDebugUnitTest :core:test :renderer:desktopTest :ui:desktopTest :desktop:compileKotlin :desktop:test lintDebug
```

## Sharing the UI

The next step after the renderer: a `:ui` module (same targets, same package names) holding the lyrics screen and its chrome, so `:app` keeps the Android shell and `:desktop` a window. A survey of `app/.../ui/` and `MainActivity.kt` at the time of the renderer move:

**Moved as they were** (step 1, done; Compose only, with the multiplatform `haze` and Material 3):
- `theme/SpicyTokens.kt`; `components/` `KeyFocus`, `LyricsSkeleton`, `ScreenSlide`, `ScrollHints`, `SpicyGlyphs`, `GlassButton`, `Backdrop`
- `controls/TransportGlyphs.kt`
- `nowplaying/` `CompactHeaderMetrics`, `LandscapeMetrics`, `HeaderMarquee`, `StageArt`
- `background/CoverColors.kt`; `settings/SystemBarsMode.kt`

`components/SettingsSkeleton.kt` stayed in `:app`: it draws its rows with `outlinedCard` from `SettingsControls.kt`, so it moves with the settings controls. The glyphs `:app` draws (`SpicyGlyphs`, `SkipGlyph`, `PlayPauseGlyph`) went from `internal` to public to be reachable across the module line. No drawables were needed then.

**Moved with CMP resources:** `:ui` has Compose Multiplatform resources in `src/commonMain/composeResources`, read as `Res.drawable.*` from the fixed package `com.tx24.spicyplayer.ui.resources`. `components/SpicyBrand.kt` and `SpicyToast.kt` moved there, drawing the brand glyph from `Res.drawable.app_glyph`. The intro screen still draws `R.drawable.app_glyph`, so the vector is in both modules for now; `BrandGlyphResourceTest` keeps the two files identical. Drop the `:app` copy when the intro screen moves.

**Big-screen settings panel:** `ui/settings/panel/` holds a settings panel for wide windows: a sidebar of tabs with search over a pane of grouped cards (toggles, selects, sliders, buttons). `settingsPanelSections` builds its rows over `CommonAppSettings`, so every row is a saved setting; actions the platform lacks (clearing the lyrics cache) leave their rows out. The desktop app opens it in a window of its own on Ctrl+, (`desktop/.../SettingsWindow.kt`), saved through `FileSettingsStore`. The phone keeps its own settings screens.

**Movable behind an interface:**
- `controls/LyricsControls.kt`, `nowplaying/CompactNowPlayingHeader.kt`, `LyricsNotice.kt`, `NowPlayingInfo.kt`, `PipSongCard.kt`, `PortraitCanvas.kt`, `CoverStage.kt` (also the animated-cover view, below), and the lyrics screen composable in `MainActivity.kt` (`LyricsApp` and below): **now-playing state + transport** (track, artwork, position clock, playing, play/pause/skip/seek), today read straight from `ExternalPlaybackViewModel`.
- `settings/AppSettings.kt` (`SharedPreferences`) and every settings page reading it: a **settings store** (key-value; `SharedPreferences` on Android, `java.util.prefs` or a file on desktop).
- `settings/` `SettingsScreen`, `SettingsPages`, `QuickSettings`, `Queue`, `CustomSources`, `DelayCalibration`, `HapticsSettings`, `SourcePicker`, `TranslationSettings`, `spotifysearch/SpotifySearch.kt`: the same **app-state interface** over the view model's lyrics/source/translation actions, plus **platform actions** (open a URL, open the system's notification-access or app settings, pick a file or folder).
- Images in the header, queue and search (Coil 2): an **image loader** like the renderer's `LyricsImageLoader`, or Coil 3 (multiplatform) on both platforms once the phone is checked on it.
- `components/SpicyModal.kt`, `SettingsControls.kt`, `background/SpicySessionBackground.kt`: `Build.VERSION` checks become a **platform capabilities** expect (backdrop blur, `RenderEffect`).
- `intro/IntroScreen.kt`: its `R.drawable` vectors move to **CMP resources** in `:ui` (strings too, once there are any to translate; the UI's text is in Kotlin today). It also reads `AppSettings`, so it waits for the settings store.
- Touch haptics (`ProvideTouchHaptics`, the groove haptics): a **haptics** interface, a no-op on desktop.
- `background/StillBackgrounds.kt`, `LegacyBackground.kt`, `CanvasAmbient.kt`: Android `Bitmap` blur and grading become a **blurred-image** expect (Skia's blur on desktop), the same split as Kawarp's hosts.

**Android-only** (a desktop counterpart is new work, not a move):
- `nowplaying/MotionCover.kt`, `MotionCoverStream.kt`, `background/MotionFrames` feed: media3 + GL video for animated covers and Canvas.
- `nowplaying/SessionArtwork.kt`: the other app's MediaSession bitmaps.
- `lyricsmanager/LyricsManager.kt` file import, `settings/LyricsFontFile.kt`: the Storage Access Framework, `Typeface`.
- `profile/ProfileModal.kt` (WebView), `settings/AppIcon.kt` (launcher aliases), `settings/DebugReport.kt`, `controls/KeyInput.kt` (Android key events), `PictureInPicture.kt`, the activity shell (window, keyguard, brightness, refresh rate), `widget/`, the notification listener in `playback/`, Gemini Nano translation (ML Kit).

**Order:**
1. `:ui` with the movable-as-is group. No behaviour change; `:app` only changes its dependency. Done.
2. The settings store, then `AppSettings` and the settings pages that need only it.
3. The now-playing state + transport interface; the header, controls, notices and the lyrics screen composable; `MainActivity` shrinks to the Android shell.
4. Backgrounds (blurred-image expect) and image loading (Coil 3 or the loader interface), each checked for smoothness on the phone.
5. Desktop counterparts for the Android-only list as wanted: video covers, file pickers, opening profiles in the browser.

iOS stays blocked on `:core` until its models, detectors and translation presentation are multiplatform; nothing in the plan above adds a new blocker.

