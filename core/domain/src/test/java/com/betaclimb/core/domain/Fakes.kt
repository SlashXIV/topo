package com.betaclimb.core.domain

import com.betaclimb.core.domain.model.Bloc
import com.betaclimb.core.domain.model.BlocDetail
import com.betaclimb.core.domain.model.BlocStatus
import com.betaclimb.core.domain.repository.BlocImageStore
import com.betaclimb.core.domain.repository.BlocRepository
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Doublures partagees par les tests du domaine.
 *
 * Mutualisees plutot que redefinies dans chaque classe : trois tests utilisaient
 * la meme, et chaque ajout a l'interface obligeait a corriger trois copies.
 */

internal class FakeBlocRepository(
    private val failOnCreate: Boolean = false,
) : BlocRepository {

    val blocs = MutableStateFlow<List<Bloc>>(emptyList())
    val created = mutableListOf<String>()

    fun seed(bloc: Bloc) {
        blocs.value = blocs.value.filterNot { it.id == bloc.id } + bloc
    }

    override fun observeBlocsForSession(sessionId: String): Flow<List<Bloc>> =
        blocs.map { list -> list.filter { it.sessionId == sessionId } }

    override fun observeBlocDetail(blocId: String): Flow<BlocDetail?> =
        blocs.map { list ->
            list.firstOrNull { it.id == blocId }?.let { BlocDetail(it, emptyList(), emptyList()) }
        }

    override suspend fun getBloc(blocId: String): Bloc? = blocs.value.firstOrNull { it.id == blocId }

    override suspend fun updateBloc(bloc: Bloc) = seed(bloc)

    override suspend fun deleteBloc(blocId: String) {
        blocs.value = blocs.value.filterNot { it.id == blocId }
    }

    override suspend fun createBloc(
        sessionId: String,
        imagePath: String,
        imageWidth: Int,
        imageHeight: Int,
        grade: String?,
    ): String {
        if (failOnCreate) error("Contrainte de cle etrangere violee")
        created += imagePath
        return "bloc-1"
    }
}

internal class FakeImageStore(
    private val failOnImport: Boolean = false,
    private val failOnDimensions: Boolean = false,
    private val captureTargetName: String = "photo.jpg",
) : BlocImageStore {

    val deletedPaths = mutableListOf<String>()
    var openedStreams = 0
        private set

    override suspend fun createCaptureTarget(): File = File(captureTargetName)
    override fun relativePathOf(file: File): String = file.name
    override fun resolve(relativePath: String): File = File(relativePath)

    override suspend fun import(source: () -> InputStream): String {
        if (failOnImport) throw java.io.IOException("flux interrompu")
        openedStreams++
        source().use { it.readBytes() }
        return "imported.jpg"
    }

    override suspend fun delete(relativePath: String) {
        deletedPaths += relativePath
    }

    override suspend fun readDimensions(relativePath: String): BlocImageStore.ImageDimensions {
        if (failOnDimensions) error("Image illisible")
        return BlocImageStore.ImageDimensions(1920, 1080)
    }
}

/** Bloc de test, aux valeurs par defaut neutres. */
internal fun testBloc(
    id: String = "bloc-1",
    status: BlocStatus = BlocStatus.PROJECT,
    attempts: Int = 0,
    sentAt: Long? = null,
    feeling: String? = null,
) = Bloc(
    id = id,
    sessionId = "session-1",
    imagePath = "$id.jpg",
    imageWidth = 3000,
    imageHeight = 4000,
    grade = "7A",
    status = status,
    attempts = attempts,
    sentAt = sentAt,
    feeling = feeling,
    createdAt = 1_700_000_000_000L,
)
