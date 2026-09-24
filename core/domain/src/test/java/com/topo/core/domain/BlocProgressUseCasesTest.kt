package com.topo.core.domain

import com.topo.core.domain.model.BlocStatus
import com.topo.core.domain.model.HoldOrigin
import com.topo.core.domain.model.Limb
import com.topo.core.domain.usecase.RecordAttemptUseCase
import com.topo.core.domain.usecase.SetBlocStatusUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Regles du carnet : essais, statuts, date de reussite.
 *
 * Elles viennent du vocabulaire de l'escalade, pas du code, et c'est pourquoi
 * elles vivent dans le domaine plutot que dans un ViewModel.
 */
class BlocProgressUseCasesTest {

    private val repository = FakeBlocRepository()
    private val recordAttempt = RecordAttemptUseCase(repository)
    private val setStatus = SetBlocStatusUseCase(repository)

    @Test
    fun `a first attempt moves a project to working`() = runTest {
        repository.seed(testBloc(status = BlocStatus.PROJECT))

        val bloc = recordAttempt("bloc-1").getOrThrow()

        // Personne ne pense a changer un statut avant de compter un essai : laisser
        // un bloc « projet » avec des essais au compteur serait une incoherence que
        // l'utilisateur devrait corriger a la main.
        assertThat(bloc.status).isEqualTo(BlocStatus.WORKING)
        assertThat(bloc.attempts).isEqualTo(1)
    }

    @Test
    fun `an attempt on a sent bloc keeps the send`() = runTest {
        repository.seed(testBloc(status = BlocStatus.SENT, attempts = 4, sentAt = 1_000L))

        val bloc = recordAttempt("bloc-1").getOrThrow()

        // Un grimpeur repete un bloc reussi : recompter un essai ne doit pas
        // effacer sa reussite.
        assertThat(bloc.status).isEqualTo(BlocStatus.SENT)
        assertThat(bloc.attempts).isEqualTo(5)
        assertThat(bloc.sentAt).isEqualTo(1_000L)
    }

    @Test
    fun `sending stamps the moment of success`() = runTest {
        repository.seed(testBloc(status = BlocStatus.WORKING, attempts = 6))

        val bloc = setStatus("bloc-1", BlocStatus.SENT, now = 42_000L).getOrThrow()

        assertThat(bloc.sentAt).isEqualTo(42_000L)
    }

    @Test
    fun `an already stamped send keeps its original date`() = runTest {
        repository.seed(testBloc(status = BlocStatus.SENT, attempts = 3, sentAt = 1_000L))

        val bloc = setStatus("bloc-1", BlocStatus.SENT, now = 99_000L).getOrThrow()

        // Re-confirmer une reussite ne doit pas la redater : ce serait perdre la
        // seule information de progression que le carnet conserve.
        assertThat(bloc.sentAt).isEqualTo(1_000L)
    }

    @Test
    fun `reverting to working clears the send date`() = runTest {
        repository.seed(testBloc(status = BlocStatus.SENT, attempts = 3, sentAt = 1_000L))

        val bloc = setStatus("bloc-1", BlocStatus.WORKING).getOrThrow()

        assertThat(bloc.sentAt).isNull()
        // Les essais racontent le travail fourni : repasser un bloc en projet ne
        // les efface pas.
        assertThat(bloc.attempts).isEqualTo(3)
    }

    @Test
    fun `a flash is refused when the bloc was already worked`() = runTest {
        repository.seed(testBloc(status = BlocStatus.WORKING, attempts = 5))

        val result = setStatus("bloc-1", BlocStatus.FLASHED)

        // Refus explicite plutot que correction silencieuse : c'est l'utilisateur
        // qui sait s'il s'est trompe de bouton ou de compteur.
        assertThat(result.isFailure).isTrue()
        assertThat(repository.getBloc("bloc-1")?.status).isEqualTo(BlocStatus.WORKING)
    }

    @Test
    fun `a flash declared without any attempt counts as one`() = runTest {
        repository.seed(testBloc(status = BlocStatus.PROJECT, attempts = 0))

        val bloc = setStatus("bloc-1", BlocStatus.FLASHED, now = 7_000L).getOrThrow()

        // On ne reussit pas un bloc sans l'avoir essaye une fois.
        assertThat(bloc.attempts).isEqualTo(1)
        assertThat(bloc.sentAt).isEqualTo(7_000L)
    }

    @Test
    fun `acting on an unknown bloc fails instead of creating one`() = runTest {
        assertThat(recordAttempt("inconnu").isFailure).isTrue()
        assertThat(setStatus("inconnu", BlocStatus.SENT).isFailure).isTrue()
    }

    /**
     * Les ordinaux de ces enumerations sont PERSISTES en base, et ceux de
     * [BlocStatus] sont en plus ecrits litteralement dans l'agregation SQL des
     * seances (`status IN (2, 3)`). Les reordonner corromprait silencieusement
     * toutes les donnees existantes : ce test fait echouer un tel changement.
     */
    @Test
    fun `persisted enum ordinals are frozen`() {
        assertThat(BlocStatus.PROJECT.ordinal).isEqualTo(0)
        assertThat(BlocStatus.WORKING.ordinal).isEqualTo(1)
        assertThat(BlocStatus.FLASHED.ordinal).isEqualTo(2)
        assertThat(BlocStatus.SENT.ordinal).isEqualTo(3)
        assertThat(BlocStatus.SQL_SENT_ORDINALS).isEqualTo("2, 3")

        assertThat(Limb.LEFT_HAND.ordinal).isEqualTo(0)
        assertThat(Limb.RIGHT_HAND.ordinal).isEqualTo(1)
        assertThat(Limb.LEFT_FOOT.ordinal).isEqualTo(2)
        assertThat(Limb.RIGHT_FOOT.ordinal).isEqualTo(3)

        assertThat(HoldOrigin.MANUAL.ordinal).isEqualTo(0)
        assertThat(HoldOrigin.MODEL.ordinal).isEqualTo(1)
    }
}
