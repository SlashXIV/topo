package com.topo.core.domain.model

/**
 * Une prise PERSISTEE, rattachee a un bloc.
 *
 * A distinguer de [DetectedHold], qui est le resultat transitoire d'une detection
 * et n'a pas encore d'identite. La separation evite qu'un identifiant nul circule
 * dans le domaine : une prise a un id, une detection n'en a pas.
 */
data class Hold(
    val id: String,
    val blocId: String,
    val box: BoundingBox,
    val confidence: Float,
    val origin: HoldOrigin,
    val kind: HoldKind = HoldKind.HOLD,
    /** Mesuree a la detection ; nulle pour une prise posee a la main. */
    val color: HoldColor? = null,
)

/**
 * Provenance d'une prise.
 *
 * Aucun modele YOLO n'est encore disponible, et l'application doit rester complete
 * sans lui : [MANUAL] est le chemin nominal du MVP, pas un repli. Cette distinction
 * sert aussi le fonctionnement "human-in-the-loop" (l'utilisateur corrige les faux
 * positifs du modele) et permettra d'exporter ces corrections comme jeu de donnees
 * d'entrainement.
 *
 * Les ordinaux sont persistes en base : ne JAMAIS reordonner ni inserer au milieu.
 * Toute nouvelle valeur s'ajoute a la fin.
 */
enum class HoldOrigin {
    MANUAL,
    MODEL,
}
