package com.topo.feature.session

import com.topo.core.ui.component.CircuitColorPicker
import com.topo.core.ui.component.GradePicker
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.AppSettings
import com.topo.core.domain.model.BlocStatus
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.DifficultyDisplay
import com.topo.core.ui.text.labelRes
import com.topo.core.ui.theme.BetaColors

const val ProgressSheetTestTag = "bloc-progress-sheet"
const val AddAttemptTestTag = "add-attempt"

/**
 * Fiche de progression d'un bloc : statut, essais, ressenti, suppression.
 *
 * Presentee en feuille modale plutot qu'en ecran : elle se remplit entre deux
 * essais, au pied du mur, et ne doit pas faire perdre la grille de vue.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocProgressSheet(
    bloc: Bloc,
    settings: AppSettings,
    circuit: List<CircuitColor>,
    onColorChange: (String?) -> Unit,
    onGradeChange: (String?) -> Unit,
    onStatusChange: (BlocStatus) -> Unit,
    onAttemptAdded: () -> Unit,
    onFeelingChange: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    statusError: String? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(ProgressSheetTestTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = bloc.grade
                    ?.let { stringResource(R.string.session_bloc_title_with_grade, it) }
                    ?: stringResource(R.string.session_bloc_title),
                style = MaterialTheme.typography.titleLarge,
            )

            // Difficulte. Les deux systemes sont proposes, celui choisi dans les
            // reglages en premier : la salle etiquette une couleur, le grimpeur
            // estime un niveau, ce sont deux informations distinctes.
            if (settings.difficultyDisplay == DifficultyDisplay.COLOR) {
                CircuitColorPicker(circuit, bloc.colorId, onColorChange)
                GradePicker(settings.gradeScale, bloc.grade, onGradeChange)
            } else {
                GradePicker(settings.gradeScale, bloc.grade, onGradeChange)
                CircuitColorPicker(circuit, bloc.colorId, onColorChange)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.session_bloc_status), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlocStatus.entries.forEach { status ->
                        FilterChip(
                            selected = status == bloc.status,
                            onClick = { onStatusChange(status) },
                            label = { Text(stringResource(status.labelRes)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = status.color().copy(alpha = 0.25f),
                            ),
                        )
                    }
                }
                // Le refus d'un flash incoherent est affiche ici, au plus pres du
                // bouton qui l'a declenche, plutot que dans un message ephemere.
                if (statusError != null) {
                    Text(
                        text = statusError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.session_bloc_attempts), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "${bloc.attempts}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                IconButton(
                    onClick = onAttemptAdded,
                    modifier = Modifier.testTag(AddAttemptTestTag),
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.session_bloc_add_attempt))
                }
            }

            FeelingField(
                initial = bloc.feeling.orEmpty(),
                onCommit = onFeelingChange,
            )

            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text(
                    text = stringResource(R.string.session_bloc_delete),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/**
 * Champ de ressenti.
 *
 * La saisie est locale et n'est propagee qu'a la validation : ecrire en base a
 * chaque frappe declencherait une reemission du Flow des blocs par caractere,
 * donc une recomposition de la grille entiere sous le clavier.
 */
@Composable
private fun FeelingField(
    initial: String,
    onCommit: (String) -> Unit,
) {
    var draft by rememberSaveable(initial) { mutableStateOf(initial) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(R.string.session_bloc_feeling)) },
            placeholder = { Text(stringResource(R.string.session_bloc_feeling_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (draft != initial) {
            AssistChip(
                onClick = { onCommit(draft) },
                label = { Text(stringResource(R.string.session_bloc_feeling_save)) },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

/**
 * Couleurs du circuit. Un second appui sur la couleur deja choisie la retire :
 * sans cela, une couleur posee par erreur serait definitive, exactement le
 * defaut releve sur les prises lors du test appareil.
 */
@OptIn(ExperimentalLayoutApi::class)
@get:StringRes
internal val BlocStatus.labelRes: Int
    get() = when (this) {
        BlocStatus.PROJECT -> R.string.session_status_project
        BlocStatus.WORKING -> R.string.session_status_working
        BlocStatus.FLASHED -> R.string.session_status_flashed
        BlocStatus.SENT -> R.string.session_status_sent
    }

internal fun BlocStatus.color(): Color = when (this) {
    BlocStatus.PROJECT -> BetaColors.StatusProject
    BlocStatus.WORKING -> BetaColors.StatusWorking
    BlocStatus.FLASHED -> BetaColors.StatusFlashed
    BlocStatus.SENT -> BetaColors.StatusSent
}
