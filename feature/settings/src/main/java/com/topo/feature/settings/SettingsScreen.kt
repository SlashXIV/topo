package com.topo.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.DifficultyDisplay
import com.topo.core.domain.model.GradeScale
import com.topo.core.domain.model.MotionPreference
import com.topo.core.domain.model.ThemePreference
import com.topo.core.ui.component.ButtonStyle
import com.topo.core.ui.component.ColorDot
import com.topo.core.ui.component.ConfirmDialog
import com.topo.core.ui.component.IconAction
import com.topo.core.ui.component.LoadingState
import com.topo.core.ui.component.PageHeader
import com.topo.core.ui.component.Rule
import com.topo.core.ui.component.SegmentedChoice
import com.topo.core.ui.component.TopoButton
import com.topo.core.ui.component.TopoDialog
import com.topo.core.ui.component.TopoIcons
import com.topo.core.ui.component.TopoText
import com.topo.core.ui.component.TopoTextField
import com.topo.core.ui.component.Eyebrow
import com.topo.core.ui.motion.rememberHaptics
import com.topo.core.ui.text.labelRes
import com.topo.core.ui.theme.TopoTheme
import com.topo.core.ui.R as CoreR
import androidx.compose.foundation.layout.statusBarsPadding
import com.topo.core.ui.component.bleed

const val CircuitListTestTag = "circuit-list"
const val AddColorTestTag = "add-color"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var colorToDelete by remember { mutableStateOf<CircuitColor?>(null) }
    val haptics = rememberHaptics()

    val header: @Composable () -> Unit = {
        PageHeader(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
            backLabel = stringResource(R.string.settings_back),
        )
    }

    // statusBarsPadding ici consomme l'encart : l'en-tete, qui defile avec les
    // reglages, ne passe jamais sous la barre d'etat.
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        if (uiState.isLoading) {
            header()
            LoadingState()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(CircuitListTestTag),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            // L'en-tete defile avec les reglages : fixe, il prenait le tiers d'un
            // petit ecran.
            item { Box(Modifier.bleed(20.dp)) { header() } }
            item { Rule() }
            item {
                Section(
                    title = stringResource(R.string.settings_difficulty_display_title),
                    hint = stringResource(R.string.settings_difficulty_display_hint),
                ) {
                    SegmentedChoice(
                        options = DifficultyDisplay.entries.map { stringResource(it.labelRes) },
                        selectedIndex = uiState.settings.difficultyDisplay.ordinal,
                        onSelect = {
                            haptics.tick()
                            viewModel.selectDisplay(DifficultyDisplay.entries[it])
                        },
                    )
                }
            }

            item {
                Section(
                    title = stringResource(R.string.settings_grade_scale_title),
                    hint = stringResource(R.string.settings_grade_scale_hint),
                ) {
                    SegmentedChoice(
                        options = GradeScale.entries.map { stringResource(it.labelRes) },
                        selectedIndex = uiState.settings.gradeScale.ordinal,
                        onSelect = {
                            haptics.tick()
                            viewModel.selectScale(GradeScale.entries[it])
                        },
                    )
                }
            }

            item {
                Section(
                    title = stringResource(R.string.settings_theme_title),
                    hint = stringResource(R.string.settings_theme_hint),
                ) {
                    SegmentedChoice(
                        options = ThemePreference.entries.map { stringResource(it.labelRes) },
                        selectedIndex = uiState.settings.theme.ordinal,
                        onSelect = {
                            haptics.tick()
                            viewModel.selectTheme(ThemePreference.entries[it])
                        },
                    )
                }
            }

            item {
                Section(
                    title = stringResource(R.string.settings_motion_title),
                    hint = stringResource(R.string.settings_motion_hint),
                ) {
                    SegmentedChoice(
                        options = MotionPreference.entries.map { stringResource(it.labelRes) },
                        selectedIndex = uiState.settings.motion.ordinal,
                        onSelect = {
                            haptics.tick()
                            viewModel.selectMotion(MotionPreference.entries[it])
                        },
                    )
                }
            }

            item {
                Section(
                    title = stringResource(R.string.settings_circuit_title),
                    // L'ordre porte un sens metier : il ne doit pas se deviner.
                    hint = stringResource(R.string.settings_circuit_hint),
                    divider = false,
                ) {}
            }

            itemsIndexed(uiState.colors, key = { _, it -> it.id }) { index, color ->
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
                    modifier = Modifier.animateItem(),
                )
            }

            item {
                TopoButton(
                    text = stringResource(R.string.settings_add_color),
                    onClick = { showAddDialog = true },
                    style = ButtonStyle.Line,
                    icon = TopoIcons.Add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .navigationBarsPadding()
                        .testTag(AddColorTestTag),
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
            title = stringResource(R.string.settings_delete_color_title, color.name),
            // Portee annoncee : la suppression touche des blocs deja enregistres.
            message = stringResource(R.string.settings_delete_color_message),
            confirmLabel = stringResource(R.string.settings_delete_confirm),
            onConfirm = {
                viewModel.deleteColor(color.id)
                colorToDelete = null
            },
            onDismiss = { colorToDelete = null },
        )
    }
}

private val MotionPreference.labelRes: Int
    get() = when (this) {
        MotionPreference.EXPRESSIVE -> R.string.settings_motion_expressive
        MotionPreference.CALM -> R.string.settings_motion_calm
    }

private val ThemePreference.labelRes: Int
    get() = when (this) {
        ThemePreference.SYSTEM -> R.string.settings_theme_system
        ThemePreference.LIGHT -> R.string.settings_theme_light
        ThemePreference.DARK -> R.string.settings_theme_dark
    }

/** Section de reglages : titre, aide, controle, puis un filet. Pas de carte. */
@Composable
private fun Section(
    title: String,
    hint: String,
    divider: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TopoText(title, style = TopoTheme.type.heading)
        TopoText(hint, style = TopoTheme.type.bodySmall, color = TopoTheme.colors.ink2)
        content()
        if (divider) Rule(Modifier.padding(top = 8.dp), color = TopoTheme.colors.rule)
    }
}

/**
 * Une couleur du circuit, numerotee comme une ligne de topo : sans numero, rien
 * ne dit que la position dans la liste EST le niveau de difficulte.
 */
@Composable
private fun ColorRow(
    position: Int,
    color: CircuitColor,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TopoTheme.colors
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TopoText(
                text = position.toString().padStart(2, '0'),
                style = TopoTheme.type.cote,
                color = colors.ink2,
                modifier = Modifier.width(22.dp),
            )
            ColorDot(Color(color.argb), size = 18.dp)
            TopoText(color.name, Modifier.weight(1f), style = TopoTheme.type.body)
            // Serrees entre elles : leurs cibles de 48 dp les separent deja, et a
            // 320 dp les ecarts ne laissaient que 46 dp au nom (« Roug/e »).
            Row {
                IconAction(TopoIcons.Up, stringResource(R.string.settings_move_easier), onMoveUp, enabled = canMoveUp)
                IconAction(TopoIcons.Down, stringResource(R.string.settings_move_harder), onMoveDown, enabled = canMoveDown)
                IconAction(TopoIcons.Delete, stringResource(R.string.settings_delete_color, color.name), onDelete, tint = colors.ink2)
            }
        }
        Rule(color = colors.rule)
    }
}

@Composable
private fun AddColorDialog(
    onConfirm: (name: String, argb: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var argb by rememberSaveable { mutableIntStateOf(Palette.first().second) }

    TopoDialog(
        title = stringResource(R.string.settings_new_color_title),
        onDismissRequest = onDismiss,
        actions = {
            TopoButton(stringResource(CoreR.string.action_cancel), onClick = onDismiss, style = ButtonStyle.Text)
            TopoButton(
                text = stringResource(R.string.settings_new_color_confirm),
                onClick = { onConfirm(name, argb) },
                enabled = name.isNotBlank(),
            )
        },
    ) {
        TopoTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.settings_new_color_name),
        )
        Eyebrow(stringResource(R.string.settings_new_color_hue), color = TopoTheme.colors.ink2)
        // Palette fermee plutot qu'une roue chromatique : le nom porte
        // l'information, la teinte ne sert qu'a la reconnaitre d'un coup
        // d'oeil sur une vignette.
        PaletteSwatches(selected = argb, onSelect = { argb = it })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteSwatches(selected: Int, onSelect: (Int) -> Unit) {
    val ink = TopoTheme.colors.ink
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Palette.forEach { (label, argb) ->
            val isSelected = argb == selected
            Box(
                Modifier
                    .size(44.dp)
                    // Le nom de la teinte, pour TalkBack : une pastille muette ne se choisit pas a l'oreille.
                    .semantics { contentDescription = label }
                    .selectable(selected = isSelected, onClick = { onSelect(argb) }, role = Role.RadioButton)
                    .then(if (isSelected) Modifier.border(2.dp, ink, CircleShape) else Modifier)
                    .padding(5.dp)
                    .background(Color(argb), CircleShape)
                    .border(1.dp, ink.copy(alpha = 0.4f), CircleShape),
            )
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
