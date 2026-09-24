package com.topo.core.domain.usecase

import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.Viewport
import javax.inject.Inject

/** Prise magnetisee, avec sa distance au pointeur — utile au retour visuel. */
data class SnapTarget(
    val hold: Hold,
    val distancePx: Float,
)

/**
 * Trouve la prise vers laquelle magnetiser le trace en cours.
 *
 * COMPLEXITE — O(N), lineaire, et c'est un choix defendu.
 * Un KD-Tree ou un R-Tree ramenerait la recherche du plus proche voisin a
 * O(log N). Mais un mur de bloc depasse tres rarement 150 prises : parcourir
 * 150 candidats a chaque trame de 16 ms represente quelques microsecondes sur
 * n'importe quel ARM moderne. Construire et maintenir un arbre spatial couterait
 * plus cher que la recherche elle-meme, ajouterait une structure a invalider a
 * chaque ajout de prise, et de la complexite a relire. Voir le test de budget
 * temporel qui accompagne cette classe.
 *
 * GEOMETRIE — le calcul se fait en PIXELS, pas en coordonnees normalisees, sinon
 * le rayon d'attraction serait ovale sur une image non carree (cf. [Viewport]).
 *
 * RACINE CARREE — la distance euclidienne est
 * `d = sqrt((xc - xp)^2 + (yc - yp)^2)`. La comparaison au seuil est faite sur les
 * distances au CARRE : l'ordre est identique, et cela evite N racines carrees par
 * trame. La racine n'est calculee qu'une fois, sur le gagnant, parce que la valeur
 * elle-meme sert au retour visuel.
 */
class FindSnapTargetUseCase @Inject constructor() {

    /**
     * @param pointerX,pointerY position du doigt, en coordonnees normalisees [0,1].
     * @param thresholdPx rayon d'attraction, en pixels de l'ecran.
     * @return la prise la plus proche sous le seuil, ou `null` si aucune.
     */
    operator fun invoke(
        pointerX: Float,
        pointerY: Float,
        holds: List<Hold>,
        viewport: Viewport,
        thresholdPx: Float,
    ): SnapTarget? {
        if (holds.isEmpty() || thresholdPx <= 0f) return null

        val thresholdSquared = thresholdPx * thresholdPx
        var best: Hold? = null
        var bestDistanceSquared = Float.MAX_VALUE

        // Boucle indexee et non `minByOrNull` : une iteration sur List<Hold> par
        // index n'alloue aucun iterateur, ce qui compte quand la boucle tourne a
        // chaque evenement de pointeur.
        for (index in holds.indices) {
            val hold = holds[index]
            val dx = (hold.box.centerX - pointerX) * viewport.widthPx
            val dy = (hold.box.centerY - pointerY) * viewport.heightPx
            val distanceSquared = dx * dx + dy * dy

            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared
                best = hold
            }
        }

        if (best == null || bestDistanceSquared > thresholdSquared) return null

        return SnapTarget(
            hold = best,
            distancePx = kotlin.math.sqrt(bestDistanceSquared),
        )
    }
}
