package com.topo.vision

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.topo.core.common.di.DefaultDispatcher
import com.topo.core.common.di.IoDispatcher
import com.topo.core.domain.model.DetectedHold
import com.topo.core.domain.repository.HoldDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [HoldDetector] de l'application : le moteur natif, alimente par le modele
 * embarque dans les assets s'il existe.
 *
 * AUCUN modele n'est embarque aujourd'hui. Le detecteur reste alors non pret et
 * [detect] retourne une liste vide : c'est le chemin nominal du MVP, ou
 * l'utilisateur pose ses prises a la main. Deposer un modele sous [MODEL_ASSET]
 * suffit a activer la detection, sans toucher a aucune autre couche.
 *
 * Le modele est charge a la PREMIERE detection, pas a la creation : Hilt
 * construit ce singleton au demarrage de l'ecran qui l'injecte, et plusieurs
 * centaines de millisecondes d'analyse du graphe n'ont rien a faire la.
 *
 * Singleton de processus, jamais ferme : la session vit aussi longtemps que
 * l'application, et le systeme recupere sa memoire native a la mort du
 * processus.
 */
@Singleton
internal class BundledModelHoldDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HoldDetector {

    private val engine = VisionEngine(dispatcher)
    private val loading = Mutex()
    private var loadAttempted = false

    override val isReady: Boolean get() = engine.isReady

    /**
     * @throws IllegalArgumentException a la premiere detection si le modele
     *         embarque est invalide. Les appels suivants retournent une liste
     *         vide : un modele casse ne se repare pas en reessayant.
     */
    override suspend fun detect(image: HoldDetector.ImageSource): List<DetectedHold> {
        ensureModelLoaded()
        // Sans modele, inutile de decoder la photo pour une reponse vide connue.
        if (!engine.isReady) return emptyList()
        return engine.detect(image)
    }

    override fun close() = engine.close()

    private suspend fun ensureModelLoaded() = loading.withLock {
        if (loadAttempted) return@withLock
        loadAttempted = true

        val model = withContext(ioDispatcher) { readModel(context.assets) }
        if (model == null) {
            Log.i(TAG, "Aucun modele embarque : detection automatique inactive")
            return@withLock
        }
        engine.loadModel(model)
    }

    private fun readModel(assets: AssetManager): ByteBuffer? {
        val directory = MODEL_ASSET.substringBeforeLast('/')
        val name = MODEL_ASSET.substringAfterLast('/')
        if (assets.list(directory)?.contains(name) != true) return null

        return try {
            // Asset non compresse : projete en memoire, jamais recopie dans le
            // heap Java. Un MappedByteBuffer est direct, le pont le lit tel quel.
            assets.openFd(MODEL_ASSET).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                }
            }
        } catch (compressed: IOException) {
            // openFd refuse un asset compresse. Repli par copie, une seule fois
            // par processus : le modele est lu au premier appel puis oublie, ONNX
            // Runtime en ayant sa propre copie.
            Log.w(TAG, "Modele compresse dans l'APK : lecture par copie", compressed)
            val bytes = assets.open(MODEL_ASSET).use { it.readBytes() }
            ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
        }
    }

    private companion object {
        const val TAG = "TopoVision"
        const val MODEL_ASSET = "models/holds.onnx"
    }
}
