package com.topo.core.domain

import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.usecase.ReorderCircuitColorUseCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Reordonnancement du circuit de couleurs.
 *
 * L'ordre EST la difficulte : ces tests portent donc sur du metier, pas sur de
 * la presentation. Un index UNIQUE porte sur le rang en base, d'ou la
 * renumerotation systematique de la liste entiere.
 */
class ReorderCircuitColorUseCaseTest {

    private val reorder = ReorderCircuitColorUseCase()

    private val circuit = listOf(
        color("jaune", 0),
        color("vert", 1),
        color("bleu", 2),
        color("rouge", 3),
    )

    @Test
    fun `moving a colour up swaps it with the easier one`() {
        val result = reorder(circuit, colorId = "bleu", delta = -1)

        assertThat(result.map { it.id }).containsExactly("jaune", "bleu", "vert", "rouge").inOrder()
    }

    @Test
    fun `moving a colour down swaps it with the harder one`() {
        val result = reorder(circuit, colorId = "vert", delta = 1)

        assertThat(result.map { it.id }).containsExactly("jaune", "bleu", "vert", "rouge").inOrder()
    }

    @Test
    fun `ranks stay contiguous from zero`() {
        val result = reorder(circuit, colorId = "rouge", delta = -1)

        // Sans renumerotation, des suppressions successives laisseraient des
        // trous et un rang cesserait de correspondre a la position affichee.
        assertThat(result.map { it.rank }).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `moving the easiest colour up changes nothing`() {
        val result = reorder(circuit, colorId = "jaune", delta = -1)

        // Rien plutot qu'une erreur : les fleches sont desactivees aux
        // extremites, ce cas ne doit de toute facon pas etre atteignable.
        assertThat(result).isEqualTo(circuit)
    }

    @Test
    fun `moving the hardest colour down changes nothing`() {
        assertThat(reorder(circuit, colorId = "rouge", delta = 1)).isEqualTo(circuit)
    }

    @Test
    fun `an unknown colour leaves the circuit untouched`() {
        assertThat(reorder(circuit, colorId = "inconnue", delta = 1)).isEqualTo(circuit)
    }

    @Test
    fun `a circuit with holes is renumbered contiguously`() {
        val withHoles = listOf(color("jaune", 0), color("vert", 5), color("bleu", 9))

        val result = reorder(withHoles, colorId = "vert", delta = 1)

        assertThat(result.map { it.id }).containsExactly("jaune", "bleu", "vert").inOrder()
        assertThat(result.map { it.rank }).containsExactly(0, 1, 2).inOrder()
    }

    @Test
    fun `reordering works from an unsorted list`() {
        // La source peut arriver dans n'importe quel ordre : c'est le rang qui
        // fait foi, pas la position dans la collection recue.
        val shuffled = listOf(color("rouge", 3), color("jaune", 0), color("bleu", 2), color("vert", 1))

        val result = reorder(shuffled, colorId = "bleu", delta = -1)

        assertThat(result.map { it.id }).containsExactly("jaune", "bleu", "vert", "rouge").inOrder()
    }

    private fun color(id: String, rank: Int) =
        CircuitColor(id = id, name = id.replaceFirstChar(Char::uppercase), argb = 0xFF000000.toInt(), rank = rank)
}
