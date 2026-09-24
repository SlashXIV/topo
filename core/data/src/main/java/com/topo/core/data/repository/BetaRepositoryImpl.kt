package com.topo.core.data.repository

import com.topo.core.data.dao.HoldDao
import com.topo.core.data.dao.MovementDao
import com.topo.core.data.mapper.toEntity
import com.topo.core.domain.model.DetectedHold
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.Movement
import com.topo.core.domain.repository.BetaRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BetaRepositoryImpl @Inject constructor(
    private val holdDao: HoldDao,
    private val movementDao: MovementDao,
) : BetaRepository {

    override suspend fun replaceHolds(blocId: String, holds: List<DetectedHold>): List<Hold> {
        // Les identifiants sont attribues AVANT l'ecriture, pour que la liste
        // retournee soit exactement ce qui se trouve en base. Relire apres coup
        // couterait une requete de plus et ouvrirait une fenetre de concurrence.
        val persisted = holds.map { it.toHold(blocId) }
        holdDao.replaceForBloc(blocId, persisted.map(Hold::toEntity))
        return persisted
    }

    override suspend fun addHold(blocId: String, hold: DetectedHold): Hold {
        val persisted = hold.toHold(blocId)
        holdDao.insert(persisted.toEntity())
        return persisted
    }

    override suspend fun updateHold(hold: Hold) {
        holdDao.update(hold.toEntity())
    }

    override suspend fun deleteHold(holdId: String) {
        // Les mouvements concernes sont traites par SQLite : CASCADE sur la cible,
        // SET NULL sur la source (cf. MovementEntity). Aucun nettoyage manuel ici,
        // donc aucun risque d'oubli lors d'une evolution du schema.
        holdDao.deleteById(holdId)
    }

    override suspend fun replaceMovements(
        blocId: String,
        movements: List<Movement>,
    ): List<Movement> {
        // Les rangs sont RENUMEROTES de facon contigue a partir de 0 : l'appelant
        // (le trace au doigt) fournit un ordre relatif, pas une numerotation. Un
        // index UNIQUE porte sur (bloc_id, sequence_order), donc un doublon de rang
        // ferait echouer toute la transaction.
        val renumbered = movements.mapIndexed { index, movement ->
            movement.copy(blocId = blocId, sequenceOrder = index)
        }
        movementDao.replaceForBloc(blocId, renumbered.map(Movement::toEntity))
        return renumbered
    }

    override suspend fun deleteMovement(movementId: String) {
        movementDao.deleteById(movementId)
    }

    private fun DetectedHold.toHold(blocId: String): Hold = Hold(
        id = UUID.randomUUID().toString(),
        blocId = blocId,
        box = box,
        confidence = confidence,
        origin = origin,
    )
}
