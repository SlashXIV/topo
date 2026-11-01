package com.topo.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.topo.core.ui.motion.LocalMotionLevel
import com.topo.core.ui.motion.Motion
import com.topo.core.ui.theme.TopoTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Dialogue de Topo : une fiche d'encre au centre, titre d'affiche, actions en bas
 * a droite. Construit sur la fenetre de dialogue de Compose UI, qui fournit deja
 * le retour arriere, le focus et le voile.
 */
@Composable
fun TopoDialog(
    title: String,
    onDismissRequest: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        val colors = TopoTheme.colors
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.paper)
                .border(1.5.dp, colors.ink)
                .semantics { paneTitle = title }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UppercaseText(title, style = TopoTheme.type.posterSmall.copy(fontSize = TopoTheme.type.heading.fontSize * 1.6f), maxLines = 3)
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * Feuille montante, sans Material : poignee, glisser vers le bas pour fermer,
 * retour arriere, voile qui ferme au toucher. Elle vit dans sa propre fenetre de
 * dialogue, plein ecran, pour passer au-dessus de tout et suivre le clavier.
 *
 * Fermer anime la sortie puis appelle [onDismissRequest] : l'appelant retire la
 * feuille de la composition quand elle a fini de descendre.
 */
@Composable
fun TopoSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TopoTheme.colors
    val animates = LocalMotionLevel.current.animates
    val scope = rememberCoroutineScope()
    val screenPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val offset = remember { Animatable(if (animates) screenPx else 0f) }
    val sheetHeight = remember { floatArrayOf(screenPx) }
    LaunchedEffect(Unit) {
        offset.animateTo(0f, tween(if (animates) Motion.ENTER_MS else 0, easing = Motion.Enter))
    }
    val close: () -> Unit = {
        scope.launch {
            if (animates) offset.animateTo(sheetHeight[0], tween(Motion.CALM_MS + 100, easing = Motion.Transition))
            onDismissRequest()
        }
    }
    // Le retour arriere arrive ici : la feuille descend avant de disparaitre.
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // La fenetre de dialogue assombrit deja l'arriere-plan, d'un coup : le voile
        // de la feuille le remplace, pour suivre la feuille quand on la glisse.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }

        Box(Modifier.fillMaxSize()) {
            val progress = 1f - (offset.value / sheetHeight[0]).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress }
                    .background(colors.scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
            )
            Column(
                modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.92f)
                    .onSizeChanged { sheetHeight[0] = it.height.toFloat().coerceAtLeast(1f) }
                    .offset { IntOffset(0, offset.value.roundToInt()) }
                    .background(colors.paper)
                    .drawBehind { drawLine(colors.ink, Offset.Zero, Offset(size.width, 0f), 1.5.dp.toPx()) }
                    .semantics { dismiss { close(); true } }
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .draggable(
                        state = rememberDraggableState { delta ->
                            scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
                        },
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (offset.value > sheetHeight[0] * 0.3f || velocity > 1_500f) {
                                close()
                            } else {
                                offset.animateTo(0f, tween(Motion.CALM_MS + 100, easing = Motion.Release))
                            }
                        },
                    ),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
                    Spacer(Modifier.width(36.dp).height(3.dp).background(colors.ink2, RoundedCornerShape(2.dp)))
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}
