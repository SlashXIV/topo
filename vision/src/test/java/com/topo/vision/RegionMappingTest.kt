package com.topo.vision

import com.google.common.truth.Truth.assertThat
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.DetectedHold
import com.topo.core.domain.model.HoldOrigin
import org.junit.Test

/**
 * La correspondance zone affichee -> zone brute est verifiee contre une rotation
 * SIMULEE, et non contre les formules inverses qu'elle implemente : une
 * inversion fausse (90 et 270 permutes, le piege classique) ferait passer un test
 * ecrit avec les memes formules.
 */
class RegionMappingTest {

    /**
     * Ou s'affiche un point brut apres la rotation horaire EXIF — c'est ce que
     * fait Coil a l'ecran. Rotation d'un quart de tour horaire : (x, y) -> (1 - y, x).
     */
    private fun displayed(x: Float, y: Float, rotation: Int): Pair<Float, Float> {
        var point = x to y
        repeat(rotation / 90) { point = (1f - point.second) to point.first }
        return point
    }

    @Test
    fun every_raw_corner_of_the_mapped_zone_lands_on_the_drawn_zone() {
        val drawn = NormalizedRect(left = 0.1f, top = 0.2f, right = 0.4f, bottom = 0.9f)

        for (rotation in listOf(0, 90, 180, 270)) {
            val raw = drawn.toRaw(rotation)
            val corners = listOf(
                raw.left to raw.top, raw.right to raw.top,
                raw.left to raw.bottom, raw.right to raw.bottom,
            ).map { (x, y) -> displayed(x, y, rotation) }

            // Les quatre coins bruts, une fois affiches, sont exactement les quatre
            // coins de la zone tracee.
            val xs = corners.map { it.first }
            val ys = corners.map { it.second }
            assertThat(xs.min()).isWithin(1e-6f).of(drawn.left)
            assertThat(xs.max()).isWithin(1e-6f).of(drawn.right)
            assertThat(ys.min()).isWithin(1e-6f).of(drawn.top)
            assertThat(ys.max()).isWithin(1e-6f).of(drawn.bottom)
        }
    }

    @Test
    fun a_detection_in_the_zone_is_placed_back_in_the_whole_photo() {
        val zone = NormalizedRect(left = 0.5f, top = 0.25f, right = 1f, bottom = 0.75f)
        val inZone = DetectedHold(BoundingBox(0.5f, 0.5f, 0.2f, 0.4f), 0.9f, HoldOrigin.MODEL)

        val box = inZone.fromRegion(zone).box

        // Centre de la zone -> centre de la zone dans la photo ; tailles a l'echelle.
        assertThat(box.centerX).isWithin(1e-6f).of(0.75f)
        assertThat(box.centerY).isWithin(1e-6f).of(0.5f)
        assertThat(box.width).isWithin(1e-6f).of(0.1f)
        assertThat(box.height).isWithin(1e-6f).of(0.2f)
    }

    @Test
    fun a_zone_drawn_past_the_edge_is_clipped_to_the_photo() {
        val rect = NormalizedRect.of(BoundingBox(0.95f, 0.05f, 0.3f, 0.3f))

        assertThat(rect.left).isWithin(1e-6f).of(0.8f)
        assertThat(rect.top).isEqualTo(0f)
        assertThat(rect.right).isEqualTo(1f)
        assertThat(rect.bottom).isWithin(1e-6f).of(0.2f)
    }
}
