package com.topo.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.GradeScale
import com.topo.core.ui.R
import com.topo.core.ui.text.labelRes

// Selecteurs de difficulte d'un bloc : couleur du circuit (celle des fleches de
// depart) et cotation chiffree. Partages par la fiche de progression de la seance
// et par l'etape de difficulte qui suit la photo d'un bloc : deux ecrans, un seul
// endroit ou le choix se dessine.

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CircuitColorPicker(
    circuit: List<CircuitColor>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.difficulty_circuit), style = MaterialTheme.typography.labelLarge)
        if (circuit.isEmpty()) {
            Text(
                text = stringResource(R.string.difficulty_circuit_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            circuit.forEach { color ->
                FilterChip(
                    selected = color.id == selectedId,
                    onClick = { onSelect(color.id.takeIf { it != selectedId }) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(Color(color.argb)),
                        )
                    },
                    label = { Text(color.name) },
                )
            }
        }
    }
}

/**
 * Cotation chiffree, en rangee ordonnee et defilante.
 *
 * Des puces plutot qu'un champ texte : aucune faute de frappe possible — et une
 * cotation mal orthographiee serait un niveau de plus dans les statistiques —
 * tandis que l'echelle devient visible dans son ordre.
 */
@Composable
fun GradePicker(
    gradeScale: GradeScale,
    selectedGrade: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.difficulty_grade, stringResource(gradeScale.labelRes)),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            gradeScale.grades.forEach { grade ->
                FilterChip(
                    selected = grade == selectedGrade,
                    onClick = { onSelect(grade.takeIf { it != selectedGrade }) },
                    label = { Text(grade) },
                )
            }
        }
    }
}
