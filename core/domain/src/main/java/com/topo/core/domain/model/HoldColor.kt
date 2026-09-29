package com.topo.core.domain.model

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Couleur d'une prise, en CIELAB (illuminant D65), mesuree sur la photo.
 *
 * Lab et non RVB : la distance y suit a peu pres la perception. Surtout, la
 * teinte y est SEPAREE de la luminosite, ce qui compte sur un mur ou la meme
 * prise passe de l'ombre a la lumiere d'un projecteur.
 *
 * @property l luminosite, de 0 (noir) a 100 (blanc).
 * @property a axe vert (negatif) - rouge (positif).
 * @property b axe bleu (negatif) - jaune (positif).
 */
data class HoldColor(val l: Float, val a: Float, val b: Float) {

    /** Saturation perceptuelle : distance a l'axe des gris. */
    val chroma: Float get() = hypot(a, b)

    /** Teinte en degres, dans `[0, 360)`. Sans signification pour une couleur neutre. */
    val hue: Float
        get() = Math.toDegrees(atan2(b, a).toDouble()).toFloat().let { if (it < 0f) it + 360f else it }
}

/**
 * Nature d'un objet du mur.
 *
 * Les ordinaux sont persistes en base : ne JAMAIS reordonner ni inserer au milieu.
 */
enum class HoldKind {
    /** Prise vissee : appartient a un bloc, par sa couleur. */
    HOLD,

    /** Volume : fait partie du mur, sert a tous les blocs quelle que soit sa couleur. */
    VOLUME,
}
