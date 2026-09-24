package com.topo.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.topo.core.data.database.entity.BlocEntity
import com.topo.core.data.database.relation.BlocWithBeta
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocDao {

    @Query("SELECT * FROM blocs WHERE session_id = :sessionId ORDER BY created_at DESC")
    fun observeBlocsForSession(sessionId: String): Flow<List<BlocEntity>>

    /**
     * Le bloc avec ses prises et ses mouvements.
     *
     * `@Transaction` est indispensable : Room emet ici trois requetes distinctes,
     * et sans transaction une ecriture concurrente pourrait s'intercaler entre
     * elles, produisant un mouvement pointant vers une prise absente du meme
     * instantane.
     */
    @Transaction
    @Query("SELECT * FROM blocs WHERE id = :blocId")
    fun observeBlocDetail(blocId: String): Flow<BlocWithBeta?>

    @Query("SELECT * FROM blocs WHERE id = :blocId")
    suspend fun getBloc(blocId: String): BlocEntity?

    @Query("SELECT image_path FROM blocs WHERE id = :blocId")
    suspend fun getImagePath(blocId: String): String?

    @Insert
    suspend fun insert(bloc: BlocEntity)

    @Update
    suspend fun update(bloc: BlocEntity)

    /** Emporte prises et mouvements par cascade SQLite. */
    @Query("DELETE FROM blocs WHERE id = :blocId")
    suspend fun deleteById(blocId: String)
}
