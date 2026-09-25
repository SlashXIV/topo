package com.topo.feature.annotator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.floor

/**
 * Instantane du rejeu a un instant donne.
 *
 * [completedMovements] sont traces entierement ; [activeIndex] est le mouvement
 * en cours de dessin, avance de [activeProgress] entre sa prise de depart et sa
 * prise d'arrivee. [activeIndex] vaut -1 quand il n'y a rien a interpoler, soit
 * au tout debut, soit une fois la sequence terminee.
 */
data class PlaybackFrame(
    val completedMovements: Int,
    val activeIndex: Int,
    val activeProgress: Float,
)

/**
 * Convertit une progression continue en instantane de rejeu.
 *
 * La progression est exprimee en NOMBRE DE MOUVEMENTS, pas en fraction de la
 * sequence : `2.4` signifie « deux mouvements traces, le troisieme avance de
 * 40 % ». Une fraction de 0 a 1 aurait oblige a remultiplier par le nombre de
 * mouvements partout, et aurait rendu le pas-a-pas dependant de la longueur de
 * la sequence.
 *
 * Fonction pure, sans dependance a Compose : c'est le coeur du rejeu, et il se
 * teste sur JVM en quelques millisecondes.
 */
fun playbackFrameAt(progress: Float, movementCount: Int): PlaybackFrame {
    if (movementCount <= 0) return PlaybackFrame(0, -1, 0f)

    val clamped = progress.coerceIn(0f, movementCount.toFloat())
    val completed = floor(clamped).toInt().coerceAtMost(movementCount)
    val fraction = clamped - completed

    // Une progression pile sur un entier — y compris la fin de sequence — n'a
    // aucun mouvement en cours : tout ce qui precede est deja trace en entier.
    val active = if (completed >= movementCount || fraction <= 0f) -1 else completed

    return PlaybackFrame(
        completedMovements = completed,
        activeIndex = active,
        activeProgress = fraction,
    )
}

/** Vitesses de lecture proposees. */
enum class PlaybackSpeed(val factor: Float, val label: String) {
    HALF(0.5f, "0,5x"),
    NORMAL(1f, "1x"),
    DOUBLE(2f, "2x"),
    ;

    fun next(): PlaybackSpeed = entries[(ordinal + 1) % entries.size]
}

/**
 * Etat du rejeu.
 *
 * POINT DE PERFORMANCE — [progress] est un [Animatable], et sa valeur n'est lue
 * QUE dans la lambda de dessin du canevas. Une animation emet une valeur par
 * trame : la lire dans un composable declencherait 60 a 120 recompositions par
 * seconde de tout le sous-arbre. C'est la meme regle que pour le trace au doigt
 * (voir [BetaTraceState]), appliquee ici a une source d'emissions encore plus
 * reguliere.
 *
 * [isPlaying] et [speed], eux, sont lus en composition : ils ne changent qu'a
 * l'appui sur un bouton, et pilotent l'apparence des controles.
 */
@Stable
class BetaPlaybackState(movementCount: Int) {

    var movementCount by mutableStateOf(movementCount)
        internal set

    val progress = Animatable(0f)

    var isPlaying by mutableStateOf(false)
        internal set

    var speed by mutableStateOf(PlaybackSpeed.NORMAL)
        internal set

    /** Progression au moment du dernier arret, pour reprendre au bon endroit. */
    private var pausedAt by mutableFloatStateOf(0f)

    val isFinished: Boolean get() = movementCount > 0 && pausedAt >= movementCount

    /**
     * Joue la sequence depuis la position courante.
     *
     * Repartir de zero si la lecture precedente etait terminee : appuyer sur
     * « lecture » a la fin d'un rejeu doit le rejouer, pas ne rien faire.
     */
    suspend fun play() {
        if (movementCount <= 0) return

        val from = if (progress.value >= movementCount) 0f else progress.value
        isPlaying = true
        try {
            progress.snapTo(from)
            progress.animateTo(
                targetValue = movementCount.toFloat(),
                animationSpec = tween(
                    durationMillis = durationFor(movementCount - from),
                    // Lineaire : un beta se lit a cadence constante. Une courbe
                    // d'acceleration donnerait l'impression que le grimpeur
                    // ralentit en fin de sequence.
                    easing = LinearEasing,
                ),
            )
        } finally {
            pausedAt = progress.value
            isPlaying = false
        }
    }

    /** Arrete la lecture en place. La coroutine de [play] est annulee par l'appelant. */
    fun pause() {
        pausedAt = progress.value
        isPlaying = false
    }

    suspend fun reset() {
        progress.snapTo(0f)
        pausedAt = 0f
        isPlaying = false
    }

    /** Avance ou recule d'un mouvement entier, en s'alignant sur la grille. */
    suspend fun step(delta: Int) {
        if (movementCount <= 0) return
        val target = (floor(progress.value).toInt() + delta).coerceIn(0, movementCount)
        progress.snapTo(target.toFloat())
        pausedAt = target.toFloat()
        isPlaying = false
    }

    fun cycleSpeed() {
        speed = speed.next()
    }

    private fun durationFor(movements: Float): Int =
        ((movements * MILLIS_PER_MOVEMENT) / speed.factor).toInt().coerceAtLeast(1)

    private companion object {
        /** Duree d'un mouvement a vitesse normale : assez lent pour etre suivi a l'oeil. */
        const val MILLIS_PER_MOVEMENT = 700f
    }
}
