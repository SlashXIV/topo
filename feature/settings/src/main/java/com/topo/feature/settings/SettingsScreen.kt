package com.topo.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.DifficultyDisplay
import com.topo.core.domain.model.GradeScale
import com.topo.core.ui.component.ConfirmDialog
import com.topo.core.ui.component.LoadingState

const val CircuitListTestTag = "circuit-list"
const val AddColorTestTag = "add-color"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var colorToDelete by remember { mutableStateOf<CircuitColor?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Reglages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag(CircuitListTestTag),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section(
                    title = "Difficulte affichee",
                    hint = "Ce que la vignette d'un bloc met en avant. Les deux restent " +
                        "enregistrables : la salle etiquette une couleur, toi tu estimes un niveau.",
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DifficultyDisplay.entries.forEach { display ->
                            FilterChip(
                                selected = display == uiState.settings.difficultyDisplay,
                                onClick = { viewModel.selectDisplay(display) },
                                label = { Text(display.label) },
                            )
                        }
                    }
                }
            }

            item {
                Section(
                    title = "Echelle de cotation",
                    hint = "Utilisee partout ou une cotation chiffree est proposee.",
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradeScale.entries.forEach { scale ->
                            FilterChip(
                                selected = scale == uiState.settings.gradeScale,
                                onClick = { viewModel.selectScale(scale) },
                                label = { Text(scale.label) },
                            )
                        }
                    }
                }
            }

            item {
                Section(
                    title = "Circuit de la salle",
                    // L'ordre porte un sens metier : il ne doit pas se deviner.
                    hint = "Du plus facile en haut au plus dur en bas. Chaque salle a son " +
                        "propre ordre : utilise les fleches pour le reproduire.",
                ) {
                    TextButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag(AddColorTestTag),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Ajouter une couleur", Modifier.padding(start = 8.dp))
                    }
                }
            }

            itemsIndexed(uiState.colors) { index, color ->
                ColorRow(
                    position = index + 1,
                    color = color,
                    // Desactivees aux extremites : un bouton actif qui ne produit
                    // rien se lit comme une panne, la lecon des boutons
                    // « annuler » du sprint precedent.
                    canMoveUp = index > 0,
                    canMoveDown = index < uiState.colors.lastIndex,
                    onMoveUp = { viewModel.moveColor(color.id, -1) },
                    onMoveDown = { viewModel.moveColor(color.id, 1) },
                    onDelete = { colorToDelete = color },
                )
            }
        }
    }

    if (showAddDialog) {
        AddColorDialog(
            onConfirm = { name, argb ->
                viewModel.addColor(name, argb)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    colorToDelete?.let { color ->
        ConfirmDialog(
            title = "Supprimer ${color.name} ?",
            // Portee annoncee : la suppression touche des blocs deja enregistres.
            message = "Les blocs de cette couleur la perdront, mais seront conserves.",
            confirmLabel = "Supprimer",
            onConfirm = {
                viewModel.deleteColor(color.id)
                colorToDelete = null
            },
            onDismiss = { colorToDelete = null },
        )
    }
}

@Composable
private fun Section(
    title: String,
    hint: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun ColorRow(
    position: Int,
    color: CircuitColor,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(shape = RoundedCornerShape(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Le rang est affiche : sans numero, rien ne dit que la position
            // dans la liste EST le niveau de difficulte.
            Text(
                text = "$position",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(color.argb)),
            )
            Text(color.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)

            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Rendre plus facile")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Rendre plus difficile")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer ${color.name}")
            }
        }
    }
}

@Composable
private fun AddColorDialog(
    onConfirm: (name: String, argb: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var argb by remember { mutableIntStateOf(Palette.first().second) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle couleur") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Teinte", style = MaterialTheme.typography.labelMedium)
                // Palette fermee plutot qu'une roue chromatique : le nom porte
                // l'information, la teinte ne sert qu'a la reconnaitre d'un coup
                // d'oeil sur une vignette.
                FlowRowPalette(selected = argb, onSelect = { argb = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, argb) }, enabled = name.isNotBlank()) {
                Text("Ajouter")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowPalette(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Palette.forEach { (label, argb) ->
            Surface(
                shape = CircleShape,
                color = Color(argb),
                border = if (argb == selected) {
                    androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                } else {
                    null
                },
                onClick = { onSelect(argb) },
                modifier = Modifier.size(40.dp),
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = label.take(1),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Transparent,
                    )
                }
            }
        }
    }
}

/** Teintes courantes des circuits de salle. */
private val Palette = listOf(
    "Jaune" to 0xFFF2C14E.toInt(),
    "Vert" to 0xFF5FAD56.toInt(),
    "Bleu" to 0xFF2E86AB.toInt(),
    "Rouge" to 0xFFD1495B.toInt(),
    "Violet" to 0xFF8E6C99.toInt(),
    "Orange" to 0xFFF2A65A.toInt(),
    "Rose" to 0xFFE87EA1.toInt(),
    "Blanc" to 0xFFF5F3EF.toInt(),
    "Noir" to 0xFF2B2A28.toInt(),
    "Gris" to 0xFF9AA0A6.toInt(),
)
