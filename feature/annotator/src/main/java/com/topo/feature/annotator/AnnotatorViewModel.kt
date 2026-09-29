package com.topo.feature.annotator

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topo.core.domain.model.BlocDetail
import com.topo.core.domain.model.Limb
import com.topo.core.domain.repository.BetaRepository
import com.topo.core.domain.repository.BlocImageStore
import com.topo.core.domain.repository.BlocRepository
import com.topo.core.domain.usecase.AddManualHoldUseCase
import com.topo.core.domain.usecase.BuildMovementSequenceUseCase
import com.topo.core.domain.usecase.DetectHoldsUseCase
import com.topo.core.domain.usecase.KeepHoldColorUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
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

data class AnnotatorUiState(
    val blocDetail: BlocDetail? = null,
    val imageFile: File? = null,
    val mode: AnnotationMode = AnnotationMode.ADD_HOLD,
    val selectedLimb: Limb = Limb.RIGHT_HAND,
    /**
     * Le prochain appui sur une prise en designe la couleur au lieu de la
     * retirer. Etat explicite et visible : le meme geste change de sens.
     */
    val isPickingColor: Boolean = false,
)

@HiltViewModel
class AnnotatorViewModel @Inject constructor(
    private val blocRepository: BlocRepository,
    private val betaRepository: BetaRepository,
    private val imageStore: BlocImageStore,
    private val addManualHold: AddManualHoldUseCase,
    private val buildMovementSequence: BuildMovementSequenceUseCase,
    private val imageExporter: BetaImageExporter,
    private val detectHolds: DetectHoldsUseCase,
    private val keepHoldColor: KeepHoldColorUseCase,
) : ViewModel() {

    private val blocId = MutableStateFlow<String?>(null)

    private val editorState = MutableStateFlow(
        AnnotatorUiState(mode = AnnotationMode.ADD_HOLD, selectedLimb = Limb.RIGHT_HAND),
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val blocDetail = blocId
        .filterNotNull()
        // flatMapLatest : changer de bloc abandonne l'abonnement precedent. Sans
        // cela, deux flux emettraient en concurrence apres une navigation et
        // l'ecran afficherait par intermittence l'ancien bloc.
        .flatMapLatest(blocRepository::observeBlocDetail)

    val uiState: StateFlow<AnnotatorUiState> = combine(
        blocDetail,
        editorState,
    ) { detail, editor ->
        editor.copy(
            blocDetail = detail,
            imageFile = detail?.let { imageStore.resolve(it.bloc.imagePath) },
        )
    }.stateIn(
        scope = viewModelScope,
        // WhileSubscribed(5 s) : l'abonnement survit a une rotation d'ecran, mais
        // s'arrete si l'utilisateur quitte vraiment l'ecran. A zero, chaque
        // changement de configuration relancerait une requete.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AnnotatorUiState(),
    )

    /** Point d'entree tant que la navigation n'est pas cablee (sprint d'assemblage). */
    fun bind(blocId: String) {
        this.blocId.value = blocId
    }

    fun selectMode(mode: AnnotationMode) {
        // Changer de mode abandonne la pipette : revenir aux prises plus tard ne
        // doit pas trouver un appui encore detourne.
        editorState.value = editorState.value.copy(mode = mode, isPickingColor = false)
    }

    fun startColorPick() {
        editorState.value = editorState.value.copy(isPickingColor = true)
    }

    fun cancelColorPick() {
        editorState.value = editorState.value.copy(isPickingColor = false)
    }

    /**
     * Ne garde que les prises de la couleur de [holdId], et quitte la pipette.
     *
     * @return null si le bloc n'est pas encore charge.
     */
    suspend fun keepColorOf(holdId: String): KeepHoldColorUseCase.Result? {
        cancelColorPick()
        val detail = uiState.value.blocDetail ?: return null
        return keepHoldColor(detail, holdId)
    }

    fun selectLimb(limb: Limb) {
        editorState.value = editorState.value.copy(selectedLimb = limb)
    }

    fun addHoldAt(normalizedX: Float, normalizedY: Float) {
        val currentBlocId = blocId.value ?: return
        viewModelScope.launch {
            addManualHold(currentBlocId, normalizedX, normalizedY)
        }
    }

    /**
     * Convertit un trace en mouvements et les ajoute a la sequence existante.
     *
     * La sequence complete est reecrite plutot que des mouvements ajoutes un a un :
     * c'est le contrat de [BetaRepository.replaceMovements], qui renumerote les
     * rangs de facon contigue en une transaction. Des insertions unitaires
     * exigeraient de reconcilier des rangs partiels, avec un index UNIQUE sur
     * `(bloc_id, sequence_order)` prompt a les rejeter.
     */
    fun completeTrace(holdIds: List<String>) {
        val currentBlocId = blocId.value ?: return
        val existing = uiState.value.blocDetail?.movements.orEmpty()
        val limb = uiState.value.selectedLimb

        val appended = buildMovementSequence(
            blocId = currentBlocId,
            limb = limb,
            holdIds = holdIds,
            startOrder = existing.size,
        )
        if (appended.isEmpty()) return

        viewModelScope.launch {
            betaRepository.replaceMovements(currentBlocId, existing + appended)
        }
    }

    /**
     * Retire une prise.
     *
     * Les mouvements qui en dependent sont traites par SQLite : ceux qui y
     * ARRIVENT disparaissent avec elle, ceux qui en PARTENT deviennent des
     * mouvements de depart. Aucun nettoyage manuel ici, donc aucun risque
     * d'oubli lors d'une evolution du schema.
     */
    fun removeHold(holdId: String) {
        viewModelScope.launch { betaRepository.deleteHold(holdId) }
    }

    fun undoLastHold() {
        val last = uiState.value.blocDetail?.holds?.lastOrNull() ?: return
        viewModelScope.launch { betaRepository.deleteHold(last.id) }
    }

    fun clearHolds() {
        val currentBlocId = blocId.value ?: return
        viewModelScope.launch { betaRepository.replaceHolds(currentBlocId, emptyList()) }
    }

    fun undoLastMovement() {
        val currentBlocId = blocId.value ?: return
        val existing = uiState.value.blocDetail?.movements.orEmpty()
        if (existing.isEmpty()) return

        viewModelScope.launch {
            betaRepository.replaceMovements(currentBlocId, existing.dropLast(1))
        }
    }

    /**
     * Ecrit [image] en PNG et renvoie le selecteur de partage systeme.
     *
     * Rien d'autre ne quitte le telephone : l'image sort par le selecteur, vers
     * l'application que l'utilisateur choisit, et aucune donnee de la base ne
     * l'accompagne.
     *
     * @throws java.io.IOException si l'image n'a pas pu etre ecrite.
     */
    suspend fun prepareShare(image: ImageBitmap): Intent =
        imageExporter.shareIntent(imageExporter.export(image))

    /**
     * Propose les prises detectees sur la photo, en plus de celles deja posees.
     *
     * Suspendue et appelee par l'ecran, comme [prepareShare] : le resultat est un
     * message a afficher une fois, pas un etat a conserver.
     *
     * @return null si le bloc n'est pas encore charge.
     * @throws IllegalArgumentException si la photo ou le modele sont illisibles.
     */
    suspend fun detectHolds(): DetectHoldsUseCase.Result? {
        val currentBlocId = blocId.value ?: return null
        val state = uiState.value
        val image = state.imageFile ?: return null
        return detectHolds(currentBlocId, image, state.blocDetail?.holds.orEmpty())
    }

    fun clearBeta() {
        val currentBlocId = blocId.value ?: return
        viewModelScope.launch {
            betaRepository.replaceMovements(currentBlocId, emptyList())
        }
    }
}
