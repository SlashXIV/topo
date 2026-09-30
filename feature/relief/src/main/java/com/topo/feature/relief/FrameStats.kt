package com.topo.feature.relief

import java.util.Locale

/**
 * Cadence du fil GL, resumee par fenetres de quelques secondes.
 *
 * `gfxinfo` ne voit que l'interface Compose, et SurfaceFlinger ne publie plus
 * l'historique des trames par couche : la vue 3D se mesure donc elle-meme. L'intervalle
 * entre deux trames suit la presentation (eglSwapBuffers bloque jusqu'a la vsync) ; le
 * travail est le temps passe dans onDrawFrame avant l'echange de tampons.
 *
 * Tableaux prealloues : rien n'est alloue par trame, seulement un resume par fenetre.
 */
internal class FrameStats(
    private val windowNanos: Long = 5_000_000_000L,
    capacity: Int = 1024,
) {
    private val intervals = FloatArray(capacity)
    private val works = FloatArray(capacity)
    private var count = 0
    private var windowStart = 0L
    private var previousFrame = 0L

    /** @return un resume lisible quand la fenetre est ecoulee, sinon null. */
    fun record(frameStart: Long, workNanos: Long): String? {
        // Un ecart de plus de 250 ms est une pause (ecran quitte, surface recreee),
        // pas une saccade : il n'entre pas dans la mesure.
        if (previousFrame != 0L && count < intervals.size && frameStart - previousFrame < PAUSE_NANOS) {
            intervals[count] = (frameStart - previousFrame) / 1e6f
            works[count] = workNanos / 1e6f
            count++
        }
        previousFrame = frameStart
        if (windowStart == 0L) windowStart = frameStart
        if (frameStart - windowStart < windowNanos || count == 0) return null

        val n = count
        val median = intervals.copyOfRange(0, n).also { it.sort() }
        val medianInterval = median[n / 2]
        // Trame « en retard » : au-dela d'une periode et demie de la cadence mediane.
        val late = median.count { it > medianInterval * 1.5f }
        works.sort(0, n)
        val summary = String.format(
            Locale.ROOT,
            "Trames GL : %d en %.1f s, %.0f i/s, intervalle median %.2f ms, p99 %.2f ms, " +
                "max %.1f ms, en retard %d (%.1f %%) | travail median %.2f ms, p99 %.2f ms",
            n, (frameStart - windowStart) / 1e9f, n * 1e9f / (frameStart - windowStart),
            medianInterval, median[minOf(n - 1, n * 99 / 100)], median[n - 1],
            late, 100f * late / n, works[n / 2], works[minOf(n - 1, n * 99 / 100)],
        )
        count = 0
        windowStart = frameStart
        return summary
    }

    private companion object {
        const val PAUSE_NANOS = 250_000_000L
    }
}
