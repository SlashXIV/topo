package com.betaclimb.core.domain

import com.betaclimb.core.domain.model.Bloc
import com.betaclimb.core.domain.model.BlocDetail
import com.betaclimb.core.domain.repository.BlocImageStore
import com.betaclimb.core.domain.repository.BlocRepository
import com.betaclimb.core.domain.usecase.SaveCapturedBlocUseCase
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Le point delicat de l'enregistrement d'une capture : deux magasins de nature
 * differente (systeme de fichiers et SQLite) sans transaction commune.
 *
 * Ces tests tournent sur JVM pure, sans emulateur, parce que `:core:domain` n'a
 * aucune dependance Android. C'est precisement ce que garantit le decoupage en
 * modules : la logique de rattrapage se teste en quelques millisecondes.
 */
class SaveCapturedBlocUseCaseTest {

    @Test
    fun `a successful save returns the created bloc id`() = runTest {
        val imageStore = FakeImageStore()
        val repository = FakeBlocRepository()
        val useCase = SaveCapturedBlocUseCase(repository, imageStore)

        val result = useCase(sessionId = "session-1", capturedFile = File("photo.jpg"))

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("bloc-1")
        assertThat(imageStore.deletedPaths).isEmpty()
    }

    @Test
    fun `a failed insert deletes the captured file`() = runTest {
        val imageStore = FakeImageStore()
        val repository = FakeBlocRepository(failOnCreate = true)
        val useCase = SaveCapturedBlocUseCase(repository, imageStore)

        val result = useCase(sessionId = "session-1", capturedFile = File("photo.jpg"))

        assertThat(result.isFailure).isTrue()
        // Sans ce rattrapage, chaque echec laisserait une photo de plusieurs Mo que
        // rien ne viendrait jamais nettoyer.
        assertThat(imageStore.deletedPaths).containsExactly("photo.jpg")
    }

    @Test
    fun `an unreadable image deletes the file and never reaches the database`() = runTest {
        val imageStore = FakeImageStore(failOnDimensions = true)
        val repository = FakeBlocRepository()
        val useCase = SaveCapturedBlocUseCase(repository, imageStore)

        val result = useCase(sessionId = "session-1", capturedFile = File("photo.jpg"))

        assertThat(result.isFailure).isTrue()
        // L'ordre compte : une ligne pointant vers un fichier illisible serait pire
        // qu'un fichier orphelin, car l'ecran de detail n'aurait aucun recours.
        assertThat(repository.created).isEmpty()
        assertThat(imageStore.deletedPaths).containsExactly("photo.jpg")
    }

    private class FakeImageStore(
        private val failOnDimensions: Boolean = false,
    ) : BlocImageStore {
        val deletedPaths = mutableListOf<String>()

        override suspend fun createCaptureTarget(): File = File("photo.jpg")
        override fun relativePathOf(file: File): String = file.name
        override fun resolve(relativePath: String): File = File(relativePath)
        override suspend fun import(source: () -> InputStream): String = "imported.jpg"
        override suspend fun delete(relativePath: String) { deletedPaths += relativePath }

        override suspend fun readDimensions(relativePath: String): BlocImageStore.ImageDimensions {
            if (failOnDimensions) error("Image illisible")
            return BlocImageStore.ImageDimensions(1920, 1080)
        }
    }

    private class FakeBlocRepository(
        private val failOnCreate: Boolean = false,
    ) : BlocRepository {
        val created = mutableListOf<String>()

        override fun observeBlocsForSession(sessionId: String): Flow<List<Bloc>> = emptyFlow()
        override fun observeBlocDetail(blocId: String): Flow<BlocDetail?> = emptyFlow()
        override suspend fun updateBloc(bloc: Bloc) = Unit
        override suspend fun deleteBloc(blocId: String) = Unit

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
}
