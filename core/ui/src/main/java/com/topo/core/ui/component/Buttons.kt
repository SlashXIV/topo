package com.topo.core.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.topo.core.ui.motion.pressScale
import com.topo.core.ui.theme.TopoTheme

enum class ButtonStyle {
    /** Aplat d'encre : l'action principale de l'ecran, une seule. */
    Ink,

    /** Contour d'encre : action secondaire. */
    Line,

    /** Texte seul : annuler, actions tertiaires. */
    Text,
}

/**
 * Bouton de Topo : coins a peine adoucis, libelle d'affiche en capitales,
 * enfoncement elastique a la pression au lieu d'une ondulation.
 */
@Composable
fun TopoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Ink,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentColor: Color = Color.Unspecified,
) {
    val colors = TopoTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val fg = when {
        contentColor != Color.Unspecified -> contentColor
        style == ButtonStyle.Ink -> colors.onInk
        else -> colors.ink
    }
    Row(
        modifier = modifier
            .pressScale(interaction)
            .defaultMinSize(minHeight = 48.dp)
            .clip(Shape)
            .then(
                when (style) {
                    ButtonStyle.Ink -> Modifier.background(colors.ink)
                    ButtonStyle.Line -> Modifier.border(1.5.dp, colors.ink, Shape)
                    ButtonStyle.Text -> Modifier
                },
            )
            .clickable(interaction, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(horizontal = if (style == ButtonStyle.Text) 12.dp else 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) TopoIcon(icon, contentDescription = null, tint = fg, size = 20.dp)
        UppercaseText(text, style = TopoTheme.type.action, color = fg)
    }
}

/** Action en icone seule, avec une cible tactile de 48 dp. */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = TopoTheme.colors.ink,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .pressScale(interaction)
            .clip(RoundedCornerShape(50))
            .clickable(interaction, LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        TopoIcon(icon, contentDescription, tint = tint)
    }
}

private val Shape = RoundedCornerShape(3.dp)
internal const val DISABLED_ALPHA = 0.38f
