package com.topo.core.data.repository

import com.topo.core.data.dao.CircuitColorDao
import com.topo.core.data.mapper.toDomain
import com.topo.core.data.mapper.toEntity
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.repository.CircuitColorRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
internal class CircuitColorRepositoryImpl @Inject constructor(
    private val circuitColorDao: CircuitColorDao,
) : CircuitColorRepository {

    override fun observeColors(): Flow<List<CircuitColor>> =
        circuitColorDao.observeColors().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getColors(): List<CircuitColor> =
        circuitColorDao.getColors().map { it.toDomain() }

    override suspend fun replaceColors(colors: List<CircuitColor>) {
        circuitColorDao.replaceAll(colors.map(CircuitColor::toEntity))
    }

    override suspend fun addColor(name: String, argb: Int): CircuitColor {
        // En fin de liste : une couleur qu'on ajoute est le plus souvent un
        // niveau supplementaire par le haut, et la placer ailleurs supposerait
        // de deviner son niveau.
        val color = CircuitColor(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            argb = argb,
            rank = circuitColorDao.nextRank(),
        )
        circuitColorDao.insert(color.toEntity())
        return color
    }

    override suspend fun deleteColor(colorId: String) {
        circuitColorDao.deleteById(colorId)
        // Renumerotation apres coup : la suppression laisse un trou dans les
        // rangs, et un rang non contigu ferait mentir la position affichee sur
        // la difficulte reelle.
        val remaining = circuitColorDao.getColors()
            .mapIndexed { index, entity -> entity.copy(rank = index) }
        circuitColorDao.replaceAll(remaining)
    }
}
