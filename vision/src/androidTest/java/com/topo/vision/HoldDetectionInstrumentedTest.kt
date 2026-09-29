package com.topo.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.topo.core.domain.model.DetectedHold
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Chaine d'inference complete : ONNX Runtime, XNNPACK, pretraitement, decodage
 * et suppression des non-maxima, sur le noyau 16 Ko.
 *
 * Le modele est celui de SUBSTITUTION (YOLO nano COCO), genere par
 * `ml/export_placeholder.py` et jamais versionne pour raison de licence. Sans lui,
 * les tests qui en dependent sont IGNORES avec un message explicite, et non
 * declares verts ; ceux du NMS et du rejet de modele n'en ont pas besoin.
 *
 * La reference a ete calculee en Python par un miroir du code natif, sur ONNX
 * Runtime CPU : un ecart de letterbox, d'ordre des canaux ou de decodage des
 * boites fait echouer [detections_match_the_python_reference].
 */
@RunWith(AndroidJUnit4::class)
class HoldDetectionInstrumentedTest {

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun a_loaded_model_makes_the_engine_ready_and_close_stays_idempotent() = runTest {
        val engine = VisionEngine()
        assertThat(engine.isReady).isFalse()

        engine.loadModel(placeholderModel())

        assertThat(engine.isReady).isTrue()
        engine.close()
        assertThat(engine.isReady).isFalse()
        engine.close()
    }

    @Test
    fun an_invalid_model_is_rejected_at_load_time_without_crashing() = runTest {
        VisionEngine().use { engine ->
            val garbage = ByteBuffer.allocateDirect(1024).apply {
                repeat(1024) { put(it.toByte()) }
                flip()
            }

            val error = runCatching { engine.loadModel(garbage) }.exceptionOrNull()

            // Une exception C++ qui traverserait la frontiere JNI tuerait le
            // processus de test : arriver ici prouve deja la conversion.
            assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
            assertThat(engine.isReady).isFalse()
        }
    }

    @Test
    fun detections_match_the_python_reference() = runTest {
        val reference = JSONObject(assets.open("$PLACEHOLDER/reference.json").bufferedReader().readText())
        val expected = reference.getJSONArray("detections").let { array ->
            (0 until array.length()).map { i ->
                array.getJSONArray(i).let { d -> FloatArray(5) { d.getDouble(it).toFloat() } }
            }
        }

        VisionEngine().use { engine ->
            engine.loadModel(placeholderModel())
            val detections = engine.detect(VisionEngine.BitmapSource(testImage()))

            assertThat(detections).hasSize(expected.size)
            // Appariement par recouvrement, pas par rang : deux scores quasi egaux
            // peuvent s'inverser d'un moteur de calcul a l'autre.
            for (want in expected) {
                val match = detections.maxBy { iou(it, want) }
                assertThat(iou(match, want)).isGreaterThan(0.95f)
                assertThat(abs(match.confidence - want[4])).isLessThan(0.02f)
            }
        }
    }

    @Test
    fun overlapping_candidates_collapse_to_the_most_confident() {
        val kept = NativeBridge.nativeNonMaxSuppression(
            floatArrayOf(
                0.50f, 0.50f, 0.20f, 0.20f, 0.70f,
                0.51f, 0.50f, 0.20f, 0.20f, 0.90f, // meme prise, plus sure
                0.20f, 0.20f, 0.10f, 0.10f, 0.40f, // prise distincte
            ),
            0.45f,
        )

        // Plus sure d'abord, puis la prise distincte ; le doublon disparait.
        assertThat(kept.toList()).containsExactly(
            0.51f, 0.50f, 0.20f, 0.20f, 0.90f,
            0.20f, 0.20f, 0.10f, 0.10f, 0.40f,
        ).inOrder()
    }

    @Test
    fun adjacent_holds_below_the_overlap_threshold_are_both_kept() {
        // Deux prises voisines qui se touchent a peine : IoU = 1/7, bien sous le
        // seuil. Un NMS trop agressif fusionnerait des prises reellement
        // distinctes, frequentes sur un pan dense.
        val kept = NativeBridge.nativeNonMaxSuppression(
            floatArrayOf(
                0.40f, 0.50f, 0.20f, 0.20f, 0.80f,
                0.55f, 0.50f, 0.20f, 0.20f, 0.60f,
            ),
            0.45f,
        )

        assertThat(kept.size / NativeBridge.DETECTION_STRIDE).isEqualTo(2)
    }

    /**
     * Latence sur ce materiel, a titre INDICATIF : l'emulateur x86_64 ne dit rien
     * de l'appareil ARM64 vise, et le code natif de test est compile en -O0. Le
     * critere des 100 ms se verifie sur telephone physique, en release.
     *
     * Chaque modele depose dans les assets `benchmark/` est mesure — typiquement
     * les exports FP32 et INT8 de `ml/train.py` — pour comparer les variantes sur
     * le meme materiel, dans la meme execution. Repertoire hors git, comme tout
     * `.onnx`.
     */
    @Test
    fun inference_latency_is_logged() = runTest {
        val models = assets.list(BENCHMARK).orEmpty().filter { it.endsWith(".onnx") }.sorted()
        assumeTrue("Aucun modele a mesurer : copier des .onnx dans assets/$BENCHMARK", models.isNotEmpty())

        for (model in models) {
            VisionEngine().use { engine ->
                engine.loadModel(directBuffer("$BENCHMARK/$model"))
                val source = VisionEngine.BitmapSource(testImage())
                repeat(WARM_UP) { engine.detect(source) }

                val timings = (1..RUNS).map {
                    val started = System.nanoTime()
                    engine.detect(source)
                    (System.nanoTime() - started) / 1_000_000.0
                }.sorted()

                Log.i(TAG, "Latence $model sur $RUNS inferences : mediane %.1f ms, max %.1f ms"
                    .format(timings[RUNS / 2], timings.last()))
                assertThat(timings).isNotEmpty()
            }
        }
    }

    private fun placeholderModel(): ByteBuffer {
        assumeTrue(
            "Modele de substitution absent : lancer ml/export_placeholder.py",
            assets.list(PLACEHOLDER)?.contains(MODEL) == true,
        )
        return directBuffer("$PLACEHOLDER/$MODEL")
    }

    private fun directBuffer(asset: String): ByteBuffer {
        val bytes = assets.open(asset).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
    }

    private fun testImage(): Bitmap {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            // PNG decode tel quel, pixel pour pixel comme PIL : aucune mise a
            // l'echelle selon la densite de l'ecran.
            inScaled = false
        }
        return assets.open("$PLACEHOLDER/bus.png").use { BitmapFactory.decodeStream(it, null, options)!! }
    }

    private fun iou(hold: DetectedHold, want: FloatArray): Float {
        val a = hold.box
        val ix = max(0f, min(a.centerX + a.width / 2, want[0] + want[2] / 2) -
            max(a.centerX - a.width / 2, want[0] - want[2] / 2))
        val iy = max(0f, min(a.centerY + a.height / 2, want[1] + want[3] / 2) -
            max(a.centerY - a.height / 2, want[1] - want[3] / 2))
        val inter = ix * iy
        return inter / (a.width * a.height + want[2] * want[3] - inter)
    }

    private companion object {
        const val TAG = "TopoVisionTest"
        const val PLACEHOLDER = "placeholder"
        const val MODEL = "yolo11n_coco.onnx"
        const val BENCHMARK = "benchmark"
        const val WARM_UP = 3
        const val RUNS = 10
    }
}
