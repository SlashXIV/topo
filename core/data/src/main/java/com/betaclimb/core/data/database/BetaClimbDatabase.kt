package com.betaclimb.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.betaclimb.core.data.dao.BlocDao
import com.betaclimb.core.data.dao.HoldDao
import com.betaclimb.core.data.dao.MovementDao
import com.betaclimb.core.data.dao.SessionDao
import com.betaclimb.core.data.database.entity.BlocEntity
import com.betaclimb.core.data.database.entity.HoldEntity
import com.betaclimb.core.data.database.entity.MovementEntity
import com.betaclimb.core.data.database.entity.SessionEntity

/**
 * Base locale de BetaClimb. Unique source de verite de l'application.
 *
 * `exportSchema = true` : le schema est ecrit en JSON dans `core/data/schemas/` et
 * versionne dans git. C'est ce qui rend les migrations verifiables — Room compare
 * le schema attendu au schema reel, et un test peut rejouer une version anterieure
 * pour valider la migration. Sans export, une migration ne peut etre testee.
 *
 * Aucun TypeConverter : les enumerations sont stockees en `Int` (leur ordinal) et
 * converties dans les mappers. Un convertisseur global masquerait le fait que ces
 * ordinaux sont un format PERSISTE, qu'on ne peut donc plus reordonner.
 */
@Database(
    entities = [
        SessionEntity::class,
        BlocEntity::class,
        HoldEntity::class,
        MovementEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class BetaClimbDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun blocDao(): BlocDao
    abstract fun holdDao(): HoldDao
    abstract fun movementDao(): MovementDao

    companion object {
        const val NAME = "betaclimb.db"
    }
}
