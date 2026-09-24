package com.topo.core.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.topo.core.data.database.entity.SessionEntity
import com.topo.core.data.database.relation.SessionWithCounts
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /**
     * Seances les plus recentes d'abord, avec le nombre de blocs et de reussites.
     *
     * Les compteurs sont agreges en SQL : la liste n'a besoin que de deux entiers
     * par seance, inutile de rapatrier toutes les lignes de blocs pour les compter
     * cote Kotlin.
     *
     * LEFT JOIN et non INNER JOIN : une seance sans bloc doit apparaitre, avec 0.
     *
     * Les valeurs 2 et 3 sont les ordinaux de BlocStatus.FLASHED et SENT, repris
     * de BlocStatus.SQL_SENT_ORDINALS. Room exige une requete litterale, donc la
     * constante ne peut pas etre interpolee ici : un test verrouille ces ordinaux.
     */
    @Transaction
    @Query(
        """
        SELECT s.*,
               COUNT(b.id) AS bloc_count,
               COALESCE(SUM(CASE WHEN b.status IN (2, 3) THEN 1 ELSE 0 END), 0) AS completed_count
        FROM sessions AS s
        LEFT JOIN blocs AS b ON b.session_id = s.id
        GROUP BY s.id
        ORDER BY s.date DESC
        """,
    )
    fun observeSessionsWithCounts(): Flow<List<SessionWithCounts>>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    fun observeSession(sessionId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE id = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?

    @Upsert
    suspend fun upsert(session: SessionEntity)

    /**
     * Ne selectionne QUE la colonne utile : la suppression d'une seance doit aussi
     * effacer les fichiers image, mais n'a aucun besoin des lignes completes.
     */
    @Query("SELECT image_path FROM blocs WHERE session_id = :sessionId")
    suspend fun getImagePathsForSession(sessionId: String): List<String>

    /** Emporte blocs, prises et mouvements par cascade SQLite. */
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: String)
}
