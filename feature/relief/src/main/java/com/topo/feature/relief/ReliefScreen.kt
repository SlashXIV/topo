package com.topo.feature.relief

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import com.topo.core.ui.component.ButtonStyle
import com.topo.core.ui.component.Eyebrow
import com.topo.core.ui.component.PageHeader
import com.topo.core.ui.component.TopoButton
import com.topo.core.ui.component.TopoIcons
import com.topo.core.ui.component.TopoProgress
import com.topo.core.ui.component.TopoText
import com.topo.core.ui.theme.LocalContentColor
import com.topo.core.ui.theme.LocalTopoColors
import com.topo.core.ui.theme.TopoPalettes
import com.topo.core.ui.theme.TopoTheme

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

@Composable
fun ReliefScreen(
    blocId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReliefViewModel = hiltViewModel(),
) {
    LaunchedEffect(blocId) { viewModel.bind(blocId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // La scene est sombre : l'ecran entier passe en ardoise de nuit pour ne pas
    // poser un en-tete papier au-dessus d'une chambre noire.
    CompositionLocalProvider(
        LocalTopoColors provides TopoPalettes.Night,
        LocalContentColor provides TopoPalettes.Night.ink,
    ) {
        Column(modifier.fillMaxSize().background(SceneBackground)) {
            PageHeader(
                title = stringResource(R.string.relief_title),
                onBack = onBack,
                backLabel = stringResource(R.string.relief_back),
                compact = true,
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when (val state = uiState) {
                    ReliefUiState.Loading -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TopoProgress()
                        TopoText(stringResource(R.string.relief_loading), color = TopoTheme.colors.ink2)
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

        // Cartouche du topo : nombre de prises et geste a faire, sur une fiche sombre.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .background(TopoTheme.colors.paper.copy(alpha = 0.92f))
                .border(1.dp, TopoTheme.colors.ink)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Eyebrow(pluralStringResource(R.plurals.relief_hold_count, state.holdCount, state.holdCount))
                TopoText(
                    text = stringResource(if (state.hasBeta) R.string.relief_hint_beta else R.string.relief_hint),
                    color = TopoTheme.colors.ink2,
                    style = TopoTheme.type.bodySmall,
                )
            }
            if (state.hasBeta) {
                TopoButton(
                    text = stringResource(R.string.relief_replay),
                    onClick = renderer::replay,
                    style = ButtonStyle.Line,
                    icon = TopoIcons.Replay,
                )
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
