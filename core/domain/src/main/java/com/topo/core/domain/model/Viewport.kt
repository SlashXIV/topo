package com.topo.core.domain.model

/**
 * Dimensions, en pixels, de la zone ou l'image du bloc est affichee.
 *
 * Indispensable au calcul de magnetisation, et pas seulement pour convertir des
 * unites : l'espace normalise `[0,1]x[0,1]` est ANISOTROPE des que l'image n'est
 * pas carree. Sur une photo 3000x4000 affichee en 900x1200, un ecart de 0,01 en X
 * vaut 9 px a l'ecran et le meme ecart en Y en vaut 12.
 *
 * Calculer la distance directement en coordonnees normalisees produirait donc un
 * rayon d'attraction OVALE : la prise du dessus se magnetiserait plus tot que celle
 * de cote, a distance visuelle egale. Le doigt, lui, se deplace dans l'espace
 * ecran. Toute la geometrie de magnetisation se fait donc en pixels.
 */
data class Viewport(
    val widthPx: Float,
    val heightPx: Float,
) {
    init {
        require(widthPx > 0f && heightPx > 0f) {
            "Viewport degenere : ${widthPx}x$heightPx"
        }
    }
}
