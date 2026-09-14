package com.betaclimb.vision

import android.graphics.Bitmap
import com.betaclimb.core.domain.model.BoundingBox
import com.betaclimb.core.domain.model.DetectedHold
import com.betaclimb.core.domain.model.HoldOrigin
import com.betaclimb.core.domain.repository.HoldDetector
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
 * Sprint 0 : le pont est traverse pour de vrai, mais aucun modele n'est charge,
 * donc [detect] retourne toujours une liste vide.
 */
class VisionEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : HoldDetector {

    private val handle = AtomicLong(NativeBridge.nativeCreate())

    override val isReady: Boolean
        get() = false // Aucun modele embarque a ce stade (cf. sprint Vision).

    /** Image portee par un [Bitmap] ARGB_8888 (photo capturee ou importee). */
    data class BitmapSource(val bitmap: Bitmap) : HoldDetector.ImageSource

    /** Image portee par un plan direct d'`ImageProxy` CameraX. */
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
