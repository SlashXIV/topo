package com.topo.core.domain

import com.google.common.truth.Truth.assertThat
import com.topo.core.domain.model.BlocDetail
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.DetectedHold
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.HoldColor
import com.topo.core.domain.model.HoldKind
import com.topo.core.domain.model.HoldOrigin
import com.topo.core.domain.model.Limb
import com.topo.core.domain.model.Movement
import com.topo.core.domain.repository.BetaRepository
import com.topo.core.domain.usecase.KeepHoldColorUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Couleurs CIELAB. Violet, gris, jaune et rouge sont MESUREES sur une photo de
 * salle reelle (Climb Up), par le miroir Python de la mesure native ; blanc et
 * rose, absents de cette photo, sont choisis a la main.
 */
class KeepHoldColorUseCaseTest {

    // Mesurees.
    private val purple = HoldColor(29f, 7.6f, -2.5f)
    private val purpleLit = HoldColor(52f, 7.0f, -0.7f)
    private val purpleOther = HoldColor(30f, 8.0f, -4.1f)
    private val downclimbGrey = HoldColor(25f, 1.1f, 2.8f)
    private val greyVolume = HoldColor(45f, 0.1f, 4.0f)
    private val yellow = HoldColor(42f, 7.6f, 43.3f)
    private val red = HoldColor(38f, 37.7f, 24.5f)
    private val redLit = HoldColor(54f, 24.7f, 11.0f)

    // Choisies a la main.
    private val white = HoldColor(90f, 0f, 3f)
    private val whiteInShade = HoldColor(78f, 1f, 4f)
    private val lightGrey = HoldColor(62f, 0f, 2f)
    private val pink = HoldColor(80f, 25f, 2f)

    @Test
    fun white_holds_are_kept_even_in_shade_and_light_grey_ones_go() = runTest {
        val beta = DeletingBetaRepository()
        val detail = detail(
            hold("ref", white),
            hold("shade", whiteInShade),
            hold("grey", lightGrey), // prise de desescalade
            hold("yellow", yellow),
        )

        val result = KeepHoldColorUseCase(beta)(detail, referenceHoldId = "ref")

        assertThat(beta.deleted).containsExactly("grey", "yellow")
        assertThat(result).isEqualTo(KeepHoldColorUseCase.Result.Filtered(kept = 2, removed = 2))
    }

    @Test
    fun desaturated_purple_holds_are_kept_together_and_grey_ones_go() = runTest {
        val beta = DeletingBetaRepository()
        val detail = detail(
            hold("ref", purple),
            hold("lit", purpleLit),
            hold("other", purpleOther),
            hold("grey", downclimbGrey),
            hold("yellow", yellow),
            hold("red", red),
        )

        KeepHoldColorUseCase(beta)(detail, "ref")

        // Le violet de salle n'est sature qu'a 7-9 : il reste distinct du gris (3-4).
        assertThat(beta.deleted).containsExactly("grey", "yellow", "red")
    }

    @Test
    fun a_grey_volume_stays_but_a_grey_downclimb_hold_goes() = runTest {
        val beta = DeletingBetaRepository()
        val detail = detail(
            hold("ref", yellow),
            hold("volume", greyVolume, kind = HoldKind.VOLUME),
            hold("downclimb", downclimbGrey),
        )

        KeepHoldColorUseCase(beta)(detail, "ref")

        assertThat(beta.deleted).containsExactly("downclimb")
    }

    @Test
    fun a_red_hold_stays_red_under_a_spotlight() = runTest {
        val beta = DeletingBetaRepository()

        KeepHoldColorUseCase(beta)(detail(hold("ref", red), hold("lit", redLit), hold("y", yellow)), "ref")

        assertThat(beta.deleted).containsExactly("y")
    }

    @Test
    fun a_pink_hold_is_not_mistaken_for_a_red_one() = runTest {
        val beta = DeletingBetaRepository()

        KeepHoldColorUseCase(beta)(detail(hold("ref", red), hold("pink", pink)), "ref")

        // Teintes voisines, luminosites tres differentes.
        assertThat(beta.deleted).containsExactly("pink")
    }

    @Test
    fun volumes_manual_holds_and_holds_carrying_moves_always_stay() = runTest {
        val beta = DeletingBetaRepository()
        val detail = detail(
            hold("ref", white),
            hold("volume", lightGrey, kind = HoldKind.VOLUME),
            hold("manual", null, origin = HoldOrigin.MANUAL),
            hold("foot", yellow),
            hold("undated", null),
            hold("other", yellow),
            movements = listOf(Movement("m", "bloc-1", 0, Limb.LEFT_FOOT, "ref", "foot")),
        )

        KeepHoldColorUseCase(beta)(detail, "ref")

        // Seule la prise jaune sans mouvement part. « undated » n'a pas de couleur
        // mesuree : rien ne permet de dire qu'elle est hors du bloc.
        assertThat(beta.deleted).containsExactly("other")
    }

    @Test
    fun a_reference_without_a_measured_colour_filters_nothing() = runTest {
        val beta = DeletingBetaRepository()
        val detail = detail(hold("manual", null, origin = HoldOrigin.MANUAL), hold("x", red))

        val result = KeepHoldColorUseCase(beta)(detail, "manual")

        assertThat(result).isEqualTo(KeepHoldColorUseCase.Result.NoReferenceColor)
        assertThat(beta.calls).isEqualTo(0)
    }

    @Test
    fun the_removal_is_a_single_write() = runTest {
        val beta = DeletingBetaRepository()
        val detail = detail(hold("ref", white), *Array(20) { hold("y$it", yellow) })

        KeepHoldColorUseCase(beta)(detail, "ref")

        assertThat(beta.calls).isEqualTo(1)
        assertThat(beta.deleted).hasSize(20)
    }

    private fun hold(
        id: String,
        color: HoldColor?,
        kind: HoldKind = HoldKind.HOLD,
        origin: HoldOrigin = HoldOrigin.MODEL,
    ) = Hold(id, "bloc-1", BoundingBox(0.5f, 0.5f, 0.05f, 0.05f), 0.9f, origin, kind, color)

    private fun detail(vararg holds: Hold, movements: List<Movement> = emptyList()) =
        BlocDetail(testBloc(), holds.toList(), movements)

    private class DeletingBetaRepository : BetaRepository {
        val deleted = mutableListOf<String>()
        var calls = 0

        override suspend fun deleteHolds(holdIds: List<String>) {
            calls++
            deleted += holdIds
        }

        override suspend fun replaceHolds(blocId: String, holds: List<DetectedHold>) = unexpected()
        override suspend fun addHold(blocId: String, hold: DetectedHold) = unexpected()
        override suspend fun addHolds(blocId: String, holds: List<DetectedHold>) = unexpected()
        override suspend fun updateHold(hold: Hold) = unexpected()
        override suspend fun deleteHold(holdId: String) = unexpected()
        override suspend fun replaceMovements(blocId: String, movements: List<Movement>) = unexpected()
        override suspend fun deleteMovement(movementId: String) = unexpected()

        private fun unexpected(): Nothing = error("Le filtre ne doit que supprimer des prises")
    }
}
