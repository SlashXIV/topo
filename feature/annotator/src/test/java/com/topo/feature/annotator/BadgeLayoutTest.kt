package com.topo.feature.annotator

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Placement des numeros du beta : aucun ne doit en cacher un autre ni sortir de
 * la photo. C'est ce que montrait la capture du beta de 15 mouvements, ou six
 * numeros etaient invisibles.
 */
class BadgeLayoutTest {

    private val radius = 27f
    private val bounds = Size(1080f, 1440f)

    @Test
    fun `two moves to the same hold get two separate numbers`() {
        val hold = Offset(500f, 700f)

        val badges = layoutBadges(listOf(hold, hold), radius, bounds).filterNotNull()

        assertNoOverlap(badges)
    }

    @Test
    fun `a hold in the top right corner keeps its number inside the photo`() {
        val badges = layoutBadges(listOf(Offset(1075f, 5f)), radius, bounds).filterNotNull()

        assertInside(badges)
    }

    @Test
    fun `fifteen moves on three neighbouring holds give fifteen visible numbers`() {
        val holds = listOf(Offset(300f, 400f), Offset(340f, 430f), Offset(1060f, 20f))
        val targets = List(15) { holds[it % holds.size] }

        val badges = layoutBadges(targets, radius, bounds).filterNotNull()

        assertThat(badges).hasSize(15)
        assertNoOverlap(badges)
        assertInside(badges)
    }

    @Test
    fun `the first number keeps its usual place and a missing hold has none`() {
        val hold = Offset(500f, 700f)

        val badges = layoutBadges(listOf(hold, null), radius, bounds)

        val usual = radius * MARKER_OFFSET
        assertThat(badges[0]!!.x).isWithin(0.01f).of(hold.x + usual)
        assertThat(badges[0]!!.y).isWithin(0.01f).of(hold.y - usual)
        assertThat(badges[1]).isNull()
    }

    private fun assertNoOverlap(badges: List<Offset>) {
        for (i in badges.indices) {
            for (j in i + 1 until badges.size) {
                assertThat((badges[i] - badges[j]).getDistance()).isAtLeast(2f * radius)
            }
        }
    }

    private fun assertInside(badges: List<Offset>) {
        badges.forEach {
            assertThat(it.x).isIn(com.google.common.collect.Range.closed(radius, bounds.width - radius))
            assertThat(it.y).isIn(com.google.common.collect.Range.closed(radius, bounds.height - radius))
        }
    }
}
