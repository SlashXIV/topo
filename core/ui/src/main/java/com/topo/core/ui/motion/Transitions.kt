package com.topo.core.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.topo.core.ui.theme.TopoTheme

/**
 * Tourner la page du topo : l'ecran qui arrive se decouvre de gauche a droite
 * derriere un filet orange ; au retour, celui qui part se replie dans l'autre
 * sens. Seul l'ecran du dessus balaie, l'autre reste entier dessous : deux
 * balayages simultanes laisseraient un vide entre eux. Expressif seulement.
 *
 * @param isPop la navigation en cours est un retour.
 */
@Composable
fun Modifier.pageTurn(scope: AnimatedVisibilityScope, isPop: Boolean): Modifier {
    if (!LocalMotionLevel.current.decorative) return this
    val edgeColor = TopoTheme.colors.accentFill
    val transition = scope.transition
    val entering = transition.targetState == EnterExitState.Visible
    val onTop = if (isPop) !entering else entering
    val reveal by transition.animateFloat(
        transitionSpec = { tween(Motion.TRANSITION_MS, easing = Motion.Transition) },
        label = "page",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return drawWithContent {
        if (!onTop || reveal >= 1f) {
            drawContent()
            return@drawWithContent
        }
        val edge = size.width * reveal
        val stroke = 2.dp.toPx()
        clipRect(right = edge) { this@drawWithContent.drawContent() }
        drawRect(edgeColor, Offset(edge - stroke, 0f), Size(stroke, size.height))
    }
}
