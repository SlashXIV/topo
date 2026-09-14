package com.betaclimb.core.domain.model

/**
 * Une prise d'escalade, decrite par sa boite englobante.
 *
 * Toutes les coordonnees sont NORMALISEES dans `[0, 1]` par rapport a l'image
 * source. C'est un invariant du projet, pas un detail : il rend les prises
 * independantes de la resolution de la photo, du facteur de zoom du Canvas et
 * de l'orientation de l'ecran. La denormalisation est faite une seule fois, a
 * l'affichage, a partir des dimensions stockees sur le bloc.
 */
data class DetectedHold(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val confidence: Float,
    val origin: Origin,
) {
    /**
     * Provenance de la prise.
     *
     * Le modele YOLO n'existe pas encore, et l'application doit rester complete
     * sans lui : [MANUAL] est donc le chemin nominal du MVP, pas un repli. Cette
     * distinction sert aussi le recit "human-in-the-loop" (l'utilisateur corrige
     * les faux positifs) et permettra plus tard d'exporter les corrections comme
     * jeu de donnees d'entrainement.
     */
    enum class Origin { MANUAL, MODEL }

    init {
        require(width > 0f && height > 0f) { "Boite englobante degeneree" }
    }
}
