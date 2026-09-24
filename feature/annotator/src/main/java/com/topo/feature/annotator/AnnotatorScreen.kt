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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (detail != null && imageFile != null) {
                    AnnotationCanvas(
                        blocDetail = detail,
                        imageFile = imageFile,
                        mode = uiState.mode,
                        selectedLimb = uiState.selectedLimb,
                        onHoldRequested = viewModel::addHoldAt,
                        onTraceCompleted = viewModel::completeTrace,
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
