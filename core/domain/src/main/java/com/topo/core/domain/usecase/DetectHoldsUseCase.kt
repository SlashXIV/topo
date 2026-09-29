package com.topo.core.domain.usecase

import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.DetectedHold
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.HoldKind
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
 * prise existante n'en cree pas de seconde. Elle COMPLETE en revanche une prise
 * existante encore sans couleur — posee a la main, ou detectee avant que la
 * couleur ne soit mesuree — sans toucher a sa position ni a ses mouvements. C'est
 * ce qui rend le filtre par couleur utilisable sur un bloc deja annote.
 */
class DetectHoldsUseCase @Inject constructor(
    private val detector: HoldDetector,
    private val betaRepository: BetaRepository,
) {

    sealed interface Result {
        /**
         * @param added prises ajoutees au bloc.
         * @param alreadyPlaced detections tombant sur une prise existante.
         * @param completed prises existantes auxquelles la detection a donne une couleur.
         */
        data class Added(val added: Int, val alreadyPlaced: Int, val completed: Int = 0) : Result

        /** Aucun modele embarque : le placement manuel reste le seul chemin. */
        data object Unavailable : Result
    }

    /**
     * @param zone zone du bloc tracee par l'utilisateur, ou null pour toute la
     *        photo. Une photo de pan contient souvent plusieurs blocs : ne
     *        detecter que dans la zone ecarte les prises voisines a la source.
     */
    suspend operator fun invoke(
        blocId: String,
        image: File,
        existing: List<Hold>,
        zone: BoundingBox? = null,
    ): Result {
        val detections = detector.detect(HoldDetector.ImageFile(image, zone))
        // Lu APRES la detection : le modele est charge a la premiere demande.
        if (!detector.isReady) return Result.Unavailable

        val confident = demoteSmallVolumes(detections.filter { it.confidence >= SUGGESTION_THRESHOLD })

        val fresh = mutableListOf<DetectedHold>()
        val completed = mutableListOf<Hold>()
        for (detection in confident) {
            val placed = existing.firstOrNull { it.box.sameHoldAs(detection.box) }
            when {
                placed == null -> fresh += detection
                placed.color == null && detection.color != null ->
                    completed += placed.copy(color = detection.color, kind = detection.kind)
            }
        }
        if (fresh.isNotEmpty()) betaRepository.addHolds(blocId, fresh)
        // Une prise existante peut recouvrir plusieurs detections : elle n'est
        // completee qu'une fois, par la plus sure, qui vient la premiere.
        val uniqueCompleted = completed.distinctBy { it.id }
        if (uniqueCompleted.isNotEmpty()) betaRepository.updateHolds(uniqueCompleted)

        return Result.Added(
            added = fresh.size,
            alreadyPlaced = confident.size - fresh.size,
            completed = uniqueCompleted.size,
        )
    }

    /**
     * Ramene au rang de prise un « volume » a peine plus gros qu'une prise.
     *
     * Le modele confond parfois une petite prise grise avec un volume, et un
     * volume survit au filtre par couleur : cette prise de desescalade resterait
     * alors dans le bloc. Mesure sur une photo de salle reelle : les faux volumes
     * font 1 a 1,2 fois la prise mediane de la photo, les vrais 5 a 34 fois.
     *
     * RELATIF a la photo, pas absolu : la taille apparente depend de la distance
     * de prise de vue. Et seulement dans ce sens : un gros macro colore que le
     * modele appelle prise reste une prise, la taille seule ne fait pas un volume.
     */
    private fun demoteSmallVolumes(detections: List<DetectedHold>): List<DetectedHold> {
        val holdAreas = detections.filter { it.kind == HoldKind.HOLD }.map { it.box.area }.sorted()
        if (holdAreas.isEmpty()) return detections
        val medianHold = holdAreas[holdAreas.size / 2]
        return detections.map {
            if (it.kind == HoldKind.VOLUME && it.box.area < MIN_VOLUME_TO_HOLD_AREA * medianHold) {
                it.copy(kind = HoldKind.HOLD)
            } else {
                it
            }
        }
    }

    private val BoundingBox.area: Float get() = width * height

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

        /**
         * Un volume doit couvrir au moins 3 fois l'aire de la prise mediane de la
         * photo. Pris dans l'ecart mesure entre faux volumes (1 a 1,2) et vrais
         * volumes (5 et plus), loin des deux.
         */
        const val MIN_VOLUME_TO_HOLD_AREA = 3f
    }
}
