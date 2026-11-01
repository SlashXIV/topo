package com.topo.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.GradeScale
import com.topo.core.ui.R
import com.topo.core.ui.text.labelRes
import com.topo.core.ui.theme.TopoTheme

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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Eyebrow(stringResource(R.string.difficulty_circuit), color = TopoTheme.colors.ink2)
        if (circuit.isEmpty()) {
            TopoText(
                text = stringResource(R.string.difficulty_circuit_empty),
                style = TopoTheme.type.bodySmall,
                color = TopoTheme.colors.ink2,
            )
            return@Column
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            circuit.forEach { color ->
                Tag(
                    text = color.name,
                    selected = color.id == selectedId,
                    onClick = { onSelect(color.id.takeIf { it != selectedId }) },
                    leading = { ColorDot(Color(color.argb)) },
                )
            }
        }
    }
}

/**
 * Cotation chiffree, en rangee ordonnee et defilante.
 *
 * Des etiquettes plutot qu'un champ texte : aucune faute de frappe possible — et
 * une cotation mal orthographiee serait un niveau de plus dans les statistiques —
 * tandis que l'echelle devient visible dans son ordre.
 */
@Composable
fun GradePicker(
    gradeScale: GradeScale,
    selectedGrade: String?,
    onSelect: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Eyebrow(
            stringResource(R.string.difficulty_grade, stringResource(gradeScale.labelRes)),
            color = TopoTheme.colors.ink2,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            gradeScale.grades.forEach { grade ->
                Tag(
                    text = grade,
                    selected = grade == selectedGrade,
                    onClick = { onSelect(grade.takeIf { it != selectedGrade }) },
                )
            }
        }
    }
}

/**
 * Pastille de couleur du circuit. Cerclee d'encre : un circuit a souvent du blanc
 * ET du noir, et l'une des deux disparaitrait sinon sur le papier.
 */
@Composable
fun ColorDot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 12.dp) {
    Box(
        modifier
            .size(size)
            .background(color, CircleShape)
            .border(1.dp, TopoTheme.colors.ink.copy(alpha = 0.5f), CircleShape),
    )
}
