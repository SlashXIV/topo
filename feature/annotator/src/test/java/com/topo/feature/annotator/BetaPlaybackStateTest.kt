package com.topo.feature.annotator

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Machine a etats du rejeu : pas a pas, remise a zero, vitesses.
 *
 * Seules les operations instantanees sont testees ici. `play()` s'appuie sur
 * `animateTo`, qui exige une horloge de trames : son comportement visuel est
 * verifie sur appareil, pas en test unitaire.
 */
class BetaPlaybackStateTest {

    @Test
    fun `stepping forward advances one whole movement`() = runTest {
        val playback = BetaPlaybackState(movementCount = 5)

        playback.step(1)

        assertThat(playback.progress.value).isEqualTo(1f)
    }

    @Test
    fun `stepping realigns a progress caught mid movement`() = runTest {
        val playback = BetaPlaybackState(movementCount = 5)
        playback.progress.snapTo(2.7f)

        playback.step(1)

        // Le pas a pas se cale sur la grille des mouvements : avancer depuis 2,7
        // mene a 3 et non a 3,7. Sans cet alignement, les appuis successifs
        // laisseraient un residu fractionnaire indefiniment.
        assertThat(playback.progress.value).isEqualTo(3f)
    }

    @Test
    fun `stepping backward from mid movement returns to its start`() = runTest {
        val playback = BetaPlaybackState(movementCount = 5)
        playback.progress.snapTo(2.7f)

        playback.step(-1)

        assertThat(playback.progress.value).isEqualTo(1f)
    }

    @Test
    fun `stepping is bounded by the sequence`() = runTest {
        val playback = BetaPlaybackState(movementCount = 3)

        repeat(10) { playback.step(1) }
        assertThat(playback.progress.value).isEqualTo(3f)

        repeat(10) { playback.step(-1) }
        assertThat(playback.progress.value).isEqualTo(0f)
    }

    @Test
    fun `stepping stops playback`() = runTest {
        val playback = BetaPlaybackState(movementCount = 5)
        playback.progress.snapTo(2f)

        playback.step(1)

        // Avancer a la main pendant la lecture doit rendre la main a
        // l'utilisateur, sinon l'animation reprendrait par-dessus son pas a pas.
        assertThat(playback.isPlaying).isFalse()
    }

    @Test
    fun `an empty beta ignores stepping`() = runTest {
        val playback = BetaPlaybackState(movementCount = 0)

        playback.step(1)

        assertThat(playback.progress.value).isEqualTo(0f)
    }

    @Test
    fun `reset returns to the start`() = runTest {
        val playback = BetaPlaybackState(movementCount = 5)
        playback.progress.snapTo(4f)

        playback.reset()

        assertThat(playback.progress.value).isEqualTo(0f)
        assertThat(playback.isFinished).isFalse()
    }

    @Test
    fun `cycling speed walks through every value`() {
        val playback = BetaPlaybackState(movementCount = 5)

        assertThat(playback.speed).isEqualTo(PlaybackSpeed.NORMAL)
        playback.cycleSpeed()
        assertThat(playback.speed).isEqualTo(PlaybackSpeed.DOUBLE)
        playback.cycleSpeed()
        assertThat(playback.speed).isEqualTo(PlaybackSpeed.HALF)
        playback.cycleSpeed()
        assertThat(playback.speed).isEqualTo(PlaybackSpeed.NORMAL)
    }
}
