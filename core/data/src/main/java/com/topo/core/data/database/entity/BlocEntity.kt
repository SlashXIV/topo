package com.topo.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un bloc photographie au sein d'une seance.
 *
 * `image_path` est RELATIF au repertoire d'images prive de l'application. Jamais un
 * chemin absolu (il change lors d'une restauration d'appareil), jamais une
 * `content://` externe (la permission peut etre revoquee, ce qui casserait le
 * fonctionnement hors ligne).
 *
 * `image_width` / `image_height` sont conserves pour denormaliser les prises sans
 * decoder le bitmap : une photo de telephone en ARGB_8888 pese couramment ~48 Mo,
 * la decoder juste pour connaitre ses dimensions serait un candidat direct a
 * l'OutOfMemoryError.
 *
 * SCHEMA v3 — la difficulte devient exploitable : `grade` porte une etiquette
 * canonique d'echelle plutot que du texte libre, et `color_id` rattache le bloc
 * au circuit de la salle. Les deux coexistent : la salle etiquette « vert », le
 * grimpeur estime 6B, ce sont deux informations differentes.
 *
 * SCHEMA v2 — `is_completed` a ete REMPLACE par `status`, et non double par lui.
 * Un booleen ne distinguait pas « jamais essaye » de « travaille sans succes »,
 * precisement la distinction qui fait l'interet d'un carnet. Garder les deux aurait
 * cree deux sources de verite pour la meme question, promises a diverger.
 */
@Entity(
    tableName = "blocs",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            // Supprimer une seance supprime ses blocs. Realise par le moteur SQLite,
            // pas par du code metier : c'est atomique et impossible a oublier.
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CircuitColorEntity::class,
            parentColumns = ["id"],
            childColumns = ["color_id"],
            // SET NULL et surtout pas CASCADE : effacer une etiquette de circuit
            // ne doit pas effacer les ascensions qui la portaient. Le bloc perd
            // sa couleur, pas son existence.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    // Index obligatoire sur une cle etrangere : sans lui, chaque suppression de
    // seance provoque un balayage complet de la table des blocs.
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["created_at"]),
        Index(value = ["color_id"]),
    ],
)
data class BlocEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "image_path")
    val imagePath: String,

    @ColumnInfo(name = "image_width")
    val imageWidth: Int,

    @ColumnInfo(name = "image_height")
    val imageHeight: Int,

    /**
     * Etiquette de cotation, tiree de l'echelle designee par [gradeScale].
     *
     * SCHEMA v3 — ce n'est plus du texte libre. La colonne existait depuis la v1
     * sans jamais etre alimentee ; elle porte desormais une valeur canonique,
     * seule forme exploitable par un tri de progression.
     */
    @ColumnInfo(name = "grade")
    val grade: String?,

    /** Ordinal de `GradeScale` auquel appartient [grade]. */
    @ColumnInfo(name = "grade_scale")
    val gradeScale: Int?,

    /** Couleur du circuit, independante de la cotation : un bloc peut porter les deux. */
    @ColumnInfo(name = "color_id")
    val colorId: String?,

    /** Ordinal de `BlocStatus`. Ne jamais reordonner l'enumeration. */
    @ColumnInfo(name = "status")
    val status: Int,

    @ColumnInfo(name = "attempts")
    val attempts: Int,

    /** Instant de la reussite, nul tant que le bloc n'est pas reussi. */
    @ColumnInfo(name = "sent_at")
    val sentAt: Long?,

    /** Ressenti libre du grimpeur. */
    @ColumnInfo(name = "feeling")
    val feeling: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
