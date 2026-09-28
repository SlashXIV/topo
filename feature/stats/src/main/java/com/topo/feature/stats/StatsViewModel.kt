package com.topo.feature.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topo.core.domain.model.ClimbingStatistics
import com.topo.core.domain.repository.BlocRepository
import com.topo.core.domain.repository.CircuitColorRepository
import com.topo.core.domain.usecase.ComputeStatisticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val statistics: ClimbingStatistics = ClimbingStatistics.Empty,
    val isLoading: Boolean = true,
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    blocRepository: BlocRepository,
    circuitColorRepository: CircuitColorRepository,
    computeStatistics: ComputeStatisticsUseCase,
) : ViewModel() {

    /**
     * Les statistiques se recalculent a chaque mutation de la base, sans que
     * l'ecran ait a se rafraichir : noter une reussite depuis la fiche d'un bloc
     * met la progression a jour immediatement.
     *
     * L'agregation est faite en Kotlin plutot qu'en SQL parce que ses regles sont
     * metier — un flash compte parmi les reussites, les deux echelles ne se
     * melangent pas — et se testent sur JVM en millisecondes.
     */
    val uiState: StateFlow<StatsUiState> = combine(
        blocRepository.observeAllBlocs(),
        circuitColorRepository.observeColors(),
    ) { blocs, circuit ->
        StatsUiState(statistics = computeStatistics(blocs, circuit), isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )
}
