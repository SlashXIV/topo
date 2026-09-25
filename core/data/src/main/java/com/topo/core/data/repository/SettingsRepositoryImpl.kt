package com.topo.core.data.repository

import com.topo.core.data.dao.SettingsDao
import com.topo.core.data.mapper.toDomain
import com.topo.core.data.mapper.toEntity
import com.topo.core.domain.model.AppSettings
import com.topo.core.domain.model.DifficultyDisplay
import com.topo.core.domain.model.GradeScale
import com.topo.core.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
internal class SettingsRepositoryImpl @Inject constructor(
    private val settingsDao: SettingsDao,
) : SettingsRepository {

    /**
     * La ligne est creee par la migration, mais le repli sur les valeurs par
     * defaut reste present : une base restauree depuis une sauvegarde partielle
     * ne doit pas laisser l'application sans reglages du tout.
     */
    override fun observeSettings(): Flow<AppSettings> =
        settingsDao.observeSettings().map { it?.toDomain() ?: AppSettings.Default }

    override suspend fun setDifficultyDisplay(display: DifficultyDisplay) {
        update { it.copy(difficultyDisplay = display) }
    }

    override suspend fun setGradeScale(scale: GradeScale) {
        update { it.copy(gradeScale = scale) }
    }

    /**
     * Lecture puis ecriture d'un seul bloc de reglages.
     *
     * Les reglages tiennent en une ligne : la reecrire entierement evite une
     * requete de mise a jour par champ, et garde l'ecriture atomique.
     */
    private suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settingsDao.getSettings()?.toDomain() ?: AppSettings.Default
        settingsDao.upsert(transform(current).toEntity())
    }
}
