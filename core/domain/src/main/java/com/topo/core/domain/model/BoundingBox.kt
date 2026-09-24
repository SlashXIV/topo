package com.topo.core.domain.model

/**
 * Boite englobante d'une prise, en coordonnees NORMALISEES dans `[0, 1]`.
 *
 * L'invariant de normalisation est central au projet, pas un detail de stockage :
 * il rend une prise independante de la resolution de la photo, du facteur de zoom
 * du Canvas et de l'orientation de l'ecran. La denormalisation n'a lieu qu'une
 * fois, a l'affichage, a partir des dimensions enregistrees sur le bloc.
 *
 * Les bornes sont volontairement verifiees ici plutot que dans la couche data :
 * une donnee invalide doit etre impossible a construire, pas seulement impossible
 * a enregistrer.
 */
data class BoundingBox(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
) {
    init {
        require(width > 0f && height > 0f) {
            "Boite degeneree : largeur=$width hauteur=$height"
        }
        require(centerX in 0f..1f && centerY in 0f..1f) {
            "Centre hors image : ($centerX, $centerY)"
        }
    }

    val left: Float get() = centerX - width / 2f
    val top: Float get() = centerY - height / 2f
    val right: Float get() = centerX + width / 2f
    val bottom: Float get() = centerY + height / 2f

    /**
     * Distance euclidienne entre le centre de cette prise et un point.
     *
     * Base de l'algorithme de magnetisation du sprint suivant. Complexite lineaire
     * assumee : un mur de bloc depasse rarement 150 prises, et 150 distances par
     * trame de 16 ms sont gratuites sur n'importe quel ARM moderne. Un KD-Tree
     * serait de la sur-ingenierie a ce volume.
     */
    fun distanceToCenter(x: Float, y: Float): Float {
        val dx = centerX - x
        val dy = centerY - y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
