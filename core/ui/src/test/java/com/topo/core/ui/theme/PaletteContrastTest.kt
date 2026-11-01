package com.topo.core.ui.theme

import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Garde-fou de lisibilite, applique a CHAQUE palette : une palette modifiee qui
 * ne tient pas ces seuils fait echouer le build. On grimpe en salle sombre comme
 * au soleil de Fontainebleau.
 *
 * Seuils WCAG 2.2 : 7:1 (AAA) pour le texte courant, 4,5:1 (AA) pour le texte
 * secondaire, l'accent et les tampons de statut.
 */
class PaletteContrastTest {

    @Test
    fun `le texte courant atteint 7 pour 1 sur les deux papiers`() = TopoPalettes.all.forEach { (name, c) ->
        assertContrast(name, "ink/paper", c.ink, c.paper, 7.0)
        assertContrast(name, "ink/paper2", c.ink, c.paper2, 7.0)
        assertContrast(name, "onInk/ink", c.onInk, c.ink, 7.0)
    }

    @Test
    fun `le texte secondaire, l'accent et les tampons atteignent 4,5 pour 1`() = TopoPalettes.all.forEach { (name, c) ->
        listOf(
            "ink2" to c.ink2, "accent" to c.accent, "error" to c.error,
            "statusProject" to c.statusProject, "statusWorking" to c.statusWorking,
            "statusFlashed" to c.statusFlashed, "statusSent" to c.statusSent,
        ).forEach { (role, color) ->
            assertContrast(name, "$role/paper", color, c.paper, 4.5)
            assertContrast(name, "$role/paper2", color, c.paper2, 4.5)
        }
    }

    /**
     * Les deux mains du beta doivent rester distinctes pour les daltoniens :
     * protanopie et deuteranopie simulees (Machado 2009, severite 1), ecart OKLab
     * x 100 d'au moins 8. Meme exigence pour « flash » et « reussi », que seule
     * la teinte separe sur le tampon.
     */
    @Test
    fun `les couleurs de sens restent distinctes pour les daltoniens`() {
        val pairs = listOf(
            "mains" to (BetaColors.LeftHand to BetaColors.RightHand),
        ) + TopoPalettes.all.map { (name, c) -> "$name flash/reussi" to (c.statusFlashed to c.statusSent) }
        pairs.forEach { (label, pair) ->
            listOf("protan" to PROTAN, "deutan" to DEUTAN).forEach { (kind, matrix) ->
                val delta = deltaE(simulate(pair.first, matrix), simulate(pair.second, matrix))
                assertWithMessage("$label en $kind, ΔE = %.1f, minimum 8".format(delta)).that(delta).isAtLeast(8.0)
            }
        }
    }

    private fun assertContrast(palette: String, pair: String, fg: Color, bg: Color, min: Double) {
        val ratio = contrast(fg, bg)
        assertWithMessage("$palette : $pair = %.2f:1, minimum $min:1".format(ratio)).that(ratio).isAtLeast(min)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun simulate(c: Color, m: Array<DoubleArray>): DoubleArray {
        val rgb = doubleArrayOf(srgbToLinear(c.red), srgbToLinear(c.green), srgbToLinear(c.blue))
        return DoubleArray(3) { i -> (m[i][0] * rgb[0] + m[i][1] * rgb[1] + m[i][2] * rgb[2]).coerceIn(0.0, 1.0) }
    }

    private fun deltaE(a: DoubleArray, b: DoubleArray): Double {
        val (la, lb) = oklab(a) to oklab(b)
        return 100 * sqrt((0..2).sumOf { (la[it] - lb[it]).pow(2) })
    }

    private fun oklab(rgb: DoubleArray): DoubleArray {
        val (r, g, b) = Triple(rgb[0], rgb[1], rgb[2])
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }

    private fun srgbToLinear(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)

    /** Luminance relative sRGB, definition WCAG. */
    private fun luminance(c: Color): Double = 0.2126 * srgbToLinear(c.red) + 0.7152 * srgbToLinear(c.green) + 0.0722 * srgbToLinear(c.blue)

    private companion object {
        val PROTAN = arrayOf(
            doubleArrayOf(0.152286, 1.052583, -0.204868),
            doubleArrayOf(0.114503, 0.786281, 0.099216),
            doubleArrayOf(-0.003882, -0.048116, 1.051998),
        )
        val DEUTAN = arrayOf(
            doubleArrayOf(0.367322, 0.860646, -0.227968),
            doubleArrayOf(0.280085, 0.672501, 0.047413),
            doubleArrayOf(-0.011820, 0.042940, 0.968881),
        )
    }
}
