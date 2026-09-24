package com.betaclimb.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.betaclimb.core.data.database.BetaClimbDatabase
import com.betaclimb.core.data.database.MIGRATION_1_2
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration du schema v1 vers v2.
 *
 * Le test rejoue une base v1 AUTHENTIQUE, creee par Room a partir du schema JSON
 * exporte au Sprint 1 et versionne dans `core/data/schemas/`. Ce n'est pas une
 * reconstitution approximative : c'est exactement la base qu'un utilisateur de la
 * version precedente a sur son telephone.
 *
 * `MigrationTestHelper.runMigrationsAndValidate` compare en plus le schema obtenu
 * apres migration au schema v2 attendu. Une colonne oubliee, un index disparu ou
 * une contrainte de cle etrangere differente font echouer le test — ce qui couvre
 * la classe d'erreur la plus courante du patron « recreer la table ».
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val databaseName = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BetaClimbDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrating_from_v1_preserves_blocs_and_maps_completion_to_a_status() {
        helper.createDatabase(databaseName, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO sessions (id, date, location_name, notes)
                VALUES ('s1', 1700000000000, 'Arkose', NULL)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO blocs
                    (id, session_id, image_path, image_width, image_height, grade,
                     is_completed, created_at)
                VALUES
                    ('b1', 's1', 'reussi.jpg', 3000, 4000, '7A', 1, 1700000001000),
                    ('b2', 's1', 'projet.jpg', 3000, 4000, '7B', 0, 1700000002000)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(databaseName, 2, true, MIGRATION_1_2)

        db.query(
            "SELECT id, status, attempts, sent_at, feeling, grade FROM blocs ORDER BY id",
        ).use { cursor ->
            assertThat(cursor.count).isEqualTo(2)

            cursor.moveToFirst()
            // is_completed = 1 devient SENT (3) et surtout PAS FLASHED : la v1 ne
            // dit pas si la reussite s'est faite du premier coup, et inventer un
            // flash fausserait les statistiques a venir.
            assertThat(cursor.getString(0)).isEqualTo("b1")
            assertThat(cursor.getInt(1)).isEqualTo(3)
            assertThat(cursor.getInt(2)).isEqualTo(0)
            assertThat(cursor.isNull(3)).isFalse()
            assertThat(cursor.isNull(4)).isTrue()
            assertThat(cursor.getString(5)).isEqualTo("7A")

            cursor.moveToNext()
            // is_completed = 0 devient PROJECT (0) : sans compteur d'essais en v1,
            // rien ne permet d'affirmer que le bloc a ete travaille.
            assertThat(cursor.getString(0)).isEqualTo("b2")
            assertThat(cursor.getInt(1)).isEqualTo(0)
            assertThat(cursor.isNull(3)).isTrue()
        }
    }

    /**
     * Le point le plus dangereux de cette migration.
     *
     * Le patron « recreer la table » passe par un `DROP TABLE blocs`. Si les cles
     * etrangeres restaient actives a cet instant, la cascade emporterait les prises
     * et les mouvements qui referencent ces blocs : la totalite des annotations de
     * l'utilisateur, effacees par une mise a jour.
     */
    @Test
    fun migrating_does_not_cascade_away_holds_and_movements() {
        helper.createDatabase(databaseName, 1).use { db ->
            db.execSQL(
                "INSERT INTO sessions (id, date, location_name, notes) " +
                    "VALUES ('s1', 1700000000000, 'Arkose', NULL)",
            )
            db.execSQL(
                "INSERT INTO blocs (id, session_id, image_path, image_width, image_height, " +
                    "grade, is_completed, created_at) " +
                    "VALUES ('b1', 's1', 'mur.jpg', 3000, 4000, '7A', 0, 1700000001000)",
            )
            db.execSQL(
                "INSERT INTO holds (id, bloc_id, bbox_x_center, bbox_y_center, bbox_width, " +
                    "bbox_height, confidence_score, origin) " +
                    "VALUES ('h1', 'b1', 0.2, 0.5, 0.08, 0.08, 1.0, 0), " +
                    "('h2', 'b1', 0.8, 0.5, 0.08, 0.08, 1.0, 0)",
            )
            db.execSQL(
                "INSERT INTO movements (id, bloc_id, sequence_order, limb_type, " +
                    "source_hold_id, target_hold_id) VALUES ('m1', 'b1', 0, 1, 'h1', 'h2')",
            )
        }

        val db = helper.runMigrationsAndValidate(databaseName, 2, true, MIGRATION_1_2)

        db.query("SELECT COUNT(*) FROM holds").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(2)
        }
        db.query("SELECT COUNT(*) FROM movements").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(1)
        }
    }

    /**
     * Les index ne survivent pas au remplacement de la table. Sans leur
     * recreation, chaque suppression de seance balaierait toute la table des blocs,
     * et le schema valide ne correspondrait plus a celui attendu par Room.
     */
    @Test
    fun migrating_restores_the_indices_on_the_rebuilt_table() {
        helper.createDatabase(databaseName, 1).close()

        val db = helper.runMigrationsAndValidate(databaseName, 2, true, MIGRATION_1_2)

        val indices = mutableListOf<String>()
        db.query("PRAGMA index_list(`blocs`)").use { cursor ->
            while (cursor.moveToNext()) {
                indices += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
        }

        assertThat(indices).containsAtLeast(
            "index_blocs_session_id",
            "index_blocs_created_at",
        )
    }
}
