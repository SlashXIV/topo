package com.topo.feature.annotator

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Place les numeros des mouvements autour de leur prise cible.
 *
 * Plusieurs mouvements visent souvent la meme prise (une main la prend, l'autre
 * la rejoint) ou des prises voisines : poses tous au meme decalage, leurs numeros
 * s'empilaient et seul le dernier restait lisible. Chaque numero essaie donc des
 * positions en eventail autour de sa prise, de l'angle haut-droite habituel vers
 * l'arriere, puis sur des anneaux plus larges, et garde la premiere qui ne touche
 * aucun numero deja pose et reste dans la photo.
 *
 * Glouton dans l'ordre des mouvements : le 1 garde toujours sa place habituelle,
 * et un mouvement ajoute en fin de beta ne deplace aucun numero existant.
 *
 * @param targets centre de la prise cible de chaque mouvement, en pixels ; null
 *   si la prise n'existe plus.
 * @return le centre de chaque numero, null la ou la cible l'est.
 */
internal fun layoutBadges(targets: List<Offset?>, radius: Float, bounds: Size): List<Offset?> {
    val placed = ArrayList<Offset>(targets.size)
    val minDistance = 2f * radius + radius * BADGE_GAP
    val baseDistance = radius * MARKER_OFFSET * SQRT_2

    fun inside(at: Offset) = at.x >= radius && at.x <= bounds.width - radius &&
        at.y >= radius && at.y <= bounds.height - radius

    fun free(at: Offset) = placed.none { (it - at).getDistance() < minDistance }

    return targets.map { target ->
        if (target == null) return@map null
        var chosen: Offset? = null
        search@ for (ring in 0 until RINGS) {
            val distance = baseDistance + ring * minDistance
            for (step in FAN_STEPS) {
                val angle = Math.toRadians((START_ANGLE + step).toDouble())
                val at = Offset(
                    target.x + distance * cos(angle).toFloat(),
                    target.y + distance * sin(angle).toFloat(),
                )
                if (inside(at) && free(at)) {
                    chosen = at
                    break@search
                }
            }
        }
        // Rien de libre : la place habituelle, ramenee dans la photo. Un numero
        // recouvert vaut mieux qu'un numero rejete hors du cadre.
        val at = chosen ?: Offset(
            (target.x + baseDistance / SQRT_2).coerceIn(radius, maxOf(radius, bounds.width - radius)),
            (target.y - baseDistance / SQRT_2).coerceIn(radius, maxOf(radius, bounds.height - radius)),
        )
        placed += at
        at
    }
}

// Assez loin pour que le numero ne mange ni la prise ni le trait qui y arrive.
internal const val MARKER_OFFSET = 1.4f

private val SQRT_2 = sqrt(2f)

/** Ecart minimal entre deux numeros, en fraction de rayon. */
private const val BADGE_GAP = 0.25f

/** Haut-droite, en degres dans le repere de l'ecran (y vers le bas). */
private const val START_ANGLE = -45f

/** Eventail alterne de part et d'autre de l'angle de depart. */
private val FAN_STEPS = floatArrayOf(0f, 30f, -30f, 60f, -60f, 90f, -90f, 120f, -120f, 150f, -150f, 180f)

private const val RINGS = 4
