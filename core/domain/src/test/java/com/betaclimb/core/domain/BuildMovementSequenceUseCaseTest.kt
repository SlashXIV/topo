package com.betaclimb.core.domain

import com.betaclimb.core.domain.model.Limb
import com.betaclimb.core.domain.usecase.BuildMovementSequenceUseCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BuildMovementSequenceUseCaseTest {

    private val buildSequence = BuildMovementSequenceUseCase()
    private val ids = generateSequence(1) { it + 1 }.map { "mvt-$it" }.iterator()

    @Test
    fun `a chain of three holds produces two movements`() {
        val movements = buildSequence(
            blocId = "bloc-1",
            limb = Limb.RIGHT_HAND,
            holdIds = listOf("a", "b", "c"),
            idGenerator = ids::next,
        )

        assertThat(movements).hasSize(2)
        assertThat(movements[0].sourceHoldId).isEqualTo("a")
        assertThat(movements[0].targetHoldId).isEqualTo("b")
        assertThat(movements[1].sourceHoldId).isEqualTo("b")
        assertThat(movements[1].targetHoldId).isEqualTo("c")
    }

    @Test
    fun `a single hold produces a starting move with no source`() {
        val movements = buildSequence(
            blocId = "bloc-1",
            limb = Limb.LEFT_FOOT,
            holdIds = listOf("a"),
            idGenerator = ids::next,
        )

        // Un depart de bloc : la main ou le pied vient du sol. Inventer une prise
        // fictive pour satisfaire une source non nulle polluerait le modele.
        assertThat(movements).hasSize(1)
        assertThat(movements.single().isStartingMove).isTrue()
        assertThat(movements.single().targetHoldId).isEqualTo("a")
    }

    @Test
    fun `consecutive duplicates are collapsed`() {
        val movements = buildSequence(
            blocId = "bloc-1",
            limb = Limb.RIGHT_HAND,
            holdIds = listOf("a", "a", "a", "b", "b"),
            idGenerator = ids::next,
        )

        // Un doigt qui s'attarde sur une prise reemet la meme cible a chaque
        // evenement de pointeur. Sans ce filtrage, Movement rejetterait un
        // mouvement dont la source egale la cible.
        assertThat(movements).hasSize(1)
        assertThat(movements.single().sourceHoldId).isEqualTo("a")
        assertThat(movements.single().targetHoldId).isEqualTo("b")
    }

    @Test
    fun `a returning chain keeps both movements`() {
        val movements = buildSequence(
            blocId = "bloc-1",
            limb = Limb.RIGHT_HAND,
            holdIds = listOf("a", "b", "a"),
            idGenerator = ids::next,
        )

        // Revenir sur une prise deja utilisee est courant en escalade (repose,
        // changement de main) : seuls les doublons CONSECUTIFS sont filtres.
        assertThat(movements).hasSize(2)
        assertThat(movements[1].targetHoldId).isEqualTo("a")
    }

    @Test
    fun `numbering continues from the existing sequence`() {
        val movements = buildSequence(
            blocId = "bloc-1",
            limb = Limb.LEFT_HAND,
            holdIds = listOf("a", "b", "c"),
            startOrder = 7,
            idGenerator = ids::next,
        )

        // Un nouveau trace se chaine a la suite au lieu d'ecraser l'existant.
        assertThat(movements.map { it.sequenceOrder }).containsExactly(7, 8).inOrder()
    }

    @Test
    fun `an empty trace produces nothing`() {
        assertThat(buildSequence("bloc-1", Limb.RIGHT_HAND, emptyList())).isEmpty()
    }
}
