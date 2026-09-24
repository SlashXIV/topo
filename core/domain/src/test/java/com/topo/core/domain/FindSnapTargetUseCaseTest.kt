package com.topo.core.domain

import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.HoldOrigin
import com.topo.core.domain.model.Viewport
import com.topo.core.domain.usecase.FindSnapTargetUseCase
import com.google.common.truth.Truth.assertThat
import kotlin.system.measureNanoTime
import org.junit.Test

class FindSnapTargetUseCaseTest {

    private val findSnapTarget = FindSnapTargetUseCase()

    /** 1000x1000 : un pixel vaut 0,001 en coordonnees normalisees. */
    private val squareViewport = Viewport(widthPx = 1000f, heightPx = 1000f)

    @Test
    fun `returns the nearest hold within the threshold`() {
        val near = hold(id = "proche", x = 0.52f, y = 0.5f)
        val far = hold(id = "loin", x = 0.9f, y = 0.9f)

        val target = findSnapTarget(
            pointerX = 0.5f,
            pointerY = 0.5f,
            holds = listOf(far, near),
            viewport = squareViewport,
            thresholdPx = 40f,
        )

        assertThat(target?.hold?.id).isEqualTo("proche")
        assertThat(target?.distancePx).isWithin(0.01f).of(20f)
    }

    @Test
    fun `returns null when every hold is beyond the threshold`() {
        val target = findSnapTarget(
            pointerX = 0.5f,
            pointerY = 0.5f,
            holds = listOf(hold(id = "loin", x = 0.6f, y = 0.5f)),
            viewport = squareViewport,
            thresholdPx = 40f,
        )

        // 0,1 normalise = 100 px : bien au-dela des 40 px d'attraction.
        assertThat(target).isNull()
    }

    /**
     * Le seuil est teste avec une marge de part et d'autre, jamais a l'egalite
     * exacte : `0.54f - 0.5f` vaut 0,040000021 et non 0,04. Un test pose sur la
     * frontiere flottante exacte serait un faux positif en attente.
     */
    @Test
    fun `the threshold separates just inside from just outside`() {
        val inside = findSnapTarget(
            pointerX = 0.5f,
            pointerY = 0.5f,
            holds = listOf(hold(id = "dedans", x = 0.539f, y = 0.5f)), // ~39 px
            viewport = squareViewport,
            thresholdPx = 40f,
        )
        val outside = findSnapTarget(
            pointerX = 0.5f,
            pointerY = 0.5f,
            holds = listOf(hold(id = "dehors", x = 0.541f, y = 0.5f)), // ~41 px
            viewport = squareViewport,
            thresholdPx = 40f,
        )

        assertThat(inside?.hold?.id).isEqualTo("dedans")
        assertThat(outside).isNull()
    }

    /**
     * Le coeur de la correction geometrique : sur une image non carree, un meme
     * ecart normalise ne represente pas la meme distance a l'ecran. Un calcul mene
     * en coordonnees normalisees donnerait ici la MAUVAISE prise.
     */
    @Test
    fun `distance is measured in pixels, not in normalized space`() {
        // Viewport 400x1200 : un ecart de 0,05 vaut 20 px en X, mais 60 px en Y.
        val viewport = Viewport(widthPx = 400f, heightPx = 1200f)
        val horizontal = hold(id = "horizontal", x = 0.55f, y = 0.5f)
        val vertical = hold(id = "vertical", x = 0.5f, y = 0.55f)

        val target = findSnapTarget(
            pointerX = 0.5f,
            pointerY = 0.5f,
            holds = listOf(vertical, horizontal),
            viewport = viewport,
            thresholdPx = 40f,
        )

        // En normalise, les deux prises sont a egale distance (0,05) et la premiere
        // trouvee gagnerait. En pixels, seule l'horizontale est reellement proche.
        assertThat(target?.hold?.id).isEqualTo("horizontal")
        assertThat(target?.distancePx).isWithin(0.01f).of(20f)
    }

    @Test
    fun `an empty wall never snaps`() {
        val target = findSnapTarget(0.5f, 0.5f, emptyList(), squareViewport, 40f)

        assertThat(target).isNull()
    }

    @Test
    fun `a non positive threshold disables snapping`() {
        val target = findSnapTarget(
            pointerX = 0.5f,
            pointerY = 0.5f,
            holds = listOf(hold(id = "collee", x = 0.5f, y = 0.5f)),
            viewport = squareViewport,
            thresholdPx = 0f,
        )

        assertThat(target).isNull()
    }

    /**
     * Justification chiffree du choix O(N) plutot qu'un KD-Tree.
     *
     * Le budget d'une trame a 60 FPS est de 16,6 ms, et la magnetisation n'est
     * qu'une fraction du travail d'une trame. Le seuil retenu ici (0,2 ms pour 150
     * prises) laisse deux ordres de grandeur de marge : s'il etait franchi, la
     * conclusion "un arbre spatial serait de la sur-ingenierie" deviendrait fausse
     * et ce test le signalerait.
     *
     * Volontairement tres large pour ne pas devenir instable sur une machine de CI
     * chargee : on mesure un ordre de grandeur, pas une performance fine.
     */
    @Test
    fun `snapping over a full wall stays far below one frame`() {
        val wall = List(150) { index ->
            hold(id = "prise-$index", x = (index % 12) / 12f + 0.04f, y = (index / 12) / 13f + 0.04f)
        }

        // Rodage : on ne mesure pas l'interpretation des premiers appels.
        repeat(1_000) { findSnapTarget(0.5f, 0.5f, wall, squareViewport, 40f) }

        val iterations = 10_000
        val elapsedNanos = measureNanoTime {
            repeat(iterations) { findSnapTarget(0.5f, 0.5f, wall, squareViewport, 40f) }
        }
        val perCallMicros = elapsedNanos / iterations / 1_000.0

        println("Magnetisation sur 150 prises : %.2f us/appel".format(perCallMicros))
        assertThat(perCallMicros).isLessThan(200.0)
    }

    private fun hold(id: String, x: Float, y: Float) = Hold(
        id = id,
        blocId = "bloc-1",
        box = BoundingBox(centerX = x, centerY = y, width = 0.08f, height = 0.08f),
        confidence = 1f,
        origin = HoldOrigin.MANUAL,
    )
}
