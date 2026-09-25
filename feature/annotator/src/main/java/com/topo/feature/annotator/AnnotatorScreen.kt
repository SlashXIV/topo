package com.topo.feature.annotator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.topo.core.domain.model.Limb
import com.topo.core.ui.theme.BetaColors

/**
 * Ecran d'annotation.
 *
 * Il n'est pas encore atteignable par la navigation : celle-ci est assemblee au
 * sprint dedie. Le `blocId` est donc passe en parametre, ce qui a l'avantage de
 * garder l'ecran affichable isolement dans une preview ou un test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotatorScreen(
    blocId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnnotatorViewModel = hiltViewModel(),
) {
    LaunchedEffect(blocId) { viewModel.bind(blocId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val detail = uiState.blocDetail
    val imageFile = uiState.imageFile

    // -----------------------------------------------------------------------
    //  Rejeu
    //
    //  L'etat survit aux changements de mode : basculer vers « poser les prises »
    //  puis revenir ne doit pas rembobiner la sequence.
    // -----------------------------------------------------------------------
    val playback = remember { BetaPlaybackState(movementCount = 0) }
    val movementCount = detail?.movements?.size ?: 0
    playback.movementCount = movementCount

    val scope = rememberCoroutineScope()

    // Porteur remember, et non une simple variable locale : une `var` declaree
    // dans le corps d'un composable est reinitialisee a chaque recomposition, et
    // la reference au job de lecture serait perdue — la pause n'aurait alors plus
    // rien a annuler. Un holder plutot qu'un mutableStateOf : changer de job ne
    // doit rien recomposer, c'est `isPlaying` qui porte l'etat visible.
    val playbackJob = remember { PlaybackJobHolder() }

    // Quitter l'ecran arrete l'animation : une coroutine de lecture laissee
    // vivante continuerait a faire tourner l'horloge de Compose.
    DisposableEffect(Unit) {
        onDispose { playbackJob.current?.cancel() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(detail?.bloc?.grade ?: "Annotation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnnotationToolbar(
                mode = uiState.mode,
                selectedLimb = uiState.selectedLimb,
                onModeChange = viewModel::selectMode,
                onLimbChange = viewModel::selectLimb,
                onUndo = viewModel::undoLastMovement,
                onClear = viewModel::clearBeta,
            )

            if (uiState.mode == AnnotationMode.REPLAY) {
                PlaybackControls(
                    playback = playback,
                    enabled = movementCount > 0,
                    onPlayPause = {
                        if (playback.isPlaying) {
                            playbackJob.current?.cancel()
                            playback.pause()
                        } else {
                            playbackJob.current?.cancel()
                            playbackJob.current = scope.launch { playback.play() }
                        }
                    },
                    onStep = { delta ->
                        playbackJob.current?.cancel()
                        scope.launch { playback.step(delta) }
                    },
                    onRestart = {
                        playbackJob.current?.cancel()
                        scope.launch { playback.reset() }
                    },
                )
            }

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (detail != null && imageFile != null) {
                    AnnotationCanvas(
                        blocDetail = detail,
                        imageFile = imageFile,
                        mode = uiState.mode,
                        selectedLimb = uiState.selectedLimb,
                        onHoldRequested = viewModel::addHoldAt,
                        onTraceCompleted = viewModel::completeTrace,
                        // Hors mode rejeu, le canevas ignore la progression et
                        // trace la sequence entiere : c'est le mode edition.
                        playback = playback.takeIf { uiState.mode == AnnotationMode.REPLAY },
                    )
                } else {
                    Text("Chargement du bloc...", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun AnnotationToolbar(
    mode: AnnotationMode,
    selectedLimb: Limb,
    onModeChange: (AnnotationMode) -> Unit,
    onLimbChange: (Limb) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == AnnotationMode.ADD_HOLD,
                onClick = { onModeChange(AnnotationMode.ADD_HOLD) },
                label = { Text("Poser les prises") },
            )
            FilterChip(
                selected = mode == AnnotationMode.TRACE_BETA,
                onClick = { onModeChange(AnnotationMode.TRACE_BETA) },
                label = { Text("Tracer le beta") },
            )
            FilterChip(
                selected = mode == AnnotationMode.REPLAY,
                onClick = { onModeChange(AnnotationMode.REPLAY) },
                label = { Text("Rejouer") },
            )
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Limb.entries.forEach { limb ->
                FilterChip(
                    selected = limb == selectedLimb,
                    onClick = { onLimbChange(limb) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(limb.swatch()),
                        )
                    },
                    label = { Text(limb.label()) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onUndo) { Text("Annuler le dernier") }
            TextButton(onClick = onClear) { Text("Effacer le beta") }
        }
    }
}

private fun Limb.label(): String = when (this) {
    Limb.LEFT_HAND -> "Main G"
    Limb.RIGHT_HAND -> "Main D"
    Limb.LEFT_FOOT -> "Pied G"
    Limb.RIGHT_FOOT -> "Pied D"
}

private fun Limb.swatch(): Color = when (this) {
    Limb.LEFT_HAND -> BetaColors.LeftHand
    Limb.RIGHT_HAND -> BetaColors.RightHand
    Limb.LEFT_FOOT -> BetaColors.LeftFoot
    Limb.RIGHT_FOOT -> BetaColors.RightFoot
}

/** Reference mutable vers la coroutine de lecture, hors du systeme de snapshots. */
private class PlaybackJobHolder {
    var current: Job? = null
}
