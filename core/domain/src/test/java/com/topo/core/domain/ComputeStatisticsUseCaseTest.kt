package com.topo.core.domain

import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.BlocStatus
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.GradeScale
import com.topo.core.domain.usecase.ComputeStatisticsUseCase
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ComputeStatisticsUseCaseTest {

    private val compute = ComputeStatisticsUseCase()

    private val circuit = listOf(
        CircuitColor("jaune", "Jaune", 0xFFF2C14E.toInt(), 0),
        CircuitColor("vert", "Vert", 0xFF5FAD56.toInt(), 1),
        CircuitColor("bleu", "Bleu", 0xFF2E86AB.toInt(), 2),
    )

    @Test
    fun `an empty log yields empty statistics`() {
        val stats = compute(emptyList(), circuit)

        assertThat(stats.isEmpty).isTrue()
        assertThat(stats.attemptsPerSend).isNull()
    }

    @Test
    fun `the best grade comes from sent blocs only`() {
        val stats = compute(
            listOf(
                bloc(grade = "6A", status = BlocStatus.SENT),
                // Un 8A essaye sans succes ne fait pas de soi un grimpeur de 8A.
                bloc(grade = "8A", status = BlocStatus.WORKING),
                bloc(grade = "7A", status = BlocStatus.FLASHED),
            ),
            circuit,
        )

        assertThat(stats.bestGrade).isEqualTo("7A")
    }

    @Test
    fun `the flash rate is measured among sends, not among all blocs`() {
        val stats = compute(
            listOf(
                bloc(status = BlocStatus.FLASHED),
                bloc(status = BlocStatus.SENT),
                bloc(status = BlocStatus.PROJECT),
                bloc(status = BlocStatus.PROJECT),
            ),
            circuit,
        )

        // 1 flash sur 2 reussites. Rapporte aux 4 blocs, le taux tomberait a 25 %
        // et penaliserait le fait d'ouvrir des projets difficiles.
        assertThat(stats.flashRate).isWithin(0.001f).of(0.5f)
        assertThat(stats.successRate).isWithin(0.001f).of(0.5f)
    }

    @Test
    fun `grade buckets are ordered by difficulty, not alphabetically`() {
        val stats = compute(
            listOf(
                bloc(grade = "V10", scale = GradeScale.V_SCALE),
                bloc(grade = "V9", scale = GradeScale.V_SCALE),
                bloc(grade = "V2", scale = GradeScale.V_SCALE),
            ),
            circuit,
        )

        // Le piege du tri textuel : « V10 » precede « V9 » en alphabetique.
        assertThat(stats.gradeBuckets.map { it.label })
            .containsExactly("V2", "V9", "V10").inOrder()
    }

    @Test
    fun `only represented grades get a bucket`() {
        val stats = compute(listOf(bloc(grade = "7A"), bloc(grade = "7A")), circuit)

        // Afficher les 24 crans de l'echelle pour deux blocs noierait le signal.
        assertThat(stats.gradeBuckets).hasSize(1)
        assertThat(stats.gradeBuckets.single().total).isEqualTo(2)
    }

    @Test
    fun `untried colours are left out, in circuit order`() {
        val stats = compute(
            listOf(bloc(colorId = "bleu", status = BlocStatus.WORKING), bloc(colorId = "jaune", status = BlocStatus.SENT)),
            circuit,
        )

        assertThat(stats.colorBuckets.map { it.label }).containsExactly("Jaune", "Bleu").inOrder()
    }

    /**
     * Le jeu des captures du 2026-11-02, Villenave et Merignac : six violets dont
     * deux reussis, et un bloc sans couleur flashe. Les metriques disaient 3
     * reussis quand la seule barre disait 2/6 ; le seau « sans couleur » remet
     * les barres d'accord avec les chiffres.
     */
    @Test
    fun `colour bars add up to the headline figures, uncoloured blocs included`() {
        val violet = CircuitColor(id = "violet", name = "Violet", argb = 0xFF8E6C99.toInt(), rank = 3)
        val blocs = listOf(
            bloc(colorId = "violet", status = BlocStatus.FLASHED, attempts = 1),
            bloc(colorId = "violet", status = BlocStatus.FLASHED, attempts = 1),
            bloc(colorId = "violet", status = BlocStatus.WORKING, attempts = 4),
            bloc(colorId = "violet", status = BlocStatus.WORKING, attempts = 2),
            bloc(colorId = "violet", status = BlocStatus.PROJECT),
            bloc(colorId = "violet", status = BlocStatus.PROJECT),
            bloc(colorId = null, status = BlocStatus.FLASHED, attempts = 1),
        )

        val stats = compute(blocs, circuit + violet)

        assertThat(stats.blocCount).isEqualTo(7)
        assertThat(stats.sentCount).isEqualTo(3)
        assertThat(stats.flashCount).isEqualTo(3)
        assertThat(stats.colorBuckets.map { Triple(it.argb, it.sent, it.total) })
            .containsExactly(Triple(violet.argb, 2, 6), Triple(null, 1, 1)).inOrder()
        assertThat(stats.colorBuckets.sumOf { it.total }).isEqualTo(stats.blocCount)
        assertThat(stats.colorBuckets.sumOf { it.sent }).isEqualTo(stats.sentCount)
    }

    @Test
    fun `a colour removed from the circuit counts as uncoloured`() {
        val stats = compute(listOf(bloc(colorId = "retiree", status = BlocStatus.SENT)), circuit)

        assertThat(stats.colorBuckets.single().argb).isNull()
        assertThat(stats.colorBuckets.single().sent).isEqualTo(1)
    }

    @Test
    fun `the best colour follows the circuit order, not the insertion order`() {
        val stats = compute(
            listOf(
                bloc(colorId = "bleu", status = BlocStatus.SENT),
                bloc(colorId = "jaune", status = BlocStatus.SENT),
            ),
            circuit,
        )

        assertThat(stats.bestColor?.name).isEqualTo("Bleu")
    }

    @Test
    fun `attempts per send averages over sends`() {
        val stats = compute(
            listOf(
                bloc(status = BlocStatus.SENT, attempts = 3),
                bloc(status = BlocStatus.SENT, attempts = 5),
                bloc(status = BlocStatus.PROJECT, attempts = 2),
            ),
            circuit,
        )

        // 10 essais au total pour 2 reussites.
        assertThat(stats.attemptsPerSend).isWithin(0.01f).of(5f)
    }

    private fun bloc(
        grade: String? = null,
        scale: GradeScale = GradeScale.FRENCH,
        colorId: String? = null,
        status: BlocStatus = BlocStatus.PROJECT,
        attempts: Int = 0,
    ) = Bloc(
        id = "b${counter++}",
        sessionId = "s1",
        imagePath = "m.jpg",
        imageWidth = 300,
        imageHeight = 400,
        grade = grade,
        gradeScale = grade?.let { scale },
        colorId = colorId,
        status = status,
        attempts = attempts,
        sentAt = null,
        feeling = null,
        createdAt = 0L,
    )

    private companion object { var counter = 0 }
}
