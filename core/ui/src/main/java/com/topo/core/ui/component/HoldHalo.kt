package com.topo.core.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Halo d'une prise du bloc, a la couleur de sa difficulte.
 *
 * Un seul dessin pour l'ecran d'annotation et pour la vignette de la seance : le
 * grimpeur doit reconnaitre le meme halo aux deux endroits. Trois couches pour
 * rester lisible sur n'importe quel mur : un disque translucide qui teinte la
 * prise, un anneau plein de la couleur, et un lisere sombre qui le detache d'un
 * mur de la meme couleur.
 *
 * Les traits sont fournis par l'appelant, alloues une fois : ce dessin tourne a
 * chaque trame du trace, et un Stroke par appel serait une allocation par prise
 * et par trame.
 */
fun DrawScope.drawHoldHalo(center: Offset, radius: Float, color: Color, ring: Stroke, outline: Stroke) {
    drawCircle(color = color.copy(alpha = HALO_FILL_ALPHA), radius = radius, center = center)
    drawCircle(color = color, radius = radius, center = center, style = ring)
    drawCircle(color = HaloOutline, radius = radius + ring.width / 2f, center = center, style = outline)
}

private const val HALO_FILL_ALPHA = 0.30f
private val HaloOutline = Color.Black.copy(alpha = 0.55f)
