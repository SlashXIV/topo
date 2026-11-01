package com.topo.core.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations du schema Topo.
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

/**
 * v2 -> v3 : la cotation devient exploitable.
 *
 * Ajoute le circuit de couleurs, les reglages, et rattache un bloc aux deux
 * systemes de difficulte.
 *
 * POURQUOI DE SIMPLES ALTER TABLE ICI
 * -----------------------------------
 * Contrairement a la v1 -> v2, rien n'est supprime : on ajoute. `ALTER TABLE
 * ADD COLUMN` suffit donc, sans reconstruire la table ni desactiver les cles
 * etrangeres. SQLite autorise l'ajout d'une colonne portant une clause
 * `REFERENCES` a la condition que sa valeur par defaut soit NULL — c'est le cas
 * de `color_id`.
 *
 * AMORCAGE
 * --------
 * La migration insere un circuit par defaut et la ligne de reglages. Sans cela
 * la fonctionnalite s'ouvrirait sur une liste vide, c'est-a-dire sur une
 * impasse : l'utilisateur devrait deviner qu'il faut d'abord construire un
 * circuit avant de pouvoir coter quoi que ce soit.
 *
 * Les couleurs retenues sont celles qu'on croise le plus souvent en salle, du
 * plus facile au plus dur. L'ordre n'est qu'un point de depart : il est fait
 * pour etre remanie, chaque salle ayant le sien.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `circuit_colors` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `argb` INTEGER NOT NULL,
                `rank` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_circuit_colors_rank` " +
                "ON `circuit_colors` (`rank`)",
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `settings` (
                `id` INTEGER NOT NULL,
                `difficulty_display` INTEGER NOT NULL,
                `grade_scale` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )

        db.execSQL("ALTER TABLE `blocs` ADD COLUMN `grade_scale` INTEGER")
        db.execSQL(
            "ALTER TABLE `blocs` ADD COLUMN `color_id` TEXT " +
                "REFERENCES `circuit_colors`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_blocs_color_id` ON `blocs` (`color_id`)")

        // 0 = DifficultyDisplay.COLOR, 0 = GradeScale.FRENCH.
        db.execSQL(
            "INSERT OR IGNORE INTO `settings` (id, difficulty_display, grade_scale) " +
                "VALUES (0, 0, 0)",
        )

        DEFAULT_CIRCUIT.forEachIndexed { rank, (name, argb) ->
            db.execSQL(
                "INSERT OR IGNORE INTO `circuit_colors` (id, name, argb, rank) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(name.lowercase(), name, argb, rank),
            )
        }
    }
}

/** Circuit propose au premier lancement, du plus facile au plus dur. */
private val DEFAULT_CIRCUIT = listOf(
    "Jaune" to 0xFFF2C14E.toInt(),
    "Vert" to 0xFF5FAD56.toInt(),
    "Bleu" to 0xFF2E86AB.toInt(),
    "Rouge" to 0xFFD1495B.toInt(),
    "Noir" to 0xFF2B2A28.toInt(),
)

/**
 * v3 -> v4 : nature (prise ou volume) et couleur de chaque prise.
 *
 * De simples ajouts de colonnes : rien n'est supprime, aucune reconstruction de
 * table n'est necessaire. Les prises existantes deviennent des prises (kind = 0)
 * sans couleur — ce qu'elles sont : posees a la main, ou detectees avant que la
 * couleur ne soit mesuree. Relancer la detection la leur donnera.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `holds` ADD COLUMN `kind` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `holds` ADD COLUMN `color_l` REAL")
        db.execSQL("ALTER TABLE `holds` ADD COLUMN `color_a` REAL")
        db.execSQL("ALTER TABLE `holds` ADD COLUMN `color_b` REAL")
    }
}

/**
 * v4 -> v5 : cadre du bloc sur sa photo.
 *
 * Simples ajouts de colonnes nulles : un bloc existant n'a pas de cadre, il
 * s'affiche et se detecte sur toute la photo, comme avant.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (column in listOf("zone_center_x", "zone_center_y", "zone_width", "zone_height")) {
            db.execSQL("ALTER TABLE `blocs` ADD COLUMN `$column` REAL")
        }
    }
}

/**
 * v5 -> v6 : ambiance du mouvement.
 *
 * Une colonne a valeur par defaut sur la ligne unique des reglages : une
 * installation existante garde l'ambiance expressive, celle d'avant la refonte.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `settings` ADD COLUMN `motion_preference` INTEGER NOT NULL DEFAULT 0")
    }
}

/** Toutes les migrations, dans l'ordre. */
val TOPO_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
