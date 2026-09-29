package com.topo.feature.relief

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.topo.core.domain.model.BoundingBox
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Photo du mur, recadree sur la zone du bloc, pour la texture de la vue 3D.
 *
 * Decodee par Coil, comme a l'affichage : la zone a ete tracee sur la photo redressee
 * par Coil, la texture l'est donc de la meme facon, EXIF compris. Hors du cache
 * memoire : une photo decodee en grand pour sa zone n'a rien a y faire.
 */
internal class ReliefTextureLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun load(file: File, zone: BoundingBox?): Bitmap? {
        val left = zone?.left?.coerceIn(0f, 1f) ?: 0f
        val top = zone?.top?.coerceIn(0f, 1f) ?: 0f
        val right = zone?.right?.coerceIn(0f, 1f) ?: 1f
        val bottom = zone?.bottom?.coerceIn(0f, 1f) ?: 1f
        if (right <= left || bottom <= top) return null

        // Decodee assez grand pour que la ZONE atteigne la resolution de texture visee.
        val side = (TEXTURE_SIDE / max(right - left, bottom - top)).roundToInt().coerceAtMost(MAX_DECODE_SIDE)
        val request = ImageRequest.Builder(context)
            .data(file)
            .size(side)
            .precision(Precision.INEXACT)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()
        val photo = (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap ?: return null

        val x = (left * photo.width).toInt()
        val y = (top * photo.height).toInt()
        val width = ((right - left) * photo.width).toInt().coerceIn(1, photo.width - x)
        val height = ((bottom - top) * photo.height).toInt().coerceIn(1, photo.height - y)
        val crop = Bitmap.createBitmap(photo, x, y, width, height)
        val longest = max(crop.width, crop.height)
        if (longest <= TEXTURE_SIDE) return crop
        val ratio = TEXTURE_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(crop, (crop.width * ratio).roundToInt(), (crop.height * ratio).roundToInt(), true)
    }

    private companion object {
        /** Grand cote de la texture : au-dela, l'ecran ne montre pas la difference. */
        const val TEXTURE_SIDE = 1536
        const val MAX_DECODE_SIDE = 4096
    }
}
