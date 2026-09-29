package com.topo.feature.session

import android.net.Uri
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onCaptureBloc: (sessionId: String) -> Unit,
    onBlocClick: (blocId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) { viewModel.bind(sessionId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var progressBlocId by rememberSaveable { mutableStateOf<String?>(null) }
    var blocToDelete by remember { mutableStateOf<Bloc?>(null) }

    val imageOnlyRequest = remember {
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }

    // PickVisualMedia : selecteur systeme, AUCUNE permission requise. L'application
    // ne recoit que l'image choisie, jamais l'acces a la photothegue entiere.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFromGallery {
                // L'autorisation sur cette URI est temporaire : le flux est ouvert
                // immediatement et son contenu copie dans le stockage prive.
                requireNotNull(context.contentResolver.openInputStream(uri)) {
                    "Flux introuvable pour $uri"
                }
            }
        }
    }

    uiState.importError?.let { failure ->
        val message = failure.detail
            ?.let { stringResource(R.string.session_import_error_detail, it) }
            ?: stringResource(R.string.session_import_error)
        LaunchedEffect(failure) {
            snackbarHostState.showSnackbar(message)
            viewModel.acknowledgeImportError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.session?.locationName ?: stringResource(R.string.session_detail_title_fallback)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.session_detail_back))
                    }
                },
                actions = {
                    // L'import reste atteignable une fois la grille remplie : le
                    // proposer uniquement dans l'etat vide le rendrait introuvable
                    // des le premier bloc ajoute.
                    IconButton(
                        onClick = { pickImage.launch(imageOnlyRequest) },
                        modifier = Modifier.testTag(ImportBlocTestTag),
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = stringResource(R.string.session_detail_import),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCaptureBloc(sessionId) },
                modifier = Modifier.testTag(CaptureBlocTestTag),
                text = { Text(stringResource(R.string.session_detail_capture)) },
                icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Les filtres n'apparaissent qu'a partir du deuxieme bloc : sur une
            // seance qui en contient un seul, ils n'ont rien a filtrer.
            if (uiState.totalBlocs > 1) {
                BlocFilterRow(
                    selected = uiState.filter,
                    onSelect = viewModel::selectFilter,
                )
            }

            when {
                uiState.isLoading -> LoadingState()

                uiState.totalBlocs == 0 -> EmptyState(
                    title = stringResource(R.string.session_detail_empty_title),
                    message = stringResource(R.string.session_detail_empty_message),
                    actionLabel = stringResource(R.string.session_detail_import),
                    onAction = { pickImage.launch(imageOnlyRequest) },
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
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.blocs, key = Bloc::id) { bloc ->
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
                            statusColor = bloc.status.color(),
                            attempts = bloc.attempts,
                            isCompleted = bloc.isCompleted,
                            onClick = { onBlocClick(bloc.id) },
                            onLongClick = { progressBlocId = bloc.id },
                        )
                    }
                }
            }
        }
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

@Composable
private fun BlocFilterRow(
    selected: BlocFilter,
    onSelect: (BlocFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BlocFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes)) },
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

    is StatusFailure.Other -> failure.detail
        ?.let { stringResource(R.string.session_status_error_detail, it) }
        ?: stringResource(R.string.session_status_error)
}
