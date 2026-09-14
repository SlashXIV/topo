package com.betaclimb.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Une seance d'escalade. Racine de l'arborescence : supprimer une seance emporte
 * ses blocs, leurs prises et leurs mouvements, par cascade SQLite.
 */
@Entity(
    tableName = "sessions",
    // La liste des seances est toujours triee du plus recent au plus ancien :
    // l'index evite un tri complet a chaque emission du Flow.
    indices = [Index(value = ["date"])],
)
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Instant epoch en millisecondes UTC. */
    @ColumnInfo(name = "date")
    val date: Long,

    @ColumnInfo(name = "location_name")
    val locationName: String,

    @ColumnInfo(name = "notes")
    val notes: String?,
)
