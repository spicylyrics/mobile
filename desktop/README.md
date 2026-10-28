# Spicy Lyrics Desktop

A lyrics window for whatever plays in the system's media controls. On Windows it reads the media session through a small Rust helper (`gsmtc/`), on Linux through `playerctl` (see [Media sources](#media-sources)), and it draws lyrics with the same renderer as the Android app.

## Desktop

You need JDK 17, and on Windows Rust (`cargo`): Gradle builds the helper with `cargo build --release` itself. Linux needs `playerctl` instead.

```
.\gradlew.bat :desktop:run                    # run from the checkout
.\gradlew.bat :desktop:createDistributable    # portable app: desktop\build\compose\binaries\main\app\Spicy Lyrics Desktop\
.\gradlew.bat :desktop:packageMsi             # installer:    desktop\build\compose\binaries\main\msi\
```

- The installer is an MSI that installs per user (no admin prompt), adds a Start menu entry and a desktop shortcut, and upgrades an earlier install in place. Keep `upgradeUuid` in `build.gradle.kts` the same forever, or upgrades install side by side.
- The version comes from `version` in `build.gradle.kts`, and must be `MAJOR.MINOR.BUILD`.
- The helper ships in the app's resources folder (`app\resources\gsmtc.exe`). The app looks there first, then in `gsmtc\target\release\` of the checkout.
- The publishable Spicy Lyrics key in the root `.env` (`SPICY_LYRICS_CLIENT_KEY=sl_pk_...`) is built in. Without it, the app leaves that source out.
- The builds are unsigned, so Windows SmartScreen warns about an unknown publisher the first time.

## Media sources

The app follows other players through a `MediaSource` (`MediaSource.kt`): a flow of now-playing snapshots (app id, title, artist, album, duration, position with the time it was true, status, rate, artwork) and transport commands, with a `capabilities` set saying which commands the platform takes. `mediaSource()` picks the implementation from `os.name`. `MediaSession` turns the snapshots into the lyric clock the same way on every platform, including the per-player corrections and the `--offset`.

| Platform | Source | Status |
| --- | --- | --- |
| Windows | `GsmtcMediaSource` | Works. Reads the gsmtc helper's JSON lines (`parseGsmtcLine`). Play, pause, seek, next and previous. |
| Linux | `MprisMediaSource` | Untested on a real desktop. Runs `playerctl --follow metadata --format ...` and parses its lines (`parsePlayerctlLine`), so it needs `playerctl` installed. All commands, through `playerctl --player=<instance>`. MPRIS gives no position stamp or rate, so each line counts from when it arrived, at 1x. Remote artwork (`https://` art URLs, e.g. Spotify) isn't downloaded yet; `file://` artwork works. |
| macOS | `macMediaSource()` | Not supported; it reports nothing. The likely routes are the MediaRemote framework through a helper, or AppleScript for Spotify and Music. |

To add a platform, implement `MediaSource`, keep the line parsing in a pure function with tests in `src/test/kotlin`, and add it to `mediaSource()`.

## Updates

An installed build checks GitHub's releases for a newer `desktop-vX.Y.Z` tag at most once a day (`update/UpdateCheck.kt`) and shows a dismissible banner that opens the release page. Drafts and the phone app's `v*` tags are ignored; prereleases count while the version is below 1.0.0. The last check is kept in `%APPDATA%\Spicy Lyrics Desktop\update-check.json` (Windows) or `~/.config/spicy-lyrics-desktop/` (elsewhere). Runs from the checkout report their version as `dev` and never check; pass `-Djpackage.app-version=0.0.1` to try the banner. Offline, it shows the last answer or nothing.

## CI builds

`.github/workflows/desktop-build.yml` builds the MSI on a Windows runner, a `.deb` on Ubuntu and a `.dmg` on macOS for pull requests that touch `desktop/`, for `desktop-vX.Y.Z` tags and on demand (Actions → Desktop installer → Run workflow). The installers are workflow artifacts; nothing is released.

- A `desktop-vX.Y.Z` tag sets the version through `-PdesktopVersion=X.Y.Z`. Other runs use the default in `build.gradle.kts`.
- The Spicy Lyrics key comes from the `SPICY_LYRICS_CLIENT_KEY` repository secret, or the variable of that name. Without either, the build leaves that source out.
- macOS won't take a version starting with 0, so the `.dmg` of 0.y.z says 1.y.z (`macPackageVersion` in `build.gradle.kts`).

## Sync delay

Ctrl+D in the lyrics window opens the sync-delay window (`SyncDelayWindow.kt`; `openSyncDelay()` for a menu). It nudges the followed player's offset in 10 or 50 ms steps with the current line in view, or works it out from taps along to the song's beats (`TapSync`, from the Spotify analysis). Positive shows the lyrics earlier. Offsets are saved per player app id in `sync-delay.properties` beside the settings file, as `desktop.offset.<app id>` and move the lyrics at once; `--offset` overrides them for every player while given.

## Linux

`./gradlew :desktop:packageDeb` builds a `.deb` (package `spicy-lyrics-desktop`) in `desktop/build/compose/binaries/main/deb/`. It installs the app under `/opt` with a menu entry. The app reads players through `playerctl`, so install that too (`sudo apt install playerctl`): without it the app stops at launch, naming what's missing. No Rust is needed on Linux.
