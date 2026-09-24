package com.betaclimb.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betaclimb.core.domain.model.SessionSummary
import com.betaclimb.core.ui.component.ConfirmDialog
import com.betaclimb.core.ui.component.EmptyState
import com.betaclimb.core.ui.component.LoadingState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val SessionListTestTag = "session-list"
const val AddSessionTestTag = "add-session"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onSessionClick: (sessionId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SessionSummary?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Mes seances") }) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.testTag(AddSessionTestTag),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nouvelle seance")
            }
        },
    ) { padding ->
        when (val state = uiState) {
            SessionListUiState.Loading -> LoadingState(Modifier.padding(padding))

            SessionListUiState.Empty -> EmptyState(
                title = "Aucune seance",
                message = "Cree une seance pour commencer a enregistrer tes blocs.",
                actionLabel = "Creer une seance",
                onAction = { showCreateDialog = true },
                modifier = Modifier.padding(padding),
            )

            is SessionListUiState.Content -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag(SessionListTestTag),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // key : sans elle, une suppression ferait reutiliser l'etat de
                // composition d'une autre ligne a la reemission de la liste.
                items(state.sessions, key = { it.session.id }) { summary ->
                    SessionCard(
                        summary = summary,
                        onClick = { onSessionClick(summary.session.id) },
                        onDelete = { sessionToDelete = summary },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateSessionDialog(
            onConfirm = { location, notes ->
                viewModel.createSession(location, notes)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    sessionToDelete?.let { summary ->
        ConfirmDialog(
            title = "Supprimer la seance ?",
            message = "Les ${summary.blocCount} bloc(s) de cette seance, leurs prises et " +
                "leurs mouvements seront supprimes. Cette action est irreversible.",
            confirmLabel = "Supprimer",
            onConfirm = {
                viewModel.deleteSession(summary.session.id)
                sessionToDelete = null
            },
            onDismiss = { sessionToDelete = null },
        )
    }
}

@Composable
private fun SessionCard(
    summary: SessionSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.session.locationName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = summary.session.date.formatAsDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = summary.describeProgress(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer la seance")
            }
        }
    }
}

@Composable
private fun CreateSessionDialog(
    onConfirm: (locationName: String, notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var location by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle seance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lieu") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optionnel)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(location, notes) },
                // Un lieu vide est refuse par le domaine : mieux vaut desactiver
                // le bouton que laisser l'utilisateur declencher un echec muet.
                enabled = location.isNotBlank(),
            ) { Text("Creer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

private fun SessionSummary.describeProgress(): String = when {
    blocCount == 0 -> "Aucun bloc"
    completedCount == 0 -> "$blocCount bloc(s)"
    else -> "$completedCount / $blocCount reussi(s)"
}

/** Conversion en date LOCALE : l'instant est stocke en UTC (cf. `Session.date`). */
private fun Long.formatAsDate(): String = DateTimeFormatter
    .ofPattern("d MMMM yyyy", Locale.FRENCH)
    .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
