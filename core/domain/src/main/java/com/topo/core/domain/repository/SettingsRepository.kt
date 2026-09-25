package com.topo.core.domain.repository

import com.topo.core.domain.model.AppSettings
import com.topo.core.domain.model.DifficultyDisplay
import com.topo.core.domain.model.GradeScale
import kotlinx.coroutines.flow.Flow

/**
 * Reglages de l'application.
 *
 * Lecture en [Flow] comme le reste : changer l'echelle ou le systeme d'affichage
 * doit se repercuter immediatement sur toutes les vignettes deja a l'ecran, sans
 * qu'aucun ecran n'ait a se rafraichir lui-meme.
 */
interface SettingsRepository {

    fun observeSettings(): Flow<AppSettings>

    suspend fun setDifficultyDisplay(display: DifficultyDisplay)

    suspend fun setGradeScale(scale: GradeScale)
}
