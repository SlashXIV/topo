package com.betaclimb.feature.camera

import androidx.camera.core.ImageCapture
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betaclimb.core.domain.usecase.SaveCapturedBlocUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Etat de l'ecran de capture, flux unidirectionnel. */
sealed interface CaptureUiState {
    data object Ready : CaptureUiState
    data object Capturing : CaptureUiState
    data class Saved(val blocId: String) : CaptureUiState
    data class Failed(val message: String) : CaptureUiState
}

@HiltViewModel
class CameraCaptureViewModel @Inject constructor(
    private val photoCapture: BlocPhotoCapture,
    private val saveCapturedBloc: SaveCapturedBlocUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CaptureUiState>(CaptureUiState.Ready)
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    /**
     * Le cas d'usage ImageCapture appartient au ViewModel, pas au composable.
     *
     * Ce n'est pas une vue : c'est un objet de configuration que CameraX lie a un
     * cycle de vie. Le conserver ici lui fait survivre a une rotation d'ecran,
     * alors qu'un `remember` le recreerait a chaque changement de configuration —
     * et une recreation en pleine capture perdrait la photo.
     */
    val imageCapture: ImageCapture = photoCapture.newImageCapture()

    /**
     * Capture puis enregistre le bloc.
     *
     * L'etat passe par [CaptureUiState.Capturing] : sans lui, un double appui sur
     * le declencheur lancerait deux captures concurrentes et creerait deux blocs
     * pour la meme photo.
     */
    fun captureBloc(sessionId: String) {
        if (_uiState.value is CaptureUiState.Capturing) return

        _uiState.value = CaptureUiState.Capturing
        viewModelScope.launch {
            val result = runCatching { photoCapture.capture(imageCapture) }
                .mapCatching { file ->
                    saveCapturedBloc(sessionId = sessionId, capturedFile = file).getOrThrow()
                }

            _uiState.value = result.fold(
                onSuccess = { CaptureUiState.Saved(it) },
                onFailure = {
                    CaptureUiState.Failed(it.message ?: "Capture impossible")
                },
            )
        }
    }

    /** A appeler apres consommation d'un etat terminal, pour reautoriser une capture. */
    fun acknowledge() {
        _uiState.value = CaptureUiState.Ready
    }
}
