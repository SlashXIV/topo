package com.topo.vision

import android.graphics.Bitmap
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.DetectedHold
import com.topo.core.domain.model.HoldOrigin
import com.topo.core.domain.repository.HoldDetector
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Facade Kotlin du moteur de vision natif.
 *
 * Seul point du depot qui connaisse l'existence de [NativeBridge]. Elle
 * implemente [HoldDetector], si bien que les couches superieures ne dependent
 * que du domaine.
 *
 * Gestion de la duree de vie : le handle natif est detenu dans un [AtomicLong]
 * et [close] est idempotent et sur en concurrence. C'est volontairement
 * deterministe - aucun finaliseur, aucun `Cleaner` : une session ONNX Runtime
 * pese plusieurs dizaines de Mo hors du heap Java et ne doit pas attendre le
 * bon vouloir du ramasse-miettes.
 *
 * Sans modele charge, [detect] traverse le pont et retourne une liste vide :
 * c'est le chemin nominal tant qu'aucun modele de prises n'est embarque.
 */
class VisionEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : HoldDetector {

    private val handle = AtomicLong(NativeBridge.nativeCreate())

    override val isReady: Boolean
        get() = handle.get().let { it != 0L && NativeBridge.nativeIsReady(it) }

    /**
     * Charge un modele ONNX au format YOLO `[1, 4 + classes, N]`.
     *
     * Hors du thread principal : l'analyse et l'optimisation du graphe prennent
     * plusieurs centaines de millisecondes.
     *
     * @throws IllegalArgumentException si le modele est illisible ou n'a pas le
     *         format attendu — verifie au chargement, pas a la premiere image.
     */
    suspend fun loadModel(model: ByteBuffer) = withContext(dispatcher) {
        require(model.isDirect) { "Le modele doit etre dans un ByteBuffer direct" }
        val current = handle.get()
        check(current != 0L) { "VisionEngine deja ferme" }
        NativeBridge.nativeLoadModel(current, model)
    }

    /** Image portee par un [Bitmap] ARGB_8888 (photo capturee ou importee). */
    data class BitmapSource(val bitmap: Bitmap) : HoldDetector.ImageSource

    /** Image RGBA_8888 portee par un plan direct d'`ImageProxy` CameraX. */
    data class DirectBufferSource(
        val buffer: ByteBuffer,
        val width: Int,
        val height: Int,
        val rowStride: Int,
    ) : HoldDetector.ImageSource {
        init {
            require(buffer.isDirect) {
                "Le ByteBuffer doit etre direct : ByteBuffer.allocateDirect() " +
                    "ou un plan d'ImageProxy CameraX"
            }
        }
    }

    override suspend fun detect(image: HoldDetector.ImageSource): List<DetectedHold> =
        withContext(dispatcher) {
            val current = handle.get()
            check(current != 0L) { "VisionEngine deja ferme" }

            val flat = when (image) {
                is BitmapSource ->
                    NativeBridge.nativeDetectFromBitmap(current, image.bitmap)

                is DirectBufferSource ->
                    NativeBridge.nativeDetectFromDirectBuffer(
                        current,
                        image.buffer,
                        image.width,
                        image.height,
                        image.rowStride,
                    )

                else -> error("Source d'image non supportee : ${image::class.java.name}")
            }
            flat.toDetections()
        }

    override fun close() {
        // getAndSet(0) : deux close() concurrents ne peuvent pas liberer deux fois.
        val released = handle.getAndSet(0L)
        if (released != 0L) {
            NativeBridge.nativeDestroy(released)
        }
    }

    private fun FloatArray.toDetections(): List<DetectedHold> {
        val stride = NativeBridge.DETECTION_STRIDE
        require(size % stride == 0) {
            "Tableau natif malforme : taille $size non multiple de $stride"
        }
        return (indices step stride).map { i ->
            DetectedHold(
                box = BoundingBox(
                    centerX = this[i],
                    centerY = this[i + 1],
                    width = this[i + 2],
                    height = this[i + 3],
                ),
                confidence = this[i + 4],
                origin = HoldOrigin.MODEL,
            )
        }
    }
}
