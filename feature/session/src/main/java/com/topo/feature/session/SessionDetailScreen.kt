package com.topo.feature.session

import android.net.Uri
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.topo.core.ui.component.Eyebrow
import com.topo.core.ui.component.Rule
import com.topo.core.ui.motion.CountUp
import com.topo.core.ui.motion.riseIn
import kotlin.math.roundToInt
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import com.topo.core.ui.component.IconAction
import com.topo.core.ui.component.PageHeader
import com.topo.core.ui.component.Tag
import com.topo.core.ui.component.TopoButton
import com.topo.core.ui.component.TopoIcons
import com.topo.core.ui.component.TopoSnackbarHost
import com.topo.core.ui.component.TopoSnackbarState
import com.topo.core.ui.motion.rememberHaptics
import com.topo.core.ui.theme.TopoTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.graphics.Color
import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.DifficultyDisplay
import com.topo.core.ui.component.BlocThumbnail
import com.topo.core.ui.component.ConfirmDialog
import com.topo.core.ui.component.EmptyState
import com.topo.core.ui.component.LoadingState

const val BlocGridTestTag = "bloc-grid"
const val ImportBlocTestTag = "import-bloc"
const val CaptureBlocTestTag = "capture-bloc"

@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onCaptureBloc: (sessionId: String) -> Unit,
    onBlocClick: (blocId: String) -> Unit,
    onBlocImported: (blocId: String) -> Unit = onBlocClick,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) { viewModel.bind(sessionId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarState = remember { TopoSnackbarState() }
    var progressBlocId by rememberSaveable { mutableStateOf<String?>(null) }
    var blocToDelete by remember { mutableStateOf<Bloc?>(null) }
    var confirmSessionDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val imageOnlyRequest = remember {
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }

    // PickVisualMedia : selecteur systeme, AUCUNE permission requise. L'application
    // ne recoit que l'image choisie, jamais l'acces a la photothegue entiere.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFromGallery(
                openStream = {
                    // L'autorisation sur cette URI est temporaire : le flux est ouvert
                    // immediatement et son contenu copie dans le stockage prive.
                    requireNotNull(context.contentResolver.openInputStream(uri)) {
                        "Flux introuvable pour $uri"
                    }
                },
                onImported = onBlocImported,
            )
        }
    }

    uiState.importError?.let { failure ->
        val message = stringResource(
            when (failure) {
                ImportFailure.SOURCE_UNREADABLE -> R.string.session_import_error_unreadable
                ImportFailure.NOT_AN_IMAGE -> R.string.session_import_error_not_image
                ImportFailure.INTERNAL -> R.string.session_import_error
            },
        )
        LaunchedEffect(failure) {
            snackbarState.show(message)
            viewModel.acknowledgeImportError()
        }
    }

    Box(modifier.fillMaxSize()) {
      Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = uiState.session?.locationName ?: stringResource(R.string.session_detail_title_fallback),
            eyebrow = uiState.session?.date?.formatAsDate(),
            onBack = onBack,
            backLabel = stringResource(R.string.session_detail_back),
            compact = true,
            actions = {
                // Ici plutot que sur chaque ligne de la liste : une action
                // destructrice n'a pas a etre a portee de pouce en feuilletant.
                IconAction(
                    TopoIcons.Delete,
                    stringResource(R.string.session_card_delete),
                    onClick = { confirmSessionDelete = true },
                    tint = TopoTheme.colors.ink2,
                )
            },
        )
        if (uiState.totalBlocs > 0) {
            SessionRecap(uiState.totalBlocs, uiState.sentCount, uiState.flashCount)
        }
        // Les filtres n'apparaissent qu'a partir du deuxieme bloc : sur une
        // seance qui en contient un seul, ils n'ont rien a filtrer.
        if (uiState.totalBlocs > 1) {
            BlocFilterRow(
                selected = uiState.filter,
                counts = uiState.filterCounts,
                onSelect = viewModel::selectFilter,
            )
        }

        Box(Modifier.weight(1f)) {
            when {
                uiState.isLoading -> LoadingState()

                uiState.totalBlocs == 0 -> EmptyState(
                    title = stringResource(R.string.session_detail_empty_title),
                    message = stringResource(R.string.session_detail_empty_message),
                )

                uiState.blocs.isEmpty() -> EmptyState(
                    title = stringResource(R.string.session_detail_filter_empty_title),
                    message = stringResource(R.string.session_detail_filter_empty_message),
                    actionLabel = stringResource(R.string.session_detail_filter_empty_action),
                    onAction = { viewModel.selectFilter(BlocFilter.ALL) },
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(BlocGridTestTag),
                    // Marge de fin : la derniere fiche ne doit pas buter contre la barre d'action.
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(uiState.blocs, key = { _, bloc -> bloc.id }) { index, bloc ->
                        // Resolution ici plutot que dans la vignette : le
                        // composant partage n'a pas a connaitre les reglages ni
                        // le circuit.
                        val color = bloc.colorId
                            ?.takeIf { uiState.settings.difficultyDisplay == DifficultyDisplay.COLOR }
                            ?.let { id -> uiState.circuit.firstOrNull { it.id == id } }
                        BlocThumbnail(
                            imageFile = viewModel.imageFileOf(bloc),
                            // Le ratio vient des colonnes enregistrees a la
                            // creation : aucune photo n'est decodee pour la grille.
                            aspectRatio = bloc.aspectRatio,
                            grade = bloc.grade,
                            circuitColor = color?.let { Color(it.argb) },
                            circuitName = color?.name,
                            statusLabel = stringResource(bloc.status.labelRes),
                            statusColor = TopoTheme.colors.status(bloc.status),
                            attempts = bloc.attempts,
                            isCompleted = bloc.isCompleted,
                            onClick = { onBlocClick(bloc.id) },
                            onLongClick = { progressBlocId = bloc.id },
                            zone = bloc.detectionZone,
                            holds = uiState.holdsByBloc[bloc.id].orEmpty(),
                            // Les fiches entrent en cascade, comme les lignes de la liste.
                            modifier = Modifier.animateItem().riseIn(index),
                        )
                    }
                }
            }
        }
        // L'import reste a cote de la photo, toujours atteignable : le proposer
        // uniquement dans l'etat vide le rendrait introuvable des le premier bloc.
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TopoButton(
                text = stringResource(R.string.session_detail_capture),
                onClick = { onCaptureBloc(sessionId) },
                icon = TopoIcons.Camera,
                modifier = Modifier.weight(1f).testTag(CaptureBlocTestTag),
            )
            Box(Modifier.border(1.5.dp, TopoTheme.colors.ink, RoundedCornerShape(3.dp))) {
                IconAction(
                    TopoIcons.Gallery,
                    contentDescription = stringResource(R.string.session_detail_import),
                    onClick = { pickImage.launch(imageOnlyRequest) },
                    modifier = Modifier.testTag(ImportBlocTestTag),
                )
            }
        }
      }
      TopoSnackbarHost(snackbarState, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp))
    }

    // La feuille lit le bloc dans l'etat courant plutot que d'en garder une copie :
    // un essai ajoute doit s'afficher immediatement, et une copie capturee a
    // l'ouverture resterait figee.
    progressBlocId?.let { id ->
        val bloc = uiState.blocs.firstOrNull { it.id == id }
        if (bloc == null) {
            progressBlocId = null
        } else {
            BlocProgressSheet(
                bloc = bloc,
                settings = uiState.settings,
                circuit = uiState.circuit,
                onColorChange = { viewModel.setBlocColor(bloc.id, it) },
                onGradeChange = { viewModel.setBlocGrade(bloc.id, it, uiState.settings.gradeScale) },
                statusError = uiState.statusError?.let { statusFailureMessage(it, bloc.attempts) },
                onStatusChange = { viewModel.changeStatus(bloc.id, it) },
                onAttemptAdded = { viewModel.addAttempt(bloc.id) },
                onFeelingChange = { viewModel.updateFeeling(bloc.id, it) },
                onDelete = {
                    blocToDelete = bloc
                    progressBlocId = null
                },
                onDismiss = {
                    progressBlocId = null
                    viewModel.acknowledgeStatusError()
                },
            )
        }
    }

    if (confirmSessionDelete) {
        ConfirmDialog(
            title = stringResource(R.string.session_delete_title),
            // Une seance vide a son propre texte : « contient 0 bloc » n'annoncerait rien.
            message = if (uiState.totalBlocs == 0) {
                stringResource(R.string.session_delete_message_empty)
            } else {
                pluralStringResource(R.plurals.session_delete_message, uiState.totalBlocs, uiState.totalBlocs)
            },
            confirmLabel = stringResource(R.string.session_delete_confirm),
            onConfirm = {
                confirmSessionDelete = false
                scope.launch {
                    viewModel.deleteSession()
                    onBack()
                }
            },
            onDismiss = { confirmSessionDelete = false },
        )
    }

    blocToDelete?.let { bloc ->
        ConfirmDialog(
            title = stringResource(R.string.session_bloc_delete_title),
            message = stringResource(R.string.session_bloc_delete_message),
            confirmLabel = stringResource(R.string.session_delete_confirm),
            onConfirm = {
                viewModel.deleteBloc(bloc.id)
                blocToDelete = null
            },
            onDismiss = { blocToDelete = null },
        )
    }
}

/**
 * Recapitulatif de la seance : trois chiffres entre deux filets, qui comptent
 * jusqu'a leur valeur a l'ouverture. Lu par TalkBack en une phrase par chiffre.
 */
@Composable
private fun SessionRecap(blocs: Int, sent: Int, flashes: Int) {
    val colors = TopoTheme.colors
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)) {
        Rule()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            listOf(
                R.string.session_recap_blocs to blocs,
                R.string.session_recap_sent to sent,
                R.string.session_recap_flashes to flashes,
            ).forEachIndexed { i, (label, value) ->
                if (i > 0) Spacer(Modifier.width(1.dp).fillMaxHeight().background(colors.rule))
                val text = stringResource(label)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .clearAndSetSemantics { contentDescription = "$text, $value" },
                ) {
                    CountUp(value.toDouble(), { "${it.roundToInt()}" }, style = TopoTheme.type.figure.copy(fontSize = TopoTheme.type.figure.fontSize * 0.8f))
                    Eyebrow(text, color = colors.ink2)
                }
            }
        }
        Rule()
    }
}

@Composable
private fun BlocFilterRow(
    selected: BlocFilter,
    counts: Map<BlocFilter, Int>,
    onSelect: (BlocFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BlocFilter.entries.forEach { filter ->
            Tag(
                text = stringResource(
                    R.string.session_filter_with_count,
                    stringResource(filter.labelRes),
                    counts[filter] ?: 0,
                ),
                selected = filter == selected,
                onClick = {
                    if (filter != selected) haptics.tick()
                    onSelect(filter)
                },
            )
        }
    }
}

@get:StringRes
private val BlocFilter.labelRes: Int
    get() = when (this) {
        BlocFilter.ALL -> R.string.session_filter_all
        BlocFilter.PROJECTS -> R.string.session_filter_projects
        BlocFilter.WORKING -> R.string.session_filter_working
        BlocFilter.SENT -> R.string.session_filter_sent
    }

/**
 * Le nombre d'essais vient du bloc affiche et non de l'erreur : c'est celui que
 * l'utilisateur a sous les yeux dans la fiche, donc celui qui justifie le refus.
 */
@Composable
private fun statusFailureMessage(failure: StatusFailure, attempts: Int): String = when (failure) {
    StatusFailure.FlashAfterSeveralAttempts ->
        pluralStringResource(R.plurals.session_status_error_flash, attempts, attempts)

    StatusFailure.BlocNotFound -> stringResource(R.string.session_status_error_missing)
}
