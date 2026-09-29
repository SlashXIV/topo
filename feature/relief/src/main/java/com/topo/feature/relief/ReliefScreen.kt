package com.topo.feature.relief

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topo.core.ui.component.EmptyState

/** Fond de la scene : le noir bleute de la demo du portfolio, sur lequel les halos ressortent. */
private val SceneBackground = Color(0xFF0D0F12)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliefScreen(
    blocId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReliefViewModel = hiltViewModel(),
) {
    LaunchedEffect(blocId) { viewModel.bind(blocId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.relief_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.relief_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Pas de fond sous la scene : une SurfaceView est composee SOUS la
                // fenetre, et tout fond dessine ici la masquerait. Le rendu efface
                // lui-meme avec la meme couleur.
                .then(if (uiState is ReliefUiState.Ready) Modifier else Modifier.background(SceneBackground)),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                ReliefUiState.Loading -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), color = Color.White)
                    Text(stringResource(R.string.relief_loading), color = Color.White.copy(alpha = 0.8f))
                }

                ReliefUiState.Unavailable -> EmptyState(
                    title = stringResource(R.string.relief_unavailable_title),
                    message = stringResource(R.string.relief_unavailable_message),
                )

                ReliefUiState.Failed -> EmptyState(
                    title = stringResource(R.string.relief_failed_title),
                    message = stringResource(R.string.relief_failed_message),
                )

                is ReliefUiState.Ready -> ReliefContent(state)
            }
        }
    }
}

@Composable
private fun ReliefContent(state: ReliefUiState.Ready) {
    val renderer = remember { ReliefRenderer(SceneBackground.toArgb()) }
    // La scene est confiee au rendu a chaque nouvel etat ; le rendu la televerse au
    // fil GL, a la trame suivante.
    LaunchedEffect(state) { renderer.setScene(state.scene, state.texture) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Retenue hors composition : la vue est creee une fois, l'observateur la relit.
        val surface = remember { arrayOfNulls<ReliefSurfaceView>(1) }
        AndroidView(
            factory = { context -> ReliefSurfaceView(context, renderer).also { surface[0] = it } },
            modifier = Modifier.fillMaxSize().testTag(RELIEF_SURFACE_TAG),
        )
        GlPauseWithLifecycle { surface[0] }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = pluralStringResource(R.plurals.relief_hold_count, state.holdCount, state.holdCount),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(if (state.hasBeta) R.string.relief_hint_beta else R.string.relief_hint),
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.hasBeta) {
                FilledTonalButton(onClick = renderer::replay) {
                    Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.relief_replay), modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/**
 * Le fil GL s'arrete avec l'ecran : une surface en rendu continu viderait la batterie
 * en arriere-plan.
 */
@Composable
private fun GlPauseWithLifecycle(surface: () -> ReliefSurfaceView?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> surface()?.onPause()
                Lifecycle.Event.ON_RESUME -> surface()?.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

internal const val RELIEF_SURFACE_TAG = "relief_surface"
