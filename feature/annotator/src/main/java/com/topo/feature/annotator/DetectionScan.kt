package com.topo.feature.annotator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextLayoutResult
import com.topo.core.domain.model.BoundingBox

/**
 * Ordonnee normalisee de la ligne de scan pour une progression dans `[0, 1]`.
 *
 * Le scan MONTE du pied du mur vers son sommet, comme on grimpe : a 0 il est en bas
 * de la zone analysee, a 1 en haut. Fonction pure, testee sur JVM.
 */
fun scanLineY(progress: Float, zoneTop: Float, zoneBottom: Float): Float =
    zoneBottom - progress.coerceIn(0f, 1f) * (zoneBottom - zoneTop)

/**
 * Une prise tout juste detectee n'apparait qu'une fois la ligne passee : sous elle, donc a
 * une ordonnee plus grande (l'origine est en haut de la photo).
 */
fun isRevealed(holdY: Float, lineY: Float): Boolean = holdY >= lineY

/**
 * Scan de detection : une ligne balaie la zone analysee, et les prises proposees
 * s'allument a son passage avec leur score.
 *
 * Il se joue APRES l'inference, qui ne dure qu'une soixantaine de millisecondes : il
 * montre le resultat, il ne simule pas un calcul. Les prises deja posees restent
 * affichees ; seules [revealing] attendent la ligne.
 *
 * Meme regle de performance que le rejeu ([BetaPlaybackState]) : [progress] n'est lu que
 * dans la phase de dessin, jamais en composition.
 */
@Stable
class DetectionScanState {

    /** Au repos, la progression est pleine : aucune prise n'est cachee. */
    val progress = Animatable(DONE)

    var revealing: Set<String> by mutableStateOf(emptySet())
        private set

    var zone: BoundingBox? by mutableStateOf(null)
        private set

    /**
     * Etiquettes de score, mesurees une fois par prise au premier dessin. Hors du systeme
     * de snapshots : c'est un cache, sa mise a jour ne doit rien invalider.
     */
    internal val labels = HashMap<String, TextLayoutResult>()

    val isRunning: Boolean get() = progress.value < DONE

    suspend fun play(holdIds: List<String>, zone: BoundingBox?) {
        if (holdIds.isEmpty()) return
        labels.clear()
        revealing = holdIds.toSet()
        this.zone = zone
        progress.snapTo(0f)
        progress.animateTo(DONE, tween(DURATION_MS, easing = FastOutSlowInEasing))
        revealing = emptySet()
    }

    /** Pour les tests : place la ligne sans animer. */
    internal suspend fun snapTo(value: Float, holdIds: Set<String>, zone: BoundingBox? = null) {
        revealing = holdIds
        this.zone = zone
        progress.snapTo(value)
    }

    internal companion object {
        const val DONE = 1f
        const val DURATION_MS = 1400

        /** Hauteur, en fraction de zone, pendant laquelle une prise revelee « pulse ». */
        const val PULSE_SPAN = 0.08f

        /** Hauteur pendant laquelle le score reste affiche sous la ligne. */
        const val LABEL_SPAN = 0.18f
    }
}
