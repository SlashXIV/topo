package com.topo.vision

import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.DetectedHold

/**
 * Rectangle normalise dans `[0, 1]`, par ses bords.
 *
 * Distinct de [BoundingBox], qui refuse une boite de centre hors image mais
 * accepte une boite qui deborde : ici, une zone de decodage doit tenir
 * ENTIEREMENT dans l'image, bords compris.
 */
internal data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        /** Zone de l'utilisateur, rognee aux bords de l'image. */
        fun of(box: BoundingBox) = NormalizedRect(
            left = box.left.coerceIn(0f, 1f),
            top = box.top.coerceIn(0f, 1f),
            right = box.right.coerceIn(0f, 1f),
            bottom = box.bottom.coerceIn(0f, 1f),
        )
    }
}

/**
 * Zone designee sur la photo REDRESSEE -> meme zone dans l'image BRUTE du fichier.
 *
 * L'utilisateur trace la zone sur la photo affichee, que Coil redresse selon
 * l'EXIF ; BitmapRegionDecoder, lui, lit le fichier brut, sans rotation. La
 * rotation EXIF indique de combien tourner le brut, dans le sens horaire, pour
 * obtenir l'affiche : on applique ici la rotation inverse.
 *
 * Pour 90° : un pixel brut (x, y) s'affiche en (1 - y, x). Donc un point affiche
 * (u, v) vient du brut (v, 1 - u). Pour 270°, c'est le sens contraire.
 */
internal fun NormalizedRect.toRaw(rotationDegrees: Int): NormalizedRect = when (rotationDegrees) {
    0 -> this
    90 -> NormalizedRect(left = top, top = 1f - right, right = bottom, bottom = 1f - left)
    180 -> NormalizedRect(left = 1f - right, top = 1f - bottom, right = 1f - left, bottom = 1f - top)
    270 -> NormalizedRect(left = 1f - bottom, top = left, right = 1f - top, bottom = right)
    else -> throw IllegalArgumentException("Rotation EXIF inattendue : $rotationDegrees")
}

/**
 * Detection faite dans la zone -> meme detection dans la photo entiere.
 *
 * Le moteur ne voit que la zone et rend des boites normalisees DANS la zone ;
 * l'application, elle, stocke des boites normalisees dans la photo.
 */
internal fun DetectedHold.fromRegion(region: NormalizedRect): DetectedHold = copy(
    box = BoundingBox(
        centerX = region.left + box.centerX * region.width,
        centerY = region.top + box.centerY * region.height,
        width = box.width * region.width,
        height = box.height * region.height,
    ),
)
