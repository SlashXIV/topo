package com.topo.feature.relief

import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topo.core.common.di.DefaultDispatcher
import com.topo.core.domain.model.Limb
import com.topo.core.domain.repository.BlocImageStore
import com.topo.core.domain.repository.BlocRepository
import com.topo.core.domain.repository.CircuitColorRepository
import com.topo.core.domain.repository.ReliefEstimator
import com.topo.core.ui.theme.BetaColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReliefUiState {
    data object Loading : ReliefUiState

    /** Aucun modele de relief embarque : l'ecran le dit au lieu d'afficher un mur plat. */
    data object Unavailable : ReliefUiState

    data object Failed : ReliefUiState

    /** Pas une data class : une scene et un Bitmap n'ont pas d'egalite structurelle utile. */
    class Ready internal constructor(
        internal val scene: ReliefScene,
        internal val texture: Bitmap,
        val colorName: String?,
    ) : ReliefUiState {
        val holdCount: Int get() = scene.holdCount
        val hasBeta: Boolean get() = !scene.arcs.isEmpty
    }
}

@HiltViewModel
class ReliefViewModel @Inject internal constructor(
    private val blocRepository: BlocRepository,
    private val circuitColorRepository: CircuitColorRepository,
    private val imageStore: BlocImageStore,
    private val reliefEstimator: ReliefEstimator,
    private val textureLoader: ReliefTextureLoader,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReliefUiState>(ReliefUiState.Loading)
    val uiState: StateFlow<ReliefUiState> = _uiState.asStateFlow()

    private var loading: Job? = null
    private var boundBlocId: String? = null

    /** Calcule la scene une fois par bloc : une rotation d'ecran la retrouve telle quelle. */
    fun bind(blocId: String) {
        if (blocId == boundBlocId) return
        boundBlocId = blocId
        loading?.cancel()
        loading = viewModelScope.launch { load(blocId) }
    }

    private suspend fun load(blocId: String) {
        if (!reliefEstimator.isAvailable) {
            _uiState.value = ReliefUiState.Unavailable
            return
        }
        _uiState.value = ReliefUiState.Loading
        _uiState.value = runCatching {
            // Instantane du bloc a l'ouverture : la vue 3D se regarde, elle n'edite rien.
            val detail = blocRepository.observeBlocDetail(blocId).filterNotNull().first()
            val color = detail.bloc.colorId?.let { id -> circuitColorRepository.getColors().firstOrNull { it.id == id } }
            val file = imageStore.resolve(detail.bloc.imagePath)
            val zone = detail.bloc.detectionZone
            val relief = reliefEstimator.estimate(file, zone) ?: return@runCatching ReliefUiState.Unavailable
            val texture = textureLoader.load(file, zone) ?: return@runCatching ReliefUiState.Failed
            val scene = withContext(defaultDispatcher) {
                ReliefGeometry.build(
                    relief = relief,
                    aspect = safeAspect(texture.width, texture.height),
                    zone = zone,
                    holds = detail.holds,
                    movements = detail.movements,
                    // Meme code couleur que l'annotation : le halo prend la couleur de
                    // difficulte, a defaut le blanc neutre des prises.
                    holdArgb = color?.argb ?: BetaColors.Hold.toArgb(),
                    volumeArgb = VOLUME_ARGB,
                    palette = { limb -> limb.color().toArgb() },
                )
            }
            ReliefUiState.Ready(scene, texture, color?.name)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            ReliefUiState.Failed
        }
    }

    private companion object {
        /** Volumes en blanc franc : ils structurent le mur sans appartenir au bloc. */
        const val VOLUME_ARGB = 0xE6FFFFFF.toInt()
    }
}

private fun Limb.color() = when (this) {
    Limb.LEFT_HAND -> BetaColors.LeftHand
    Limb.RIGHT_HAND -> BetaColors.RightHand
    Limb.LEFT_FOOT -> BetaColors.LeftFoot
    Limb.RIGHT_FOOT -> BetaColors.RightFoot
}
