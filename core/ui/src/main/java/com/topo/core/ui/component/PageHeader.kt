package com.topo.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.topo.core.ui.motion.RisingText
import com.topo.core.ui.theme.TopoTheme

/**
 * En-tete d'ecran : le titre est imprime dans la page, en affiche, entre des
 * reperes de coupe. Il remplace la barre d'application Material.
 *
 * @param compact titre plus petit, pour un nom propre (lieu, bloc).
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    compact: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = TopoTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp)
            .padding(top = 4.dp, bottom = 14.dp)
            .cropMarks(colors.ink, length = 9.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconAction(TopoIcons.Back, contentDescription = backLabel.orEmpty(), onClick = onBack)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp), content = actions)
        }
        Column(
            Modifier.padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (eyebrow != null) Eyebrow(eyebrow)
            RisingText(
                text = title.uppercase(),
                style = if (compact) TopoTheme.type.posterSmall else TopoTheme.type.poster,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
