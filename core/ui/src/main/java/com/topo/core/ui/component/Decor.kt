package com.topo.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.topo.core.ui.theme.TopoTheme
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak

/** Filet d'encre : ce qui separe les choses, a la place des ombres et des cartes. */
@Composable
fun Rule(modifier: Modifier = Modifier, color: Color = TopoTheme.colors.ink, thickness: Dp = 1.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

/**
 * Deborde de [horizontal] de chaque cote : annule la marge laterale d'une liste
 * pour un element qui doit aller bord a bord, comme l'en-tete de page place dans
 * la liste pour defiler avec elle.
 */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** Fiche : un bord d'encre fin, sans coin arrondi ni ombre. */
@Composable
fun Modifier.fiche(color: Color = TopoTheme.colors.ink): Modifier = border(1.dp, color)

/** Reperes de coupe aux quatre coins, comme sur une page avant massicot. */
fun Modifier.cropMarks(color: Color, length: Dp = 10.dp, stroke: Dp = 1.dp): Modifier = drawWithContent {
    drawContent()
    val l = length.toPx()
    val s = stroke.toPx()
    val w = size.width
    val h = size.height
    listOf(
        Offset(0f, 0f) to Offset(1f, 1f), Offset(w, 0f) to Offset(-1f, 1f),
        Offset(0f, h) to Offset(1f, -1f), Offset(w, h) to Offset(-1f, -1f),
    ).forEach { (corner, dir) ->
        drawLine(color, corner, Offset(corner.x + dir.x * l, corner.y), s)
        drawLine(color, corner, Offset(corner.x, corner.y + dir.y * l), s)
    }
}

/**
 * Hachures a 45°, le rocher des croquis de topo. Remplissent ce qui est tente
 * sans etre acquis, sans ajouter de couleur : lisibles meme pour un daltonien.
 */
fun Modifier.hatched(color: Color, spacing: Dp = 4.dp, stroke: Dp = 1.dp): Modifier = drawBehind {
    val gap = spacing.toPx()
    val s = stroke.toPx()
    clipRect {
        var x = -size.height
        while (x < size.width) {
            drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), s)
            x += gap
        }
    }
}

/** Numero cercle des topos : ordre d'un mouvement, d'une seance, d'une couleur. */
@Composable
fun NumberMark(
    number: Int,
    modifier: Modifier = Modifier,
    color: Color = TopoTheme.colors.ink,
    size: Dp = 28.dp,
) {
    Box(
        modifier.size(size).border(1.3.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        TopoText(number.toString(), style = TopoTheme.type.cote.copy(letterSpacing = TopoTheme.type.label.letterSpacing), color = color)
    }
}

/**
 * Petite ligne en capitales au-dessus d'un titre : cote, date, contexte.
 *
 * Cesure automatique : dans une case de chiffre a 320 dp ou en grande police, un
 * mot trop long se coupait n'importe ou (« REUSSI/S ») ; il se coupe desormais a
 * une syllabe, avec un tiret. Pas de mesure en sous-composition : les cases
 * l'utilisent sous une hauteur intrinseque, que BoxWithConstraints refuse.
 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TopoTheme.colors.accent,
    maxLines: Int = 2,
) {
    UppercaseText(
        text,
        modifier,
        style = TopoTheme.type.cote.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
        color = color,
        maxLines = maxLines,
    )
}

/**
 * Texte affiche en capitales, mais expose tel quel a l'accessibilite : TalkBack
 * epelle parfois une suite de capitales comme un sigle, et les tests cherchent
 * le libelle de `strings.xml`.
 */
@Composable
fun UppercaseText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
) {
    TopoText(
        text.uppercase(),
        modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        style = style,
        color = color,
        maxLines = maxLines,
    )
}
