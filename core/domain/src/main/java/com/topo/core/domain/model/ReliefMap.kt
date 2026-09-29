package com.topo.core.domain.model

/**
 * Relief estime d'une photo de bloc : une grille de valeurs dans `[0, 1]`, ligne
 * par ligne, 1 designant le point le plus proche de l'objectif.
 *
 * RELATIF, jamais metrique : une estimation monoculaire ne connait pas l'echelle.
 * Il sert a montrer le mur en volume, pas a mesurer une prise.
 *
 * La grille recouvre la zone estimee dans son ensemble, en coordonnees
 * normalisees : `(0, 0)` en haut a gauche, `(1, 1)` en bas a droite.
 *
 * Pas une data class : l'egalite structurelle d'un [FloatArray] serait une
 * egalite d'identite, trompeuse.
 */
class ReliefMap(val width: Int, val height: Int, val values: FloatArray) {
    init {
        require(width > 0 && height > 0) { "Relief vide : ${width}x$height" }
        require(values.size == width * height) {
            "Relief malforme : ${values.size} valeurs pour ${width}x$height"
        }
    }

    /** Relief au point normalise `(x, y)`, par interpolation bilineaire. */
    fun sample(x: Float, y: Float): Float {
        val fx = (x.coerceIn(0f, 1f) * width - 0.5f).coerceIn(0f, (width - 1).toFloat())
        val fy = (y.coerceIn(0f, 1f) * height - 0.5f).coerceIn(0f, (height - 1).toFloat())
        val x0 = fx.toInt()
        val y0 = fy.toInt()
        val x1 = minOf(x0 + 1, width - 1)
        val y1 = minOf(y0 + 1, height - 1)
        val tx = fx - x0
        val ty = fy - y0
        val top = values[y0 * width + x0] * (1 - tx) + values[y0 * width + x1] * tx
        val bottom = values[y1 * width + x0] * (1 - tx) + values[y1 * width + x1] * tx
        return top * (1 - ty) + bottom * ty
    }
}
