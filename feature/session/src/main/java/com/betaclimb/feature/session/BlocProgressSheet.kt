package com.betaclimb.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.betaclimb.core.domain.model.Bloc
import com.betaclimb.core.domain.model.BlocStatus
import com.betaclimb.core.ui.theme.BetaColors

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
                text = bloc.grade?.let { "Bloc $it" } ?: "Bloc",
                style = MaterialTheme.typography.titleLarge,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Statut", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BlocStatus.entries.forEach { status ->
                        FilterChip(
                            selected = status == bloc.status,
                            onClick = { onStatusChange(status) },
                            label = { Text(status.shortLabel()) },
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
                Text("Essais", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "${bloc.attempts}",
                    style = MaterialTheme.typography.headlineSmall,
                )
                IconButton(
                    onClick = onAttemptAdded,
                    modifier = Modifier.testTag(AddAttemptTestTag),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter un essai")
                }
            }

            FeelingField(
                initial = bloc.feeling.orEmpty(),
                onCommit = onFeelingChange,
            )

            TextButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Text(
                    text = "Supprimer ce bloc",
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
    var draft by remember(initial) { mutableStateOf(initial) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("Ressenti") },
            placeholder = { Text("La cle du passage, les sensations...") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (draft != initial) {
            AssistChip(
                onClick = { onCommit(draft) },
                label = { Text("Enregistrer le ressenti") },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
    }
}

internal fun BlocStatus.shortLabel(): String = when (this) {
    BlocStatus.PROJECT -> "Projet"
    BlocStatus.WORKING -> "En travail"
    BlocStatus.FLASHED -> "Flash"
    BlocStatus.SENT -> "Reussi"
}

internal fun BlocStatus.color(): Color = when (this) {
    BlocStatus.PROJECT -> BetaColors.StatusProject
    BlocStatus.WORKING -> BetaColors.StatusWorking
    BlocStatus.FLASHED -> BetaColors.StatusFlashed
    BlocStatus.SENT -> BetaColors.StatusSent
}
