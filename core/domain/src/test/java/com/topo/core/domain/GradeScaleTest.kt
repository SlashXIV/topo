package com.topo.core.domain

import com.topo.core.domain.model.GradeScale
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Echelles de cotation.
 *
 * L'interet de ces listes est d'etre ORDONNEES : c'est ce qui rendra les
 * statistiques de progression possibles, la ou un champ texte libre ne se trie
 * pas. Ces tests verrouillent l'ordre et la resolution d'une etiquette.
 */
class GradeScaleTest {

    @Test
    fun `french grades are ordered from easiest to hardest`() {
        val french = GradeScale.FRENCH

        assertThat(french.rankOf("6A")).isLessThan(french.rankOf("6A+"))
        assertThat(french.rankOf("6A+")).isLessThan(french.rankOf("6B"))
        assertThat(french.rankOf("6C+")).isLessThan(french.rankOf("7A"))
        assertThat(french.rankOf("7C+")).isLessThan(french.rankOf("8A"))
    }

    @Test
    fun `v grades order numerically rather than alphabetically`() {
        val v = GradeScale.V_SCALE

        // Le piege du tri alphabetique : "V10" precede "V9" en texte. L'ordre
        // vient de la liste, jamais d'une comparaison de chaines.
        assertThat(v.rankOf("V9")).isLessThan(v.rankOf("V10"))
        assertThat(v.rankOf("V2")).isLessThan(v.rankOf("V10"))
    }

    @Test
    fun `an unknown label has no rank`() {
        // Null plutot que 0 : un bloc cote dans une echelle puis relu dans
        // l'autre ne doit pas se retrouver classe le plus facile de la liste.
        assertThat(GradeScale.FRENCH.rankOf("V5")).isNull()
        assertThat(GradeScale.V_SCALE.rankOf("7A")).isNull()
        assertThat(GradeScale.FRENCH.rankOf("7a")).isNull()
    }

    @Test
    fun `a label resolves to the single scale that knows it`() {
        assertThat(GradeScale.scaleOf("7A")).isEqualTo(GradeScale.FRENCH)
        assertThat(GradeScale.scaleOf("V6")).isEqualTo(GradeScale.V_SCALE)
        assertThat(GradeScale.scaleOf("inconnu")).isNull()
    }

    @Test
    fun `no label is shared between the two scales`() {
        val shared = GradeScale.FRENCH.grades.intersect(GradeScale.V_SCALE.grades.toSet())

        // Une etiquette commune rendrait `scaleOf` ambigue et l'echelle d'un
        // bloc indeterminable a la relecture.
        assertThat(shared).isEmpty()
    }

    @Test
    fun `each scale lists every label once`() {
        GradeScale.entries.forEach { scale ->
            assertThat(scale.grades).containsNoDuplicates()
            assertThat(scale.grades).isNotEmpty()
        }
    }

    /** Les ordinaux sont persistes dans la colonne `grade_scale`. */
    @Test
    fun `persisted scale ordinals are frozen`() {
        assertThat(GradeScale.FRENCH.ordinal).isEqualTo(0)
        assertThat(GradeScale.V_SCALE.ordinal).isEqualTo(1)
    }
}
