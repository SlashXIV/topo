package com.topo.core.domain.model

/**
 * Un mouvement de la sequence d'ascension (le "beta").
 *
 * Modelisation volontairement SIMPLIFIEE par rapport aux specifications initiales :
 * celles-ci prevoyaient une table d'association many-to-many entre mouvements et
 * prises. Or un mouvement a **au plus une** prise de depart et **exactement une**
 * prise d'arrivee — c'est une relation 1-1, pas N-N. La table de liaison etait de
 * la sur-normalisation, sa cle primaire composee interdisait de toute facon
 * plusieurs cibles, et elle ajoutait une jointure sur le chemin chaud du rendu.
 *
 * [sourceHoldId] est nul pour un mouvement de depart (la main vient du sol).
 */
data class Movement(
    val id: String,
    val blocId: String,
    val sequenceOrder: Int,
    val limb: Limb,
    val sourceHoldId: String?,
    val targetHoldId: String,
) {
    init {
        require(sequenceOrder >= 0) { "Ordre de sequence negatif : $sequenceOrder" }
        require(sourceHoldId != targetHoldId) {
            "Un mouvement ne peut pas partir et arriver sur la meme prise"
        }
    }

    /** true si le mouvement demarre au sol plutot que sur une prise. */
    val isStartingMove: Boolean get() = sourceHoldId == null
}

/**
 * Membre engage dans le mouvement.
 *
 * Les ordinaux sont persistes en base (colonne `limb_type`) et correspondent au
 * codage des specifications : 0 = main gauche, 1 = main droite, etc.
 * Ne JAMAIS reordonner ni inserer au milieu.
 */
enum class Limb {
    LEFT_HAND,
    RIGHT_HAND,
    LEFT_FOOT,
    RIGHT_FOOT,
    ;

    val isHand: Boolean get() = this == LEFT_HAND || this == RIGHT_HAND
}
