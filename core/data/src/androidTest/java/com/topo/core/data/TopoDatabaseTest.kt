package com.topo.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.topo.core.data.database.TopoDatabase
import com.topo.core.data.database.applyTopoConfiguration
import com.topo.core.data.database.entity.BlocEntity
import com.topo.core.data.database.entity.HoldEntity
import com.topo.core.data.database.entity.MovementEntity
import com.topo.core.data.database.entity.SessionEntity
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validation du schema et de la configuration de la base (Sprint 1).
 *
 * La base est ouverte SUR FICHIER, pas en memoire : le mode WAL n'existe pas pour
 * une base en memoire, et c'est precisement l'une des proprietes a verifier.
 *
 * La configuration testee est celle de la production : le test appelle la meme
 * `applyTopoConfiguration()` que le module Hilt.
 */
@RunWith(AndroidJUnit4::class)
class TopoDatabaseTest {

    private lateinit var database: TopoDatabase
    private val databaseName = "topo-test-${UUID.randomUUID()}.db"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.databaseBuilder(context, TopoDatabase::class.java, databaseName)
            .applyTopoConfiguration()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(databaseName)
    }

    // --- Configuration -----------------------------------------------------

    @Test
    fun journal_mode_is_write_ahead_logging() {
        val mode = database.openHelper.readableDatabase
            .query("PRAGMA journal_mode")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }

        // Sans WAL, une ecriture verrouille tout le fichier et gele les lectures :
        // c'est le gel de l'interface que toute l'architecture cherche a eviter.
        assertThat(mode.lowercase()).isEqualTo("wal")
    }

    @Test
    fun foreign_key_enforcement_is_active() {
        val enabled = database.openHelper.readableDatabase
            .query("PRAGMA foreign_keys")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }

        // Toute l'integrite du modele repose sur les cascades ON DELETE. Si la
        // contrainte n'etait pas appliquee, elles seraient silencieusement ignorees.
        assertThat(enabled).isEqualTo(1)
    }

    @Test
    fun orphan_bloc_is_rejected() = runTest {
        val orphan = bloc(sessionId = "session-inexistante")

        val error = runCatching { database.blocDao().insert(orphan) }.exceptionOrNull()

        assertThat(error).isNotNull()
    }

    // --- Cascades ----------------------------------------------------------

    @Test
    fun deleting_a_session_cascades_to_blocs_holds_and_movements() = runTest {
        val session = session()
        val bloc = bloc(sessionId = session.id)
        val start = hold(blocId = bloc.id)
        val target = hold(blocId = bloc.id)

        database.sessionDao().upsert(session)
        database.blocDao().insert(bloc)
        database.holdDao().insertAll(listOf(start, target))
        database.movementDao().insertAll(
            listOf(movement(blocId = bloc.id, order = 0, source = start.id, target = target.id)),
        )

        database.sessionDao().deleteById(session.id)

        assertThat(database.holdDao().getHolds(bloc.id)).isEmpty()
        assertThat(database.movementDao().getMovements(bloc.id)).isEmpty()
        assertThat(database.blocDao().getImagePath(bloc.id)).isNull()
    }

    /**
     * Cascade sur la prise CIBLE : un mouvement qui arrive sur une prise supprimee
     * n'a plus de sens, il disparait avec elle.
     */
    @Test
    fun deleting_a_target_hold_deletes_the_movement() = runTest {
        val session = session()
        val bloc = bloc(sessionId = session.id)
        val start = hold(blocId = bloc.id)
        val target = hold(blocId = bloc.id)
        val move = movement(blocId = bloc.id, order = 0, source = start.id, target = target.id)

        database.sessionDao().upsert(session)
        database.blocDao().insert(bloc)
        database.holdDao().insertAll(listOf(start, target))
        database.movementDao().insertAll(listOf(move))

        database.holdDao().deleteById(target.id)

        assertThat(database.movementDao().getMovements(bloc.id)).isEmpty()
    }

    /**
     * SET NULL sur la prise SOURCE : le mouvement reste valide, il devient un
     * mouvement de depart (la main vient du sol). Le supprimer perdrait de
     * l'information. C'est la dissymetrie volontaire du modele.
     */
    @Test
    fun deleting_a_source_hold_nulls_the_reference_without_deleting_the_movement() = runTest {
        val session = session()
        val bloc = bloc(sessionId = session.id)
        val start = hold(blocId = bloc.id)
        val target = hold(blocId = bloc.id)
        val move = movement(blocId = bloc.id, order = 0, source = start.id, target = target.id)

        database.sessionDao().upsert(session)
        database.blocDao().insert(bloc)
        database.holdDao().insertAll(listOf(start, target))
        database.movementDao().insertAll(listOf(move))

        database.holdDao().deleteById(start.id)

        val remaining = database.movementDao().getMovements(bloc.id)
        assertThat(remaining).hasSize(1)
        assertThat(remaining.single().sourceHoldId).isNull()
        assertThat(remaining.single().targetHoldId).isEqualTo(target.id)
    }

    // --- Contraintes d'integrite -------------------------------------------

    @Test
    fun two_movements_cannot_share_the_same_sequence_order() = runTest {
        val session = session()
        val bloc = bloc(sessionId = session.id)
        val target = hold(blocId = bloc.id)

        database.sessionDao().upsert(session)
        database.blocDao().insert(bloc)
        database.holdDao().insertAll(listOf(target))
        database.movementDao().insertAll(
            listOf(movement(blocId = bloc.id, order = 0, source = null, target = target.id)),
        )

        val error = runCatching {
            database.movementDao().insertAll(
                listOf(movement(blocId = bloc.id, order = 0, source = null, target = target.id)),
            )
        }.exceptionOrNull()

        assertThat(error).isNotNull()
    }

    // --- Reactivite et volume ----------------------------------------------

    @Test
    fun hold_flow_emits_on_insert() = runTest {
        val session = session()
        val bloc = bloc(sessionId = session.id)
        database.sessionDao().upsert(session)
        database.blocDao().insert(bloc)

        database.holdDao().observeHolds(bloc.id).test {
            assertThat(awaitItem()).isEmpty()

            database.holdDao().insertAll(listOf(hold(blocId = bloc.id)))

            // La base est la source de verite : aucune notification manuelle n'est
            // emise, c'est l'ecriture elle-meme qui declenche la reemission.
            assertThat(awaitItem()).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun inserting_several_hundred_holds_stays_transactional() = runTest {
        val session = session()
        val bloc = bloc(sessionId = session.id)
        database.sessionDao().upsert(session)
        database.blocDao().insert(bloc)

        // Un mur de bloc depasse rarement 150 prises ; 500 valide une marge large.
        val holds = List(500) { hold(blocId = bloc.id) }
        database.holdDao().replaceForBloc(bloc.id, holds)

        assertThat(database.holdDao().getHolds(bloc.id)).hasSize(500)

        // Le remplacement est integral : l'ancien jeu ne survit pas.
        database.holdDao().replaceForBloc(bloc.id, List(3) { hold(blocId = bloc.id) })
        assertThat(database.holdDao().getHolds(bloc.id)).hasSize(3)
    }

    @Test
    fun session_summary_counts_blocs_and_completions() = runTest {
        val session = session()
        database.sessionDao().upsert(session)
        database.blocDao().insert(bloc(sessionId = session.id, completed = true))
        database.blocDao().insert(bloc(sessionId = session.id, completed = false))
        database.blocDao().insert(bloc(sessionId = session.id, completed = true))

        database.sessionDao().observeSessionsWithCounts().test {
            val row = awaitItem().single()
            assertThat(row.blocCount).isEqualTo(3)
            assertThat(row.completedCount).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Une seance sans bloc doit apparaitre avec 0, d'ou le LEFT JOIN. */
    @Test
    fun session_without_bloc_is_listed_with_zero_counts() = runTest {
        database.sessionDao().upsert(session())

        database.sessionDao().observeSessionsWithCounts().test {
            val row = awaitItem().single()
            assertThat(row.blocCount).isEqualTo(0)
            assertThat(row.completedCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Fabriques ---------------------------------------------------------

    private fun session() = SessionEntity(
        id = UUID.randomUUID().toString(),
        date = System.currentTimeMillis(),
        locationName = "Salle de test",
        notes = null,
    )

    private fun bloc(sessionId: String, completed: Boolean = false) = BlocEntity(
        id = UUID.randomUUID().toString(),
        sessionId = sessionId,
        imagePath = "${UUID.randomUUID()}.jpg",
        imageWidth = 3000,
        imageHeight = 4000,
        grade = "7A",
        gradeScale = null,
        colorId = null,
        // 3 = BlocStatus.SENT, 0 = PROJECT.
        status = if (completed) 3 else 0,
        attempts = 0,
        sentAt = null,
        feeling = null,
        createdAt = System.currentTimeMillis(),
    )

    private fun hold(blocId: String) = HoldEntity(
        id = UUID.randomUUID().toString(),
        blocId = blocId,
        bboxXCenter = 0.5f,
        bboxYCenter = 0.5f,
        bboxWidth = 0.08f,
        bboxHeight = 0.08f,
        confidenceScore = 1f,
        origin = 0,
    )

    private fun movement(blocId: String, order: Int, source: String?, target: String) =
        MovementEntity(
            id = UUID.randomUUID().toString(),
            blocId = blocId,
            sequenceOrder = order,
            limbType = 1,
            sourceHoldId = source,
            targetHoldId = target,
        )
}
