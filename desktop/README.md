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

## CI builds

`.github/workflows/desktop-build.yml` builds the MSI on a Windows runner for pull requests that touch `desktop/`, for `desktop-vX.Y.Z` tags and on demand (Actions → Desktop installer → Run workflow). The installer is a workflow artifact; nothing is released.

- A `desktop-vX.Y.Z` tag sets the version through `-PdesktopVersion=X.Y.Z`. Other runs use the default in `build.gradle.kts`.
- The Spicy Lyrics key comes from the `SPICY_LYRICS_CLIENT_KEY` repository secret, or the variable of that name. Without either, the build leaves that source out.
