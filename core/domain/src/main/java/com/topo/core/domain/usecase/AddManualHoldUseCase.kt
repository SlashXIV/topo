package com.topo.core.domain.usecase

import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.DetectedHold
import com.topo.core.domain.model.Hold
import com.topo.core.domain.repository.BetaRepository
import javax.inject.Inject

/**
 * Ajoute une prise designee au doigt par l'utilisateur.
 *
 * C'est le chemin NOMINAL du MVP : aucun modele de detection n'est embarque, et
 * l'application doit rester pleinement utilisable sans lui. La detection
 * automatique viendra pre-remplir cette meme liste, sans rien changer ici.
 *
 * Le geste utilisateur est un point, pas une boite : on en derive une boite
 * centree, dont la taille par defaut est exprimee en fraction de l'image et non en
 * pixels — sinon la prise changerait de taille selon la resolution de la photo.
 */
class AddManualHoldUseCase @Inject constructor(
    private val betaRepository: BetaRepository,
) {

    suspend operator fun invoke(
        blocId: String,
        centerX: Float,
        centerY: Float,
        size: Float = DEFAULT_SIZE,
    ): Hold {
        val box = BoundingBox(
            // Le centre est ramene dans l'image : un appui pres du bord ne doit pas
            // produire une prise invalide, juste une prise collee au bord.
            centerX = centerX.coerceIn(0f, 1f),
            centerY = centerY.coerceIn(0f, 1f),
            width = size.coerceIn(MIN_SIZE, MAX_SIZE),
            height = size.coerceIn(MIN_SIZE, MAX_SIZE),
        )
        return betaRepository.addHold(blocId, DetectedHold.manual(box))
    }

    private companion object {
        /** ~8 % de la largeur de l'image : ordre de grandeur d'une prise de bloc. */
        const val DEFAULT_SIZE = 0.08f
        const val MIN_SIZE = 0.01f
        const val MAX_SIZE = 0.5f
    }
}