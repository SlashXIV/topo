package com.topo.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.topo.core.data.database.entity.HoldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldDao {

    @Query("SELECT * FROM holds WHERE bloc_id = :blocId")
    fun observeHolds(blocId: String): Flow<List<HoldEntity>>

    @Query("SELECT * FROM holds WHERE bloc_id = :blocId")
    suspend fun getHolds(blocId: String): List<HoldEntity>

    @Insert
    suspend fun insert(hold: HoldEntity)

    @Insert
    suspend fun insertAll(holds: List<HoldEntity>)

    @Update
    suspend fun updateAll(holds: List<HoldEntity>)

    @Update
    suspend fun update(hold: HoldEntity)

    @Query("DELETE FROM holds WHERE id = :holdId")
    suspend fun deleteById(holdId: String)

    /** Une seule requete, donc une seule transaction et une seule emission du Flow. */
    @Query("DELETE FROM holds WHERE id IN (:holdIds)")
    suspend fun deleteByIds(holdIds: List<String>)

    @Query("DELETE FROM holds WHERE bloc_id = :blocId")
    suspend fun deleteAllForBloc(blocId: String)

    /**
     * Remplace l'integralite des prises d'un bloc.
     *
     * En UNE transaction : entre la suppression et l'insertion, la table est vide
     * pour ce bloc. Un lecteur qui verrait cet etat intermediaire afficherait un
     * mur sans prises, et surtout les mouvements referencant les anciennes prises
     * seraient deja tombes en cascade. La transaction rend cet instant invisible.
     *
     * Consequence assumee : les mouvements existants sont perdus, puisqu'ils
     * pointaient vers des prises qui n'existent plus. C'est le comportement voulu
     * apres une nouvelle detection — l'ancienne annotation ne correspond plus.
     */
    @Transaction
    suspend fun replaceForBloc(blocId: String, holds: List<HoldEntity>) {
        deleteAllForBloc(blocId)
        insertAll(holds)
    }
}
