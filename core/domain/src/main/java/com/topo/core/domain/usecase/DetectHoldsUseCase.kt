package com.topo.core.domain.usecase

import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.DetectedHold
import com.topo.core.domain.model.Hold
import com.topo.core.domain.repository.BetaRepository
import com.topo.core.domain.repository.HoldDetector
import java.io.File
import javax.inject.Inject

/**
 * Pre-remplit les prises d'un bloc par detection automatique.
 *
 * La detection PROPOSE, l'utilisateur dispose : les prises detectees rejoignent
 * celles deja posees, et se corrigent avec les memes gestes — un appui sur une
 * prise la retire. Rien n'est jamais remplace : une prise posee a la main peut
 * porter des mouvements, la supprimer emporterait le beta avec elle.
 *
 * Relancer la detection est donc sans risque : une detection qui tombe sur une
 * prise existante est ecartee, et la seconde passe n'ajoute que ce que la
 * premiere avait manque.
 */
class DetectHoldsUseCase @Inject constructor(
    private val detector: HoldDetector,
    private val betaRepository: BetaRepository,
) {

    sealed interface Result {
        /**
         * @param added prises ajoutees au bloc.
         * @param alreadyPlaced detections ecartees car tombant sur une prise existante.
         */
        data class Added(val added: Int, val alreadyPlaced: Int) : Result

        /** Aucun modele embarque : le placement manuel reste le seul chemin. */
        data object Unavailable : Result
    }

    suspend operator fun invoke(blocId: String, image: File, existing: List<Hold>): Result {
        val detections = detector.detect(HoldDetector.ImageFile(image))
        // Lu APRES la detection : le modele est charge a la premiere demande.
        if (!detector.isReady) return Result.Unavailable

        val confident = detections.filter { it.confidence >= SUGGESTION_THRESHOLD }
        val fresh = confident.filterNot { detection ->
            existing.any { it.box.sameHoldAs(detection.box) }
        }
        if (fresh.isNotEmpty()) betaRepository.addHolds(blocId, fresh)

        return Result.Added(added = fresh.size, alreadyPlaced = confident.size - fresh.size)
    }

    /**
     * Deux boites designent la meme prise si le centre de l'une tombe dans
     * l'autre.
     *
     * Pas d'IoU ici : une prise posee au doigt a une boite de taille fixe, sans
     * rapport avec la vraie prise. Leur recouvrement serait faible alors qu'il
     * s'agit bien de la meme — c'est le centre, designe par le doigt, qui fait foi.
     */
    private fun BoundingBox.sameHoldAs(other: BoundingBox): Boolean =
        other.contains(centerX, centerY) || contains(other.centerX, other.centerY)

    private fun BoundingBox.contains(x: Float, y: Float): Boolean =
        x in left..right && y in top..bottom

    internal companion object {
        /**
         * Confiance minimale d'une prise PROPOSEE, plus stricte que le seuil du
         * moteur (0,25).
         *
         * Mesure sur une photo de salle reelle : au-dessus de 0,5, la quasi
         * totalite des detections sont des prises ; en dessous, on trouve le corps
         * d'un grimpeur, des taches sur le mur. Une fausse prise coute un appui a
         * retirer, et une trentaine de fausses prises rendraient la detection
         * plus penible que le placement manuel.
         */
        const val SUGGESTION_THRESHOLD = 0.5f
    }
}
