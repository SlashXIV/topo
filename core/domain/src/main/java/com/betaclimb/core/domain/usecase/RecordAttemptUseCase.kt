package com.betaclimb.core.domain.usecase

import com.betaclimb.core.domain.model.Bloc
import com.betaclimb.core.domain.model.BlocStatus
import com.betaclimb.core.domain.repository.BlocRepository
import javax.inject.Inject

/**
 * Enregistre un essai sur un bloc.
 *
 * Un bloc repere mais jamais essaye passe automatiquement de [BlocStatus.PROJECT] a
 * [BlocStatus.WORKING] : au pied du mur, personne ne pense a changer un statut
 * avant de compter un essai, et laisser un bloc « projet » avec trois essais au
 * compteur serait une incoherence que l'utilisateur devrait corriger a la main.
 *
 * Un essai sur un bloc deja reussi est conserve tel quel : un grimpeur repete un
 * bloc reussi, et remettre le statut a « travaille » effacerait sa reussite.
 */
class RecordAttemptUseCase @Inject constructor(
    private val blocRepository: BlocRepository,
) {

    suspend operator fun invoke(blocId: String): Result<Bloc> {
        val bloc = blocRepository.getBloc(blocId)
            ?: return Result.failure(IllegalArgumentException("Bloc introuvable : $blocId"))

        val updated = bloc.copy(
            attempts = bloc.attempts + 1,
            status = if (bloc.status == BlocStatus.PROJECT) BlocStatus.WORKING else bloc.status,
        )
        blocRepository.updateBloc(updated)
        return Result.success(updated)
    }
}
