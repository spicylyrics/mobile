# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Spicy Lyrics Mobile (called Spicy Player until 0.9.1; the package and repository still use the old name) is an Android (Kotlin + Jetpack Compose) lyrics app for music playing in *another* app. It has no library and no playback service of its own. It reads the other app's MediaSession through a notification-listener grant, fetches lyrics from many online sources, and draws them with a word-synced renderer and animated backgrounds. Application ID: `com.tx24.spicyplayer.next`. The app module is `:app` with namespace `com.tx24.spicyplayer`. `:core` is a plain JVM Kotlin module (no Android SDK) holding the sources, blends, Spotify matching, TTML/LRC parsing, lyric models, romanization, translation and the lyric clock, in the same packages; `:renderer` is a Kotlin Multiplatform + Compose Multiplatform library (Android and desktop JVM targets) holding the word-synced lyrics renderer, its fonts and the Kawarp background engine (`docs/renderer-module.md`). `:ui`, also multiplatform, sits on `:renderer` and holds the shared Compose chrome: design tokens, glass, skeleton, glyphs, header metrics. `:app` depends on all three, and so does `:desktop`, the Compose Desktop app. Code in `:core` logs through `platform/CoreLog`, which `NextLyricsBackend` sends to logcat.

Code comments describe what the code does, not where it was ported from. Credits live in `THIRD_PARTY_NOTICES.md` and the README. Spicy Lyrics as a lyrics *source* (its API, provider, key) is fine to name. `PublicDocsTest` keeps `CLAUDE.md` and `docs/` free of provenance notes.

## Commands

Windows / PowerShell (use `./gradlew` from Git Bash):

```
.\gradlew.bat assembleDebug testDebugUnitTest :core:test :renderer:desktopTest :ui:desktopTest :desktop:compileKotlin :desktop:test lintDebug      # full check (from README)
.\gradlew.bat :core:test --tests "com.tx24.spicyplayer.lyrics.spicy.parser.TtmlLyricsParserTest"
.\gradlew.bat testDebugUnitTest --tests "*ScrollManagerTest.some test name*"
.\gradlew.bat installDebug
```

- Unit tests are plain JVM JUnit4. Provider, parser, blend and clock tests live in `:core` (`:core:test`) and need no Android SDK; the renderer's are in `:renderer` (`:renderer:desktopTest`, plain JVM) and the shared UI's in `:ui` (`:ui:desktopTest`); the rest are in `:app` (`testDebugUnitTest`), where `unitTests.isReturnDefaultValues = true` turns Android logging and clock calls into no-ops. XML parsing in tests uses kxml2.
- The live-network tests skip themselves unless you opt in: `RUN_SPICY_LYRICS_PROVIDER_TEST=1` (also needs `SPICY_LYRICS_CLIENT_KEY` in the environment) and `RUN_AMLL_NETWORK_TEST=1`. `RUN_MOTION_COVER_TEST=1` checks that the Apple Music animated-cover lookup still works. `RUN_SPOTIFY_CANVAS_TEST=1` checks that the Spotify Canvas lookup service still answers. `RUN_ALL_SOURCES_TEST=1` asks every source for a few well-known songs and prints a table (run with `--info`); use it to check which APIs still work.
- `SPICY_LYRICS_CLIENT_KEY` is read from a git-ignored root `.env` into `BuildConfig`. It must be a publishable `sl_pk_` key, never a secret `sl_sk_` key. Users can override it in Settings → Sources.
- `server/canvas/` is a Cloudflare Worker at `canvas.tx24.dev` that 0.14.4 asks for Spotify Canvas; it now forwards to Spicy Lyrics' Canvas service, which later versions ask directly. Keep it up until 0.14.4 is gone (`npm test` there, no Android needed).

## More docs

Read these only when the task calls for them:

- `docs/architecture.md`: before working inside a layer (session tracking, backend, sources, normalisation, rendering, UI).
- `docs/renderer-module.md`: before changing what `:renderer` holds, its platform seams (`expect`/`actual`, injected loaders) or how `:app` and `:desktop` use it.
- `docs/desktop.md`: before working on `:desktop` or the big-screen UI (tablets, DeX): its modules, seams, data files, shortcuts, packaging and update check.
- `docs/settings.md`: before adding or changing a setting.
- `docs/releases.md`: when asked to make a release. Tagged builds produce signed APK GitHub prereleases, and they double as the changelog.

## Architecture

The data flows through five layers. `docs/architecture.md` has the detail for each; read it before working on one.

1. **Session tracking**: `playback/`, reading the other app's MediaSession and keeping the lyric clock.
2. **Lyrics backend**: `lyrics/NextLyricsBackend.kt`, which holds the providers, the caches and the source preferences.
3. **Source selection**: `network/data/`, covering source ranking, Spotify matching and blends.
4. **Normalisation**: TTML, LRC or plain text become renderer `Line`/`Word` models.
5. **Rendering**: `lyrics/spicy/canvas/` in `:renderer`, the word-synced renderer. The Kawarp engine and shader are in `:renderer` too; its Android host (`kawarp/KawarpBackground.kt`) and the other backgrounds (`ui/background/`) are in `:app`.

The UI is split across these places:
- The lyrics screen is `ui/lyrics/LyricsScreen.kt` in `:ui`, shared with the desktop. `MainActivity.kt` hosts it on Android (class `LyricsActivity`; the name `MainActivity` is the default launcher alias, so pinned icons survive the icon choice), supplying the phone-only parts through `LyricsScreenHost`: picture-in-picture, the lock screen, haptics, refresh rate, the background, the pop-ups and settings.
- `ui/nowplaying/` holds the header, and `ui/controls/` the controls. Both live in `:ui`, behind the `NowPlaying` interface and the `NowPlayingPlatform` seam (`docs/renderer-module.md`); the Android-only parts (session artwork, animated-cover video, the view-model adapter) stay in `:app`'s `ui/nowplaying/`.
- `ui/settings/` holds settings: the screens in `:ui` behind `SettingsHost`, the phone-only pages in `:app` (`docs/settings.md`).

The design tokens and glass live in `ui/theme/SpicyTokens.kt` and `ui/components/` (`Spicy*` components), split between `:ui` (tokens, glass, skeleton, glyphs) and `:app` (the rest, until they move). New chrome uses those, not Material defaults.

Rules that apply even without the detail doc:
- Bump `CACHE_VERSION` whenever matching, conversion or TTML layout changes. Otherwise stale cached lyrics keep being served.
- The Spotify matching heuristics are settled, so don't re-tune them without cause.
- Judge smoothness on a release build (R8-minified; `assembleRelease` signs with the debug key locally). Debug builds are a separate app, `.next.debug` named "Spicy Lyrics Mobile Debug".
- TTML is first-class: word timing never gets flattened to LRC.

## Renderer conventions learned the hard way

- Effect sizes (blur, glow) scale by lyric text size relative to 56px desktop lyrics, **not** by screen density.
- Compose keeps a `TextLayoutResult`'s last shadow. Always pass `Shadow.None` and draw glow as a separate pass.
- For visual bugs, capture the device screen (e.g. `adb shell screenrecord`) and measure pixels before theorising. Do not drive the phone with `adb input` or launch intents on it.
