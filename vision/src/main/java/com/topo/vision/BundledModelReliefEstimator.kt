package com.topo.vision

import android.content.Context
import android.util.Log
import com.topo.core.common.di.DefaultDispatcher
import com.topo.core.common.di.IoDispatcher
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.ReliefMap
import com.topo.core.domain.repository.ReliefEstimator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [ReliefEstimator] de l'application : Depth Anything V2 Small, INT8, embarque sous
 * [MODEL_ASSET] s'il existe.
 *
 * Le relief d'une photo ne change pas : il est calcule une fois (~460 ms sur le
 * Xiaomi) puis garde dans le cache de l'application. Le cache, et non le stockage
 * des blocs : il se reconstruit, et le systeme peut le vider sans rien perdre.
 * La cle couvre la photo, sa zone et le modele : changer l'un recalcule.
 *
 * Le modele n'est charge que pour une photo SANS relief en cache : ouvrir une vue
 * 3D deja calculee ne paie pas les 27 Mo de la session. Il l'est des l'ouverture du
 * bloc ([prepare]) plutot qu'a la premiere vue 3D : ~0,3 s de moins a attendre (284 ms
 * mesurees sur le Xiaomi) ; le reste de l'attente est l'inference.
 */
@Singleton
internal class BundledModelReliefEstimator @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ReliefEstimator {

    private val engine = DepthEngine(dispatcher)
    private val loading = Mutex()
    private var loadAttempted = false

    override val isAvailable: Boolean by lazy { context.assets.hasAsset(MODEL_ASSET) }

    override suspend fun prepare(file: File, region: BoundingBox?) {
        if (!isAvailable || engine.isReady) return
        // Relief deja en cache : la vue 3D n'aura pas besoin du modele, inutile d'en payer
        // la memoire.
        val known = withContext(ioDispatcher) { cacheFile(file, region).exists() }
        if (!known) ensureModelLoaded()
    }

    override suspend fun estimate(file: File, region: BoundingBox?): ReliefMap? {
        if (!isAvailable) return null
        val cached = cacheFile(file, region)
        withContext(ioDispatcher) { readCache(cached) }?.let { return it }

        ensureModelLoaded()
        if (!engine.isReady) return null
        val bitmap = withContext(ioDispatcher) {
            decodeUpright(file, region?.let(NormalizedRect::of) ?: FULL_IMAGE, MIN_DECODED_SIDE)
        }
        val relief = try {
            engine.estimate(bitmap)
        } finally {
            bitmap.recycle()
        } ?: return null
        withContext(ioDispatcher) { writeCache(cached, relief) }
        return relief
    }

    private suspend fun ensureModelLoaded() = loading.withLock {
        if (loadAttempted) return@withLock
        loadAttempted = true
        val started = System.nanoTime()
        val model = withContext(ioDispatcher) { readModelAsset(context.assets, MODEL_ASSET) }
            ?: return@withLock
        engine.loadModel(model)
        Log.i(TAG, "Modele de relief pret en %.0f ms".format(Locale.ROOT, (System.nanoTime() - started) / 1e6))
    }

    private fun cacheFile(file: File, region: BoundingBox?) =
        File(context.cacheDir, "$CACHE_DIR/${cacheKey(file, region)}.bin")

    private fun cacheKey(file: File, region: BoundingBox?): String {
        val zone = region?.let {
            String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f", it.left, it.top, it.right, it.bottom)
        }
        // La taille identifie le modele sans le lire ; illisible si l'asset est compresse.
        val modelSize = runCatching { context.assets.openFd(MODEL_ASSET).use { it.declaredLength } }
            .getOrDefault(0L)
        val key = "${file.name}|${file.length()}|${file.lastModified()}|$zone|$modelSize|v$FORMAT"
        return MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun readCache(file: File): ReliefMap? {
        if (!file.exists()) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val width = input.readInt()
                val height = input.readInt()
                ReliefMap(width, height, FloatArray(width * height) { input.readFloat() })
            }
        } catch (corrupted: IOException) {
            // Ecriture interrompue par la mort du processus : on recalcule.
            Log.w(TAG, "Relief en cache illisible, recalcule", corrupted)
            file.delete()
            null
        }
    }

    private fun writeCache(file: File, relief: ReliefMap) {
        file.parentFile?.mkdirs()
        // Ecrit a cote puis renomme : une lecture ne voit jamais un fichier a moitie ecrit.
        val partial = File(file.path + ".part")
        DataOutputStream(partial.outputStream().buffered()).use { output ->
            output.writeInt(relief.width)
            output.writeInt(relief.height)
            relief.values.forEach(output::writeFloat)
        }
        if (!partial.renameTo(file)) partial.delete()
    }

    private companion object {
        const val TAG = "TopoDepth"
        const val MODEL_ASSET = "models/depth.onnx"
        const val CACHE_DIR = "relief"

        /** Version du format du cache : l'incrementer invalide les anciens fichiers. */
        const val FORMAT = 1

        /** Deux fois le grand cote d'entree du modele (518), cf. [decodeUpright]. */
        const val MIN_DECODED_SIDE = 1036

        val FULL_IMAGE = NormalizedRect(0f, 0f, 1f, 1f)
    }
}
