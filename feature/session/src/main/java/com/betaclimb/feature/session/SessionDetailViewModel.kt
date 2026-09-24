package com.betaclimb.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.betaclimb.core.domain.model.Bloc
import com.betaclimb.core.domain.model.Session
import com.betaclimb.core.domain.repository.BlocImageStore
import com.betaclimb.core.domain.repository.BlocRepository
import com.betaclimb.core.domain.repository.SessionRepository
import com.betaclimb.core.domain.usecase.ImportBlocUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionDetailUiState(
    val session: Session? = null,
    val blocs: List<Bloc> = emptyList(),
    val isLoading: Boolean = true,
    val importError: String? = null,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val blocRepository: BlocRepository,
    private val imageStore: BlocImageStore,
    private val importBloc: ImportBlocUseCase,
) : ViewModel() {

    private val sessionId = MutableStateFlow<String?>(null)
    private val importError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SessionDetailUiState> = combine(
        // flatMapLatest : changer de seance abandonne l'abonnement precedent, sinon
        // deux flux emettraient en concurrence et l'ecran alternerait entre les deux.
        sessionId.filterNotNull().flatMapLatest(sessionRepository::observeSession),
        sessionId.filterNotNull().flatMapLatest(blocRepository::observeBlocsForSession),
        importError,
    ) { session, blocs, error ->
        SessionDetailUiState(
            session = session,
            blocs = blocs,
            isLoading = false,
            importError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionDetailUiState(),
    )

    val currentSessionId: StateFlow<String?> = sessionId.asStateFlow()

    fun bind(sessionId: String) {
        this.sessionId.value = sessionId
    }

    /** Fichier image d'un bloc, pour l'affichage de la vignette. */
    fun imageFileOf(bloc: Bloc): File = imageStore.resolve(bloc.imagePath)

    /**
     * Importe une image choisie dans la galerie.
     *
     * [openStream] est fourni par l'ecran, qui seul a acces au ContentResolver : le
     * ViewModel n'a donc besoin d'aucun Context, et le cas d'usage reste testable
     * sur JVM.
     */
    fun importFromGallery(openStream: () -> InputStream) {
        val currentSessionId = sessionId.value ?: return
        viewModelScope.launch {
            importBloc(sessionId = currentSessionId, openStream = openStream)
                .onFailure { importError.value = "Import impossible : ${it.message}" }
        }
    }

    fun acknowledgeImportError() {
        importError.value = null
    }

    fun deleteBloc(blocId: String) {
        viewModelScope.launch { blocRepository.deleteBloc(blocId) }
    }
}
