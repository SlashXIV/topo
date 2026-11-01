package com.topo.core.ui.motion

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.topo.core.domain.model.MotionPreference

/**
 * Langage de mouvement : trois courbes et rien d'autre. Ce sont celles des autres
 * applications de l'auteur (Dromos, le site) : la famille bouge de la meme facon.
 */
object Motion {
    /** Entrees : depart vif, arrivee douce. */
    val Enter = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

    /** Pages, traces, scan : symetrique, appuye au milieu. */
    val Transition = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

    /** Relachement et tampon, avec un leger depassement. */
    val Release = CubicBezierEasing(0.2f, 0.9f, 0.2f, 1.2f)

    const val ENTER_MS = 500
    const val TRANSITION_MS = 640
    const val RELEASE_MS = 600
    const val DRAW_MS = 1_400

    /** Duree unique de tout mouvement en ambiance sobre. */
    const val CALM_MS = 180

    const val LETTER_STAGGER_MS = 45
    const val ITEM_STAGGER_MS = 60

    /** Au-dela, une cascade ferait attendre : le reste entre d'un bloc. */
    const val MAX_STAGGERED_ITEMS = 8
}

enum class MotionLevel {
    /** Ambiance expressive : tout le mouvement. */
    FULL,

    /** Ambiance sobre : fondus brefs, aucun effet decoratif. */
    REDUCED,

    /** « Supprimer les animations » du systeme : aucun mouvement. */
    NONE;

    val decorative: Boolean get() = this == FULL
    val animates: Boolean get() = this != NONE
}

val LocalMotionLevel = staticCompositionLocalOf { MotionLevel.FULL }

/**
 * Niveau effectif. Lu une fois par composition de l'activite : le reglage
 * systeme change rarement, et le relire a chaque trame couterait un appel au
 * ContentResolver.
 */
@Composable
fun rememberMotionLevel(preference: MotionPreference): MotionLevel {
    val context = LocalContext.current
    val systemScale = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    return motionLevelOf(systemScale, preference)
}

/** Le reglage systeme d'accessibilite prime toujours sur l'ambiance choisie. */
fun motionLevelOf(systemAnimatorScale: Float, preference: MotionPreference): MotionLevel = when {
    systemAnimatorScale == 0f -> MotionLevel.NONE
    preference == MotionPreference.CALM -> MotionLevel.REDUCED
    else -> MotionLevel.FULL
}
