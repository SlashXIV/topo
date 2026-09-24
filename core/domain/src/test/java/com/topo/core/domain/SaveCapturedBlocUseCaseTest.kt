package com.topo.core.domain

import com.topo.core.domain.usecase.SaveCapturedBlocUseCase
import com.google.common.truth.Truth.assertThat
import java.io.File
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
}
