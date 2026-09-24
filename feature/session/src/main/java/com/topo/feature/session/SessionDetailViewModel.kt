package com.topo.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.BlocStatus
import com.topo.core.domain.model.Session
import com.topo.core.domain.repository.BlocImageStore
import com.topo.core.domain.repository.BlocRepository
import com.topo.core.domain.repository.SessionRepository
import com.topo.core.domain.usecase.ImportBlocUseCase
import com.topo.core.domain.usecase.RecordAttemptUseCase
import com.topo.core.domain.usecase.SetBlocStatusUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Filtre de la grille de blocs. */
enum class BlocFilter {
    ALL,
    PROJECTS,
    WORKING,
    SENT,
    ;

    fun matches(bloc: Bloc): Boolean = when (this) {
        ALL -> true
        PROJECTS -> bloc.status == BlocStatus.PROJECT
        WORKING -> bloc.status == BlocStatus.WORKING
        SENT -> bloc.status.isSent
    }
}

data class SessionDetailUiState(
    val session: Session? = null,
    /** Blocs apres application du filtre — ce que la grille affiche. */
    val blocs: List<Bloc> = emptyList(),
    /** Nombre total de blocs de la seance, filtre ignore. */
    val totalBlocs: Int = 0,
    val filter: BlocFilter = BlocFilter.ALL,
    val isLoading: Boolean = true,
    val importError: String? = null,
    val statusError: String? = null,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val blocRepository: BlocRepository,
    private val imageStore: BlocImageStore,
    private val importBloc: ImportBlocUseCase,
    private val recordAttempt: RecordAttemptUseCase,
    private val setBlocStatus: SetBlocStatusUseCase,
) : ViewModel() {

    private val sessionId = MutableStateFlow<String?>(null)
    private val filter = MutableStateFlow(BlocFilter.ALL)
    private val importError = MutableStateFlow<String?>(null)
    private val statusError = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SessionDetailUiState> = combine(
        // flatMapLatest : changer de seance abandonne l'abonnement precedent, sinon
        // deux flux emettraient en concurrence et l'ecran alternerait entre les deux.
        sessionId.filterNotNull().flatMapLatest(sessionRepository::observeSession),
        sessionId.filterNotNull().flatMapLatest(blocRepository::observeBlocsForSession),
        filter,
        importError,
        statusError,
    ) { session, blocs, activeFilter, importFailure, statusFailure ->
        SessionDetailUiState(
            session = session,
            // Le filtrage se fait ici et non en SQL : la grille d'une seance tient
            // en quelques dizaines de lignes deja chargees, et une requete par
            // filtre multiplierait les abonnements Room pour rien.
            blocs = blocs.filter(activeFilter::matches),
            totalBlocs = blocs.size,
            filter = activeFilter,
            isLoading = false,
            importError = importFailure,
            statusError = statusFailure,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionDetailUiState(),
    )

    /** Point d'entree tant que la navigation n'est pas cablee au ViewModel. */
    fun bind(sessionId: String) {
        this.sessionId.value = sessionId
    }

    fun selectFilter(filter: BlocFilter) {
        this.filter.value = filter
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

    fun addAttempt(blocId: String) {
        viewModelScope.launch { recordAttempt(blocId) }
    }

    fun changeStatus(blocId: String, status: BlocStatus) {
        viewModelScope.launch {
            statusError.value = null
            setBlocStatus(blocId, status)
                .onFailure { statusError.value = it.message }
        }
    }

    fun updateFeeling(blocId: String, feeling: String) {
        viewModelScope.launch {
            val bloc = blocRepository.getBloc(blocId) ?: return@launch
            blocRepository.updateBloc(
                bloc.copy(feeling = feeling.trim().takeIf(String::isNotEmpty)),
            )
        }
    }

    fun acknowledgeImportError() {
        importError.value = null
    }

    fun acknowledgeStatusError() {
        statusError.value = null
    }

    fun deleteBloc(blocId: String) {
        viewModelScope.launch { blocRepository.deleteBloc(blocId) }
    }
}
