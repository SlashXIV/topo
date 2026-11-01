package com.topo.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.topo.core.ui.R
import com.topo.core.ui.motion.riseIn
import com.topo.core.ui.theme.TopoTheme

/**
 * Les trois etats qu'un ecran pilote par un Flow doit savoir afficher.
 *
 * Ils vivent dans `:core:ui` parce que tous les ecrans les repetent sinon, et
 * qu'un etat vide bacle est le defaut le plus visible d'une application par
 * ailleurs correcte : c'est le premier ecran que voit un nouvel utilisateur.
 */

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BlankTopo(Modifier.width(180.dp).height(110.dp).riseIn())
            TopoText(title, style = TopoTheme.type.heading, textAlign = TextAlign.Center, modifier = Modifier.riseIn(1))
            TopoText(
                text = message,
                style = TopoTheme.type.bodySmall,
                color = TopoTheme.colors.ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier.riseIn(2),
            )
            if (actionLabel != null && onAction != null) {
                TopoButton(actionLabel, onClick = onAction, modifier = Modifier.padding(top = 8.dp).riseIn(3))
            }
        }
    }
}

/**
 * Croquis d'un topo encore vierge : un rocher hachure et un trace pointille qui
 * attend son premier numero.
 */
@Composable
private fun BlankTopo(modifier: Modifier) {
    val ink = TopoTheme.colors.ink
    val accent = TopoTheme.colors.accentFill
    val paper = TopoTheme.colors.paper
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val rock = Path().apply {
            moveTo(w * 0.02f, h)
            lineTo(w * 0.18f, h * 0.38f)
            lineTo(w * 0.36f, h * 0.48f)
            lineTo(w * 0.52f, h * 0.12f)
            lineTo(w * 0.72f, h * 0.36f)
            lineTo(w * 0.86f, h * 0.26f)
            lineTo(w * 0.98f, h)
            close()
        }
        val stroke = 1.4.dp.toPx()
        clipPath(rock) {
            var x = -h
            val gap = 5.dp.toPx()
            while (x < w) {
                drawLine(ink.copy(alpha = 0.45f), Offset(x, h), Offset(x + h, 0f), 1.dp.toPx())
                x += gap
            }
        }
        drawPath(rock, ink, style = Stroke(stroke, join = StrokeJoin.Round))
        val route = Path().apply {
            moveTo(w * 0.3f, h * 0.92f)
            cubicTo(w * 0.34f, h * 0.7f, w * 0.5f, h * 0.62f, w * 0.52f, h * 0.36f)
        }
        drawPath(
            route, accent,
            style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 6.dp.toPx()))),
        )
        drawCircle(paper, radius = 7.dp.toPx(), center = Offset(w * 0.3f, h * 0.92f))
        drawCircle(ink, radius = 7.dp.toPx(), center = Offset(w * 0.3f, h * 0.92f), style = Stroke(stroke))
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        TopoProgress()
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TopoText(
                text = message,
                style = TopoTheme.type.body,
                color = TopoTheme.colors.error,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                TopoButton(stringResource(R.string.action_retry), onClick = onRetry, style = ButtonStyle.Line)
            }
        }
    }
}
