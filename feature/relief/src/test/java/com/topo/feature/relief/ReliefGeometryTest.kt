package com.topo.feature.relief

import com.google.common.truth.Truth.assertThat
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.HoldKind
import com.topo.core.domain.model.HoldOrigin
import com.topo.core.domain.model.Limb
import com.topo.core.domain.model.Movement
import com.topo.core.domain.model.ReliefMap
import org.junit.Test

class ReliefGeometryTest {

    /** Relief qui monte de gauche (0) a droite (1). */
    private val ramp = ReliefMap(4, 4, FloatArray(16) { (it % 4) / 3f })

    private fun hold(id: String, x: Float, y: Float, kind: HoldKind = HoldKind.HOLD) = Hold(
        id = id,
        blocId = "b",
        box = BoundingBox(x, y, 0.04f, 0.04f),
        confidence = 1f,
        origin = HoldOrigin.MANUAL,
        kind = kind,
    )

    private fun build(
        holds: List<Hold> = emptyList(),
        movements: List<Movement> = emptyList(),
        zone: BoundingBox? = null,
        relief: ReliefMap = ramp,
    ) = ReliefGeometry.build(
        relief = relief,
        aspect = 1.5f,
        zone = zone,
        holds = holds,
        movements = movements,
        holdArgb = HOLD,
        volumeArgb = VOLUME,
        palette = { limb -> if (limb == Limb.LEFT_HAND) LEFT else RIGHT },
    )

    @Test
    fun the_wall_is_centered_with_y_up_and_relief_toward_the_camera() {
        val topLeft = ReliefGeometry.surface(ramp, 1.5f, 0f, 0f)
        val bottomRight = ReliefGeometry.surface(ramp, 1.5f, 1f, 1f)

        assertThat(topLeft.x).isWithin(1e-6f).of(-0.5f)
        assertThat(topLeft.y).isWithin(1e-6f).of(0.75f)
        assertThat(topLeft.z).isWithin(1e-6f).of(0f)
        assertThat(bottomRight.y).isWithin(1e-6f).of(-0.75f)
        assertThat(bottomRight.z).isWithin(1e-6f).of(ReliefGeometry.DEPTH_SCALE)
    }

    @Test
    fun every_mesh_index_points_to_an_existing_vertex() {
        val scene = build(relief = ReliefMap(500, 400, FloatArray(200_000)))
        val vertexCount = scene.meshVertices.size / ReliefGeometry.MESH_STRIDE

        assertThat(scene.meshIndices.max()).isLessThan(vertexCount)
        // Un relief de 500 de large est sous-echantillonne : le maillage reste borne.
        assertThat(vertexCount).isAtMost(ReliefGeometry.MAX_MESH_SIDE * ReliefGeometry.MAX_MESH_SIDE)
    }

    @Test
    fun a_flat_wall_has_normals_facing_the_camera() {
        val scene = build(relief = ReliefMap(8, 8, FloatArray(64) { 0.5f }))
        for (o in scene.meshVertices.indices step ReliefGeometry.MESH_STRIDE) {
            assertThat(scene.meshVertices[o + 7]).isWithin(1e-5f).of(1f)
        }
    }

    @Test
    fun a_rising_ramp_tilts_normals_away_from_the_high_side() {
        val scene = build()
        // Le relief monte vers la droite : la normale penche vers la gauche.
        assertThat(scene.meshVertices[5]).isLessThan(0f)
    }

    @Test
    fun holds_outside_the_zone_are_left_out_and_inside_ones_are_mapped_to_it() {
        val zone = BoundingBox(centerX = 0.75f, centerY = 0.5f, width = 0.5f, height = 1f)
        val scene = build(holds = listOf(hold("in", 0.75f, 0.5f), hold("out", 0.2f, 0.5f)), zone = zone)

        assertThat(scene.holdCount).isEqualTo(1)
        // Centre de la zone -> centre du mur, x = 0.
        assertThat(scene.halos.vertices[0]).isWithin(0.1f).of(0f)
    }

    @Test
    fun volumes_keep_their_own_color() {
        val scene = build(holds = listOf(hold("v", 0.5f, 0.5f, HoldKind.VOLUME)))
        val red = scene.halos.vertices[8]
        val blue = scene.halos.vertices[10]

        assertThat(red).isWithin(1e-3f).of(1f)
        assertThat(blue).isWithin(1e-3f).of(1f)
    }

    @Test
    fun an_arc_rises_above_the_wall_between_its_holds_and_progresses_in_sequence() {
        val holds = listOf(hold("a", 0.2f, 0.8f), hold("b", 0.8f, 0.2f), hold("c", 0.5f, 0.5f))
        val movements = listOf(
            Movement("m1", "b", 0, Limb.LEFT_HAND, sourceHoldId = null, targetHoldId = "a"),
            Movement("m2", "b", 1, Limb.RIGHT_HAND, sourceHoldId = "a", targetHoldId = "b"),
            Movement("m3", "b", 2, Limb.LEFT_HAND, sourceHoldId = "b", targetHoldId = "c"),
        )
        val scene = build(holds, movements)
        val stride = ReliefGeometry.RIBBON_STRIDE
        val vertices = scene.arcs.vertices
        val count = vertices.size / stride

        // Le depart depuis le sol n'a pas d'arc ; il garde pourtant sa place dans le temps.
        assertThat(scene.movementCount).isEqualTo(3)
        val progress = (0 until count).map { vertices[it * stride + 7] }
        assertThat(progress.min()).isWithin(1e-5f).of(1f / 3)
        assertThat(progress.max()).isWithin(1e-5f).of(1f)

        val start = ReliefGeometry.surface(ramp, 1.5f, 0.2f, 0.8f)
        assertThat(vertices[0]).isWithin(1e-5f).of(start.x)
        val peak = (0 until count).maxOf { vertices[it * stride + 2] }
        assertThat(peak).isGreaterThan(ReliefGeometry.DEPTH_SCALE)

        // Chaque arc prend la couleur de son membre.
        assertThat(vertices[8]).isWithin(1e-3f).of(0f)
        assertThat(vertices[10]).isWithin(1e-3f).of(1f)
    }

    private companion object {
        const val HOLD = 0xFF00FF00.toInt()
        const val VOLUME = 0xFFFFFFFF.toInt()
        const val LEFT = 0xFFFF0000.toInt()
        const val RIGHT = 0xFF0000FF.toInt()
    }
}
