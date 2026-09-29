package com.topo.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Chaine de relief complete : pretraitement, ONNX Runtime, normalisation.
 *
 * La reference vient de `ml/depth_model.py`, miroir NumPy du code natif sur ONNX
 * Runtime CPU x86. Une erreur d'ordre des canaux, de normalisation ImageNet ou
 * d'orientation de la grille la fait echouer. Sans elle, le test est ignore.
 */
@RunWith(AndroidJUnit4::class)
class DepthEstimationInstrumentedTest {

    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val appAssets = InstrumentationRegistry.getInstrumentation().targetContext.assets

    @Test
    fun without_a_model_the_engine_answers_null() = runTest {
        DepthEngine().use { engine ->
            assertThat(engine.isReady).isFalse()
            assertThat(engine.estimate(Bitmap.createBitmap(28, 28, Bitmap.Config.ARGB_8888))).isNull()
        }
    }

    @Test
    fun an_invalid_model_is_rejected_without_crashing() = runTest {
        DepthEngine().use { engine ->
            val garbage = ByteBuffer.allocateDirect(1024).apply {
                repeat(1024) { put(it.toByte()) }
                flip()
            }
            val error = runCatching { engine.loadModel(garbage) }.exceptionOrNull()
            assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
            assertThat(engine.isReady).isFalse()
        }
    }

    @Test
    fun input_tensor_matches_the_python_mirror() {
        assumeTrue("Reference absente : lancer ml/depth_model.py", testAssets.hasAsset(REFERENCE))
        val expected = readFloats("depth/input.bin")

        val flat = NativeBridge.nativeDepthPreprocess(testImage())

        val reference = JSONObject(testAssets.open(REFERENCE).bufferedReader().readText())
        assertThat(flat[0].toInt()).isEqualTo(reference.getInt("width"))
        assertThat(flat[1].toInt()).isEqualTo(reference.getInt("height"))
        assertThat(flat.size - 2).isEqualTo(expected.size)
        // Meme calcul en float32 des deux cotes : seul l'ordre des operations
        // flottantes differe. Un canal inverse ou un ecart-type oublie depasse 0,1.
        val worst = expected.indices.maxOf { abs(flat[it + 2] - expected[it]) }
        assertThat(worst).isLessThan(1e-3f)
    }

    @Test
    fun relief_matches_the_python_reference() = runTest {
        assumeTrue("Reference absente : lancer ml/depth_model.py", testAssets.hasAsset(REFERENCE))
        val model = readModelAsset(appAssets, MODEL)
        assumeTrue("Modele de relief absent : lancer ml/depth_model.py", model != null)
        val reference = JSONObject(testAssets.open(REFERENCE).bufferedReader().readText())
        val expected = readFloats("depth/reference.bin")
        val bitmap = testImage()

        DepthEngine().use { engine ->
            engine.loadModel(model!!)
            val relief = engine.estimate(bitmap)!!

            assertThat(relief.width).isEqualTo(reference.getInt("width"))
            assertThat(relief.height).isEqualTo(reference.getInt("height"))
            val errors = relief.values.indices.map { abs(relief.values[it] - expected[it]) }.sorted()
            val mean = errors.average()
            val p99 = errors[errors.size * 99 / 100]
            Log.i(TAG, "Relief contre reference : ecart moyen %.4f, p99 %.4f".format(mean, p99))
            // Tolerance, pas egalite : la quantification dynamique arrondit ses
            // activations avec les noyaux entiers de la plateforme, qui different
            // entre x86 et ARM (mesure sur le Xiaomi : 0,0032 en moyenne, p99
            // 0,013). Ce test garde le chargement du modele, la disposition de la
            // grille et la normalisation ; le pretraitement fin est garde par
            // input_tensor_matches_the_python_mirror.
            assertThat(mean).isLessThan(MAX_MEAN_ERROR)
            assertThat(p99).isLessThan(MAX_P99_ERROR)
        }
    }

    private fun readFloats(path: String): FloatArray =
        testAssets.open(path).use { it.readBytes() }
            .let { ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer() }
            .let { buffer -> FloatArray(buffer.remaining()).also(buffer::get) }

    private fun testImage(): Bitmap = testAssets.open("depth/image.png").use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
        })
    }!!

    private companion object {
        const val TAG = "TopoVisionTest"
        const val MODEL = "models/depth.onnx"
        const val REFERENCE = "depth/reference.json"
        const val MAX_MEAN_ERROR = 0.01
        const val MAX_P99_ERROR = 0.04f
    }
}
