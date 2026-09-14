package com.betaclimb.core.data.repository

import com.betaclimb.core.data.dao.SessionDao
import com.betaclimb.core.data.mapper.toDomain
import com.betaclimb.core.data.mapper.toEntity
import com.betaclimb.core.domain.model.Session
import com.betaclimb.core.domain.model.SessionSummary
import com.betaclimb.core.domain.repository.BlocImageStore
import com.betaclimb.core.domain.repository.SessionRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
internal class SessionRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val imageStore: BlocImageStore,
) : SessionRepository {

    override fun observeSessions(): Flow<List<SessionSummary>> =
        sessionDao.observeSessionsWithCounts().map { rows -> rows.map { it.toDomain() } }

    override fun observeSession(sessionId: String): Flow<Session?> =
        sessionDao.observeSession(sessionId).map { it?.toDomain() }

    override suspend fun createSession(
        locationName: String,
        date: Long,
        notes: String?,
    ): String {
        val session = Session(
            id = UUID.randomUUID().toString(),
            date = date,
            locationName = locationName,
            notes = notes,
        )
        sessionDao.upsert(session.toEntity())
        return session.id
    }

    override suspend fun updateSession(session: Session) {
        sessionDao.upsert(session.toEntity())
    }

    override suspend fun deleteSession(sessionId: String) {
        // Les chemins sont releves AVANT la suppression : une fois la cascade
        // SQLite passee, les lignes de blocs n'existent plus et les fichiers image
        // seraient definitivement orphelins sur le disque.
        val imagePaths = sessionDao.getImagePathsForSession(sessionId)
        sessionDao.deleteById(sessionId)
        imagePaths.forEach { imageStore.delete(it) }
    }
}
