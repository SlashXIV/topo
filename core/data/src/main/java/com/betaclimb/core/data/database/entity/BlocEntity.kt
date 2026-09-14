package com.betaclimb.core.data.database.entity

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
    ],
    // Index obligatoire sur une cle etrangere : sans lui, chaque suppression de
    // seance provoque un balayage complet de la table des blocs.
    indices = [Index(value = ["session_id"]), Index(value = ["created_at"])],
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

    /** Cotation libre ("7A", "V5"), nulle tant que l'utilisateur ne l'a pas saisie. */
    @ColumnInfo(name = "grade")
    val grade: String?,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
