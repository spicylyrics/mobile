# Spicy Lyrics Desktop

A Windows lyrics window for whatever plays in Windows' media controls. It reads the media session through a small Rust helper (`gsmtc/`) and draws lyrics with the same renderer as the Android app.

## Desktop

You need JDK 17 and Rust (`cargo`). Gradle builds the helper with `cargo build --release` itself.

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

## CI builds

`.github/workflows/desktop-build.yml` builds the MSI on a Windows runner for pull requests that touch `desktop/`, for `desktop-vX.Y.Z` tags and on demand (Actions → Desktop installer → Run workflow). The installer is a workflow artifact; nothing is released.

- A `desktop-vX.Y.Z` tag sets the version through `-PdesktopVersion=X.Y.Z`. Other runs use the default in `build.gradle.kts`.
- The Spicy Lyrics key comes from the `SPICY_LYRICS_CLIENT_KEY` repository secret, or the variable of that name. Without either, the build leaves that source out.
