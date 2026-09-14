package com.betaclimb.feature.annotator

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Etat du trace en cours.
 *
 * POINT CRITIQUE DE PERFORMANCE
 * -----------------------------
 * Ces proprietes sont lues UNIQUEMENT dans la lambda de dessin
 * (`onDrawBehind`), jamais dans le corps d'un composable.
 *
 * Compose distingue trois phases : Composition, Layout, Dessin. Une lecture d'etat
 * effectuee pendant la Composition fait recomposer a chaque changement ; effectuee
 * pendant le Dessin, elle n'invalide QUE le dessin. Or [pointer] change a chaque
 * evenement de pointeur, soit potentiellement 120 fois par seconde : le lire dans
 * un composable declencherait 120 recompositions par seconde de tout le sous-arbre,
 * et c'est exactement le saccadement que ce sprint doit eviter.
 *
 * Le confinement de ces lectures a la phase de dessin est donc une contrainte
 * d'architecture, pas une micro-optimisation.
 */
@Stable
class BetaTraceState {

    /** Position courante du doigt, en pixels du canvas. Non specifiee au repos. */
    var pointer: Offset by mutableStateOf(Offset.Unspecified)
        internal set

    /** Prise actuellement magnetisee, pour le halo de retour visuel. */
    var snappedHoldId: String? by mutableStateOf(null)
        internal set

    /** Chaine des prises traversees, dans l'ordre. */
    val chain: MutableList<String> = mutableStateListOf()

    val isTracing: Boolean get() = pointer != Offset.Unspecified

    internal fun begin(position: Offset) {
        chain.clear()
        pointer = position
        snappedHoldId = null
    }

    internal fun update(position: Offset, snappedHoldId: String?) {
        pointer = position
        this.snappedHoldId = snappedHoldId

        // La chaine n'enregistre qu'un CHANGEMENT de prise magnetisee : tant que le
        // doigt reste dans le rayon d'attraction de la meme prise, rien n'est
        // ajoute. Sans cette condition, un doigt immobile empilerait des centaines
        // d'entrees identiques.
        if (snappedHoldId != null && chain.lastOrNull() != snappedHoldId) {
            chain.add(snappedHoldId)
        }
    }

    /** @return la chaine tracee, puis remet l'etat au repos. */
    internal fun finish(): List<String> {
        val result = chain.toList()
        reset()
        return result
    }

    internal fun reset() {
        chain.clear()
        pointer = Offset.Unspecified
        snappedHoldId = null
    }
}
