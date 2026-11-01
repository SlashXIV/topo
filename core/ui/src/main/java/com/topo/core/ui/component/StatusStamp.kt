package com.topo.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.topo.core.ui.motion.LocalMotionLevel
import com.topo.core.ui.motion.Motion
import com.topo.core.ui.theme.TopoTheme
import kotlinx.coroutines.launch

/**
 * Statut imprime comme un tampon : cadre et capitales de la couleur du statut,
 * legerement de travers.
 *
 * @param large le grand tampon de la fiche d'un bloc, sinon celui des vignettes.
 * @param stampKey quand cette cle change, le tampon retombe (moment « tampon ») ;
 *   null pour un tampon fixe, comme dans une grille qui defile. Le premier
 *   affichage ne tamponne pas.
 */
@Composable
fun StatusStamp(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    stampKey: Any? = null,
) {
    val decorative = LocalMotionLevel.current.decorative
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    // Le tampon tombe quand le statut CHANGE, pas a l'ouverture de l'ecran.
    val initialKey = remember { stampKey }
    LaunchedEffect(stampKey) {
        if (stampKey == null || stampKey == initialKey || !decorative) return@LaunchedEffect
        scale.snapTo(1.9f)
        alpha.snapTo(0f)
        // Le tampon arrive vite et grand, s'ecrase, puis se pose avec un leger rebond.
        launch { alpha.animateTo(1f, tween(Motion.RELEASE_MS / 3)) }
        scale.animateTo(1f, tween(620, easing = Motion.Release))
    }
    val type = if (large) TopoTheme.type.posterSmall else TopoTheme.type.cote
    Box(
        modifier
            .graphicsLayer {
                rotationZ = if (large) -9f else -4f
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
            .border(if (large) 3.dp else 1.2.dp, color, RoundedCornerShape(if (large) 5.dp else 2.dp))
            .padding(horizontal = if (large) 12.dp else 5.dp, vertical = if (large) 4.dp else 2.dp),
    ) {
        UppercaseText(text, style = if (large) type else type.copy(fontSize = type.fontSize * 0.85f), color = color)
    }
}
