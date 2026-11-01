package com.topo.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Reglages de l'application, en LIGNE UNIQUE.
 *
 * Colonnes typees plutot qu'une table cle/valeur : un stockage stringly-typed
 * obligerait a analyser des chaines a chaque lecture et laisserait passer une
 * valeur invalide jusqu'a l'execution.
 *
 * La ligne existe des la migration qui cree la table : aucun ecran n'a donc a
 * gerer l'absence de reglages, et la lecture ne peut pas rendre `null`.
 */
@Entity(tableName = "settings")
data class SettingsEntity(
    /** Toujours [SINGLE_ROW_ID] : la table ne contient qu'une ligne. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = SINGLE_ROW_ID,

    /** Ordinal de `DifficultyDisplay`. */
    @ColumnInfo(name = "difficulty_display")
    val difficultyDisplay: Int,

    /** Ordinal de `GradeScale`. */
    @ColumnInfo(name = "grade_scale")
    val gradeScale: Int,

    /** Ordinal de `MotionPreference`. */
    @ColumnInfo(name = "motion_preference", defaultValue = "0")
    val motionPreference: Int = 0,
) {
    companion object {
        const val SINGLE_ROW_ID = 0
    }
}
