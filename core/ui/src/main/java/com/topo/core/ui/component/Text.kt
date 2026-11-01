package com.topo.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.topo.core.ui.theme.LocalContentColor
import com.topo.core.ui.theme.LocalTextStyle
import kotlin.math.roundToInt

/** Texte de Topo : [BasicText] avec la couleur et le style du contexte. */
@Composable
fun TopoText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.resolve(color, textAlign),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
    )
}

@Composable
fun TopoText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.resolve(color, textAlign),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
    )
}

@Composable
private fun TextStyle.resolve(color: Color, textAlign: TextAlign): TextStyle = merge(
    color = color.takeOrElse { this.color.takeOrElse { LocalContentColor.current } },
    textAlign = textAlign,
)

/**
 * Texte sur une seule ligne qui se reduit au lieu de passer a la ligne ou de
 * deborder : petits ecrans et grandes polices systeme compris. Sous
 * [minFontSize], il est rogne en dernier recours.
 *
 * Reduction a la mise en page plutot que `TextAutoSize`, qui rogne un mot
 * unique trop large au lieu de le reduire.
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    minFontSize: TextUnit = 10.sp,
) {
    val minScale = if (style.fontSize.isSpecified && style.fontSize.value > 0f) {
        (minFontSize.value / style.fontSize.value).coerceAtMost(1f)
    } else {
        1f
    }
    TopoText(
        text = text,
        modifier = modifier.clipToBounds().shrinkToFitWidth(minScale),
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
    )
}

/** Reduit son contenu (une ligne) a la largeur disponible, sans descendre sous [minScale]. */
fun Modifier.shrinkToFitWidth(minScale: Float = 0f): Modifier = layout { measurable, constraints ->
    // Largeur naturelle sur une ligne : une mesure sans borne renverrait la
    // largeur maximale des qu'un alignement de texte est actif.
    val natural = measurable.maxIntrinsicWidth(Constraints.Infinity)
    val placeable = measurable.measure(Constraints.fixedWidth(natural))
    val maxWidth = constraints.maxWidth
    val scale = if (constraints.hasBoundedWidth && natural > maxWidth) {
        (maxWidth.toFloat() / natural).coerceAtLeast(minScale)
    } else {
        1f
    }
    val width = (natural * scale).roundToInt().coerceIn(constraints.minWidth, maxWidth)
    val height = (placeable.height * scale).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
    layout(width, height) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/** Icone au trait, teintee par le contexte. */
@Composable
fun TopoIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
