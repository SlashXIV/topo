package com.topo.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topo.core.domain.model.AppSettings
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.DifficultyDisplay
import com.topo.core.domain.model.GradeScale
import com.topo.core.domain.repository.CircuitColorRepository
import com.topo.core.domain.repository.SettingsRepository
import com.topo.core.domain.usecase.ReorderCircuitColorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings.Default,
    val colors: List<CircuitColor> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val circuitColorRepository: CircuitColorRepository,
    private val reorderColor: ReorderCircuitColorUseCase,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.observeSettings(),
        circuitColorRepository.observeColors(),
    ) { settings, colors ->
        SettingsUiState(settings = settings, colors = colors, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun selectDisplay(display: DifficultyDisplay) {
        viewModelScope.launch { settingsRepository.setDifficultyDisplay(display) }
    }

    fun selectScale(scale: GradeScale) {
        viewModelScope.launch { settingsRepository.setGradeScale(scale) }
    }

    fun addColor(name: String, argb: Int) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { circuitColorRepository.addColor(trimmed, argb) }
    }

    /**
     * Deplace une couleur d'un cran.
     *
     * Le calcul passe par le cas d'usage : un index UNIQUE porte sur le rang,
     * donc echanger deux rangs par deux ecritures violerait la contrainte entre
     * les deux. La liste entiere est renumerotee puis reecrite en une
     * transaction.
     */
    fun moveColor(colorId: String, delta: Int) {
        val current = uiState.value.colors
        val reordered = reorderColor(current, colorId, delta)
        if (reordered == current) return

        viewModelScope.launch { circuitColorRepository.replaceColors(reordered) }
    }

    fun deleteColor(colorId: String) {
        viewModelScope.launch { circuitColorRepository.deleteColor(colorId) }
    }
}
