package com.topo.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
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
                if (summary.blocCount > 0) Tally(summary.sentColors, summary.completedCount, summary.blocCount)
            }
        }
        Rule(color = colors.rule)
    }
}

/**
 * Un trait par bloc : plein dans la couleur du circuit s'il est reussi, a l'encre
 * sans couleur, hachure sinon. Un filet d'encre garde le blanc lisible sur papier.
 * Plafonne pour tenir sur la ligne.
 */
@Composable
private fun Tally(sentColors: List<Int?>, completed: Int, total: Int) {
    val colors = TopoTheme.colors
    val shown = total.coerceAtMost(MAX_TALLY)
    Row(
        Modifier.padding(top = 4.dp).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(shown) { i ->
            // 6 dp : sous le filet d'encre, il reste 4 dp de couleur, de quoi la reconnaitre.
            val mark = Modifier.width(6.dp).height(14.dp)
            Spacer(
                if (i < completed) {
                    val fill = sentColors.getOrNull(i)?.let(::Color) ?: colors.ink
                    mark.background(fill).border(1.dp, colors.ink)
                } else {
                    mark.hatched(colors.ink2, spacing = 3.dp)
                },
            )
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

/** Toujours « reussis / blocs », pour que les lignes se comparent d'un coup d'oeil. */
@Composable
private fun SessionSummary.describeProgress(): String =
    if (blocCount == 0) {
        stringResource(R.string.session_card_no_bloc)
    } else {
        pluralStringResource(R.plurals.session_card_sent_count, completedCount, completedCount, blocCount)
    }

/** Conversion en date LOCALE : l'instant est stocke en UTC (cf. `Session.date`). */
internal fun Long.formatAsDate(): String = DateTimeFormatter
    .ofPattern("EEE d MMMM yyyy", Locale.FRENCH)
    .format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

private const val MAX_TALLY = 30
