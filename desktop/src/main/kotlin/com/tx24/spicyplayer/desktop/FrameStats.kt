package com.tx24.spicyplayer.desktop

/** Frame intervals from the renderer's own frame loop, against the monitor's refresh interval. */
class FrameStats(refreshRate: Float) {
    private val vsyncMs = 1000.0 / refreshRate
    private val window = ArrayList<Double>()
    private val all = ArrayList<Double>()
    private var lastNs = 0L

    fun frame(frameNs: Long) {
        if (lastNs != 0L) {
            val ms = (frameNs - lastNs) / 1e6
            // A gap this long is the loop resting (nothing moving), not a frame.
            if (ms < 250) { window += ms; all += ms }
        }
        lastNs = frameNs
    }

    fun report(reset: Boolean): String = describe(window).also { if (reset) window.clear() }

    fun printSummary() = println("SUMMARY ${describe(all)}")

    private fun describe(ms: List<Double>): String {
        if (ms.isEmpty()) return "no frames"
        val sorted = ms.sorted()
        fun pct(p: Double) = sorted[((sorted.size - 1) * p).toInt()]
        val fps = ms.size / (ms.sum() / 1000.0)
        // A frame that took over one and a half refresh intervals showed the previous one twice.
        val missed = ms.count { it > vsyncMs * 1.5 }
        return "frames=${ms.size} fps=%.1f p50=%.2fms p95=%.2fms p99=%.2fms max=%.1fms missed=%d (%.2f%%)".format(
            fps, pct(0.5), pct(0.95), pct(0.99), sorted.last(), missed, 100.0 * missed / ms.size,
        )
    }
}
