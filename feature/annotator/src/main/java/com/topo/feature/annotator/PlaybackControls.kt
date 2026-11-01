package com.topo.feature.annotator

import com.topo.core.ui.component.ButtonStyle
import com.topo.core.ui.component.IconAction
import com.topo.core.ui.component.TopoButton
import com.topo.core.ui.component.TopoIcons
import com.topo.core.ui.component.TopoText
import com.topo.core.ui.theme.TopoTheme

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

const val PlayPauseTestTag = "playback-play-pause"
const val StepForwardTestTag = "playback-step-forward"
const val SpeedTestTag = "playback-speed"

/**
 * Controles du rejeu : lecture, pas a pas, vitesse, retour au debut.
 *
 * Aucun de ces boutons ne lit la progression de l'animation — seulement
 * [BetaPlaybackState.isPlaying] et [BetaPlaybackState.speed], qui ne changent
 * qu'a l'appui. La progression, elle, n'est lue que dans la phase de dessin du
 * canevas : la faire remonter jusqu'ici recomposerait la barre a chaque trame.
 */
@Composable
fun PlaybackControls(
    playback: BetaPlaybackState,
    onPlayPause: () -> Unit,
    onStep: (Int) -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconAction(TopoIcons.Replay, stringResource(R.string.annotator_playback_restart), onRestart, enabled = enabled)
        IconAction(TopoIcons.SkipPrevious, stringResource(R.string.annotator_playback_previous), { onStep(-1) }, enabled = enabled)
        IconAction(
            icon = if (playback.isPlaying) TopoIcons.Pause else TopoIcons.Play,
            contentDescription = stringResource(
                if (playback.isPlaying) R.string.annotator_playback_pause else R.string.annotator_playback_play,
            ),
            onClick = onPlayPause,
            enabled = enabled,
            modifier = Modifier.testTag(PlayPauseTestTag),
        )
        IconAction(
            TopoIcons.SkipNext,
            stringResource(R.string.annotator_playback_next),
            { onStep(1) },
            enabled = enabled,
            modifier = Modifier.testTag(StepForwardTestTag),
        )
        TopoButton(
            text = stringResource(playback.speed.label()),
            onClick = playback::cycleSpeed,
            style = ButtonStyle.Text,
            enabled = enabled,
            modifier = Modifier.testTag(SpeedTestTag),
        )

        if (!enabled) {
            TopoText(
                text = stringResource(R.string.annotator_playback_empty),
                style = TopoTheme.type.bodySmall,
                color = TopoTheme.colors.ink2,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@StringRes
private fun PlaybackSpeed.label(): Int = when (this) {
    PlaybackSpeed.HALF -> R.string.annotator_speed_half
    PlaybackSpeed.NORMAL -> R.string.annotator_speed_normal
    PlaybackSpeed.DOUBLE -> R.string.annotator_speed_double
}
