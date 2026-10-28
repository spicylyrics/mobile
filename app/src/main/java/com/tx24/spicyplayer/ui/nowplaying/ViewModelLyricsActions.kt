package com.tx24.spicyplayer.ui.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.playback.ExternalPlaybackViewModel
import com.tx24.spicyplayer.playback.PlayerUiState
import com.tx24.spicyplayer.playback.SourceChoice
import com.tx24.spicyplayer.translation.TranslationLanguages
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.ui.bigscreen.LanguageChoice
import com.tx24.spicyplayer.ui.bigscreen.LyricsActions
import com.tx24.spicyplayer.ui.bigscreen.LyricsOptions
import com.tx24.spicyplayer.ui.bigscreen.LyricsSourceChoice
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * The big-screen More menu's lyrics choices over the phone's view model and settings. Each
 * action makes the same call as the phone's own control: the source list's `useSource`, the
 * translate button's `toggleTranslation` (so turning it on asks for consent where the phone
 * does), the translation settings' target language, and the display settings.
 */
class ViewModelLyricsActions(
    private val viewModel: ExternalPlaybackViewModel,
    private val settings: CommonAppSettings,
) : LyricsActions {
    private val state = MutableStateFlow(lyricsOptions(viewModel.state.value, emptyList(), settings.translationMode, settings.romanizationMode))
    override val options: StateFlow<LyricsOptions> = state.asStateFlow()

    /** Follows the view model and the two display settings until cancelled. */
    suspend fun follow() {
        combine(viewModel.state, snapshotFlow { settings.translationMode to settings.romanizationMode }) { player, (translation, romanization) ->
            lyricsOptions(player, viewModel.sourceChoices(), translation, romanization)
        }.collect { state.value = it }
    }

    override fun useSource(id: String?) {
        viewModel.useSource(id)
    }

    override fun setTranslating(on: Boolean) {
        val now = viewModel.state.value
        // After a failure the translation shows off and stays asked for, as on the phone: on tries
        // again, off leaves it be.
        if (!on && now.translationFailed) return
        if (on != now.translationEnabled || (on && now.translationFailed)) viewModel.toggleTranslation()
    }

    override fun setTargetLanguage(code: String) {
        viewModel.setTranslationTarget(code)
    }

    override fun setTranslationMode(mode: TranslationMode) {
        settings.translationMode = mode
    }

    override fun setRomanizationMode(mode: RomanizationMode) {
        settings.romanizationMode = mode
    }
}

/** [ViewModelLyricsActions] for this composition, following the view model while it lasts. */
@Composable
fun rememberViewModelLyricsActions(viewModel: ExternalPlaybackViewModel, settings: CommonAppSettings): ViewModelLyricsActions {
    val actions = remember(viewModel, settings) { ViewModelLyricsActions(viewModel, settings) }
    LaunchedEffect(actions) { actions.follow() }
    return actions
}

/**
 * What the More menu shows for [state]: [choices] (the view model's `sourceChoices()`) as its
 * source list, the translation as the phone's translate button shows it (on, unless it failed),
 * the languages the chosen translator can translate into, and the display settings.
 */
fun lyricsOptions(
    state: PlayerUiState,
    choices: List<SourceChoice>,
    translationMode: TranslationMode,
    romanizationMode: RomanizationMode,
): LyricsOptions {
    val preferences = state.translationPreferences
    return LyricsOptions(
        sources = choices.map { LyricsSourceChoice(it.id, it.name, it.status, it.available, it.shown) },
        pickedSource = state.sourceOverride,
        translating = state.translationEnabled && !state.translationFailed,
        targetLanguage = preferences.targetLanguage,
        languages = TranslationLanguages.targets(preferences.provider, preferences.targetLanguage).map { (code, name) -> LanguageChoice(code, name) },
        translationMode = translationMode,
        romanizationMode = romanizationMode,
        message = when {
            state.translating -> "Translating…"
            state.translationEnabled && state.translationFailed -> "Couldn't translate this song. Turn translation on to try again."
            else -> null
        },
    )
}
