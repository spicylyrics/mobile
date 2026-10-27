package com.tx24.spicyplayer.desktop

import kotlin.random.Random

/**
 * A made-up word-synced TTML song, so the spike needs no real lyrics: placeholder words with
 * syllable splits, held words long enough for the letter animation, background vocals, a duet
 * singer on the right and an instrumental gap for the interlude dots.
 */
object SyntheticSong {
    private val words = listOf(
        "lumen", "aster", "velo", "corin", "maren", "tidal", "sora", "nimbus", "ember", "halo",
        "drift", "orbit", "vesper", "solace", "quiet", "neon", "river", "glass", "ever", "north",
    )

    fun ttml(seed: Int = 7): String {
        val random = Random(seed)
        val body = StringBuilder()
        var t = 8_000L
        repeat(48) { index ->
            if (index == 24) t += 12_000L
            val agent = if (index % 6 in 3..4) "v2" else "v1"
            val count = random.nextInt(4, 8)
            val spans = StringBuilder()
            val start = t
            repeat(count) { w ->
                val word = words[random.nextInt(words.size)]
                // Every fifth line ends on a held word, which gets the letter-by-letter animation.
                val held = w == count - 1 && index % 5 == 2
                val duration = if (held) random.nextLong(1_400, 2_200) else random.nextLong(220, 520)
                val gap = if (w == count - 1) "" else " "
                if (!held && word.length > 4 && random.nextInt(3) == 0) {
                    val cut = word.length / 2
                    val half = duration / 2
                    spans.append(span(t, t + half, word.substring(0, cut)))
                    spans.append(span(t + half, t + duration, word.substring(cut)))
                    spans.append(gap)
                } else {
                    spans.append(span(t, t + duration, word)).append(gap)
                }
                t += duration
            }
            if (index % 4 == 1) {
                val bgStart = start + 300
                spans.append("<span ttm:role=\"x-bg\">")
                spans.append(span(bgStart, bgStart + 600, "(ooh")).append(' ')
                spans.append(span(bgStart + 600, bgStart + 1_200, "aah)"))
                spans.append("</span>")
            }
            body.append("<p begin=\"${s(start)}\" end=\"${s(t)}\" ttm:agent=\"$agent\">$spans</p>")
            t += random.nextLong(250, 900)
        }
        return """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" """ +
            """xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word"><head><metadata>""" +
            """<ttm:agent type="person" xml:id="v1"/><ttm:agent type="person" xml:id="v2"/>""" +
            """</metadata></head><body><div>$body</div></body></tt>"""
    }

    private fun span(from: Long, to: Long, text: String) = "<span begin=\"${s(from)}\" end=\"${s(to)}\">$text</span>"

    private fun s(ms: Long) = "%d.%03d".format(ms / 1000, ms % 1000)
}
