package com.topo.core.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.topo.core.ui.component.FitText
import com.topo.core.ui.component.TopoText
import com.topo.core.ui.theme.LocalTextStyle
import kotlinx.coroutines.delay

/**
 * Entree « montee » : fondu et glissement de 12 dp vers le haut, decalee selon
 * [index]. Jouee une seule fois, a la premiere composition.
 */
fun Modifier.riseIn(index: Int = 0, baseDelayMs: Int = 0): Modifier = composed {
    val level = LocalMotionLevel.current
    if (!level.animates) return@composed this
    val progress = remember { Animatable(0f) }
    val offsetPx = with(LocalDensity.current) { 12.dp.toPx() }
    LaunchedEffect(Unit) {
        val stagger = if (level.decorative) Motion.ITEM_STAGGER_MS * index.coerceAtMost(Motion.MAX_STAGGERED_ITEMS) else 0
        delay((baseDelayMs + stagger).toLong())
        progress.animateTo(1f, tween(if (level.decorative) Motion.ENTER_MS else Motion.CALM_MS, easing = Motion.Enter))
    }
    graphicsLayer {
        alpha = progress.value
        translationY = if (level.decorative) (1f - progress.value) * offsetPx else 0f
    }
}

/** Pression elastique : 0,97 en appui, retour avec un leger rebond. */
fun Modifier.pressScale(interaction: InteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val decorative = LocalMotionLevel.current.decorative
    val scale by animateFloatAsState(
        targetValue = if (pressed && decorative) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "pression",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Titre d'affiche dont les lettres montent une a une derriere un masque.
 *
 * Il ne passe jamais a la ligne : il se reduit. Un seul noeud pour TalkBack —
 * animees, les lettres sont autant de textes, qui seraient epeles un a un.
 */
@Composable
fun RisingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    startDelayMs: Int = 120,
) {
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = text; heading() }) {
        val measurer = rememberTextMeasurer()
        val maxWidthPx = constraints.maxWidth
        val fitted = remember(text, style, maxWidthPx) {
            // Animees, les lettres sont posees une a une, sans crenage : leur somme
            // depasse la largeur de la phrase, et c'est elle qui doit tenir.
            fun widthOf(part: String) = measurer.measure(part, style, maxLines = 1, softWrap = false).size.width
            val width = maxOf(widthOf(text), text.sumOf { widthOf(it.toString()) })
            if (maxWidthPx == Constraints.Infinity || width <= maxWidthPx) style
            else style.copy(fontSize = style.fontSize * (maxWidthPx.toFloat() / width) * 0.98f)
        }
        if (!LocalMotionLevel.current.decorative) {
            TopoText(text, style = fitted, color = color, maxLines = 1, softWrap = false)
        } else {
            Row(Modifier.clipToBounds()) {
                text.forEachIndexed { i, ch ->
                    val progress = remember(text) { Animatable(0f) }
                    LaunchedEffect(text) {
                        delay((startDelayMs + i * Motion.LETTER_STAGGER_MS).toLong())
                        progress.animateTo(1f, tween(650, easing = Motion.Enter))
                    }
                    TopoText(
                        ch.toString(),
                        style = fitted,
                        color = color,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            alpha = progress.value
                            translationY = (1f - progress.value) * size.height * 0.8f
                        },
                    )
                }
            }
        }
    }
}

/** Nombre qui compte jusqu'a sa valeur ; [format] recoit la valeur courante. */
@Composable
fun CountUp(
    value: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    durationMs: Int = 900,
) {
    val decorative = LocalMotionLevel.current.decorative
    val animated = remember { Animatable(if (decorative) 0f else value.toFloat()) }
    LaunchedEffect(value) {
        if (decorative) animated.animateTo(value.toFloat(), tween(durationMs, easing = Motion.Enter))
        else animated.snapTo(value.toFloat())
    }
    FitText(format(if (decorative) animated.value.toDouble() else value), modifier, style = style, color = color)
}
