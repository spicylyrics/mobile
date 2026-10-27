# The `:renderer` module

The word-synced lyrics renderer and the Kawarp background engine, shared by the Android app (`:app`) and the desktop app (`:desktop`). It is a Kotlin Multiplatform + Compose Multiplatform library with two targets, `android` (AGP 9's `com.android.kotlin.multiplatform.library`) and `jvm("desktop")`. Before it, the desktop build copied these sources out of `:app` and faked the Android APIs they touched with stub classes.

Files keep the app's package names (as `:core` does), so moving one doesn't change its imports.

## Layout

```
renderer/
  src/commonMain/kotlin/com/tx24/spicyplayer/
    lyrics/spicy/RenderConfig.kt
    lyrics/spicy/parser/LetterSynthesizer.kt
    lyrics/spicy/animation/**                 springs, splines, LyricsAnimator, motion styles
    lyrics/spicy/canvas/*.kt                  LyricsView, layout, wrapping, scroll, drawing, credits
    lyrics/spicy/canvas/LyricFonts.kt         expect: the bundled lyric font families
    lyrics/spicy/canvas/LyricsImageLoader.kt  the credits' avatar loader, injected by the host
    lyrics/spicy/canvas/Platform.kt           expect: clock, identity hash, code points
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
| Display refresh rate | `@Composable internal expect fun rememberRefreshRate(): () -> Float?`, read by the frame loop | `LocalView.current.display?.refreshRate` | `LocalDisplayRefreshRate`, which the window provides |
| Credits avatars | `fun interface LyricsImageLoader`, `LocalLyricsImageLoader` (null: no avatars) | `:app` provides a Coil 2 loader around the lyrics screen | `:desktop` provides the flat placeholder it drew before |
| Monotonic clock | `internal expect fun monotonicNanos(): Long` | `System.nanoTime()` | `System.nanoTime()` |
| Identity hash | `internal expect fun identityHash(any: Any): Int` (the per-word letter-spacing nudge that keeps text layouts distinct) | `System.identityHashCode` | `System.identityHashCode` |
| Code points | `internal expect` `codePointAt`, `codePointBefore`, `charCount`, `roundHalfUp` | `java.lang` | `java.lang` |
| Haptics | `LocalHapticFeedback`, already common | Android's | a no-op |

The JVM actuals are written twice (Android and desktop), a few lines each, rather than through a custom `jvmShared` source set, which would switch off Kotlin's default source-set hierarchy.

`commonMain` holds no `java.*` or `android.*` reference. With only JVM-flavoured targets the compiler doesn't enforce that (common code compiles against the JVM on each target), so `CommonMainPlatformApiTest` scans the sources for it.

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

`./gradlew :renderer:desktopTest` runs the renderer's tests (animation, layout, scroll, blur, letter synthesis) on the JVM. The full check is

```
.\gradlew.bat assembleDebug testDebugUnitTest :core:test :renderer:desktopTest lintDebug
```
