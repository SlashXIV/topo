package com.topo.feature.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.AppSettings
import com.topo.core.domain.model.BlocStatus
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.GradeScale
import com.topo.core.domain.model.Session
import com.topo.core.domain.repository.BlocImageStore
import com.topo.core.domain.repository.BlocRepository
import com.topo.core.domain.repository.CircuitColorRepository
import com.topo.core.domain.repository.SettingsRepository
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
import kotlinx.coroutines.flow.map
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

/**
 * Echec d'import depuis la galerie. Le ViewModel ne porte aucun texte d'interface :
 * seul le detail technique transite, l'ecran compose le message.
 */
data class ImportFailure(val detail: String?)

/** Refus d'un changement de statut, traduit en message par l'ecran. */
sealed interface StatusFailure {
    /** Flash declare alors que plusieurs essais sont deja enregistres. */
    data object FlashAfterSeveralAttempts : StatusFailure

    data class Other(val detail: String?) : StatusFailure
}

data class SessionDetailUiState(
    val session: Session? = null,
    /** Blocs apres application du filtre — ce que la grille affiche. */
    val blocs: List<Bloc> = emptyList(),
    /** Nombre total de blocs de la seance, filtre ignore. */
    val totalBlocs: Int = 0,
    val filter: BlocFilter = BlocFilter.ALL,
    val isLoading: Boolean = true,
    val importError: ImportFailure? = null,
    val statusError: StatusFailure? = null,
    val settings: AppSettings = AppSettings.Default,
    val circuit: List<CircuitColor> = emptyList(),
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val blocRepository: BlocRepository,
    private val imageStore: BlocImageStore,
    private val importBloc: ImportBlocUseCase,
    settingsRepository: SettingsRepository,
    circuitColorRepository: CircuitColorRepository,
    private val recordAttempt: RecordAttemptUseCase,
    private val setBlocStatus: SetBlocStatusUseCase,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val sessionId = MutableStateFlow<String?>(null)

    // Dans le SavedStateHandle : le filtre survit a la terminaison du processus,
    // frequente quand on quitte Topo pour la galerie ou l'appareil photo. Par nom,
    // pour qu'un reordonnancement de l'enumeration ne change pas le filtre rendu.
    private val filter = savedState.getStateFlow(FILTER_KEY, BlocFilter.ALL.name)
        .map { name -> BlocFilter.entries.firstOrNull { it.name == name } ?: BlocFilter.ALL }
    private val importError = MutableStateFlow<ImportFailure?>(null)
    private val statusError = MutableStateFlow<StatusFailure?>(null)

    /**
     * Flux regroupes.
     *
     * `combine` n'offre de surcharge TYPEE que jusqu'a cinq flux : au-dela il
     * bascule sur un `Array<Any?>` qui perd tous les types. Plutot que de
     * transtyper a l'aveugle, les flux apparentes sont assembles deux a deux —
     * ce qui donne au passage des noms a des paires qui vont ensemble.
     */
    private val errors = combine(importError, statusError, ::Errors)

    private val difficultyContext = combine(
        settingsRepository.observeSettings(),
        circuitColorRepository.observeColors(),
        ::DifficultyContext,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SessionDetailUiState> = combine(
        // flatMapLatest : changer de seance abandonne l'abonnement precedent, sinon
        // deux flux emettraient en concurrence et l'ecran alternerait entre les deux.
        sessionId.filterNotNull().flatMapLatest(sessionRepository::observeSession),
        sessionId.filterNotNull().flatMapLatest(blocRepository::observeBlocsForSession),
        filter,
        errors,
        difficultyContext,
    ) { session, blocs, activeFilter, failures, difficulty ->
        SessionDetailUiState(
            session = session,
            // Le filtrage se fait ici et non en SQL : la grille d'une seance tient
            // en quelques dizaines de lignes deja chargees, et une requete par
            // filtre multiplierait les abonnements Room pour rien.
            blocs = blocs.filter(activeFilter::matches),
            totalBlocs = blocs.size,
            filter = activeFilter,
            isLoading = false,
            importError = failures.import,
            statusError = failures.status,
            settings = difficulty.settings,
            circuit = difficulty.circuit,
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
        savedState[FILTER_KEY] = filter.name
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
    /** @param onImported recoit l'identifiant du bloc cree, pour l'ouvrir. */
    fun importFromGallery(openStream: () -> InputStream, onImported: (blocId: String) -> Unit) {
        val currentSessionId = sessionId.value ?: return
        viewModelScope.launch {
            importBloc(sessionId = currentSessionId, openStream = openStream)
                .onSuccess(onImported)
                .onFailure { importError.value = ImportFailure(it.message) }
        }
    }

    fun addAttempt(blocId: String) {
        viewModelScope.launch { recordAttempt(blocId) }
    }

    fun changeStatus(blocId: String, status: BlocStatus) {
        viewModelScope.launch {
            statusError.value = null
            setBlocStatus(blocId, status)
                .onFailure { statusError.value = it.toStatusFailure() }
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

    /** Affecte ou retire la couleur de circuit d'un bloc. */
    fun setBlocColor(blocId: String, colorId: String?) {
        viewModelScope.launch {
            val bloc = blocRepository.getBloc(blocId) ?: return@launch
            blocRepository.updateBloc(bloc.copy(colorId = colorId))
        }
    }

    /**
     * Affecte ou retire la cotation chiffree d'un bloc.
     *
     * L'echelle est enregistree avec l'etiquette : « 6A » ne veut rien dire sans
     * savoir a quelle echelle il appartient, et le rang de difficulte ne peut
     * pas se calculer sans elle.
     */
    fun setBlocGrade(blocId: String, grade: String?, scale: GradeScale) {
        viewModelScope.launch {
            val bloc = blocRepository.getBloc(blocId) ?: return@launch
            blocRepository.updateBloc(
                bloc.copy(grade = grade, gradeScale = grade?.let { scale }),
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

/**
 * Le cas d'usage ne refuse un statut qu'a un seul titre, le flash apres plusieurs
 * essais, signale par une IllegalStateException ; tout autre echec (bloc disparu)
 * est remonte tel quel. Son message, redige pour les journaux, n'est jamais
 * affiche comme libelle.
 */
private fun Throwable.toStatusFailure(): StatusFailure =
    if (this is SetBlocStatusUseCase.FlashAfterSeveralAttempts) {
        StatusFailure.FlashAfterSeveralAttempts
    } else {
        StatusFailure.Other(message)
    }

/** Les deux sources d'erreur de l'ecran, regroupees pour tenir dans un `combine` type. */
private data class Errors(val import: ImportFailure?, val status: StatusFailure?)

/** Ce qu'il faut pour afficher et saisir une difficulte. */
private data class DifficultyContext(
    val settings: AppSettings,
    val circuit: List<CircuitColor>,
)

private const val FILTER_KEY = "session.filter"
