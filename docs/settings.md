# Adding a setting

The settings screen lives in `ui/settings/` in `:ui`, shared with the desktop; its controls in `ui/components/SettingsControls.kt`. What a page asks of the platform goes through `SettingsHost` (the delays, which pages and rows exist there) and `SettingsState` (the player's summaries). Pages and rows that need the phone's view model or Android stay in `:app` as host slots (`AndroidSettingsHost.kt`: This song, Romanization & Translation, Sources, Advanced, backups, app icon, haptics, the queue). A row only one platform has goes behind a host flag (`phoneDevice`, `queueAndSource`, `lockScreen`). In a wide desktop window the same pages show in one floating panel with the pages down its side (`FloatingSettings`).

1. **Pick its page.** The groups are This song, Lyrics (how the lyrics look and animate, text), Romanization & Translation, Scroll & Sync (delay and scrolling), Background, Now Playing (song header, cover, credits), Controls (hiding and floating buttons), Theme, Sources, Device (screen, performance, haptics) and Advanced. Keep each page to about ten rows. Only make a new group when none fits. A new group needs a `SettingsPage` entry, a row on the first page (`HomeGroups`) with a live one-line summary, a case in `PageContent`, and a placeholder in `PageSkeleton`.

   **Nested groups.** When one feature has a group of its own (about four rows or more, all about one thing, like Floating lyrics), give it a page inside its parent page instead of crowding the parent:
   - A `SettingsPage` entry with `parent = ...`.
   - A `SubPageCard { SubPageRow(page, icon, summary) }` on the parent page, with a live summary.
   - A case in `PageContent` and a placeholder in `PageSkeleton`.

   Nest at most two levels below the first page. Search lists a nested page's rows under its full path ("Device › Floating lyrics"), and hides the card itself.

   **Everyday rows first.** A page shows the rows people actually change, and folds the rest into one `MoreOptions("…")` at its end, whose summary names what's inside. Decide by the usage dashboard: a setting that around 5% or more of installs move off its default stays visible, and a new feature stays visible until there's data on it. Search shows folded rows unfolded, so a folded row needs nothing extra to be found. A page of about five rows or fewer needs no fold.
2. **Store it where it's used.**
   - How the lyrics screen looks and behaves (word motion, refresh rate, update channel): a property in `CommonAppSettings` (`ui/settings/CommonAppSettings.kt` in the shared `:ui` module), which reads as Compose state and is stored through `SettingsStore` (the `ui` SharedPreferences on Android). Add it to `CommonAppSettingsTest`, which pins every key and default. A setting whose type lives in `:app` is stored by name there, with a typed view in `AppSettings`.
   - Lookup, sources or playback: a field in `PlayerUiState` plus a view-model function that saves it.
   - Keep the logic free of Android code where possible, for the Kotlin Multiplatform move after v1.
3. **Build it from the shared controls**, never Material ones:
   - `ToggleRow` for on/off;
   - `SettingRow` with an `SpicySelect` for a choice between named options, or `SpicyMultiSelect` to pick several. From 12 options on, the menu gets a search field (`matchingOptions`), so a long list like the languages needs nothing extra;
   - `SettingRow` with an `SpicyButton` or `SpicyTextField` for actions and text;
   - `SettingRow(stacked = true)` with `SpicyBipolarSlider` for amounts.

   Wording follows Spicy Lyrics: a short label, and a one-sentence description that ends with a full stop. Keep descriptions to one line where possible. Give every row a fitting `Icons.Rounded` icon; the shared controls apply the neighbouring rows' secondary tint.
4. **Search matches rows by their label and description automatically.** When shortening an existing description, retain its old text in `searchDescription` so old searches still find the row. Anything else (a card, a note) needs wrapping in `Searchable(...)`, or search won't find it. A page's first rows go without a section title, because the page header already names them. Later groups on the same page go in `SettingsSection("Title")`.
5. **Keep the placeholder honest.** If a page gains or loses several rows, update its row count in `PageSkeleton`. If the first page's summary for that group should mention the new setting, update it too.
6. **Don't repeat what the lyrics screen already has.** For example, romanize is a button there.
7. **A setting only a mouse can use** (Line Hover Background, `lineHoverBackground`) still lives in `CommonAppSettings` and reaches the renderer through `renderConfig()`, but gets a row only in the big-screen settings panel (`ui/settings/panel/SettingsPanelSections.kt`), not on the phone's pages. Without a hovering pointer it must do nothing, so the phone looks the same either way.

## Romanization & Translation

`RomanizationTranslationContent` in `ui/settings/TranslationSettings.kt` owns the combined page. Romanization comes first: Romanization (Replace the line / Under each line), Human romanizations. Translation follows: Target language, Translate automatically, Don't translate. More options holds the rest: Romanize button, Translate button, Display mode, Provider with its conditional rows (the DeepL key; the AI service address, model and key for OpenAI-compatible; the Gemini model and key; Gemini Nano's state and download), Use human translations from Genius. The per-song Lyrics language override stays in This song and quick settings. The translate button itself only shows where a translation would help (`translateButtonHelps`): not on songs known to be in the target language or a Don't translate one, judged by the per-song override, the provider's answer, or else the phone's own on-device guess (`SystemLanguageDetector`, Android 10+). That guess never becomes a translation's source language, which stays per line for mixed songs.

The languages come from `TranslationLanguages.common` in `translation/TranslationModels.kt`. Don't translate and Lyrics language offer every listed language. Target language offers only the ones the chosen provider can translate into (`TranslationLanguages.supports`). Every code, whether picked, stored or detected by a provider, goes through `languageCode`, which drops the region and maps old or alternative codes to the listed one (`fil` to `tl`, `iw` to `he`, `jw` to `jv`). New aliases go there.

Moving a row changes only where it appears. Keep its label, callback and storage key. Romanize and Romanize button remain `ui.romanize` and `ui.romanizeButton`, and Translate button is `ui.translateButton`; the display choices remain `ui.romanizationMode` and `ui.translationMode`. Human romanizations remains `lyrics_sources.humanRomanizations`; translation preferences stay in `translation`, and per-song language overrides in `song_languages`.

Settings backups include the `ui`, `lyrics_sources`, `translation` and `song_languages` stores through `SettingsBackup.STORES`. `SettingsBackupStore` validates types when restoring. DeepL and AI API keys stay in their separate device-only store (the AI address and models are backed up with `translation`); translation disclosure consent is also kept on the device. Do not migrate preferences or change backup schemas for a row move.

## Big-screen page

The big-screen page (`ui/bigscreen/`, the desktop window and, later, tablets and DeX) reads its settings through `CommonAppSettings.bigScreenOptions()` into `BigScreenOptions`, and the host passes them to `BigScreenPage`. Phones never show this page, so these settings change nothing there unless the phone's own screens read them too. The page follows:

- Hide Controls, Hide After and Hide While Paused: in cinema and fullscreen the cover's controls fade once the pointer has rested off the cover for the delay (`IdleControls.kt`). Paused, they stay unless Hide While Paused is on.
- Timeline Outside Media Box: in cinema and fullscreen the timeline sits between the cover and the song instead of on the cover.
- View Controls Position (`viewControlsPosition`, Top or Bottom): where the page's view controls sit, 24 px in from that edge. Big screens only.
- No Lyrics Notice (`showNoLyricsNotice`): off, a song without lyrics in cinema and fullscreen shows only the song, its cover centred. Other notices still show. Big screens only.
- Hide the Song Header: no now-playing bar on the page and no song bar in the compact layout. Cinema and fullscreen keep the cover.
- Pinned Lyrics Footer: the host's lyrics view pins the credits at the bottom of the lyrics box.

A new setting for the page goes in `BigScreenOptions` and `bigScreenOptions()`, with a test in `BigScreenOptionsTest`, and in the settings panel (`ui/settings/panel/SettingsPanelSections.kt`).
