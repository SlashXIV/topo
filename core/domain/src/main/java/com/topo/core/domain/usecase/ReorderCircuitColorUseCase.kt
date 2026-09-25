package com.topo.core.domain.usecase

import com.topo.core.domain.model.CircuitColor
import javax.inject.Inject

/**
 * Deplace une couleur d'un cran dans l'ordre de difficulte.
 *
 * Fonction pure sur la liste, et non une mise a jour de deux lignes en base :
 * un index UNIQUE porte sur le rang, donc echanger deux rangs par deux `UPDATE`
 * successifs violerait la contrainte entre les deux. La liste entiere est
 * renumerotee puis reecrite en une transaction — exactement le patron deja
 * employe pour `sequence_order` des mouvements.
 *
 * Renumerotation contigue a partir de 0 : sans elle, des suppressions
 * successives laisseraient des trous, et un rang cesserait de correspondre a la
 * position affichee.
 */
class ReorderCircuitColorUseCase @Inject constructor() {

    /**
     * @param delta -1 pour rendre la couleur plus facile, +1 plus difficile.
     * @return la liste reordonnee et renumerotee, ou la liste d'origine si le
     *         deplacement sort des bornes — deplacer la premiere vers le haut
     *         ne doit rien changer plutot que d'echouer.
     */
    operator fun invoke(
        colors: List<CircuitColor>,
        colorId: String,
        delta: Int,
    ): List<CircuitColor> {
        val ordered = colors.sortedBy(CircuitColor::rank)
        val from = ordered.indexOfFirst { it.id == colorId }
        if (from < 0) return colors

        val to = from + delta
        if (to !in ordered.indices) return colors

        val moved = ordered.toMutableList()
        moved.add(to, moved.removeAt(from))
        return moved.renumbered()
    }

    /** Renumerote une liste a partir de 0, dans son ordre courant. */
    fun renumber(colors: List<CircuitColor>): List<CircuitColor> = colors.renumbered()

    private fun List<CircuitColor>.renumbered(): List<CircuitColor> =
        mapIndexed { index, color -> color.copy(rank = index) }
}
