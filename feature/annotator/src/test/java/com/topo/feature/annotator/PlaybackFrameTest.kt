package com.topo.feature.annotator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Conversion d'une progression continue en instantane de rejeu.
 *
 * Coeur du rejeu, et fonction pure : elle se teste sur JVM sans emulateur, alors
 * que l'animation qui l'alimente, elle, demande un appareil.
 */
class PlaybackFrameTest {

    @Test
    fun `at the start nothing is drawn`() {
        val frame = playbackFrameAt(progress = 0f, movementCount = 5)

        assertThat(frame.completedMovements).isEqualTo(0)
        // Pas de mouvement en cours : a progression nulle il n'y a rien a
        // interpoler, et dessiner un segment de longueur zero ferait apparaitre
        // un point isole sur la premiere prise.
        assertThat(frame.activeIndex).isEqualTo(-1)
    }

    @Test
    fun `a fractional progress interpolates the next movement`() {
        val frame = playbackFrameAt(progress = 2.4f, movementCount = 5)

        assertThat(frame.completedMovements).isEqualTo(2)
        assertThat(frame.activeIndex).isEqualTo(2)
        assertThat(frame.activeProgress).isWithin(0.001f).of(0.4f)
    }

    @Test
    fun `landing exactly on a movement leaves nothing in progress`() {
        val frame = playbackFrameAt(progress = 3f, movementCount = 5)

        // Trois mouvements traces en entier, le quatrieme pas commence : c'est
        // exactement l'etat obtenu par le pas-a-pas.
        assertThat(frame.completedMovements).isEqualTo(3)
        assertThat(frame.activeIndex).isEqualTo(-1)
    }

    @Test
    fun `at the end everything is drawn and nothing is animating`() {
        val frame = playbackFrameAt(progress = 5f, movementCount = 5)

        assertThat(frame.completedMovements).isEqualTo(5)
        assertThat(frame.activeIndex).isEqualTo(-1)
    }

    @Test
    fun `progress beyond the sequence is clamped`() {
        val frame = playbackFrameAt(progress = 99f, movementCount = 5)

        // L'animation peut depasser d'une fraction de trame : sans borne, l'index
        // actif sortirait de la liste des mouvements.
        assertThat(frame.completedMovements).isEqualTo(5)
        assertThat(frame.activeIndex).isEqualTo(-1)
    }

    @Test
    fun `negative progress is clamped to the start`() {
        val frame = playbackFrameAt(progress = -3f, movementCount = 5)

        assertThat(frame.completedMovements).isEqualTo(0)
        assertThat(frame.activeIndex).isEqualTo(-1)
    }

    @Test
    fun `an empty beta has nothing to replay`() {
        val frame = playbackFrameAt(progress = 1.5f, movementCount = 0)

        assertThat(frame.completedMovements).isEqualTo(0)
        assertThat(frame.activeIndex).isEqualTo(-1)
    }

    @Test
    fun `speeds cycle back to the beginning`() {
        assertThat(PlaybackSpeed.HALF.next()).isEqualTo(PlaybackSpeed.NORMAL)
        assertThat(PlaybackSpeed.NORMAL.next()).isEqualTo(PlaybackSpeed.DOUBLE)
        // Un seul bouton fait defiler les vitesses : il doit revenir au debut
        // plutot que de rester bloque sur la plus rapide.
        assertThat(PlaybackSpeed.DOUBLE.next()).isEqualTo(PlaybackSpeed.HALF)
    }
}
