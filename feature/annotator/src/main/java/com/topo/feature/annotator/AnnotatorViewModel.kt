package com.topo.feature.annotator

import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.topo.core.domain.model.BlocDetail
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.GradeScale
import com.topo.core.domain.model.Limb
import com.topo.core.domain.repository.BetaRepository
import com.topo.core.domain.repository.BlocImageStore
import com.topo.core.domain.repository.BlocRepository
import com.topo.core.domain.repository.CircuitColorRepository
import com.topo.core.domain.repository.SettingsRepository
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
    /**
     * Couleur de difficulte du bloc — celle de ses fleches de depart, saisie comme
     * couleur du circuit. Null si le bloc n'en porte pas.
     */
    val circuitColor: CircuitColor? = null,
    /** Le circuit de la salle, pour l'etape de difficulte. */
    val circuit: List<CircuitColor> = emptyList(),
    val gradeScale: GradeScale = GradeScale.FRENCH,
    val mode: AnnotationMode = AnnotationMode.ADD_HOLD,
    val selectedLimb: Limb = Limb.RIGHT_HAND,
    /**
     * Le prochain appui sur une prise en designe la couleur au lieu de la
     * retirer. Etat explicite et visible : le meme geste change de sens.
     */
    val isPickingColor: Boolean = false,
    /**
     * Zone du bloc tracee par l'utilisateur ; la detection ne regarde qu'elle.
     * Lue sur le bloc en base : elle recadre aussi sa vignette dans la seance.
     */
    val detectionZone: BoundingBox? = null,
    /** Le prochain glissement trace la zone. Etat explicite, comme la pipette. */
    val isDrawingZone: Boolean = false,
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
    private val savedState: SavedStateHandle,
    circuitColorRepository: CircuitColorRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val blocId = MutableStateFlow<String?>(null)

    private val editorState = MutableStateFlow(EditorStateSaver.restore(savedState))

    /** Toute modification de l'editeur passe ici : elle est sauvee dans la foulee. */
    private fun edit(transform: AnnotatorUiState.() -> AnnotatorUiState) {
        editorState.value = editorState.value.transform()
        EditorStateSaver.save(editorState.value, savedState)
    }

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
        circuitColorRepository.observeColors(),
        settingsRepository.observeSettings(),
    ) { detail, editor, colors, settings ->
        editor.copy(
            blocDetail = detail,
            imageFile = detail?.let { imageStore.resolve(it.bloc.imagePath) },
            circuitColor = detail?.bloc?.colorId?.let { id -> colors.firstOrNull { it.id == id } },
            circuit = colors,
            gradeScale = settings.gradeScale,
            detectionZone = detail?.bloc?.detectionZone,
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
        edit { copy(mode = mode, isPickingColor = false, isDrawingZone = false) }
    }

    fun startZoneDrawing() {
        edit { copy(isDrawingZone = true, isPickingColor = false) }
    }

    fun cancelZoneDrawing() {
        edit { copy(isDrawingZone = false) }
    }

    fun setDetectionZone(zone: BoundingBox) {
        edit { copy(isDrawingZone = false) }
        saveZone(zone)
    }

    fun clearDetectionZone() = saveZone(null)

    /** Le cadre vit avec le bloc : il recadre sa vignette, et revient a chaque ouverture. */
    private fun saveZone(zone: BoundingBox?) {
        val currentBlocId = blocId.value ?: return
        viewModelScope.launch {
            val bloc = blocRepository.getBloc(currentBlocId) ?: return@launch
            blocRepository.updateBloc(bloc.copy(detectionZone = zone))
        }
    }

    fun startColorPick() {
        edit { copy(isPickingColor = true) }
    }

    fun cancelColorPick() {
        edit { copy(isPickingColor = false) }
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

    /** Couleur du circuit du bloc — celle de ses fleches ; null la retire. */
    fun setBlocColor(colorId: String?) {
        val currentBlocId = blocId.value ?: return
        viewModelScope.launch {
            val bloc = blocRepository.getBloc(currentBlocId) ?: return@launch
            blocRepository.updateBloc(bloc.copy(colorId = colorId))
        }
    }

    /**
     * Cotation estimee du bloc. L'echelle est enregistree avec elle : « 6A » ne
     * veut rien dire sans elle, et le rang de difficulte non plus.
     */
    fun setBlocGrade(grade: String?) {
        val currentBlocId = blocId.value ?: return
        val scale = uiState.value.gradeScale
        viewModelScope.launch {
            val bloc = blocRepository.getBloc(currentBlocId) ?: return@launch
            blocRepository.updateBloc(bloc.copy(grade = grade, gradeScale = grade?.let { scale }))
        }
    }

    fun selectLimb(limb: Limb) {
        edit { copy(selectedLimb = limb) }
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
        return detectHolds(
            currentBlocId,
            image,
            state.blocDetail?.holds.orEmpty(),
            zone = state.detectionZone,
        )
    }

    fun clearBeta() {
        val currentBlocId = blocId.value ?: return
        viewModelScope.launch {
            betaRepository.replaceMovements(currentBlocId, emptyList())
        }
    }
}
