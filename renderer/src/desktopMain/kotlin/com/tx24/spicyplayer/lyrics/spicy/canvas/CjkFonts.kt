package com.tx24.spicyplayer.lyrics.spicy.canvas

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Typeface
import com.tx24.spicyplayer.lyrics.spicy.models.Line
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontSlant
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.FontWidth
import java.util.concurrent.ConcurrentHashMap

/**
 * Windows' own fonts for Chinese, Japanese and Korean lyric lines.
 *
 * Skia's fallback picks Han glyphs by the Windows display language, so on an English system a
 * Chinese line came out in Yu Gothic, with the simplified-only characters in JhengHei. Compose
 * Desktop doesn't hand a TextStyle's locale to Skia, so instead a line with CJK in it is drawn
 * in one Windows font, the way Arabic and Georgian lines get theirs. Han is read the same way for
 * the whole song: Japanese if the song has kana, else simplified or traditional Chinese by which
 * of the characters only one of them uses appear more. Hangul lines get Malgun Gothic. Latin
 * words in those lines draw in the CJK font too.
 */
object CjkFonts {
    private const val JAPANESE = "Yu Gothic"
    private const val SIMPLIFIED_CHINESE = "Microsoft YaHei"
    private const val TRADITIONAL_CHINESE = "Microsoft JhengHei"
    private const val KOREAN = "Malgun Gothic"

    // Common characters written differently in simplified and traditional Chinese, pair by pair.
    private const val SIMPLIFIED_ONLY = "们这个来说国时会对过还没为见样东车门问间头长开关觉让听爱梦乐风飞两从无忆难恋泪边谁给认识应该带声伤绝终"
    private const val TRADITIONAL_ONLY = "們這個來說國時會對過還沒為見樣東車門問間頭長開關覺讓聽愛夢樂風飛兩從無憶難戀淚邊誰給認識應該帶聲傷絕終"

    // Null when Windows lacks the font, which leaves the line to Skia's fallback.
    private val families = ConcurrentHashMap<Pair<String, FontWeight>, java.util.Optional<FontFamily>>()

    // The Han font for the song on screen; null until a song is picked, which leaves every line to ours.
    @Volatile private var han: String? = null

    /** Picks the fonts for [lines]' song; the renderer asks for them as it measures each line. */
    fun useFor(lines: List<Line>) {
        han = hanFont(lines.joinToString("") { line -> line.words.joinToString("") { it.text } })
    }

    internal fun familyFor(text: String, weight: FontWeight): FontFamily? {
        val han = han ?: return null
        return fontFor(text, han)?.let { family(it, weight) }
    }

    internal fun fontFor(text: String, han: String): String? {
        var cjk = false
        for (script in text.codePoints().mapToObj(Character.UnicodeScript::of)) when (script) {
            Character.UnicodeScript.HANGUL -> return KOREAN
            Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> cjk = true
            else -> Unit
        }
        return if (cjk) han else null
    }

    internal fun hanFont(songText: String): String {
        val kana = songText.codePoints().anyMatch {
            Character.UnicodeScript.of(it).let { s -> s == Character.UnicodeScript.HIRAGANA || s == Character.UnicodeScript.KATAKANA }
        }
        if (kana) return JAPANESE
        val simplified = songText.count { it in SIMPLIFIED_ONLY }
        val traditional = songText.count { it in TRADITIONAL_ONLY }
        return if (traditional > simplified) TRADITIONAL_CHINESE else SIMPLIFIED_CHINESE
    }

    // Skia's typeface for the weight, its nearest face where the font has none (YaHei has no
    // SemiBold). Compose looks a typeface up again by its alias, and by the family name alone it
    // finds JhengHei's Regular whatever the weight (SystemFont does the same), so each weight
    // gets an alias of its own.
    private fun family(name: String, weight: FontWeight): FontFamily? = families.getOrPut(name to weight) {
        val style = FontStyle(weight.weight, FontWidth.NORMAL, FontSlant.UPRIGHT)
        java.util.Optional.ofNullable(FontMgr.default.matchFamilyStyle(name, style)?.let { FontFamily(Typeface(it, alias = "cjk-$name-${weight.weight}")) })
    }.orElse(null)
}
