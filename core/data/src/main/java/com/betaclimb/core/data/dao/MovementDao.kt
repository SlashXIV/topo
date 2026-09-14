package com.betaclimb.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.betaclimb.core.data.database.entity.MovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MovementDao {

    @Query("SELECT * FROM movements WHERE bloc_id = :blocId ORDER BY sequence_order ASC")
    fun observeMovements(blocId: String): Flow<List<MovementEntity>>

    @Query("SELECT * FROM movements WHERE bloc_id = :blocId ORDER BY sequence_order ASC")
    suspend fun getMovements(blocId: String): List<MovementEntity>

    @Insert
    suspend fun insertAll(movements: List<MovementEntity>)

    @Query("DELETE FROM movements WHERE id = :movementId")
    suspend fun deleteById(movementId: String)

    @Query("DELETE FROM movements WHERE bloc_id = :blocId")
    suspend fun deleteAllForBloc(blocId: String)

    /**
     * Remplace la sequence complete de mouvements d'un bloc.
     *
     * Le trace au doigt produit une sequence entiere, pas des mutations unitaires.
     * La remplacer en bloc evite d'avoir a reconcilier des rangs partiels — source
     * classique de trous et de doublons dans l'ordre.
     *
     * La transaction est ici doublement necessaire : un index UNIQUE porte sur
     * `(bloc_id, sequence_order)`. Inserer la nouvelle sequence avant d'avoir
     * supprime l'ancienne violerait cette contrainte des le premier rang reutilise.
     */
    @Transaction
    suspend fun replaceForBloc(blocId: String, movements: List<MovementEntity>) {
        deleteAllForBloc(blocId)
        insertAll(movements)
    }
}
