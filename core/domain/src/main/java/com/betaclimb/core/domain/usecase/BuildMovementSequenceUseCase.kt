package com.betaclimb.core.domain.usecase

import com.betaclimb.core.domain.model.Limb
import com.betaclimb.core.domain.model.Movement
import javax.inject.Inject

/**
 * Transforme un trace gestuel en sequence de mouvements.
 *
 * Le trace arrive sous forme de chaine d'identifiants de prises, dans l'ordre ou
 * le doigt les a magnetisees. La conversion applique deux regles :
 *
 *  - une chaine de N prises produit N-1 mouvements : `h0 -> h1`, `h1 -> h2`, ... ;
 *  - une chaine d'UNE seule prise produit un mouvement de DEPART, de source nulle
 *    (la main vient du sol). C'est exactement ce que represente un depart de bloc,
 *    et cela evite d'avoir a inventer une prise fictive.
 *
 * La numerotation part de [startOrder] : un nouveau trace se chaine a la suite de
 * la sequence existante au lieu de l'ecraser.
 */
class BuildMovementSequenceUseCase @Inject constructor() {

    operator fun invoke(
        blocId: String,
        limb: Limb,
        holdIds: List<String>,
        startOrder: Int = 0,
        idGenerator: () -> String = { java.util.UUID.randomUUID().toString() },
    ): List<Movement> {
        if (holdIds.isEmpty()) return emptyList()

        // Deux prises consecutives identiques viennent d'un doigt qui s'attarde sur
        // la meme prise : ce n'est pas un mouvement, et Movement l'interdit.
        val chain = holdIds.deduplicateConsecutive()

        if (chain.size == 1) {
            return listOf(
                Movement(
                    id = idGenerator(),
                    blocId = blocId,
                    sequenceOrder = startOrder,
                    limb = limb,
                    sourceHoldId = null,
                    targetHoldId = chain.single(),
                ),
            )
        }

        return chain.zipWithNext().mapIndexed { index, (source, target) ->
            Movement(
                id = idGenerator(),
                blocId = blocId,
                sequenceOrder = startOrder + index,
                limb = limb,
                sourceHoldId = source,
                targetHoldId = target,
            )
        }
    }

    private fun List<String>.deduplicateConsecutive(): List<String> =
        filterIndexed { index, id -> index == 0 || this[index - 1] != id }
}
