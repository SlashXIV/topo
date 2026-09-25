package com.topo.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.topo.core.data.database.entity.CircuitColorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CircuitColorDao {

    @Query("SELECT * FROM circuit_colors ORDER BY rank ASC")
    fun observeColors(): Flow<List<CircuitColorEntity>>

    @Query("SELECT * FROM circuit_colors ORDER BY rank ASC")
    suspend fun getColors(): List<CircuitColorEntity>

    @Query("SELECT COALESCE(MAX(rank), -1) + 1 FROM circuit_colors")
    suspend fun nextRank(): Int

    @Insert
    suspend fun insert(color: CircuitColorEntity)

    @Insert
    suspend fun insertAll(colors: List<CircuitColorEntity>)

    @Query("DELETE FROM circuit_colors WHERE id = :colorId")
    suspend fun deleteById(colorId: String)

    @Query("DELETE FROM circuit_colors")
    suspend fun deleteAll()

    /**
     * Reecrit le circuit entier.
     *
     * En UNE transaction, et c'est indispensable : un index UNIQUE porte sur
     * `rank`, donc echanger deux rangs par deux `UPDATE` successifs violerait la
     * contrainte entre les deux ecritures. Vider puis reinserer contourne le
     * probleme sans desactiver la contrainte — qui est precisement ce qui
     * garantit qu'aucune couleur ne partage un niveau de difficulte.
     *
     * Consequence assumee : les identifiants sont conserves par l'appelant, donc
     * les blocs qui referencent ces couleurs les retrouvent apres reordonnancement.
     */
    @Transaction
    suspend fun replaceAll(colors: List<CircuitColorEntity>) {
        deleteAll()
        insertAll(colors)
    }
}
