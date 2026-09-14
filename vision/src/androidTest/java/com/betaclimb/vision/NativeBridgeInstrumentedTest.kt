package com.betaclimb.vision

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation du socle natif (Sprint 0).
 *
 * Ce test doit tourner sur DEUX cibles :
 *  - un AVD dont l'image systeme utilise des pages de 16 Ko,
 *  - un appareil physique ARM64.
 *
 * C'est la preuve d'EXECUTION de la conformite 16 Ko, complementaire de la preuve
 * STATIQUE donnee par `scripts/check_alignment.sh` : une .so mal alignee ne passe
 * meme pas le `System.loadLibrary` du bloc `init` de [NativeBridge], et tous les
 * tests de cette classe echouent immediatement avec un UnsatisfiedLinkError.
 */
@RunWith(AndroidJUnit4::class)
class NativeBridgeInstrumentedTest {

    @Test
    fun library_loads_and_native_methods_are_registered() {
        // Si RegisterNatives echouait dans JNI_OnLoad, loadLibrary leverait ici.
        val handle = NativeBridge.nativeCreate()
        try {
            assertThat(handle).isNotEqualTo(0L)
        } finally {
            NativeBridge.nativeDestroy(handle)
        }
    }

    @Test
    fun destroy_is_idempotent() {
        val handle = NativeBridge.nativeCreate()
        NativeBridge.nativeDestroy(handle)
        // Un handle nul doit etre ignore, pas crasher : close() peut etre appele
        // plusieurs fois (use {} + close() explicite, changement de configuration).
        NativeBridge.nativeDestroy(0L)
    }

    @Test
    fun direct_buffer_crosses_the_bridge() = runTest {
        VisionEngine().use { engine ->
            val width = 64
            val height = 64
            val buffer = ByteBuffer.allocateDirect(width * height * 4)

            val holds = engine.detect(
                VisionEngine.DirectBufferSource(
                    buffer = buffer,
                    width = width,
                    height = height,
                    rowStride = width * 4,
                ),
            )

            // Aucun modele charge au Sprint 0 : le contrat est une liste vide,
            // pas une exception.
            assertThat(holds).isEmpty()
            assertThat(engine.isReady).isFalse()
        }
    }

    /**
     * Le piege classique de GetDirectBufferAddress : sur un buffer NON direct, il
     * renvoie nullptr. Sans garde cote C++, on dereferencerait un pointeur nul et
     * l'application planterait en SIGSEGV au lieu de lever une exception lisible.
     */
    @Test
    fun non_direct_buffer_is_rejected() {
        val width = 16
        val height = 16
        val heapBuffer = ByteBuffer.allocate(width * height * 4)
        assertThat(heapBuffer.isDirect).isFalse()

        val handle = NativeBridge.nativeCreate()
        try {
            val error = runCatching {
                NativeBridge.nativeDetectFromDirectBuffer(
                    handle,
                    heapBuffer,
                    width,
                    height,
                    width * 4,
                )
            }.exceptionOrNull()

            assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
        } finally {
            NativeBridge.nativeDestroy(handle)
        }
    }

    @Test
    fun bitmap_pixels_are_locked_without_copy() = runTest {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        VisionEngine().use { engine ->
            val holds = engine.detect(VisionEngine.BitmapSource(bitmap))
            assertThat(holds).isEmpty()
        }
        // Le bitmap doit rester utilisable : lockPixels/unlockPixels sont apparies
        // par le garde RAII cote C++.
        assertThat(bitmap.isRecycled).isFalse()
    }

    @Test
    fun using_a_closed_engine_fails_loudly() = runTest {
        val engine = VisionEngine()
        engine.close()

        val error = runCatching {
            engine.detect(VisionEngine.BitmapSource(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
    }
}
