package com.topo.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une prise d'escalade, detectee par le modele ou posee au doigt.
 *
 * Geometrie stockee NORMALISEE dans `[0, 1]` : une prise reste valide si la photo
 * est re-encodee a une autre resolution, et l'affichage n'a qu'a multiplier par les
 * dimensions du bloc.
 *
 * `origin` distingue les prises du modele de celles de l'utilisateur. Ce n'est pas
 * cosmetique : c'est ce qui permettra d'exporter les corrections humaines comme jeu
 * de donnees d'entrainement, et c'est ce qui rend l'application utilisable alors
 * qu'aucun modele n'est encore embarque.
 */
@Entity(
    tableName = "holds",
    foreignKeys = [
        ForeignKey(
            entity = BlocEntity::class,
            parentColumns = ["id"],
            childColumns = ["bloc_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bloc_id"])],
)
data class HoldEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "bloc_id")
    val blocId: String,

    @ColumnInfo(name = "bbox_x_center")
    val bboxXCenter: Float,

    @ColumnInfo(name = "bbox_y_center")
    val bboxYCenter: Float,

    @ColumnInfo(name = "bbox_width")
    val bboxWidth: Float,

    @ColumnInfo(name = "bbox_height")
    val bboxHeight: Float,

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    /** Ordinal de `HoldOrigin`. Ne jamais reordonner l'enumeration. */
    @ColumnInfo(name = "origin")
    val origin: Int,
)
