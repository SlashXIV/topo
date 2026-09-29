package com.topo.vision

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.nio.FloatBuffer
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Latence des variantes du modele de profondeur (Depth Anything V2 Small), a comparer sur
 * appareil avant de choisir celle qu'embarque la vue 3D.
 *
 * Passe par l'API Java d'ONNX Runtime, deja livree dans l'AAR : il s'agit de choisir un
 * modele, pas encore de l'integrer au moteur natif. Modeles deposes a la main dans les assets
 * `benchmark/` (`depth_*.onnx`, hors git) ; sans eux, le test est ignore.
 */
@RunWith(AndroidJUnit4::class)
class DepthModelBenchmark {

    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun depth_model_variants_latency_is_logged() {
        val models = assets.list(BENCHMARK).orEmpty().filter { it.startsWith("depth_") }.sorted()
        assumeTrue("Aucun modele de profondeur dans assets/$BENCHMARK", models.isNotEmpty())
        val environment = OrtEnvironment.getEnvironment()
        // Photo de bloc en portrait 3:4, cotes multiples de 14 comme l'exige le modele.
        val shape = longArrayOf(1, 3, HEIGHT.toLong(), WIDTH.toLong())
        val input = FloatBuffer.wrap(FloatArray(3 * HEIGHT * WIDTH) { (it % 97) / 97f })

        for (name in models) {
            val bytes = assets.open("$BENCHMARK/$name").use { it.readBytes() }
            for (xnnpack in listOf(true, false)) {
                val options = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    if (xnnpack) {
                        setIntraOpNumThreads(1)
                        addXnnpack(mapOf("intra_op_num_threads" to "4"))
                    } else {
                        setIntraOpNumThreads(4)
                    }
                }
                val label = "Profondeur $name ${if (xnnpack) "XNNPACK" else "CPU"} ${WIDTH}x$HEIGHT"
                // XNNPACK refuse certains noeuds (Resize de la tete du modele) : on note
                // l'echec et on mesure les autres configurations.
                val session = runCatching { environment.createSession(bytes, options) }
                    .onFailure { Log.w(TAG, "$label : session impossible (${it.message?.take(160)})") }
                    .getOrNull() ?: continue
                session.use { session ->
                    OnnxTensor.createTensor(environment, input, shape).use { tensor ->
                        val feed = mapOf(session.inputNames.first() to tensor)
                        repeat(WARM_UP) { session.run(feed).close() }
                        val timings = (1..RUNS).map {
                            val started = System.nanoTime()
                            session.run(feed).close()
                            (System.nanoTime() - started) / 1_000_000.0
                        }.sorted()
                        Log.i(TAG, "$label : mediane %.0f ms, max %.0f ms"
                            .format(timings[RUNS / 2], timings.last()))
                        assertThat(timings).isNotEmpty()
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "TopoVisionTest"
        const val BENCHMARK = "benchmark"
        const val WIDTH = 392
        const val HEIGHT = 518
        const val WARM_UP = 2
        const val RUNS = 5
    }
}
