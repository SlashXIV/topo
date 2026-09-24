package com.topo.feature.camera

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.topo.core.domain.repository.BlocImageStore
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Preuve d'execution du chemin de capture : CameraX ecrit bien un JPEG valide dans
 * le fichier que nous lui designons.
 *
 * Tourne sur l'emulateur avec la camera "virtualscene". Le test lie lui-meme le cas
 * d'usage a un cycle de vie, sans passer par Compose : la partie a valider est le
 * declenchement et l'ecriture, pas l'affichage de l'apercu.
 *
 * Le magasin d'images est double par une implementation de test — le magasin reel
 * est `internal` a `:core:data` et deja couvert par ses propres tests.
 */
@RunWith(AndroidJUnit4::class)
class BlocPhotoCaptureTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val directory = File(context.cacheDir, "capture-test").apply { mkdirs() }

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var lifecycleOwner: TestLifecycleOwner
    private lateinit var capture: BlocPhotoCapture

    @Before
    fun setUp() = runTest {
        capture = BlocPhotoCapture(context, TestImageStore(directory), Dispatchers.IO)
        cameraProvider = awaitCameraProvider()
        lifecycleOwner = TestLifecycleOwner()
    }

    @After
    fun tearDown() = runTest {
        withContext(Dispatchers.Main) {
            cameraProvider.unbindAll()
            lifecycleOwner.destroy()
        }
        directory.deleteRecursively()
    }

    @Test
    fun capture_writes_a_decodable_jpeg_to_the_target_file() = runTest {
        val imageCapture = capture.newImageCapture()

        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                imageCapture,
            )
            lifecycleOwner.start()
        }

        val file = capture.capture(imageCapture)

        assertThat(file.exists()).isTrue()
        assertThat(file.length()).isGreaterThan(0L)

        // Les dimensions sont lues sans decoder les pixels : c'est exactement ce que
        // fera le magasin d'images pour alimenter BlocEntity. Une valeur nulle
        // signifierait un fichier tronque.
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        assertThat(options.outWidth).isGreaterThan(0)
        assertThat(options.outHeight).isGreaterThan(0)
    }

    /**
     * ProcessCameraProvider expose un ListenableFuture, pas une fonction suspend
     * dans cette version de CameraX. On l'enveloppe ici plutot que d'ajouter une
     * dependance a kotlinx-coroutines-guava pour un unique appel de test.
     */
    private suspend fun awaitCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    runCatching { future.get() }.fold(
                        onSuccess = continuation::resume,
                        onFailure = continuation::resumeWithException,
                    )
                },
                ContextCompat.getMainExecutor(context),
            )
        }

    /** Cycle de vie minimal : CameraX exige un LifecycleOwner, pas une Activity. */
    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun start() {
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private class TestImageStore(private val directory: File) : BlocImageStore {
        override suspend fun createCaptureTarget(): File =
            File(directory, "${UUID.randomUUID()}.jpg")

        override fun relativePathOf(file: File): String = file.name
        override fun resolve(relativePath: String): File = File(directory, relativePath)
        override suspend fun import(source: () -> InputStream): String = error("inutilise")
        override suspend fun delete(relativePath: String) { resolve(relativePath).delete() }
        override suspend fun readDimensions(relativePath: String) = error("inutilise")
    }
}
