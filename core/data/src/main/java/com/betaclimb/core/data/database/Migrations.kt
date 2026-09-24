package com.betaclimb.core.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations du schema BetaClimb.
 *
 * Le schema de chaque version est exporte en JSON dans `core/data/schemas/` et
 * versionne : Room compare le schema attendu au schema reellement obtenu apres
 * migration, et `MigrationTest` rejoue une base v1 authentique. Une migration non
 * exportee ne serait pas verifiable.
 */

/**
 * v1 -> v2 : le carnet du grimpeur.
 *
 * Remplace le booleen `is_completed` par un statut a quatre etats, et ajoute le
 * compteur d'essais, la date de reussite et le ressenti.
 *
 * POURQUOI RECREER LA TABLE PLUTOT QU'UN SIMPLE ALTER
 * ---------------------------------------------------
 * Ajouter les nouvelles colonnes se ferait en `ALTER TABLE ... ADD COLUMN`, mais
 * SUPPRIMER `is_completed` demande `DROP COLUMN`, apparu dans SQLite 3.35. Le
 * projet cible l'API 26, dont le SQLite embarque est en 3.19 : la commande n'existe
 * pas sur une partie du parc. Conserver la colonne morte serait pire — deux
 * sources de verite pour « ai-je reussi ce bloc », vouees a diverger.
 *
 * On applique donc le patron standard de SQLite : creer la nouvelle table, copier
 * les donnees en les transformant, supprimer l'ancienne, renommer. Le tout dans la
 * transaction que Room ouvre autour de la migration.
 *
 * TRADUCTION DES DONNEES EXISTANTES
 * ---------------------------------
 * `is_completed = 1` devient SENT (3) et non FLASHED : rien dans la v1 ne permet
 * de savoir si la reussite s'est faite du premier coup, et inventer un flash
 * fausserait les statistiques a venir. `is_completed = 0` devient PROJECT (0) :
 * faute de compteur d'essais en v1, on ne peut pas affirmer que le bloc a ete
 * travaille.
 *
 * ORDRE DES OPERATIONS
 * --------------------
 * Les cles etrangeres sont desactivees le temps de l'echange de tables. Sans cela,
 * le `DROP TABLE blocs` ferait tomber en cascade les prises et les mouvements qui
 * le referencent — la totalite des annotations de l'utilisateur.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = OFF")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `blocs_new` (
                `id` TEXT NOT NULL,
                `session_id` TEXT NOT NULL,
                `image_path` TEXT NOT NULL,
                `image_width` INTEGER NOT NULL,
                `image_height` INTEGER NOT NULL,
                `grade` TEXT,
                `status` INTEGER NOT NULL,
                `attempts` INTEGER NOT NULL,
                `sent_at` INTEGER,
                `feeling` TEXT,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`session_id`) REFERENCES `sessions`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT INTO `blocs_new` (
                id, session_id, image_path, image_width, image_height, grade,
                status, attempts, sent_at, feeling, created_at
            )
            SELECT
                id, session_id, image_path, image_width, image_height, grade,
                CASE WHEN is_completed != 0 THEN 3 ELSE 0 END,
                0,
                CASE WHEN is_completed != 0 THEN created_at ELSE NULL END,
                NULL,
                created_at
            FROM `blocs`
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE `blocs`")
        db.execSQL("ALTER TABLE `blocs_new` RENAME TO `blocs`")

        // Les index ne survivent pas au remplacement de la table : Room les attend
        // a l'identique, et sans eux chaque suppression de seance balaierait toute
        // la table des blocs.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_blocs_session_id` ON `blocs` (`session_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_blocs_created_at` ON `blocs` (`created_at`)")

        db.execSQL("PRAGMA foreign_keys = ON")
    }
}

/** Toutes les migrations, dans l'ordre. */
val BETACLIMB_MIGRATIONS = arrayOf(MIGRATION_1_2)
