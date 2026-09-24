package com.betaclimb.core.data.repository

import com.betaclimb.core.data.dao.BlocDao
import com.betaclimb.core.data.mapper.toDomain
import com.betaclimb.core.data.mapper.toEntity
import com.betaclimb.core.domain.model.Bloc
import com.betaclimb.core.domain.model.BlocDetail
import com.betaclimb.core.domain.model.BlocStatus
import com.betaclimb.core.domain.repository.BlocImageStore
import com.betaclimb.core.domain.repository.BlocRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
internal class BlocRepositoryImpl @Inject constructor(
    private val blocDao: BlocDao,
    private val imageStore: BlocImageStore,
) : BlocRepository {

    override fun observeBlocsForSession(sessionId: String): Flow<List<Bloc>> =
        blocDao.observeBlocsForSession(sessionId).map { rows -> rows.map { it.toDomain() } }

    override fun observeBlocDetail(blocId: String): Flow<BlocDetail?> =
        blocDao.observeBlocDetail(blocId).map { it?.toDomain() }

    override suspend fun createBloc(
        sessionId: String,
        imagePath: String,
        imageWidth: Int,
        imageHeight: Int,
        grade: String?,
    ): String {
        val bloc = Bloc(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            imagePath = imagePath,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            grade = grade,
            status = BlocStatus.PROJECT,
            attempts = 0,
            sentAt = null,
            feeling = null,
            createdAt = System.currentTimeMillis(),
        )
        blocDao.insert(bloc.toEntity())
        return bloc.id
    }

    override suspend fun getBloc(blocId: String): Bloc? = blocDao.getBloc(blocId)?.toDomain()

    override suspend fun updateBloc(bloc: Bloc) {
        blocDao.update(bloc.toEntity())
    }

    override suspend fun deleteBloc(blocId: String) {
        // Meme ordre que pour une seance : lire le chemin tant que la ligne existe,
        // supprimer la ligne (cascade sur prises et mouvements), puis le fichier.
        val imagePath = blocDao.getImagePath(blocId)
        blocDao.deleteById(blocId)
        imagePath?.let { imageStore.delete(it) }
    }
}
