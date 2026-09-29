package com.topo.core.domain.usecase

import com.topo.core.domain.model.BlocDetail
import com.topo.core.domain.model.HoldColor
import com.topo.core.domain.model.HoldKind
import com.topo.core.domain.model.HoldOrigin
import com.topo.core.domain.repository.BetaRepository
import javax.inject.Inject
import kotlin.math.abs

/**
 * Ne garde que les prises du bloc : celles de la couleur d'une prise designee.
 *
 * La couleur est celle d'une PRISE du bloc, pointee par l'utilisateur, jamais
 * celle du circuit : la salle peut ouvrir par couleur de depart, la difficulte
 * portee par des fleches et les prises d'une tout autre couleur.
 *
 * Sont toujours conserves, quelle que soit leur couleur :
 *  - les VOLUMES, qui font partie du mur et servent a tous les blocs ;
 *  - les prises posees A LA MAIN : l'utilisateur les a voulues ;
 *  - les prises qui portent un MOUVEMENT : les retirer emporterait le beta ;
 *  - les prises SANS COULEUR mesuree, detectees avant que la couleur ne le soit :
 *    sans mesure, rien ne permet de dire qu'elles sont hors du bloc.
 *
 * Les petites prises grises de desescalade, elles, partent avec les autres : elles
 * ne sont pas des volumes, et le modele sait desormais faire la difference.
 */
class KeepHoldColorUseCase @Inject constructor(
    private val betaRepository: BetaRepository,
) {

    sealed interface Result {
        data class Filtered(val kept: Int, val removed: Int) : Result

        /** La prise designee n'a pas de couleur mesuree : posee a la main, par exemple. */
        data object NoReferenceColor : Result
    }

    suspend operator fun invoke(detail: BlocDetail, referenceHoldId: String): Result {
        val reference = detail.holdsById[referenceHoldId]?.color
            ?: return Result.NoReferenceColor

        val carryingMoves = detail.movements
            .flatMap { listOfNotNull(it.sourceHoldId, it.targetHoldId) }
            .toSet()

        val removed = detail.holds.filterNot { hold ->
            hold.id == referenceHoldId ||
                hold.kind == HoldKind.VOLUME ||
                hold.origin == HoldOrigin.MANUAL ||
                hold.id in carryingMoves ||
                hold.color == null ||
                hold.color.matches(reference)
        }
        betaRepository.deleteHolds(removed.map { it.id })

        return Result.Filtered(kept = detail.holds.size - removed.size, removed = removed.size)
    }

    internal companion object {
        /**
         * En dessous, une couleur est NEUTRE (blanc, gris, noir) : sa teinte n'est
         * que du bruit de capteur, et seule sa luminosite la distingue.
         *
         * Mesure sur une photo de salle reelle : prises grises a 3-4, prises
         * VIOLETTES a 7-9 seulement — l'eclairage de salle desature fortement. Un
         * seuil a 12, la valeur intuitive, aurait pris le violet pour du gris.
         */
        const val NEUTRAL_CHROMA = 6f

        /** Ecart de luminosite toleree entre deux neutres : blanc et gris clair se separent la. */
        const val NEUTRAL_LIGHTNESS_TOLERANCE = 18f

        /**
         * Ecart de teinte tolere entre deux couleurs saturees, en degres.
         *
         * La teinte et non la distance Lab complete : la meme prise passe de
         * l'ombre au projecteur en changeant de luminosite, beaucoup moins de
         * teinte. Mesure : trois prises violettes du meme mur s'etalent sur 23
         * points de luminosite mais 21 degres de teinte seulement.
         */
        const val HUE_TOLERANCE = 30f

        /** Garde-fou sur la luminosite : un rose et un rouge ont une teinte proche. */
        const val CHROMATIC_LIGHTNESS_TOLERANCE = 30f

        internal fun HoldColor.matches(reference: HoldColor): Boolean {
            val neutral = chroma < NEUTRAL_CHROMA
            val referenceNeutral = reference.chroma < NEUTRAL_CHROMA
            if (neutral != referenceNeutral) return false
            if (neutral) return abs(l - reference.l) <= NEUTRAL_LIGHTNESS_TOLERANCE

            val hueGap = abs(hue - reference.hue).let { minOf(it, 360f - it) }
            return hueGap <= HUE_TOLERANCE &&
                abs(l - reference.l) <= CHROMATIC_LIGHTNESS_TOLERANCE
        }
    }
}
