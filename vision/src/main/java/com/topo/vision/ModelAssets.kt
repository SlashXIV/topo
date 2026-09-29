package com.topo.vision

import android.content.res.AssetManager
import android.util.Log
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** true si l'asset existe, sans l'ouvrir. */
internal fun AssetManager.hasAsset(path: String): Boolean =
    list(path.substringBeforeLast('/'))?.contains(path.substringAfterLast('/')) == true

/**
 * Modele ONNX des assets dans un buffer direct, ou null s'il n'est pas embarque.
 *
 * Asset non compresse (`noCompress += "onnx"`) : projete en memoire, jamais recopie
 * dans le heap Java. Un MappedByteBuffer est direct, le pont le lit tel quel.
 */
internal fun readModelAsset(assets: AssetManager, path: String): ByteBuffer? {
    if (!assets.hasAsset(path)) return null

    return try {
        assets.openFd(path).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).channel.use { channel ->
                channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    descriptor.startOffset,
                    descriptor.declaredLength,
                )
            }
        }
    } catch (compressed: IOException) {
        // openFd refuse un asset compresse. Repli par copie, une seule fois par
        // processus : ONNX Runtime garde sa propre copie du modele.
        Log.w("TopoVision", "Modele compresse dans l'APK : lecture par copie ($path)", compressed)
        val bytes = assets.open(path).use { it.readBytes() }
        ByteBuffer.allocateDirect(bytes.size).put(bytes).apply { flip() }
    }
}
