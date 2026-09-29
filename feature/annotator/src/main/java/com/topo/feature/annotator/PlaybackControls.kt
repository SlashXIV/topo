package com.topo.feature.annotator

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onRestart, enabled = enabled) {
            Icon(Icons.Default.Replay, contentDescription = stringResource(R.string.annotator_playback_restart))
        }

        IconButton(onClick = { onStep(-1) }, enabled = enabled) {
            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.annotator_playback_previous))
        }

        IconButton(
            onClick = onPlayPause,
            enabled = enabled,
            modifier = Modifier.testTag(PlayPauseTestTag),
        ) {
            Icon(
                imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (playback.isPlaying) R.string.annotator_playback_pause else R.string.annotator_playback_play,
                ),
            )
        }

        IconButton(
            onClick = { onStep(1) },
            enabled = enabled,
            modifier = Modifier.testTag(StepForwardTestTag),
        ) {
            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.annotator_playback_next))
        }

        TextButton(
            onClick = playback::cycleSpeed,
            enabled = enabled,
            modifier = Modifier.testTag(SpeedTestTag),
        ) {
            Text(stringResource(playback.speed.label()))
        }

        if (!enabled) {
            Text(
                text = stringResource(R.string.annotator_playback_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
