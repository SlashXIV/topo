package com.topo.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une couleur du circuit de la salle.
 *
 * `rank` porte un index UNIQUE parce que l'ordre EST la difficulte : deux
 * couleurs ne peuvent pas occuper le meme niveau. Consequence directe sur les
 * ecritures — reordonnancer ne peut pas se faire par deux `UPDATE` successifs,
 * qui violeraient la contrainte entre les deux. La liste entiere est reecrite en
 * une transaction, exactement comme `sequence_order` des mouvements.
 */
@Entity(
    tableName = "circuit_colors",
    indices = [Index(value = ["rank"], unique = true)],
)
data class CircuitColorEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** Couleur ARGB en entier : ce que manipulent SQLite et Compose, sans analyse de texte. */
    @ColumnInfo(name = "argb")
    val argb: Int,

    /** 0 = le plus facile. Contigu dans la liste. */
    @ColumnInfo(name = "rank")
    val rank: Int,
)
