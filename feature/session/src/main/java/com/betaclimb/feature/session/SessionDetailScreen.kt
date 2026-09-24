package com.betaclimb.feature.session

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betaclimb.core.domain.model.Bloc
import com.betaclimb.core.ui.component.BlocThumbnail
import com.betaclimb.core.ui.component.ConfirmDialog
import com.betaclimb.core.ui.component.EmptyState
import com.betaclimb.core.ui.component.LoadingState

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
    var blocToDelete by remember { mutableStateOf<Bloc?>(null) }

    // PickVisualMedia : selecteur systeme, AUCUNE permission requise. L'application
    // ne recoit que l'image choisie, jamais l'acces a la photothegue entiere.
    val imageOnlyRequest = remember {
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    }

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

    uiState.importError?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.acknowledgeImportError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.session?.locationName ?: "Seance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
                            contentDescription = "Importer depuis la galerie",
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onCaptureBloc(sessionId) },
                modifier = Modifier.testTag(CaptureBlocTestTag),
                text = { Text("Photographier") },
                icon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState(Modifier.padding(padding))

            uiState.blocs.isEmpty() -> EmptyState(
                title = "Aucun bloc",
                message = "Photographie un bloc ou importe une photo depuis ta galerie.",
                actionLabel = "Importer depuis la galerie",
                onAction = { pickImage.launch(imageOnlyRequest) },
                modifier = Modifier.padding(padding),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag(BlocGridTestTag),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.blocs, key = Bloc::id) { bloc ->
                    BlocThumbnail(
                        imageFile = viewModel.imageFileOf(bloc),
                        // Le ratio vient des colonnes enregistrees a la creation :
                        // aucune photo n'est decodee pour construire la grille.
                        aspectRatio = bloc.aspectRatio,
                        grade = bloc.grade,
                        isCompleted = bloc.isCompleted,
                        onClick = { onBlocClick(bloc.id) },
                        onLongClick = { blocToDelete = bloc },
                    )
                }
            }
        }
    }

    blocToDelete?.let { bloc ->
        ConfirmDialog(
            title = "Supprimer le bloc ?",
            message = "Ses prises, ses mouvements et sa photo seront supprimes.",
            confirmLabel = "Supprimer",
            onConfirm = {
                viewModel.deleteBloc(bloc.id)
                blocToDelete = null
            },
            onDismiss = { blocToDelete = null },
        )
    }
}
