package com.topo.core.data.database.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.topo.core.data.database.entity.BlocEntity
import com.topo.core.data.database.entity.HoldEntity
import com.topo.core.data.database.entity.MovementEntity

/**
 * Un bloc et l'integralite de son annotation, charges ensemble.
 *
 * Room execute trois requetes sous une meme transaction (d'ou le `@Transaction` sur
 * la methode du DAO) : l'instantane est donc coherent. Emettre les prises et les
 * mouvements dans des flux separes exposerait un etat intermediaire ou des
 * mouvements referencent des prises pas encore arrivees a l'ecran.
 */
data class BlocWithBeta(
    @Embedded
    val bloc: BlocEntity,

    @Relation(parentColumn = "id", entityColumn = "bloc_id")
    val holds: List<HoldEntity>,

    @Relation(parentColumn = "id", entityColumn = "bloc_id")
    val movements: List<MovementEntity>,
)

/**
 * Projection d'une seance avec ses compteurs.
 *
 * Calcules en SQL par agregation plutot qu'en chargeant tous les blocs pour les
 * compter en Kotlin : la liste des seances n'a besoin que de deux entiers, pas de
 * centaines de lignes transferees vers le heap.
 */
data class SessionWithCounts(
    @Embedded
    val session: com.topo.core.data.database.entity.SessionEntity,

    @androidx.room.ColumnInfo(name = "bloc_count")
    val blocCount: Int,

    @androidx.room.ColumnInfo(name = "completed_count")
    val completedCount: Int,

    /** ARGB des blocs reussis, separes par des virgules ; vide pour un bloc sans couleur. */
    @androidx.room.ColumnInfo(name = "sent_colors")
    val sentColors: String?,
)
