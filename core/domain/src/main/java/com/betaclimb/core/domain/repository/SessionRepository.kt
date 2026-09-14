package com.betaclimb.core.domain.repository

import com.betaclimb.core.domain.model.Session
import com.betaclimb.core.domain.model.SessionSummary
import kotlinx.coroutines.flow.Flow

/**
 * Acces aux seances d'escalade.
 *
 * Les lectures sont des [Flow] : la base est la source de verite, et toute mutation
 * — d'ou qu'elle vienne — reemet automatiquement vers les ecrans abonnes. Aucun
 * rafraichissement manuel, donc aucun risque d'affichage perime.
 *
 * Les ecritures sont `suspend` : elles sont ordonnancees sur l'executeur de
 * transactions de Room, sur un fil unique, ce qui evite les SQLITE_BUSY tout en
 * laissant les lectures concurrentes progresser grace au mode WAL.
 */
interface SessionRepository {

    /** Seances les plus recentes d'abord, avec leurs compteurs de blocs. */
    fun observeSessions(): Flow<List<SessionSummary>>

    fun observeSession(sessionId: String): Flow<Session?>

    /** @return l'identifiant de la seance creee. */
    suspend fun createSession(locationName: String, date: Long, notes: String? = null): String

    suspend fun updateSession(session: Session)

    /**
     * Supprime la seance ET, par cascade SQLite, ses blocs, prises et mouvements.
     * Les fichiers image correspondants sont supprimes du stockage interne : la
     * base ne les connait que par chemin, rien ne les nettoierait sinon.
     */
    suspend fun deleteSession(sessionId: String)
}
