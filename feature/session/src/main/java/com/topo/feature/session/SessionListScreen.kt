package com.topo.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topo.core.domain.model.SessionSummary
import com.topo.core.ui.component.ButtonStyle
import com.topo.core.ui.component.ConfirmDialog
import com.topo.core.ui.component.EmptyState
import com.topo.core.ui.component.IconAction
import com.topo.core.ui.component.LoadingState
import com.topo.core.ui.component.NumberMark
import com.topo.core.ui.component.PageHeader
import com.topo.core.ui.component.Rule
import com.topo.core.ui.component.TopoButton
import com.topo.core.ui.component.TopoDialog
import com.topo.core.ui.component.TopoIcons
import com.topo.core.ui.component.TopoText
import com.topo.core.ui.component.TopoTextField
import com.topo.core.ui.component.UppercaseText
import com.topo.core.ui.component.hatched
import com.topo.core.ui.motion.riseIn
import com.topo.core.ui.theme.TopoTheme
import com.topo.core.ui.R as CoreR
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val SessionListTestTag = "session-list"
const val AddSessionTestTag = "add-session"

@Composable
fun SessionListScreen(
    onSessionClick: (sessionId: String) -> Unit,
    onSettingsClick: () -> Unit = {},
    onStatsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SessionListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SessionSummary?>(null) }
    val sessionCount = (uiState as? SessionListUiState.Content)?.sessions?.size ?: 0

    Column(modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.session_list_title),
            eyebrow = pluralStringResource(R.plurals.session_list_eyebrow, sessionCount, sessionCount),
            actions = {
                IconAction(TopoIcons.Chart, stringResource(R.string.session_list_stats), onStatsClick)
                IconAction(TopoIcons.Settings, stringResource(R.string.session_list_settings), onSettingsClick)
            },
        )
        Rule(Modifier.padding(horizontal = 20.dp))
        Box(Modifier.weight(1f)) {
            when (val state = uiState) {
                SessionListUiState.Loading -> LoadingState()

                // Pas de bouton dans l'etat vide : celui du bas de page est toujours la.
                SessionListUiState.Empty -> EmptyState(
                    title = stringResource(R.string.session_list_empty_title),
                    message = stringResource(R.string.session_list_empty_message),
                )

                is SessionListUiState.Content -> LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag(SessionListTestTag),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    // key : sans elle, une suppression ferait reutiliser l'etat de
                    // composition d'une autre ligne a la reemission de la liste.
                    itemsIndexed(state.sessions, key = { _, it -> it.session.id }) { index, summary ->
                        SessionRow(
                            // Numerotee comme une voie de topo : la plus ancienne porte le 1.
                            number = state.sessions.size - index,
                            summary = summary,
                            onClick = { onSessionClick(summary.session.id) },
                            onDelete = { sessionToDelete = summary },
                            modifier = Modifier.riseIn(index),
                        )
                    }
                }
            }
        }
        TopoButton(
            text = stringResource(R.string.session_new),
            onClick = { showCreateDialog = true },
            icon = TopoIcons.Add,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .testTag(AddSessionTestTag),
        )
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
            title = stringResource(R.string.session_delete_title),
            // Une seance vide a son propre texte : « contient 0 bloc » n'annoncerait rien.
            message = if (summary.blocCount == 0) {
                stringResource(R.string.session_delete_message_empty)
            } else {
                pluralStringResource(R.plurals.session_delete_message, summary.blocCount, summary.blocCount)
            },
            confirmLabel = stringResource(R.string.session_delete_confirm),
            onConfirm = {
                viewModel.deleteSession(summary.session.id)
                sessionToDelete = null
            },
            onDismiss = { sessionToDelete = null },
        )
    }
}

/**
 * Une seance comme une entree de topo : son numero cercle, le lieu, la date en
 * cote, et une reglette d'un trait par bloc — plein s'il est reussi, hachure
 * sinon. Les cartes Material ont disparu : un filet separe les seances.
 */
@Composable
private fun SessionRow(
    number: Int,
    summary: SessionSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TopoTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberMark(number, Modifier.clearAndSetSemantics { })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TopoText(summary.session.locationName, style = TopoTheme.type.heading)
                UppercaseText(summary.session.date.formatAsDate(), style = TopoTheme.type.cote, color = colors.ink2)
                TopoText(summary.describeProgress(), style = TopoTheme.type.bodySmall, color = colors.ink2)
                if (summary.blocCount > 0) Tally(summary.completedCount, summary.blocCount)
            }
            IconAction(TopoIcons.Delete, stringResource(R.string.session_card_delete), onDelete, tint = colors.ink2)
        }
        Rule(color = colors.rule)
    }
}

/** Un trait par bloc : plein s'il est reussi, hachure sinon. Plafonne pour tenir sur la ligne. */
@Composable
private fun Tally(completed: Int, total: Int) {
    val colors = TopoTheme.colors
    val shown = total.coerceAtMost(MAX_TALLY)
    Row(
        Modifier.padding(top = 4.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(shown) { i ->
            val mark = Modifier.width(4.dp).height(14.dp)
            Spacer(if (i < completed) mark.background(colors.ink) else mark.hatched(colors.ink2, spacing = 3.dp))
        }
    }
}

@Composable
private fun CreateSessionDialog(
    onConfirm: (locationName: String, notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var location by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }

    TopoDialog(
        title = stringResource(R.string.session_new),
        onDismissRequest = onDismiss,
        actions = {
            TopoButton(stringResource(CoreR.string.action_cancel), onClick = onDismiss, style = ButtonStyle.Text)
            TopoButton(
                text = stringResource(R.string.session_create_confirm),
                onClick = { onConfirm(location, notes) },
                // Un lieu vide est refuse par le domaine : mieux vaut desactiver
                // le bouton que laisser l'utilisateur declencher un echec muet.
                enabled = location.isNotBlank(),
            )
        },
    ) {
        TopoTextField(
            value = location,
            onValueChange = { location = it },
            label = stringResource(R.string.session_create_location),
        )
        TopoTextField(
            value = notes,
            onValueChange = { notes = it },
            label = stringResource(R.string.session_create_notes),
            singleLine = false,
        )
    }
}

@Composable
private fun SessionSummary.describeProgress(): String = when {
    blocCount == 0 -> stringResource(R.string.session_card_no_bloc)
    completedCount == 0 -> pluralStringResource(R.plurals.session_card_bloc_count, blocCount, blocCount)
    else -> pluralStringResource(R.plurals.session_card_sent_count, completedCount, completedCount, blocCount)
}

/** Conversion en date LOCALE : l'instant est stocke en UTC (cf. `Session.date`). */
internal fun Long.formatAsDate(): String = DateTimeFormatter
    .ofPattern("EEE d MMMM yyyy", Locale.FRENCH)
    .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

private const val MAX_TALLY = 30
