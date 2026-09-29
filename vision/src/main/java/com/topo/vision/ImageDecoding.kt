package com.topo.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.os.Build
import java.io.File

/**
 * Decode la zone d'une photo de bloc pour l'inference, redressee.
 *
 * Seule la ZONE est lue, par BitmapRegionDecoder : c'est elle, et non la photo
 * entiere, que le modele recoit (640 pixels pour les prises, 518 pour le relief).
 * Sur un bloc qui couvre un tiers de la photo, chaque prise y gagne environ trois
 * fois plus de pixels — les
 * petites prises, les premieres manquees, en profitent le plus.
 *
 * Sous-echantillonnee : on garde au moins le double de l'entree du modele,
 * pour que le redimensionnement final reste un vrai filtrage et non un saut
 * de pixels ; au-dela, decoder coute de la memoire pour rien.
 *
 * Redressee selon l'EXIF : BitmapFactory l'ignore, alors que Coil l'applique a
 * l'affichage. La zone, tracee sur la photo affichee, est donc d'abord ramenee
 * dans l'image brute (cf. [NormalizedRect.toRaw]).
 */
internal fun decodeUpright(file: File, region: NormalizedRect, minDecodedSide: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image illisible : ${file.name}" }

    val rotation = when (
        ExifInterface(file.path).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    val raw = region.toRaw(rotation)
    val pixels = Rect(
        (raw.left * bounds.outWidth).toInt(),
        (raw.top * bounds.outHeight).toInt(),
        kotlin.math.ceil(raw.right * bounds.outWidth).toInt().coerceAtMost(bounds.outWidth),
        kotlin.math.ceil(raw.bottom * bounds.outHeight).toInt().coerceAtMost(bounds.outHeight),
    )
    require(pixels.width() > 0 && pixels.height() > 0) { "Zone vide" }

    var sample = 1
    while (maxOf(pixels.width(), pixels.height()) / (sample * 2) >= minDecodedSide) {
        sample *= 2
    }
    val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BitmapRegionDecoder.newInstance(file.path)
    } else {
        @Suppress("DEPRECATION")
        BitmapRegionDecoder.newInstance(file.path, false)
    } ?: throw IllegalArgumentException("Image illisible : ${file.name}")
    val decoded = try {
        decoder.decodeRegion(
            pixels,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    } finally {
        decoder.recycle()
    } ?: throw IllegalArgumentException("Image illisible : ${file.name}")

    if (rotation == 0) return decoded
    val upright = Bitmap.createBitmap(
        decoded, 0, 0, decoded.width, decoded.height,
        Matrix().apply { postRotate(rotation.toFloat()) }, true,
    )
    decoded.recycle()
    return upright
}
