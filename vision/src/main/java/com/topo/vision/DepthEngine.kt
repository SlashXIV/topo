package com.topo.vision

import android.graphics.Bitmap
import com.topo.core.domain.model.ReliefMap
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Facade Kotlin de l'estimateur de profondeur natif (`depth_estimator.cpp`).
 *
 * Meme gestion de vie que [VisionEngine] : handle dans un [AtomicLong], [close]
 * idempotent et deterministe. Un handle de profondeur n'est JAMAIS passe a une
 * fonction de detection, ni l'inverse : les deux moteurs natifs sont distincts.
 */
class DepthEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {

    private val handle = AtomicLong(NativeBridge.nativeDepthCreate())

    val isReady: Boolean
        get() = handle.get().let { it != 0L && NativeBridge.nativeDepthIsReady(it) }

    /** @throws IllegalArgumentException si le modele est illisible ou n'a pas le format attendu. */
    suspend fun loadModel(model: ByteBuffer) = withContext(dispatcher) {
        require(model.isDirect) { "Le modele doit etre dans un ByteBuffer direct" }
        NativeBridge.nativeDepthLoadModel(checkOpen(), model)
    }

    /** Relief d'un bitmap ARGB_8888, ou null si aucun modele n'est charge. */
    suspend fun estimate(bitmap: Bitmap): ReliefMap? = withContext(dispatcher) {
        val flat = NativeBridge.nativeEstimateDepth(checkOpen(), bitmap)
        require(flat.size >= 2) { "Tableau natif malforme : ${flat.size} valeurs" }
        val width = flat[0].toInt()
        val height = flat[1].toInt()
        if (width == 0 || height == 0) return@withContext null
        ReliefMap(width, height, flat.copyOfRange(2, flat.size))
    }

    override fun close() {
        val released = handle.getAndSet(0L)
        if (released != 0L) NativeBridge.nativeDepthDestroy(released)
    }

    private fun checkOpen(): Long = handle.get().also { check(it != 0L) { "DepthEngine deja ferme" } }
}
