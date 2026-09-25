package com.topo.feature.session

import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.BlocStatus
import com.topo.core.domain.model.GradeScale
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Semantique des filtres de la grille.
 *
 * Le point non evident est « Reussis » : il couvre AUSSI les flashs. Un grimpeur
 * qui filtre ses reussites ne s'attend pas a voir disparaitre celles obtenues du
 * premier coup — ce sont les meilleures.
 */
class BlocFilterTest {

    @Test
    fun `all keeps every bloc`() {
        BlocStatus.entries.forEach { status ->
            assertThat(BlocFilter.ALL.matches(bloc(status))).isTrue()
        }
    }

    @Test
    fun `sent covers both flashed and worked sends`() {
        assertThat(BlocFilter.SENT.matches(bloc(BlocStatus.FLASHED))).isTrue()
        assertThat(BlocFilter.SENT.matches(bloc(BlocStatus.SENT))).isTrue()
        assertThat(BlocFilter.SENT.matches(bloc(BlocStatus.WORKING))).isFalse()
        assertThat(BlocFilter.SENT.matches(bloc(BlocStatus.PROJECT))).isFalse()
    }

    @Test
    fun `projects and working do not overlap`() {
        assertThat(BlocFilter.PROJECTS.matches(bloc(BlocStatus.PROJECT))).isTrue()
        assertThat(BlocFilter.PROJECTS.matches(bloc(BlocStatus.WORKING))).isFalse()
        assertThat(BlocFilter.WORKING.matches(bloc(BlocStatus.WORKING))).isTrue()
        assertThat(BlocFilter.WORKING.matches(bloc(BlocStatus.PROJECT))).isFalse()
    }

    @Test
    fun `every status is reachable through some filter`() {
        val narrowFilters = BlocFilter.entries - BlocFilter.ALL
        BlocStatus.entries.forEach { status ->
            // Sans cette garantie, un bloc pourrait devenir invisible quel que soit
            // le filtre choisi, et l'utilisateur le croirait perdu.
            assertThat(narrowFilters.any { it.matches(bloc(status)) }).isTrue()
        }
    }

    private fun bloc(status: BlocStatus) = Bloc(
        id = "b1",
        sessionId = "s1",
        imagePath = "b1.jpg",
        imageWidth = 3000,
        imageHeight = 4000,
        grade = "7A",
        gradeScale = GradeScale.FRENCH,
        colorId = null,
        status = status,
        attempts = 0,
        sentAt = null,
        feeling = null,
        createdAt = 0L,
    )
}
