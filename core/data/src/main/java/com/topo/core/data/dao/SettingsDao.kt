package com.topo.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.topo.core.data.database.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {

    /**
     * La ligne est creee par la migration : elle ne peut pas manquer. Le type
     * reste nullable par prudence, mais le repository n'a pas a inventer de
     * valeurs par defaut a la lecture.
     */
    @Query("SELECT * FROM settings WHERE id = :id")
    fun observeSettings(id: Int = SettingsEntity.SINGLE_ROW_ID): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = :id")
    suspend fun getSettings(id: Int = SettingsEntity.SINGLE_ROW_ID): SettingsEntity?

    @Upsert
    suspend fun upsert(settings: SettingsEntity)
}
