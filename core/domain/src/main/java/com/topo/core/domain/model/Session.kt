package com.topo.core.domain.model

/**
 * Une seance d'escalade : une date, un lieu, et les blocs travailles ce jour-la.
 *
 * [date] est un instant epoch en millisecondes UTC. Aucun fuseau n'est stocke :
 * la conversion en date locale se fait a l'affichage, sinon une seance changerait
 * de jour lors d'un voyage.
 */
data class Session(
    val id: String,
    val date: Long,
    val locationName: String,
    val notes: String?,
) {
    init {
        require(locationName.isNotBlank()) { "Nom de lieu vide" }
    }
}

/** Une session accompagnee de son compteur de blocs, pour les ecrans de liste. */
data class SessionSummary(
    val session: Session,
    val blocCount: Int,
    val completedCount: Int,
) {
    init {
        require(completedCount <= blocCount) {
            "Plus de blocs reussis ($completedCount) que de blocs ($blocCount)"
        }
    }
}
