package com.tx24.spicyplayer.desktop

import com.tx24.spicyplayer.lyrics.LyricsNotices
import com.tx24.spicyplayer.lyrics.LyricsState
import com.tx24.spicyplayer.lyrics.TimedLine
import com.tx24.spicyplayer.lyrics.TimedWord
import com.tx24.spicyplayer.lyrics.spicy.romanization.RomanizationMode
import com.tx24.spicyplayer.network.data.LyricsBlends
import com.tx24.spicyplayer.network.data.LyricsSourceDescriptor
import com.tx24.spicyplayer.network.data.ProviderResult
import com.tx24.spicyplayer.network.data.RemoteLyricsPayload
import com.tx24.spicyplayer.network.data.RemoteLyricsQuality
import com.tx24.spicyplayer.network.data.RemoteLyricsResolution
import com.tx24.spicyplayer.network.data.RemoteLyricsSelection
import com.tx24.spicyplayer.translation.TranslationKey
import com.tx24.spicyplayer.translation.TranslationMode
import com.tx24.spicyplayer.translation.TranslationOutcome
import com.tx24.spicyplayer.translation.TranslationProvider
import com.tx24.spicyplayer.translation.TranslationResult
import com.tx24.spicyplayer.ui.settings.CommonAppSettings
import com.tx24.spicyplayer.ui.settings.InMemorySettingsStore
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLyricsTest {
    private val song = Track("Spotify", "Song", "Artist", "Album", 200_000L)
    private val other = Track("Spotify", "Other", "Artist", "Album", 180_000L)

    private fun descriptor(id: String) = LyricsSourceDescriptor(id, id.uppercase(), 0, emptySet())

    private fun lyrics(text: String) = LyricsState.Ready(
        lines = listOf(TimedLine(0L, 1_000L, listOf(TimedWord(text, 0L, 1_000L, attached = false)))),
        source = null, maker = null, uploader = null, songwriters = emptyList(), provider = text,
    )

    /** Source "a" has lyrics for every song, "b" only for [song]; "c" never answers. */
    private inner class FakeLookup : SongLookup {
        override val sources = listOf(descriptor("a"), descriptor("b"), descriptor("c"))
        val asked = mutableListOf<String?>()
        val spotifyIds = mutableListOf<String?>()
        /** What [known] held when each lookup started. */
        val knownAtStart = mutableListOf<Set<String>>()
        override val blends = LyricsBlends.ALL.take(2)
        var blendsOn: Set<String>? = null

        override fun useBlends(ids: Set<String>) {
            blendsOn = ids
        }

        override suspend fun find(track: Track, known: MutableMap<String, ProviderResult>, sourceId: String?, spotifyId: String?): LyricsLookup.Found {
            asked += sourceId
            spotifyIds += spotifyId
            knownAtStart += known.keys.toSet()
            val hits = mapOf("a" to "from a", "b" to "from b".takeIf { track == song })
            for (id in listOfNotNull(sourceId) .ifEmpty { listOf("a", "b") }) {
                known[id] = hits[id]?.let { ProviderResult.Hit(RemoteLyricsPayload(plainLyrics = it)) } ?: ProviderResult.Miss
            }
            val pick = (if (sourceId == null) "a" else sourceId).takeIf { hits[it] != null }
                ?: return LyricsLookup.Found(LyricsState.Idle, RemoteLyricsResolution.NotFound(emptyList()))
            val selection = RemoteLyricsSelection(descriptor(pick), RemoteLyricsPayload(plainLyrics = hits[pick]), RemoteLyricsQuality.PLAIN)
            return LyricsLookup.Found(lyrics(hits.getValue(pick)!!), RemoteLyricsResolution.Found(selection, emptyList()))
        }
    }

    private class FakeTranslator(var outcome: (String) -> TranslationOutcome) : SongTranslator {
        val targets = mutableListOf<String>()
        val providers = mutableListOf<Pair<TranslationProvider, String?>>()
        override suspend fun translate(track: Track, lyrics: LyricsState.Ready, target: String, provider: TranslationProvider, key: String?): TranslationOutcome {
            targets += target
            providers += provider to key
            return outcome(target)
        }
    }

    private fun translated(target: String) = TranslationOutcome.Translated(
        TranslationResult(TranslationKey("hash", target, TranslationProvider.Google, null, false, ""), listOf("[$target]"), detectedLanguage = "ja"),
    )

    private class Setup(val lyrics: DesktopLyrics, val lookup: FakeLookup, val store: InMemorySettingsStore, val settings: CommonAppSettings, val translator: FakeTranslator)

    private fun CoroutineScope.setup(store: InMemorySettingsStore = InMemorySettingsStore()): Setup {
        val settings = CommonAppSettings(InMemorySettingsStore(), pipButtonByDefault = false, prereleasesByDefault = true)
        val translator = FakeTranslator { translated(it) }
        val lookup = FakeLookup()
        return Setup(DesktopLyrics(lookup, translator, settings, store, this), lookup, store, settings, translator)
    }

    @Test fun aSongShowsTheAutomaticPickWithEachSourcesStatus() = runBlocking {
        val s = setup()
        s.lyrics.load(song)
        s.lyrics.settle()
        assertEquals("from a", (s.lyrics.shown.value?.lyrics as LyricsState.Ready).provider)
        val options = s.lyrics.options.value
        assertNull(options.pickedSource)
        assertEquals(listOf("a", "b", "c"), options.sources.map { it.id })
        assertEquals(listOf("Not synced", "Not synced", "Not asked yet"), options.sources.map { it.status })
        assertEquals(listOf(true, false, false), options.sources.map { it.shown })
    }

    @Test fun pickingASourceShowsItAndIsKeptForTheSong() = runBlocking {
        val s = setup()
        s.lyrics.load(song)
        s.lyrics.settle()
        s.lyrics.useSource("b")
        s.lyrics.settle()
        assertEquals("from b", (s.lyrics.shown.value?.lyrics as LyricsState.Ready).provider)
        assertEquals("b", s.store.getString(DesktopLyrics.sourceKey(song)))
        assertTrue(s.lyrics.options.value.sources.first { it.id == "b" }.shown)

        // Another session of the app, the same song: the pick comes back.
        val again = setup(s.store)
        again.lyrics.load(song)
        again.lyrics.settle()
        assertEquals("b", again.lyrics.options.value.pickedSource)
        assertEquals("from b", (again.lyrics.shown.value?.lyrics as LyricsState.Ready).provider)

        again.lyrics.useSource(null)
        again.lyrics.settle()
        assertNull(again.store.getString(DesktopLyrics.sourceKey(song)))
        assertEquals("from a", (again.lyrics.shown.value?.lyrics as LyricsState.Ready).provider)
    }

    @Test fun aSpotifyLinkRematchesTheSongAndIsKeptForIt() = runBlocking {
        val s = setup()
        s.lyrics.load(song)
        s.lyrics.settle()
        assertNull(s.lookup.spotifyIds.last())

        val id = "4uLU6hMCjMI75M1A2tKUQC"
        assertTrue(s.lyrics.setSpotifyLink("https://open.spotify.com/track/$id?si=x"))
        s.lyrics.settle()
        assertEquals(id, s.lookup.spotifyIds.last())
        assertEquals(id, s.store.getString(DesktopLyrics.spotifyKey(song)))
        assertEquals(id, s.lyrics.options.value.spotifyTrackId)
        // The answers for the old match are dropped: the sources are asked afresh.
        assertEquals(emptySet<String>(), s.lookup.knownAtStart.last())

        // Another session of the app, the same song: the match comes back.
        val again = setup(s.store)
        again.lyrics.load(song)
        again.lyrics.settle()
        assertEquals(id, again.lyrics.options.value.spotifyTrackId)
        assertEquals(id, again.lookup.spotifyIds.last())

        // Another song isn't matched by it.
        again.lyrics.load(other)
        again.lyrics.settle()
        assertNull(again.lookup.spotifyIds.last())
        assertNull(again.lyrics.options.value.spotifyTrackId)
    }

    @Test fun clearingTheSpotifyLinkGoesBackToTheAutomaticMatch() = runBlocking {
        val id = "4uLU6hMCjMI75M1A2tKUQC"
        val s = setup(InMemorySettingsStore(mapOf(DesktopLyrics.spotifyKey(song) to id)))
        s.lyrics.load(song)
        s.lyrics.settle()
        assertEquals(id, s.lookup.spotifyIds.last())

        s.lyrics.clearSpotifyLink()
        s.lyrics.settle()
        assertNull(s.store.getString(DesktopLyrics.spotifyKey(song)))
        assertNull(s.lyrics.options.value.spotifyTrackId)
        assertNull(s.lookup.spotifyIds.last())
        assertEquals(emptySet<String>(), s.lookup.knownAtStart.last())
    }

    @Test fun aBadSpotifyLinkChangesNothing() = runBlocking {
        val s = setup()
        s.lyrics.load(song)
        s.lyrics.settle()
        val lookups = s.lookup.asked.size

        assertFalse(s.lyrics.setSpotifyLink("https://open.spotify.com/album/4uLU6hMCjMI75M1A2tKUQC"))
        s.lyrics.settle()
        assertEquals(lookups, s.lookup.asked.size)
        assertNull(s.store.getString(DesktopLyrics.spotifyKey(song)))
        assertNull(s.lyrics.options.value.spotifyTrackId)
        assertEquals("Enter a valid Spotify track ID or URL", s.lyrics.options.value.message)
    }

    @Test fun blendsAreSwitchedForEverySongAndKept() = runBlocking {
        val s = setup()
        val (first, second) = LyricsBlends.ALL.take(2).map { it.id }
        assertEquals(emptySet<String>(), s.lookup.blendsOn)
        assertEquals(listOf(false, false), s.lyrics.options.value.blends.map { it.on })
        assertEquals("QQ Music timing", s.lyrics.options.value.blends.first().name)

        s.lyrics.load(song)
        s.lyrics.settle()
        val lookups = s.lookup.asked.size
        s.lyrics.setBlend(first, true)
        s.lyrics.settle()
        assertEquals(setOf(first), s.lookup.blendsOn)
        assertEquals(listOf(true, false), s.lyrics.options.value.blends.map { it.on })
        assertEquals(first, s.store.getString(DesktopLyrics.KEY_BLENDS))
        // Looked up again, over the answers already in hand.
        assertEquals(lookups + 1, s.lookup.asked.size)
        assertEquals(setOf("a", "b"), s.lookup.knownAtStart.last())

        // An unknown blend changes nothing.
        s.lyrics.setBlend("blend_nope", true)
        assertEquals(setOf(first), s.lookup.blendsOn)

        // Another session of the app starts with them on.
        val again = setup(s.store)
        assertEquals(setOf(first), again.lookup.blendsOn)
        again.lyrics.setBlend(second, true)
        again.lyrics.setBlend(first, false)
        assertEquals(setOf(second), again.lookup.blendsOn)
        assertEquals(second, again.store.getString(DesktopLyrics.KEY_BLENDS))
        again.lyrics.setBlend(second, false)
        assertNull(again.store.getString(DesktopLyrics.KEY_BLENDS))
    }

    @Test fun deepLIsUsedOnlyAfterItsConsentAndWithAKey() = runBlocking {
        val s = setup()
        assertEquals(listOf("Google", "DeepL"), s.lyrics.options.value.translators.map { it.id })
        assertEquals("Google", s.lyrics.options.value.translator)
        s.lyrics.load(song)
        s.lyrics.settle()
        s.lyrics.setTranslating(true)
        s.lyrics.settle()
        assertEquals(TranslationProvider.Google to null, s.translator.providers.last())

        // First the user's OK to what DeepL is sent; declining changes nothing.
        s.lyrics.setTranslator("DeepL")
        val consent = s.lyrics.options.value.consent!!
        assertEquals("translation_deepl", consent.id)
        assertTrue(consent.description.contains("DeepL"))
        assertEquals("Google", s.lyrics.options.value.translator)
        s.lyrics.answerConsent(false)
        assertNull(s.lyrics.options.value.consent)
        assertEquals("Google", s.lyrics.options.value.translator)
        assertNull(s.store.getString(DesktopLyrics.KEY_TRANSLATOR))

        // Agreed to, but with no key yet: it says so instead of asking DeepL.
        val asked = s.translator.providers.size
        s.lyrics.setTranslator("DeepL")
        s.lyrics.answerConsent(true)
        s.lyrics.settle()
        assertEquals("DeepL", s.lyrics.options.value.translator)
        assertEquals("DeepL", s.store.getString(DesktopLyrics.KEY_TRANSLATOR))
        assertEquals(setOf("translation_deepl"), s.store.getStringSet(DesktopLyrics.KEY_DISCLOSED))
        assertEquals(asked, s.translator.providers.size)
        assertEquals("Add a DeepL API key to translate with DeepL.", s.lyrics.options.value.message)

        s.lyrics.setTranslatorKey("  key:fx ")
        s.lyrics.settle()
        assertTrue(s.lyrics.options.value.translatorKeySet)
        assertEquals(TranslationProvider.DeepL to "key:fx", s.translator.providers.last())

        // Agreed once: switching back and forth asks no more.
        s.lyrics.setTranslator("Google")
        assertNotNull(s.lyrics.options.value.consent)
        s.lyrics.answerConsent(true)
        s.lyrics.setTranslator("DeepL")
        assertNull(s.lyrics.options.value.consent)
        assertEquals("DeepL", s.lyrics.options.value.translator)

        s.lyrics.setTranslatorKey("")
        assertFalse(s.lyrics.options.value.translatorKeySet)
        assertNull(s.store.getString(DesktopLyrics.KEY_DEEPL_KEY))
    }

    @Test fun aPickedSourceWithNothingFallsBackAndSaysSo() = runBlocking {
        val store = InMemorySettingsStore(mapOf(DesktopLyrics.sourceKey(other) to "b"))
        val s = setup(store)
        s.lyrics.load(other)
        s.lyrics.settle()
        assertEquals("from a", (s.lyrics.shown.value?.lyrics as LyricsState.Ready).provider)
        assertEquals("B has no lyrics for this song, so the usual pick is shown.", s.lyrics.options.value.message)
        assertEquals("b", s.lyrics.options.value.pickedSource)
    }

    @Test fun translationFollowsTheSwitchAndTheLanguage() = runBlocking {
        val s = setup(consented())
        s.lyrics.load(song)
        s.lyrics.settle()
        assertNull(s.lyrics.shown.value?.translation)

        s.lyrics.setTranslating(true)
        s.lyrics.setTargetLanguage("FR")
        s.lyrics.settle()
        assertEquals(listOf("[fr]"), s.lyrics.shown.value?.translation?.texts)
        assertEquals("fr", s.store.getString(DesktopLyrics.KEY_TARGET))
        assertEquals(true, s.store.getBoolean(DesktopLyrics.KEY_TRANSLATE))
        assertNull(s.lyrics.options.value.message)

        // On, the next song is translated as it lands.
        s.lyrics.load(other)
        s.lyrics.settle()
        assertEquals(listOf("[fr]"), s.lyrics.shown.value?.translation?.texts)

        s.lyrics.setTranslating(false)
        assertNull(s.lyrics.shown.value?.translation)
        assertFalse(s.lyrics.options.value.translating)
    }

    @Test fun aSongAlreadyInTheLanguageSaysSo() = runBlocking {
        val s = setup(consented())
        s.translator.outcome = { TranslationOutcome.Skipped("ja") }
        s.lyrics.load(song)
        s.lyrics.settle()
        s.lyrics.setTranslating(true)
        s.lyrics.settle()
        assertNull(s.lyrics.shown.value?.translation)
        assertEquals("Already in Japanese.", s.lyrics.options.value.message)

        s.translator.outcome = { error("offline") }
        s.lyrics.setTargetLanguage("de")
        s.lyrics.settle()
        assertEquals("Couldn't translate: offline", s.lyrics.options.value.message)
    }

    /** A store where Google Translate was already allowed. */
    private fun consented() = InMemorySettingsStore(mapOf(DesktopLyrics.KEY_DISCLOSED to setOf("translation_google")))

    @Test fun translatingFirstAsksAndSendsNothing() = runBlocking {
        val s = setup()
        s.lyrics.load(song)
        s.lyrics.settle()
        s.lyrics.setTranslating(true)
        s.lyrics.settle()
        val request = s.lyrics.options.value.pendingConsent
        assertEquals("translation_google", request?.id)
        assertEquals("Turn on translation?", request?.title)
        assertTrue(request!!.description.startsWith("Translation sends the song's original lyric lines"))
        assertTrue("Google Translate" in request.description)
        assertFalse(s.lyrics.options.value.translating)
        assertTrue(s.translator.targets.isEmpty())
        assertNull(s.store.getBoolean(DesktopLyrics.KEY_TRANSLATE))
        // A language change while it waits sends nothing either.
        s.lyrics.setTargetLanguage("fr")
        s.lyrics.settle()
        assertTrue(s.translator.targets.isEmpty())
    }

    @Test fun allowingTranslatesAndIsRemembered() = runBlocking {
        val s = setup()
        s.lyrics.load(song)
        s.lyrics.settle()
        s.lyrics.setTranslating(true)
        s.lyrics.answerConsent("translation_google", allowed = true)
        s.lyrics.settle()
        assertNull(s.lyrics.options.value.pendingConsent)
        assertTrue(s.lyrics.options.value.translating)
        assertEquals(1, s.translator.targets.size)
        assertEquals(setOf("translation_google"), s.store.getStringSet(DesktopLyrics.KEY_DISCLOSED))

        // Next run: on, with nothing to ask.
        val again = setup(s.store)
        assertTrue(again.lyrics.options.value.translating)
        again.lyrics.setTranslating(false)
        again.lyrics.setTranslating(true)
        assertNull(again.lyrics.options.value.pendingConsent)
    }

    @Test fun notNowLeavesItOff() = runBlocking {
        val s = setup()
        s.lyrics.load(song)
        s.lyrics.settle()
        s.lyrics.setTranslating(true)
        s.lyrics.answerConsent("someone_else", allowed = true)
        assertEquals("translation_google", s.lyrics.options.value.pendingConsent?.id)
        s.lyrics.answerConsent("translation_google", allowed = false)
        s.lyrics.settle()
        assertNull(s.lyrics.options.value.pendingConsent)
        assertFalse(s.lyrics.options.value.translating)
        assertEquals("Translation stays off; nothing was sent.", s.lyrics.options.value.message)
        assertTrue(s.translator.targets.isEmpty())
        assertNull(s.store.getStringSet(DesktopLyrics.KEY_DISCLOSED))
    }

    @Test fun onFromBeforeConsentStaysOffUntilAllowed() = runBlocking {
        val s = setup(InMemorySettingsStore(mapOf(DesktopLyrics.KEY_TRANSLATE to true)))
        assertFalse(s.lyrics.options.value.translating)
        s.lyrics.load(song)
        s.lyrics.settle()
        assertTrue(s.translator.targets.isEmpty())
        // The phone's consent with Genius covers Google alone too.
        assertTrue(DesktopLyrics.googleConsented(InMemorySettingsStore(mapOf(DesktopLyrics.KEY_DISCLOSED to setOf("translation_google_genius")))))
    }

    @Test fun displayModesAreTheSharedSettings() = runBlocking {
        val s = setup()
        s.lyrics.setTranslationMode(TranslationMode.Replace)
        s.lyrics.setRomanizationMode(RomanizationMode.UnderLine)
        assertEquals(TranslationMode.Replace, s.settings.translationMode)
        assertEquals(RomanizationMode.UnderLine, s.settings.romanizationMode)
        assertEquals(TranslationMode.Replace, s.lyrics.options.value.translationMode)
        assertEquals(RomanizationMode.UnderLine, s.lyrics.options.value.romanizationMode)
    }

    @Test fun clearingForgetsTheSong() = runBlocking {
        val s = setup()
        s.lyrics.load(song)
        s.lyrics.settle()
        s.lyrics.clear()
        assertNull(s.lyrics.shown.value)
        assertTrue(s.lyrics.options.value.sources.isEmpty())
        // Nothing playing: a pick has nowhere to go.
        s.lyrics.useSource("b")
        assertNull(s.store.getString(DesktopLyrics.sourceKey(song)))
    }

    @Test fun statusesForEachAnswer() {
        val sources = listOf("hit", "empty", "miss", "match", "cool", "none").map(::descriptor)
        val known = mapOf(
            "hit" to ProviderResult.Hit(RemoteLyricsPayload(syncedLyrics = "[00:01.00]hello")),
            "empty" to ProviderResult.Hit(RemoteLyricsPayload()),
            "miss" to ProviderResult.Miss,
            "match" to ProviderResult.NeedsMatch,
            "cool" to ProviderResult.CoolingDown(Instant.EPOCH),
        )
        val choices = sourceChoices(sources, known, shownId = "hit")
        assertEquals(
            listOf("Line-synced", "No usable lyrics", "No lyrics for this song", "Needs a Spotify link", "Unavailable right now", "Not asked yet"),
            choices.map { it.status },
        )
        assertEquals(listOf(true, false, false, false, false, true), choices.map { it.available })
        assertEquals(listOf(true, false, false, false, false, false), choices.map { it.shown })
    }

    @Test fun aBlendIsNeverListedAsUnasked() {
        val blend = LyricsBlends.ALL.first().descriptor
        val choices = sourceChoices(listOf(blend), emptyMap(), shownId = blend.id)
        assertEquals("Built from its sources' answers", choices.single().status)
        assertTrue(choices.single().available)
    }

    @Test fun theAppSharesOneStoreForTheChoices() {
        assertSame(DesktopLyrics.defaultStore(), DesktopLyrics.defaultStore())
    }

    @Test fun theDefaultLanguageIsTheSystemsWhereListed() {
        assertEquals("fr", DesktopLyrics.defaultTarget(Locale.FRANCE))
        assertEquals("en", DesktopLyrics.defaultTarget(Locale.forLanguageTag("tlh")))
        assertTrue(DesktopLyrics.LANGUAGES.any { it.code == "ja" && it.name == "Japanese" })
    }

    @Test fun theShownLyricsAreTheOnesTranslated() = runBlocking {
        val s = setup(consented())
        s.lyrics.load(song)
        s.lyrics.settle()
        val first = s.lyrics.shown.value?.lyrics
        s.lyrics.setTranslating(true)
        s.lyrics.settle()
        assertSame(first, s.lyrics.shown.value?.lyrics)
    }

    /**
     * Blocks its first lookup until it is cancelled, then, once [release]d, answers with an error, as
     * a lookup that swallows the cancel would: the stale answer comes after the fresh one.
     */
    private inner class CancelSwallowingLookup : SongLookup {
        private val real = FakeLookup()
        override val sources = real.sources
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        /** The stale answer has been handed back. */
        val done = CompletableDeferred<Unit>()
        private var calls = 0

        override suspend fun find(track: Track, known: MutableMap<String, ProviderResult>, sourceId: String?): LyricsLookup.Found {
            if (calls++ == 0) {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } catch (e: CancellationException) {
                    withContext(NonCancellable) { release.await() }
                    done.complete(Unit)
                    return LyricsLookup.Found(LyricsNotices.lookupFailed(e), null)
                }
            }
            return real.find(track, known, sourceId)
        }
    }

    @Test fun aCancelledLookupNeverReplacesTheNewerOne() = runBlocking {
        val lookup = CancelSwallowingLookup()
        val settings = CommonAppSettings(InMemorySettingsStore(), pipButtonByDefault = false, prereleasesByDefault = true)
        val lyrics = DesktopLyrics(lookup, FakeTranslator { translated(it) }, settings, InMemorySettingsStore(), this)
        lyrics.load(song)
        lookup.started.await()
        lyrics.useSource("b")
        lyrics.settle()
        assertEquals("from b", (lyrics.shown.value?.lyrics as LyricsState.Ready).provider)
        lookup.release.complete(Unit)
        lookup.done.await()
        yield()
        assertEquals("from b", (lyrics.shown.value?.lyrics as LyricsState.Ready).provider)
    }
}
