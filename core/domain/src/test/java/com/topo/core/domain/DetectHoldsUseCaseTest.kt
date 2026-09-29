package com.topo.core.domain

import com.google.common.truth.Truth.assertThat
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.DetectedHold
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.HoldOrigin
import com.topo.core.domain.model.Movement
import com.topo.core.domain.repository.BetaRepository
import com.topo.core.domain.repository.HoldDetector
import com.topo.core.domain.usecase.DetectHoldsUseCase
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DetectHoldsUseCaseTest {

    private val photo = File("bloc-1.jpg")

    @Test
    fun without_a_model_nothing_is_written_and_the_caller_is_told() = runTest {
        val beta = RecordingBetaRepository()
        val useCase = DetectHoldsUseCase(FakeDetector(ready = false), beta)

        val result = useCase("bloc-1", photo, existing = emptyList())

        assertThat(result).isEqualTo(DetectHoldsUseCase.Result.Unavailable)
        assertThat(beta.batches).isEmpty()
    }

    @Test
    fun the_image_reaches_the_detector_as_the_bloc_photo() = runTest {
        val detector = FakeDetector(ready = true)

        DetectHoldsUseCase(detector, RecordingBetaRepository())("bloc-1", photo, emptyList())

        assertThat(detector.received).containsExactly(HoldDetector.ImageFile(photo))
    }

    @Test
    fun only_confident_detections_are_proposed() = runTest {
        val beta = RecordingBetaRepository()
        val detector = FakeDetector(
            ready = true,
            detections = listOf(detection(0.2f, 0.2f, 0.90f), detection(0.6f, 0.6f, 0.30f)),
        )

        val result = DetectHoldsUseCase(detector, beta)("bloc-1", photo, emptyList())

        // 0,30 passe le seuil du moteur (0,25) mais pas celui de la proposition.
        assertThat(result).isEqualTo(DetectHoldsUseCase.Result.Added(added = 1, alreadyPlaced = 0))
        assertThat(beta.batches.single().map { it.confidence }).containsExactly(0.90f)
    }

    @Test
    fun a_detection_landing_on_a_placed_hold_is_not_duplicated() = runTest {
        val beta = RecordingBetaRepository()
        // Prise posee au doigt : petite boite fixe, centree la ou le doigt a appuye.
        val manual = hold(0.30f, 0.40f, size = 0.08f)
        val detector = FakeDetector(
            ready = true,
            // La vraie prise est plus grande et son centre legerement decale :
            // leur IoU est faible, c'est pourtant la meme prise.
            detections = listOf(
                detection(0.32f, 0.41f, 0.95f, size = 0.20f),
                detection(0.80f, 0.80f, 0.90f),
            ),
        )

        val result = DetectHoldsUseCase(detector, beta)("bloc-1", photo, listOf(manual))

        assertThat(result).isEqualTo(DetectHoldsUseCase.Result.Added(added = 1, alreadyPlaced = 1))
        assertThat(beta.batches.single().single().box.centerX).isEqualTo(0.80f)
    }

    @Test
    fun running_the_detection_twice_adds_nothing_the_second_time() = runTest {
        val beta = RecordingBetaRepository()
        val detector = FakeDetector(
            ready = true,
            detections = listOf(detection(0.2f, 0.2f, 0.9f), detection(0.7f, 0.7f, 0.8f)),
        )
        val useCase = DetectHoldsUseCase(detector, beta)

        useCase("bloc-1", photo, emptyList())
        val second = useCase("bloc-1", photo, existing = beta.persisted)

        assertThat(second).isEqualTo(DetectHoldsUseCase.Result.Added(added = 0, alreadyPlaced = 2))
        assertThat(beta.batches).hasSize(1)
    }

    @Test
    fun all_proposals_are_written_in_a_single_batch() = runTest {
        val beta = RecordingBetaRepository()
        val detector = FakeDetector(
            ready = true,
            detections = (1..9).map { detection(it / 10f, 0.5f, 0.9f, size = 0.05f) },
        )

        DetectHoldsUseCase(detector, beta)("bloc-1", photo, emptyList())

        // Une ecriture par prise ferait reemettre le Flow du bloc neuf fois.
        assertThat(beta.batches).hasSize(1)
        assertThat(beta.batches.single()).hasSize(9)
        assertThat(beta.batches.single().map { it.origin }.distinct()).containsExactly(HoldOrigin.MODEL)
    }

    private fun detection(x: Float, y: Float, score: Float, size: Float = 0.1f) =
        DetectedHold(BoundingBox(x, y, size, size), score, HoldOrigin.MODEL)

    private fun hold(x: Float, y: Float, size: Float) =
        Hold("manual", "bloc-1", BoundingBox(x, y, size, size), 1f, HoldOrigin.MANUAL)

    private class FakeDetector(
        private val ready: Boolean,
        private val detections: List<DetectedHold> = emptyList(),
    ) : HoldDetector {
        val received = mutableListOf<HoldDetector.ImageSource>()
        override val isReady: Boolean get() = ready
        override suspend fun detect(image: HoldDetector.ImageSource): List<DetectedHold> {
            received += image
            return if (ready) detections else emptyList()
        }
        override fun close() = Unit
    }

    /** N'implemente que l'ajout groupe : le cas d'usage ne doit rien toucher d'autre. */
    private class RecordingBetaRepository : BetaRepository {
        val batches = mutableListOf<List<DetectedHold>>()
        val persisted = mutableListOf<Hold>()

        override suspend fun addHolds(blocId: String, holds: List<DetectedHold>): List<Hold> {
            batches += holds
            return holds.mapIndexed { i, d ->
                Hold("h${persisted.size + i}", blocId, d.box, d.confidence, d.origin)
            }.also { persisted += it }
        }

        override suspend fun replaceHolds(blocId: String, holds: List<DetectedHold>) = unexpected()
        override suspend fun addHold(blocId: String, hold: DetectedHold) = unexpected()
        override suspend fun updateHold(hold: Hold) = unexpected()
        override suspend fun deleteHold(holdId: String) = unexpected()
        override suspend fun replaceMovements(blocId: String, movements: List<Movement>) = unexpected()
        override suspend fun deleteMovement(movementId: String) = unexpected()

        private fun unexpected(): Nothing = error("Le cas d'usage ne doit qu'ajouter des prises")
    }
}
