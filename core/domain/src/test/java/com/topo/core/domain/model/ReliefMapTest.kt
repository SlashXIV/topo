package com.topo.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ReliefMapTest {

    // 3 x 2, ligne par ligne : le haut a gauche vaut 0, le bas a droite 1.
    private val relief = ReliefMap(3, 2, floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f))

    @Test
    fun cell_centers_return_their_own_value() {
        assertThat(relief.sample(0.5f / 3, 0.25f)).isWithin(1e-6f).of(0f)
        assertThat(relief.sample(2.5f / 3, 0.75f)).isWithin(1e-6f).of(1f)
        assertThat(relief.sample(1.5f / 3, 0.25f)).isWithin(1e-6f).of(0.2f)
    }

    @Test
    fun between_rows_the_value_is_interpolated() {
        assertThat(relief.sample(0.5f / 3, 0.5f)).isWithin(1e-6f).of(0.3f)
    }

    @Test
    fun outside_the_grid_the_edge_is_repeated() {
        assertThat(relief.sample(-1f, -1f)).isWithin(1e-6f).of(0f)
        assertThat(relief.sample(2f, 2f)).isWithin(1e-6f).of(1f)
    }

    @Test
    fun a_grid_that_does_not_match_its_size_is_refused() {
        assertThrows(IllegalArgumentException::class.java) { ReliefMap(3, 2, FloatArray(5)) }
    }
}
