package com.topo.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.topo.core.ui.theme.TopoTheme

/**
 * Etiquette de choix : un cadre d'encre, rempli quand elle est choisie. Remplace
 * les puces Material. La cible tactile fait 48 dp de haut, le cadre dessine 36.
 */
@Composable
fun Tag(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = TopoTheme.colors
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(36.dp)
                .clip(TagShape)
                .then(if (selected) Modifier.background(colors.ink) else Modifier.border(1.dp, colors.ink, TagShape))
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            UppercaseText(text, style = TopoTheme.type.cote, color = if (selected) colors.onInk else colors.ink)
        }
    }
}

/** Choix exclusif entre quelques options, en bandeau d'encre. */
@Composable
fun SegmentedChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TopoTheme.colors
    Row(
        modifier
            .height(IntrinsicSize.Min)
            .clip(TagShape)
            .border(1.2.dp, colors.ink, TagShape)
            .selectableGroup(),
    ) {
        options.forEachIndexed { i, option ->
            val selected = i == selectedIndex
            if (i > 0) Box(Modifier.width(1.2.dp).fillMaxHeight().background(colors.ink))
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .fillMaxHeight()
                    .then(if (selected) Modifier.background(colors.ink) else Modifier)
                    .selectable(selected = selected, onClick = { onSelect(i) }, role = Role.RadioButton)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                TopoText(
                    option,
                    style = TopoTheme.type.label,
                    color = if (selected) colors.onInk else colors.ink,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val TagShape = RoundedCornerShape(2.dp)
