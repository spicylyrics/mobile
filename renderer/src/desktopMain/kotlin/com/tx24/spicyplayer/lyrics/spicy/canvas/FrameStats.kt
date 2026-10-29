package com.tx24.spicyplayer.lyrics.spicy.canvas

import java.io.File
import java.time.LocalTime

/**
 * `-Dspicy.frameStats=<file>`: every 2 s, appends the lyrics' frame rate and how long their frames
 * really took against the time step the animation used, so slowed-down motion (a step cut shorter
 * than the frame) can be measured. Recorded on the UI thread, written by a daemon thread.
 */
internal object FrameStats {
    private val path: String? = System.getProperty("spicy.frameStats")

    /** Bucket upper bounds in ms; the last bucket is everything longer. */
    private val bounds = floatArrayOf(8f, 12f, 17f, 25f, 34f, 50f, 100f)
    private val counts = LongArray(bounds.size + 1)
    @Volatile private var frames = 0L
    @Volatile private var cut = 0L
    @Volatile private var rawSum = 0.0
    @Volatile private var usedSum = 0.0

    init {
        path?.let { file ->
            Thread({
                while (true) {
                    Thread.sleep(2_000)
                    write(File(file))
                }
            }, "spicy-frame-stats").apply { isDaemon = true }.start()
        }
    }

    fun record(rawS: Float, usedS: Float) {
        if (path == null) return
        val ms = rawS * 1000f
        counts[bounds.indexOfFirst { ms < it }.let { if (it < 0) bounds.size else it }]++
        frames++
        if (usedS < rawS - 0.0005f) cut++
        rawSum += rawS
        usedSum += usedS
    }

    private fun write(file: File) {
        val n = frames
        val fps = n / 2.0
        val spread = bounds.indices.joinToString(" ") { "<${bounds[it].toInt()}ms=${counts[it]}" } + " longer=${counts[bounds.size]}"
        val line = "${LocalTime.now()} fps=${"%.1f".format(fps)} cut=$cut/$n " +
            "real=${"%.2f".format(rawSum)}s used=${"%.2f".format(usedSum)}s $spread"
        frames = 0; cut = 0; rawSum = 0.0; usedSum = 0.0
        counts.fill(0)
        runCatching { file.appendText(line + System.lineSeparator()) }
    }
}
