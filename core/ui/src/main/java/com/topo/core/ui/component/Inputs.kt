package com.topo.core.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.topo.core.ui.motion.LocalMotionLevel
import com.topo.core.ui.motion.Motion
import com.topo.core.ui.theme.TopoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Champ de saisie : libelle en cote au-dessus, une ligne d'encre dessous qui
 * passe a l'orange au focus. Le libelle est dans la decoration du champ, donc
 * dans son noeud d'accessibilite : TalkBack l'annonce avec la saisie.
 */
@Composable
fun TopoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = TopoTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val lineColor = when {
        isError -> colors.error
        focused -> colors.accentFill
        else -> colors.ink
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = TopoTheme.type.body.copy(color = colors.ink),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        cursorBrush = SolidColor(colors.accentFill),
        decorationBox = { inner ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TopoText(label, style = TopoTheme.type.cote, color = if (isError) colors.error else colors.ink2)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val w = (if (focused || isError) 2.5.dp else 1.5.dp).toPx()
                            drawLine(lineColor, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                        }
                        .padding(vertical = 10.dp),
                ) { inner() }
                if (supportingText != null) {
                    TopoText(supportingText, style = TopoTheme.type.bodySmall, color = if (isError) colors.error else colors.ink2)
                }
            }
        },
    )
}

/**
 * Attente : un arc d'encre qui tourne. Fixe quand les animations sont coupees,
 * l'arc seul dit encore « en cours ».
 */
@Composable
fun TopoProgress(modifier: Modifier = Modifier, size: Dp = 36.dp) {
    val color = TopoTheme.colors.ink
    val animates = LocalMotionLevel.current.animates
    val angle = if (animates) {
        val spin by rememberInfiniteTransition(label = "attente").animateFloat(
            label = "rotation",
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        )
        spin
    } else {
        0f
    }
    Canvas(modifier.size(size)) {
        val stroke = 2.dp.toPx()
        drawCircle(color.copy(alpha = 0.15f), radius = (this.size.minDimension - stroke) / 2, style = Stroke(stroke))
        drawArc(color, angle, 100f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** Messages brefs en bas d'ecran, un a la fois. Remplace `SnackbarHostState`. */
@Stable
class TopoSnackbarState {
    var message by mutableStateOf<String?>(null)
        private set
    private val mutex = Mutex()

    suspend fun show(text: String) = mutex.withLock {
        message = text
        try {
            delay(SNACKBAR_MS)
        } finally {
            message = null
        }
    }
}

@Composable
fun TopoSnackbarHost(state: TopoSnackbarState, modifier: Modifier = Modifier) {
    val colors = TopoTheme.colors
    val animates = LocalMotionLevel.current.animates
    val shown = state.message
    // Garde le dernier texte pendant la sortie, pour ne pas vider la bande en descendant.
    val last = remember { mutableStateOf("") }
    if (shown != null) last.value = shown
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = shown != null,
            enter = if (animates) slideInVertically(tween(Motion.ENTER_MS, easing = Motion.Enter)) { it } + fadeIn() else fadeIn(tween(0)),
            exit = if (animates) slideOutVertically(tween(Motion.CALM_MS, easing = Motion.Transition)) { it } + fadeOut() else fadeOut(tween(0)),
        ) {
            TopoText(
                last.value,
                style = TopoTheme.type.label,
                color = colors.onInk,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.ink)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

private const val SNACKBAR_MS = 4_000L
