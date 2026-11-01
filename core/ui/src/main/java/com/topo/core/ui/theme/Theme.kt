package com.topo.core.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.text.TextStyle
import com.topo.core.domain.model.MotionPreference
import com.topo.core.ui.motion.LocalMotionLevel
import com.topo.core.ui.motion.rememberMotionLevel
import kotlinx.coroutines.launch

val LocalTopoColors = staticCompositionLocalOf { TopoPalettes.Paper }
val LocalTopoType = staticCompositionLocalOf { TopoType() }

/** Couleur par defaut du texte et des icones sous ce noeud. */
val LocalContentColor = compositionLocalOf { Color.Unspecified }

/** Style par defaut de [com.topo.core.ui.component.TopoText] sous ce noeud. */
val LocalTextStyle = compositionLocalOf { TextStyle.Default }

object TopoTheme {
    val colors: TopoColors
        @Composable @ReadOnlyComposable
        get() = LocalTopoColors.current

    val type: TopoType
        @Composable @ReadOnlyComposable
        get() = LocalTopoType.current
}

/**
 * Theme de Topo, sans Material.
 *
 * Le papier suit le theme du telephone : clair par defaut, ardoise de nuit si
 * le systeme est sombre.
 */
@Composable
fun TopoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motion: MotionPreference = MotionPreference.EXPRESSIVE,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) TopoPalettes.Night else TopoPalettes.Paper
    val type = TopoType()
    CompositionLocalProvider(
        LocalTopoColors provides colors,
        LocalTopoType provides type,
        LocalMotionLevel provides rememberMotionLevel(motion),
        LocalContentColor provides colors.ink,
        LocalTextStyle provides type.body,
        LocalIndication provides InkPressIndication,
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = colors.accentFill,
            backgroundColor = colors.accentFill.copy(alpha = 0.35f),
        ),
        content = content,
    )
}

/**
 * Retour de pression par defaut : un voile d'encre a 8 %, sans ondulation. Les
 * boutons maison y ajoutent leur enfoncement (`pressScale`).
 */
private object InkPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = InkPressNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = javaClass.hashCode()
}

private class InkPressNode(private val source: InteractionSource) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            var presses = 0
            source.interactions.collect {
                when (it) {
                    is PressInteraction.Press -> presses++
                    is PressInteraction.Release, is PressInteraction.Cancel -> presses--
                }
                val now = presses > 0
                if (now != pressed) {
                    pressed = now
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(currentValueOf(LocalTopoColors).ink.copy(alpha = PRESS_ALPHA))
    }
}

private const val PRESS_ALPHA = 0.08f
