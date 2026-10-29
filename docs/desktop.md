# The desktop app and the big-screen UI

Spicy Lyrics Desktop (`:desktop`) is a Compose Desktop app for Windows, Linux and macOS. Like the phone app, it has no player of its own. It follows the music playing in another app, looks up lyrics from the same online sources and draws them with the shared renderer. The big-screen UI is the set of screens built for wide windows: the lyrics page, the settings panel and the Lyrics Manager. The desktop always uses it, and Android uses parts of it on tablets and DeX windows (see [Tablets and DeX](#tablets-and-dex)). This page shows how the pieces fit. `docs/renderer-module.md` has the detail on each module's contents and platform seams.

## Modules

Dependencies run one way: `:core` ← `:renderer` ← `:ui` ← `:app` and `:desktop`.

| Module | Kind | Holds |
|---|---|---|
| `:core` | JVM | Sources, blends, Spotify matching, TTML/LRC parsing (`LrcConverter`, `RemoteLyricsAdapter`), the local lyrics store (`LocalLyricsStore`), translation (`TranslationEngine`, `TranslationConsent`, `SourceDisclosures`), romanization, the lyric clock |
| `:renderer` | Multiplatform | The word-synced renderer (`LyricsView`, `RenderConfig`), its fonts, the Kawarp engine |
| `:ui` | Multiplatform | The shared Compose chrome and the big-screen screens (below) |
| `:app` | Android | The phone app, plus the Android adapters for the big-screen UI |
| `:desktop` | Compose Desktop | The desktop app: media sources, lookup, windows, data files |

The `:ui` packages the desktop and the big-screen UI lean on:

- `ui/bigscreen/`: the lyrics page (`BigScreenPage`, `BigScreenState`, `BigScreenMetrics`), the More menu (`MoreMenu.kt`), `LyricsActions` and the translation `ConsentPrompt`.
- `ui/settings/panel/`: the settings panel (`SettingsPanel`), its pure model (`SettingsPanelModel.kt`: `PanelRow`, `SliderSpec`, search, `panelLayoutFor`, `usesBigScreenSettings`), the row mapping (`settingsPanelSections`) and its line icons (`PanelIcons.kt`).
- `ui/lyricsmanager/`: the Lyrics Manager (`LyricsLibraryPanel`) over `LocalLyricsLibrary` and `LyricsFiles`, with the pure helpers `filterLibrary`, `pinCurrent`, `sanitizeFilename` and `acceptsFile`.
- `ui/settings/`: `SettingsStore` and its stores, `CommonAppSettings`, `PlatformActions`, and the settings-to-renderer mapping (`LyricsRendering.kt`).
- `ui/nowplaying/` and `ui/controls/`: `NowPlaying`, the header, controls, notices and cover views, shared with the phone.
- `ui/background/`: pure background helpers (`BackgroundChoice.kt`, `CoverColors`).
- `ui/components/`: tokens, glass, `SpicyBrand`, `SpicyToastHost`. Compose resources (`Res`, in `com.tx24.spicyplayer.ui.resources`) hold the app glyph.

`commonMain` in `:ui` stays free of platform APIs (no `java.`, `android.`, `System.` or `Math.`). `CommonMainPlatformApiTest` checks this. Platform pieces go behind the seams below, with the Android version in `androidMain` or `:app` and the desktop version in `desktopMain` or `:desktop`.

## Seams

**Media: `MediaSource`** (`:desktop`, `MediaSource.kt`). It provides a flow of now-playing snapshots (app id, title, artist, album, duration, position with its time stamp, status, rate, artwork) and removals, plus `control(app, transport, ms)` and a `capabilities` set listing the commands the platform takes. `mediaSource()` picks the implementation from `os.name`:

- **Windows:** `GsmtcMediaSource` runs the `gsmtc` helper, a Rust program in `desktop/gsmtc` built by cargo, Windows only. It handles play, pause, next, previous and seek.
- **Linux:** `MprisMediaSource` follows `playerctl`. Without `playerctl` on the PATH, the app won't start and says what to install.
- **macOS:** `MacMediaSource` polls Spotify and Music about once a second through `osascript`. It only asks players that are already running, so it never launches one. It handles play, pause, next, previous and seek. macOS asks for Automation permission for each player on first use. Music's artwork isn't read.

`MediaSession` turns snapshots into the lyric clock the same way on every platform, with the per-player corrections and sync delay.

**Now playing: `NowPlaying` and `PlaybackTransport`** (`:ui`, `ui/nowplaying/NowPlaying.kt`). `NowPlaying` provides `state: StateFlow<NowPlayingState>` (track, player, artwork, playing, can seek, custom actions). `PlaybackTransport` provides `positionMs()`, read every frame, plus play/pause, skip, seek and custom actions. The desktop's implementation is `DesktopNowPlaying` over `MediaSession`; Android's is `ViewModelNowPlaying` over the phone's view model. The page learns which buttons to show from `PlayerCapabilities` (`capabilitiesOf(source.capabilities)` on the desktop), never from `MediaSource`. Covers, motion covers and avatars come through `LocalNowPlayingPlatform`. Notice actions (copy, help link) come through `NoticeActions`.

**Settings: `SettingsStore` and `CommonAppSettings`** (`:ui`). `SettingsStore` is a typed key-value store with change listeners. It has three implementations:

- `SharedPreferencesStore` on Android;
- `FileSettingsStore` on the desktop: a type-tagged `.properties` file, written atomically;
- `InMemorySettingsStore` for tests.

`CommonAppSettings` holds every shared setting as Compose state, with the phone's keys and defaults. `docs/settings.md` covers adding one.

- **One instance per file.** A `FileSettingsStore` rewrites its whole file from what it holds, so two stores on one file wipe each other's writes. Each desktop file has exactly one owner, shared by every window:
  - `desktopSettings` (`SettingsPage.kt`) for `settings.properties`;
  - `DesktopLyrics.defaultStore()` for `lyrics.properties`;
  - `SyncDelays.defaultStore()` for `sync-delay.properties`.

  Put new values in one of these, or give them a file of their own. Never open a second store on an existing file.
- **One instance on Android too.** The tablet panel binds `MainActivity`'s own `AppSettings` (a `CommonAppSettings` over the phone's `SharedPreferencesStore`), so the panel and the phone pages read and write the same values.

**Platform actions: `PlatformActions`** (`:ui`, `LocalPlatformActions`). It opens a URL, the notification-access page or the app settings page, and picks a file or a folder. The desktop uses `DesktopPlatformActions` (the browser, AWT `FileDialog`, Swing `JFileChooser`; the two system pages aren't available). Android uses `rememberAndroidPlatformActions()` in `:app` (intents and the system file pickers). A screen that needs one provides it around itself.

**Lyrics options: `LyricsActions`** (`:ui`, `ui/bigscreen/LyricsActions.kt`, `LocalLyricsActions`).

- **`options` state:** a `LyricsOptions` with:
  - the song's sources in rank order, what each had and which is showing;
  - the per-song pick;
  - translation on/off, the target language and the language list;
  - the translation and romanization display modes;
  - a status line;
  - a pending consent request.
- **Calls:** `useSource`, `setTranslating`, `setTargetLanguage`, `setTranslationMode`, `setRomanizationMode`, `answerConsent`.
- **Without a provider,** the More menu's entries show as unavailable.
- **Implementations:**
  - Desktop: `DesktopLyrics`. It owns the playing song's lookup and Google Translate through `TranslationEngine`, and asks for consent before lyrics first go to Google (the `disclosed` key in `lyrics.properties`).
  - Tablets: `ViewModelLyricsActions` in `:app`, over the phone's view model.

**Lyrics library: `LocalLyricsLibrary`** (`:ui`, `ui/lyricsmanager/`). It lists entries, reads an entry's TTML and cover, gives a track's id (`idOf`), adds a file (saved, or applied just once), removes an entry and resets the just-once file. `LyricsFiles` (`LocalLyricsFiles`) opens and exports files.

The desktop's library is `DesktopLyricsLibrary`, a wrapper over `:core`'s `LocalLyricsStore`, which uses the phone's file format. It accepts LRC, converted with `LrcConverter`, and accepts a file only if it renders at least one line. `LyricsLookup` asks it before any online source unless a source was picked by hand in the More menu: first the just-once upload, then the saved entry.

**Rendering from settings: `renderConfig` and `lyricsDisplay`** (`:ui`, `ui/settings/LyricsRendering.kt`).

- `CommonAppSettings.renderConfig(songDurationMs)` maps the lyrics settings to a `RenderConfig`. The phone and the desktop call the same function.
- `lyricsDisplay(songDurationMs, landscape, romanizationAvailable)` adds the lyrics size for the orientation, romanize, the romanization and translation modes, and the credits modes.
- The font, the phone's landscape size correction and translation on/off stay with the host.
- The desktop's glue is `desktopLyrics(...)` in `DesktopSettingsGlue.kt`. Settings are Compose state, so a change on the settings page reaches the lyrics without a listener.

## Desktop windows

`Main.kt` builds one `MediaSource`, `MediaSession`, `DesktopNowPlaying`, `DesktopLyricsLibrary`, `LyricsLookup` and `DesktopLyrics`, and opens one main window plus the mini window. The main window shows the lyrics, or a page over them (`DesktopNav.kt`): Settings, the Lyrics Manager or Sync delay. A page fills the window and takes clicks and focus from the lyrics, which stay composed underneath and carry on. Esc or the page's close button goes back, and the lyrics jump to the line being sung. `--page settings|manager|sync` starts on a page, for screenshots with `--snap`.

- **The lyrics:** `BigScreenPage` with the lyrics view and the background as slots.
  - The view modes are page, cinema and fullscreen. `layoutFor` picks the side layout (cover beside the lyrics) or the compact one (song as a bar) from the window size.
  - The background follows `backgroundType` (`DesktopBackgrounds.kt`): Kawarp (`DesktopKawarp.kt`), Legacy, Color, Cover Art, Artist Header and Auto. Each falls back to Kawarp when it has no picture.
  - The update banner sits at the bottom.
- **Settings** (`SettingsPage.kt`): the phone's settings screen and quick settings over the lyrics, through `DesktopSettingsHost` (the followed player's delay, the source switches, the font picker, the About card). A wide window gets them as one floating panel with the pages down its side. The phone-only pages and rows are left out. Ctrl+, opens them; Esc steps back through them (`DesktopBack`).
- **Lyrics Manager** (`LyricsManagerPage.kt`): `LyricsLibraryPanel` as a page. After an upload, a reset or deleting the playing song's entry, it calls `DesktopLyrics.load` so the lyrics switch at once.
- **Sync delay** (`SyncDelayPage.kt`), a page: the followed player's offset, with ±10/±50 ms nudges, Reset and a tap pad that works the offset out from taps along to the beat (`TapSync`). `--offset` on the command line overrides it for every player.

### Keyboard shortcuts

| Keys | Where | Does |
|---|---|---|
| Ctrl+, (Cmd+, on a Mac) | main window | Opens the settings page |
| Ctrl+L (Cmd+L) | main window | Opens the Lyrics Manager page |
| Ctrl+D (Cmd+D) | main window | Opens the sync delay page |
| F11 | main window, on the lyrics | Toggles fullscreen (from page mode too) |
| Esc | main window | On a page, goes back to the lyrics; on the lyrics, steps back: fullscreen → cinema → page |
| Esc | settings page | Clears the search, goes back to the section list in the single-pane layout, then closes |
| Ctrl+F (Cmd+F) | settings | Focuses the search |

The page shortcuts go on the main window's `onPreviewKeyEvent`, so focused fields don't swallow them. Esc goes on `onKeyEvent`, so the settings page's own Esc steps (search, sections) run first. While a page is open, `DesktopNav.onPageKey` stops every key there, so the lyrics page's keys (`pageKeyOf`) don't act behind it.

## Where desktop data lives

**App data folder:** `FileSettingsStore.appDataDir(osName, env, home)`:

- `%APPDATA%\Spicy Lyrics Desktop\` on Windows;
- `$XDG_CONFIG_HOME/spicy-lyrics-desktop/` elsewhere, or `~/.config/spicy-lyrics-desktop/` when `XDG_CONFIG_HOME` is unset or blank.

| File | Owner | Holds |
|---|---|---|
| `settings.properties` | `desktopSettings` | Every `CommonAppSettings` value |
| `lyrics.properties` | `DesktopLyrics.defaultStore()` | The per-song source pick (`source:<title␟artist>`), the translate switch, the target language, translation consent (`disclosed`) |
| `sync-delay.properties` | `SyncDelays.defaultStore()` | Offsets per player, `desktop.offset.<app id, lowercased>` |
| `update-check.json` | `UpdateCheck` | The last release check's time and answer |
| `lyrics/` | `DesktopLyricsLibrary` | The Lyrics Manager's songs: `<key>.ttml`, `<key>.jpg`, `index.json`; the key is a SHA-256 of the trimmed, lowercased title and artist |

**Cache folder:** `%LOCALAPPDATA%\Spicy Lyrics\cache\`, or `Spicy Lyrics/cache` under the system temp folder where `LOCALAPPDATA` isn't set. It is safe to delete.

- `translations/`: Google Translate results.
- The Spotify extras cache: matches, audio analysis, artist headers.
- `backgrounds/`: artist header pictures (`DesktopImageCache`), one `.img` file per URL (SHA-1). A copy is fresh for 30 days after it was fetched or last shown; offline, a stale copy stands in. After each download the folder is trimmed to **100 MB and 500 files**, least recently used first.

## Tablets and DeX

At **≥ 840 dp wide and ≥ 480 dp tall** (`usesBigScreenSettings` in `:ui`: Material's expanded width plus a height floor), Android switches to the big-screen UI. Width alone isn't enough: a large phone on its side is about 915 × 411 dp and keeps the phone UI. The check reads the window's current size, so resizing in DeX, split screen or on unfold switches live.

| Phone below the line | Big screen at or above it |
|---|---|
| The phone's lyrics screen | `BigScreenPage` through `AndroidBigScreenPage.kt`. It adds a Popup Lyrics view control for picture-in-picture, keeps the screen on and hides the controls on the phone's idle timer. Back leaves cinema. |
| The phone's settings pages | The same pages as one floating panel with the pages down its side (`FloatingSettings`), through `AdaptiveSettings`. Back clears a search, then closes it. |
| The phone's source picker and translate button | The More menu, through `ViewModelLyricsActions`, with the phone's consent prompts |

The Lyrics Manager is not wired on Android yet. The phone keeps its own.

## CI and packaging

**Android CI** (`.github/workflows/android-ci.yml`) runs on pushes to `main`, `dev` and `desktop` and on pull requests:

```
assembleDebug testDebugUnitTest :core:test :renderer:desktopTest :ui:desktopTest :desktop:compileKotlin :desktop:test lintDebug
```

`PublicDocsTest`, `CommonMainPlatformApiTest` and the `:desktop` tests run as part of it. None of these tasks builds the Rust helper.

**Desktop installer** (`.github/workflows/desktop-build.yml`) runs on pull requests that touch `desktop/**` or the workflow, on `desktop-v*` tag pushes and by hand (`workflow_dispatch`). The workflow uploads artifacts only, kept 14 days; it creates no GitHub release.

| Job | Runner | Task | Artifact |
|---|---|---|---|
| `msi` | windows-latest | `:desktop:packageMsi` (builds `gsmtc.exe` with cargo) | `spicy-lyrics-desktop-<version or sha>` |
| `deb` | ubuntu-latest | `:desktop:packageDeb` | `spicy-lyrics-desktop-deb-<version or sha>` |
| `dmg` | macos-latest | `:desktop:packageDmg` | `spicy-lyrics-desktop-dmg-<version or sha>` |

Each artifact carries a `SHA256SUMS`.

- **Version:** a `desktop-vX.Y.Z` tag passes `-PdesktopVersion=X.Y.Z`, and builds without a tag stay at `0.1.0`. `desktop/build.gradle.kts` refuses any version that isn't `MAJOR.MINOR.BUILD` within the MSI's limits (255.255.65535). The Android release workflow's `v*` filter doesn't match `desktop-v*`, so the two never trigger each other.
- **macOS version mapping:** macOS refuses a major version of 0, so the dmg packages `0.y.z` as `1.y.z` (`macPackageVersion`). The MSI and deb keep `0.y.z`. Drop the mapping once the desktop reaches a real 1.x, or the versions collide.
- **Lyrics source key:** `SPICY_LYRICS_CLIENT_KEY` comes from the repository secret or variable of that name. A blank key leaves the Spicy Lyrics source out.
- **Unsigned packages:** the MSI is unsigned (SmartScreen warns), and the dmg is unsigned and not notarized (right-click → Open).
- **Installing:** the deb needs `playerctl` to follow players.

## Update check

`update/UpdateCheck.kt` asks GitHub's releases API (`spicylyrics/mobile`, unauthenticated) at most once a day. It caches the answer in `update-check.json`.

- **Which releases count** (`latestDesktopRelease`, in `Releases.kt`): only `desktop-vX.Y.Z` tags; drafts and the phone's `v*` tags are ignored. Prereleases count while the running version is below 1.0.0.
- **Running version:** read from `jpackage.app-version`, so only installed builds check. A run from the checkout reports `dev` and never checks. Pass `-Djpackage.app-version=0.0.1` to try the banner.
- **Offline or rate-limited:** the app keeps the last cached answer, or shows nothing, and asks again on the next launch.
- **The banner:** "Spicy Lyrics Desktop X.Y.Z is out". A click opens the release page, and × hides it until the next launch.
- **First release:** the installer workflow creates no releases, so nothing shows until a `desktop-v*` release is published on GitHub.
