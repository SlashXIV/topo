package com.betaclimb.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Un mouvement de la sequence d'ascension.
 *
 * CORRECTION DE MODELISATION (par rapport aux specifications initiales)
 * ---------------------------------------------------------------------
 * Les specifications prevoyaient une table d'association `MovementHoldCrossRef`
 * pour relier un mouvement a ses prises. Ce n'en etait pas une : un mouvement a
 * AU PLUS UNE prise source et EXACTEMENT UNE prise cible, soit une relation 1-1.
 * De plus, sa cle primaire composee `(movement_id, target_hold_id)` interdisait
 * plusieurs cibles — elle ne modelisait donc pas le many-to-many qu'elle
 * pretendait modeliser. Elle ajoutait surtout une jointure sur le chemin chaud du
 * rendu, ou tout le beta est relu a chaque recomposition.
 *
 * Deux colonnes de cle etrangere suffisent, avec des cascades DIFFERENTES et
 * volontairement dissymetriques :
 *
 *  - `target_hold_id` : ON DELETE **CASCADE**. Un mouvement qui arrive sur une
 *    prise supprimee n'a plus de sens, il disparait avec elle.
 *  - `source_hold_id` : ON DELETE **SET NULL**. Un mouvement qui partait d'une
 *    prise supprimee reste valide : il devient un mouvement de depart (la main
 *    vient du sol). Le supprimer perdrait de l'information utile.
 *
 * Cette dissymetrie est la raison d'etre de la nullabilite de `source_hold_id`.
 */
@Entity(
    tableName = "movements",
    foreignKeys = [
        ForeignKey(
            entity = BlocEntity::class,
            parentColumns = ["id"],
            childColumns = ["bloc_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = HoldEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_hold_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = HoldEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_hold_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        // Chaque cle etrangere est indexee : sans index, SQLite balaye la table
        // entiere a chaque suppression de prise, et il y en a des centaines.
        Index(value = ["bloc_id"]),
        Index(value = ["source_hold_id"]),
        Index(value = ["target_hold_id"]),
        // Deux mouvements ne peuvent pas occuper le meme rang dans la sequence
        // d'un bloc. Contrainte posee en base, pas seulement dans le code.
        Index(value = ["bloc_id", "sequence_order"], unique = true),
    ],
)
data class MovementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "bloc_id")
    val blocId: String,

    @ColumnInfo(name = "sequence_order")
    val sequenceOrder: Int,

    /** Ordinal de `Limb` : 0 = main gauche, 1 = main droite, 2/3 = pieds. */
    @ColumnInfo(name = "limb_type")
    val limbType: Int,

    /** Nul pour un mouvement de depart, ou apres suppression de la prise source. */
    @ColumnInfo(name = "source_hold_id")
    val sourceHoldId: String?,

    @ColumnInfo(name = "target_hold_id")
    val targetHoldId: String,
)
