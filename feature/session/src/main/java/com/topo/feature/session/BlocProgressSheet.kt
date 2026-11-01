package com.topo.feature.session

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.topo.core.domain.model.AppSettings
import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.BlocStatus
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.DifficultyDisplay
import com.topo.core.ui.component.ButtonStyle
import com.topo.core.ui.component.CircuitColorPicker
import com.topo.core.ui.component.Eyebrow
import com.topo.core.ui.component.GradePicker
import com.topo.core.ui.component.IconAction
import com.topo.core.ui.component.StatusStamp
import com.topo.core.ui.component.Tag
import com.topo.core.ui.component.TopoButton
import com.topo.core.ui.component.TopoIcons
import com.topo.core.ui.component.TopoSheet
import com.topo.core.ui.component.TopoText
import com.topo.core.ui.component.TopoTextField
import com.topo.core.ui.component.UppercaseText
import com.topo.core.ui.motion.rememberHaptics
import com.topo.core.ui.theme.TopoTheme

const val ProgressSheetTestTag = "bloc-progress-sheet"
const val AddAttemptTestTag = "add-attempt"

/**
 * Fiche de progression d'un bloc : statut, essais, ressenti, suppression.
 *
 * Presentee en feuille plutot qu'en ecran : elle se remplit entre deux essais,
 * au pied du mur, et ne doit pas faire perdre la grille de vue. Le statut s'y
 * imprime en tampon, qui retombe a chaque changement.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    val colors = TopoTheme.colors
    val haptics = rememberHaptics()
    TopoSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(ProgressSheetTestTag),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                circuit.firstOrNull { it.id == bloc.colorId }?.let { Eyebrow(it.name) }
                UppercaseText(
                    text = bloc.grade
                        ?.let { stringResource(R.string.session_bloc_title_with_grade, it) }
                        ?: stringResource(R.string.session_bloc_title),
                    style = TopoTheme.type.posterSmall,
                )
            }
            StatusStamp(
                text = stringResource(bloc.status.labelRes),
                color = colors.status(bloc.status),
                large = true,
                stampKey = bloc.status,
            )
            Spacer(Modifier.width(8.dp))
        }

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

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Eyebrow(stringResource(R.string.session_bloc_status), color = colors.ink2)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BlocStatus.entries.forEach { status ->
                    Tag(
                        text = stringResource(status.labelRes),
                        selected = status == bloc.status,
                        onClick = {
                            if (status != bloc.status) {
                                // Une reussite se confirme au doigt ; le reste n'est qu'un choix.
                                val success = status == BlocStatus.SENT || status == BlocStatus.FLASHED
                                if (success) haptics.confirm() else haptics.tick()
                            }
                            onStatusChange(status)
                        },
                    )
                }
            }
            // Le refus d'un flash incoherent est affiche ici, au plus pres du
            // bouton qui l'a declenche, plutot que dans un message ephemere.
            if (statusError != null) {
                TopoText(
                    text = statusError,
                    style = TopoTheme.type.bodySmall,
                    color = colors.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TopoText(
                stringResource(R.string.session_bloc_attempts),
                style = TopoTheme.type.label,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier.border(1.3.dp, colors.ink, RoundedCornerShape(3.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopoText(
                    text = "${bloc.attempts}",
                    style = TopoTheme.type.figure.copy(fontSize = TopoTheme.type.posterSmall.fontSize * 0.75f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Box(Modifier.border(1.3.dp, colors.ink, RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))) {
                    IconAction(
                        TopoIcons.Add,
                        contentDescription = stringResource(R.string.session_bloc_add_attempt),
                        onClick = {
                            haptics.tick()
                            onAttemptAdded()
                        },
                        modifier = Modifier.testTag(AddAttemptTestTag),
                    )
                }
            }
        }

        FeelingField(
            initial = bloc.feeling.orEmpty(),
            onCommit = onFeelingChange,
        )

        TopoButton(
            text = stringResource(R.string.session_bloc_delete),
            onClick = onDelete,
            style = ButtonStyle.Text,
            icon = TopoIcons.Delete,
            contentColor = colors.error,
        )
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
        TopoTextField(
            value = draft,
            onValueChange = { draft = it },
            label = stringResource(R.string.session_bloc_feeling),
            supportingText = stringResource(R.string.session_bloc_feeling_placeholder).takeIf { draft.isEmpty() },
            singleLine = false,
        )
        if (draft != initial) {
            TopoButton(
                text = stringResource(R.string.session_bloc_feeling_save),
                onClick = { onCommit(draft) },
                style = ButtonStyle.Line,
                icon = TopoIcons.Check,
            )
        }
    }
}

@get:StringRes
internal val BlocStatus.labelRes: Int
    get() = when (this) {
        BlocStatus.PROJECT -> R.string.session_status_project
        BlocStatus.WORKING -> R.string.session_status_working
        BlocStatus.FLASHED -> R.string.session_status_flashed
        BlocStatus.SENT -> R.string.session_status_sent
    }
