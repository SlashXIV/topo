package com.topo.core.data

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.topo.core.data.storage.InternalBlocImageStore
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation du stockage interne des photos de blocs.
 *
 * C'est le maillon entre CameraX et la base : la base ne conserve qu'un chemin, et
 * si ce chemin ment, l'ecran de detail est irreparable. Ces tests verifient donc
 * surtout les cas de bord — atomicite d'un import, lecture des dimensions sans
 * decoder, suppression idempotente.
 */
@RunWith(AndroidJUnit4::class)
class InternalBlocImageStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: InternalBlocImageStore

    @Before
    fun setUp() {
        store = InternalBlocImageStore(context, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        File(context.filesDir, "blocs").deleteRecursively()
    }

    @Test
    fun capture_targets_are_unique_and_private() = runTest {
        val first = store.createCaptureTarget()
        val second = store.createCaptureTarget()

        assertThat(first.absolutePath).isNotEqualTo(second.absolutePath)
        // Sous filesDir : aucune permission requise, invisible de la galerie et des
        // autres applications. Le projet est hors-ligne et prive par construction.
        assertThat(first.absolutePath).startsWith(context.filesDir.absolutePath)
    }

    @Test
    fun relative_path_round_trips_through_resolve() = runTest {
        val target = store.createCaptureTarget()

        val relative = store.relativePathOf(target)

        // Le chemin stocke en base est relatif : un chemin absolu casserait apres
        // une restauration d'appareil, ou le repertoire de donnees change de place.
        assertThat(relative).doesNotContain("/")
        assertThat(store.resolve(relative).absolutePath).isEqualTo(target.absolutePath)
    }

    @Test
    fun import_copies_the_source_into_private_storage() = runTest {
        val jpeg = encodeJpeg(width = 640, height = 480)

        val relativePath = store.import { ByteArrayInputStream(jpeg) }

        val stored = store.resolve(relativePath)
        assertThat(stored.exists()).isTrue()
        assertThat(stored.length()).isEqualTo(jpeg.size.toLong())
    }

    /**
     * Un import interrompu ne doit laisser NI fichier final tronque, NI fichier de
     * travail. Sans le renommage atomique, la base pourrait referencer un JPEG
     * incomplet indiscernable d'un fichier valide.
     */
    @Test
    fun a_failed_import_leaves_nothing_behind() = runTest {
        val directory = File(context.filesDir, "blocs")

        val error = runCatching {
            store.import { object : java.io.InputStream() {
                override fun read(): Int = throw java.io.IOException("flux interrompu")
            } }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(java.io.IOException::class.java)
        assertThat(directory.listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun dimensions_are_read_without_decoding_the_bitmap() = runTest {
        val relativePath = store.import { ByteArrayInputStream(encodeJpeg(1920, 1080)) }

        val dimensions = store.readDimensions(relativePath)

        assertThat(dimensions.width).isEqualTo(1920)
        assertThat(dimensions.height).isEqualTo(1080)
    }

    @Test
    fun a_corrupt_file_is_reported_rather_than_returning_zeroes() = runTest {
        val relativePath = store.import { ByteArrayInputStream("pas un jpeg".toByteArray()) }

        val error = runCatching { store.readDimensions(relativePath) }.exceptionOrNull()

        // Des dimensions a zero remonteraient jusqu'au domaine et y violeraient
        // l'invariant de Bloc : mieux vaut echouer ici, au plus pres de la cause.
        assertThat(error).isNotNull()
    }

    @Test
    fun deleting_is_idempotent() = runTest {
        val relativePath = store.import { ByteArrayInputStream(encodeJpeg(64, 64)) }

        store.delete(relativePath)
        // Le second appel intervient par exemple lors du rattrapage d'une capture
        // deja nettoyee : il ne doit rien casser.
        store.delete(relativePath)

        assertThat(store.resolve(relativePath).exists()).isFalse()
    }

    private fun encodeJpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            bitmap.recycle()
            output.toByteArray()
        }
    }
}
