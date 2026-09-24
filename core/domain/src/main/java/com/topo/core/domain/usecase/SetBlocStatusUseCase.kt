package com.topo.core.domain.usecase

import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.BlocStatus
import com.topo.core.domain.repository.BlocRepository
import javax.inject.Inject

/**
 * Change le statut d'un bloc, en maintenant la coherence avec les essais et la
 * date de reussite.
 *
 * Trois regles, qui viennent du vocabulaire de l'escalade et pas du code :
 *
 *  - un **flash** est une reussite du premier coup. Le declarer alors que le
 *    compteur d'essais depasse 1 est contradictoire, et c'est refuse plutot que
 *    corrige en silence : c'est l'utilisateur qui sait s'il s'est trompe de
 *    bouton ou de compteur ;
 *  - declarer un flash sans aucun essai enregistre porte le compteur a 1. On ne
 *    reussit pas un bloc sans l'avoir essaye une fois ;
 *  - une reussite horodate [Bloc.sentAt] ; revenir a un statut non reussi efface
 *    cet horodatage, qui n'aurait plus de sens.
 *
 * Les essais, eux, ne sont jamais remis a zero : ils racontent le travail fourni,
 * et repasser un bloc en projet ne l'efface pas.
 */
class SetBlocStatusUseCase @Inject constructor(
    private val blocRepository: BlocRepository,
) {

    suspend operator fun invoke(
        blocId: String,
        status: BlocStatus,
        now: Long = System.currentTimeMillis(),
    ): Result<Bloc> {
        val bloc = blocRepository.getBloc(blocId)
            ?: return Result.failure(IllegalArgumentException("Bloc introuvable : $blocId"))

        if (status == BlocStatus.FLASHED && bloc.attempts > 1) {
            return Result.failure(
                IllegalStateException(
                    "Un flash est une reussite du premier essai : ${bloc.attempts} essais enregistres",
                ),
            )
        }

        val updated = bloc.copy(
            status = status,
            attempts = if (status == BlocStatus.FLASHED) maxOf(bloc.attempts, 1) else bloc.attempts,
            sentAt = when {
                !status.isSent -> null
                bloc.sentAt != null -> bloc.sentAt
                else -> now
            },
        )
        blocRepository.updateBloc(updated)
        return Result.success(updated)
    }
}
