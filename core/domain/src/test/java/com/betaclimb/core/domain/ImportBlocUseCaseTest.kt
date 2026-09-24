package com.betaclimb.core.domain

import com.betaclimb.core.domain.model.Bloc
import com.betaclimb.core.domain.model.BlocDetail
import com.betaclimb.core.domain.repository.BlocImageStore
import com.betaclimb.core.domain.repository.BlocRepository
import com.betaclimb.core.domain.usecase.ImportBlocUseCase
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Import d'une image externe : trois etapes, deux magasins, aucune transaction
 * commune. Ces tests verrouillent le comportement a chaque point de rupture.
 */
class ImportBlocUseCaseTest {

    private val source = { ByteArrayInputStream("jpeg".toByteArray()) as InputStream }

    @Test
    fun `a successful import copies the image then creates the bloc`() = runTest {
        val imageStore = FakeImageStore()
        val repository = FakeBlocRepository()

        val result = ImportBlocUseCase(repository, imageStore)(
            sessionId = "session-1",
            openStream = source,
        )

        assertThat(result.getOrNull()).isEqualTo("bloc-1")
        assertThat(repository.created).containsExactly("imported.jpg")
        assertThat(imageStore.deletedPaths).isEmpty()
    }

    @Test
    fun `a failed copy never reaches the database`() = runTest {
        val imageStore = FakeImageStore(failOnImport = true)
        val repository = FakeBlocRepository()

        val result = ImportBlocUseCase(repository, imageStore)(
            sessionId = "session-1",
            openStream = source,
        )

        assertThat(result.isFailure).isTrue()
        assertThat(repository.created).isEmpty()
        // Rien a nettoyer : l'import atomique du magasin a deja retire son fichier
        // de travail, il n'y a aucun chemin a supprimer.
        assertThat(imageStore.deletedPaths).isEmpty()
    }

    @Test
    fun `a failed insert deletes the copy`() = runTest {
        val imageStore = FakeImageStore()
        val repository = FakeBlocRepository(failOnCreate = true)

        val result = ImportBlocUseCase(repository, imageStore)(
            sessionId = "session-1",
            openStream = source,
        )

        assertThat(result.isFailure).isTrue()
        // Sans ce rattrapage, chaque echec laisserait une copie de plusieurs Mo
        // dans le stockage interne, que rien ne viendrait jamais reclamer.
        assertThat(imageStore.deletedPaths).containsExactly("imported.jpg")
    }

    @Test
    fun `an unreadable image deletes the copy and never reaches the database`() = runTest {
        val imageStore = FakeImageStore(failOnDimensions = true)
        val repository = FakeBlocRepository()

        val result = ImportBlocUseCase(repository, imageStore)(
            sessionId = "session-1",
            openStream = source,
        )

        assertThat(result.isFailure).isTrue()
        assertThat(repository.created).isEmpty()
        assertThat(imageStore.deletedPaths).containsExactly("imported.jpg")
    }

    @Test
    fun `the source stream is opened at most once`() = runTest {
        var opened = 0
        val imageStore = FakeImageStore()

        ImportBlocUseCase(FakeBlocRepository(), imageStore)(
            sessionId = "session-1",
            openStream = {
                opened++
                ByteArrayInputStream("jpeg".toByteArray())
            },
        )

        // Une URI de selecteur externe n'est pas garantie relisable : la rouvrir
        // pourrait echouer alors que la premiere lecture avait reussi.
        assertThat(opened).isEqualTo(1)
    }

    private class FakeImageStore(
        private val failOnImport: Boolean = false,
        private val failOnDimensions: Boolean = false,
    ) : BlocImageStore {
        val deletedPaths = mutableListOf<String>()

        override suspend fun import(source: () -> InputStream): String {
            if (failOnImport) throw IOException("flux interrompu")
            source().use { it.readBytes() }
            return "imported.jpg"
        }

        override suspend fun readDimensions(relativePath: String): BlocImageStore.ImageDimensions {
            if (failOnDimensions) error("Image illisible")
            return BlocImageStore.ImageDimensions(1920, 1080)
        }

        override suspend fun delete(relativePath: String) { deletedPaths += relativePath }
        override suspend fun createCaptureTarget(): File = File("unused.jpg")
        override fun relativePathOf(file: File): String = file.name
        override fun resolve(relativePath: String): File = File(relativePath)
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
