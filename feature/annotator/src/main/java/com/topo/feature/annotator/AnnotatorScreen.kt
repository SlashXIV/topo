package com.topo.feature.annotator

import androidx.compose.runtime.saveable.rememberSaveable
import com.topo.core.domain.model.CircuitColor
import com.topo.core.domain.model.GradeScale
import com.topo.core.ui.component.CircuitColorPicker
import com.topo.core.ui.component.GradePicker
import com.topo.core.ui.R as CoreR
import android.content.res.Resources
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.topo.core.ui.component.ButtonStyle
import com.topo.core.ui.component.ColorDot
import com.topo.core.ui.component.IconAction
import com.topo.core.ui.component.Rule
import com.topo.core.ui.component.Tag
import com.topo.core.ui.component.TopoButton
import com.topo.core.ui.component.TopoIcons
import com.topo.core.ui.component.TopoSheet
import com.topo.core.ui.component.TopoSnackbarHost
import com.topo.core.ui.component.TopoSnackbarState
import com.topo.core.ui.component.TopoText
import com.topo.core.ui.component.UppercaseText
import com.topo.core.ui.motion.rememberHaptics
import com.topo.core.ui.theme.LocalContentColor
import com.topo.core.ui.theme.LocalTopoColors
import com.topo.core.ui.theme.TopoPalettes
import com.topo.core.ui.theme.TopoTheme
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.topo.core.domain.usecase.DetectHoldsUseCase
import com.topo.core.domain.usecase.KeepHoldColorUseCase
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.topo.core.domain.model.Limb
import com.topo.core.ui.theme.BetaColors

/**
 * Ecran d'annotation.
 *
 * La photo occupe tout l'ecran entre une barre sombre et le pupitre papier du
 * bas. Le `blocId` est passe en parametre, ce qui garde l'ecran affichable
 * isolement dans une preview ou un test.
 */
@Composable
fun AnnotatorScreen(
    blocId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    askDifficulty: Boolean = false,
    onOpenRelief: (blocId: String) -> Unit = {},
    viewModel: AnnotatorViewModel = hiltViewModel(),
) {
    LaunchedEffect(blocId) { viewModel.bind(blocId) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Etape de difficulte, pour un bloc tout juste photographie : la couleur des
    // fleches se lit sur la photo avant meme d'avoir essaye, et c'est elle qui
    // colore le halo des prises a detecter ensuite. Statut et essais, eux,
    // n'existent qu'apres un essai : ils restent dans la fiche de la seance.
    // Saveable : une rotation ou un retour ne la rouvre pas une fois fermee.
    var showDifficultyStep by rememberSaveable { mutableStateOf(askDifficulty) }
    uiState.blocDetail?.bloc?.let { bloc ->
        if (showDifficultyStep) {
            DifficultyStepSheet(
                circuit = uiState.circuit,
                selectedColorId = bloc.colorId,
                gradeScale = uiState.gradeScale,
                selectedGrade = bloc.grade,
                onColorChange = viewModel::setBlocColor,
                onGradeChange = viewModel::setBlocGrade,
                onDone = { showDifficultyStep = false },
            )
        }
    }

    val detail = uiState.blocDetail
    val imageFile = uiState.imageFile

    // -----------------------------------------------------------------------
    //  Rejeu
    //
    //  L'etat survit aux changements de mode : basculer vers « poser les prises »
    //  puis revenir ne doit pas rembobiner la sequence.
    // -----------------------------------------------------------------------
    val playback = remember { BetaPlaybackState(movementCount = 0) }
    val detectionScan = remember { DetectionScanState() }
    val movementCount = detail?.movements?.size ?: 0
    playback.movementCount = movementCount

    val scope = rememberCoroutineScope()

    // Porteur remember, et non une simple variable locale : une `var` declaree
    // dans le corps d'un composable est reinitialisee a chaque recomposition, et
    // la reference au job de lecture serait perdue — la pause n'aurait alors plus
    // rien a annuler. Un holder plutot qu'un mutableStateOf : changer de job ne
    // doit rien recomposer, c'est `isPlaying` qui porte l'etat visible.
    val playbackJob = remember { PlaybackJobHolder() }

    // Quitter l'ecran arrete l'animation : une coroutine de lecture laissee
    // vivante continuerait a faire tourner l'horloge de Compose.
    DisposableEffect(Unit) {
        onDispose { playbackJob.current?.cancel() }
    }

    // ------------------------------------------------------------------------
    //  Partage
    //
    //  L'image partagee est CE QUE L'ECRAN MONTRE, y compris en rejeu : une
    //  sequence arretee a mi-parcours se partage telle quelle, pour montrer « le
    //  mouvement qui coince ». En edition, c'est le beta entier.
    // ------------------------------------------------------------------------
    val exportLayer = rememberGraphicsLayer()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarState = remember { TopoSnackbarState() }
    var isExporting by remember { mutableStateOf(false) }
    var isDetecting by remember { mutableStateOf(false) }

    val share: () -> Unit = {
        scope.launch {
            // Le calque n'a pas encore ete dessine : rien a exporter.
            if (exportLayer.size == IntSize.Zero) return@launch
            isExporting = true
            try {
                val chooser = viewModel.prepareShare(exportLayer.toImageBitmap())
                context.startActivity(chooser)
            } catch (error: IOException) {
                snackbarState.show(resources.getString(R.string.annotator_share_failed))
            } finally {
                isExporting = false
            }
        }
    }

    Box(modifier.fillMaxSize().background(PhotoBackground)) {
        Column(Modifier.fillMaxSize()) {
            // Barre de tete sombre, au-dessus de la photo : la photo garde toute la
            // place, et l'ardoise ne se bat pas avec elle.
            CompositionLocalProvider(
                LocalTopoColors provides TopoPalettes.Night,
                LocalContentColor provides TopoPalettes.Night.ink,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconAction(TopoIcons.Back, stringResource(R.string.annotator_back), onBack)
                    UppercaseText(
                        blocTitle(uiState.circuitColor?.name, detail?.bloc?.grade),
                        style = TopoTheme.type.action.copy(fontSize = TopoTheme.type.heading.fontSize * 1.15f),
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    // Masque sans modele de relief : un bouton qui mene a « indisponible »
                    // n'a rien a faire la.
                    if (uiState.isReliefAvailable) {
                        IconAction(
                            TopoIcons.Relief,
                            stringResource(R.string.annotator_open_relief),
                            onClick = { onOpenRelief(blocId) },
                            enabled = detail != null,
                        )
                    }
                    IconAction(
                        TopoIcons.Share,
                        stringResource(R.string.annotator_share_beta),
                        onClick = share,
                        // Desactive pendant l'encodage : un second appui lancerait un
                        // export concurrent qui purgerait le fichier du premier.
                        enabled = detail != null && imageFile != null && !isExporting,
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (detail != null && imageFile != null) {
                    AnnotationCanvas(
                        blocDetail = detail,
                        imageFile = imageFile,
                        mode = uiState.mode,
                        selectedLimb = uiState.selectedLimb,
                        // Pendant la pipette, un appui sur le mur ne pose rien : le
                        // doigt vise une prise, un appui a cote serait un rate.
                        onHoldRequested = if (uiState.isPickingColor || uiState.isDrawingZone) {
                            { _, _ -> }
                        } else {
                            viewModel::addHoldAt
                        },
                        onHoldRemoved = { holdId ->
                            if (uiState.isDrawingZone) {
                                // Le doigt cadre : un appui ne retire rien.
                            } else if (uiState.isPickingColor) {
                                scope.launch {
                                    viewModel.keepColorOf(holdId)?.message(resources)?.let {
                                        snackbarState.show(it)
                                    }
                                }
                            } else {
                                viewModel.removeHold(holdId)
                            }
                        },
                        onTraceCompleted = viewModel::completeTrace,
                        // Hors mode rejeu, le canevas ignore la progression et
                        // trace la sequence entiere : c'est le mode edition.
                        playback = playback.takeIf { uiState.mode == AnnotationMode.REPLAY },
                        exportLayer = exportLayer,
                        detectionZone = uiState.detectionZone,
                        highlight = uiState.circuitColor?.let { Color(it.argb) },
                        scan = detectionScan,
                        isDrawingZone = uiState.isDrawingZone,
                        onZoneDrawn = viewModel::setDetectionZone,
                    )
                } else {
                    TopoText(
                        stringResource(R.string.annotator_loading_bloc),
                        style = TopoTheme.type.bodySmall,
                        color = TopoPalettes.Night.ink2,
                    )
                }
            }

            // Le pupitre en bas, a portee de pouce : modes, membres, actions.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(TopoTheme.colors.paper)
                    .navigationBarsPadding(),
            ) {
                Rule()
                if (uiState.mode == AnnotationMode.REPLAY) {
                    PlaybackControls(
                        playback = playback,
                        enabled = movementCount > 0,
                        onPlayPause = {
                            if (playback.isPlaying) {
                                playbackJob.current?.cancel()
                                playback.pause()
                            } else {
                                playbackJob.current?.cancel()
                                playbackJob.current = scope.launch { playback.play() }
                            }
                        },
                        onStep = { delta ->
                            playbackJob.current?.cancel()
                            scope.launch { playback.step(delta) }
                        },
                        onRestart = {
                            playbackJob.current?.cancel()
                            scope.launch { playback.reset() }
                        },
                    )
                }
                AnnotationToolbar(
                    mode = uiState.mode,
                    isPickingColor = uiState.isPickingColor,
                    onPickColor = viewModel::startColorPick,
                    onCancelPick = viewModel::cancelColorPick,
                    isDrawingZone = uiState.isDrawingZone,
                    hasZone = uiState.detectionZone != null,
                    onDrawZone = viewModel::startZoneDrawing,
                    onCancelZone = viewModel::cancelZoneDrawing,
                    onClearZone = viewModel::clearDetectionZone,
                    selectedLimb = uiState.selectedLimb,
                    holdCount = detail?.holds?.size ?: 0,
                    movementCount = movementCount,
                    onModeChange = viewModel::selectMode,
                    onLimbChange = viewModel::selectLimb,
                    // Les actions destructrices portent sur ce que l'utilisateur est
                    // en train de faire. Auparavant elles visaient toujours les
                    // mouvements : en mode « poser les prises », appuyer sur
                    // « annuler » ne produisait donc rien du tout, sans le dire.
                    onUndo = when (uiState.mode) {
                        AnnotationMode.ADD_HOLD -> viewModel::undoLastHold
                        else -> viewModel::undoLastMovement
                    },
                    onClear = when (uiState.mode) {
                        AnnotationMode.ADD_HOLD -> viewModel::clearHolds
                        else -> viewModel::clearBeta
                    },
                    isDetecting = isDetecting,
                    onDetect = {
                        scope.launch {
                            isDetecting = true
                            var failed = false
                            val result = try {
                                viewModel.detectHolds()
                            } catch (error: IllegalArgumentException) {
                                failed = true
                                null
                            } catch (error: IllegalStateException) {
                                failed = true
                                null
                            } finally {
                                isDetecting = false
                            }
                            // Le scan se joue pendant que le message s'affiche : il revele,
                            // prise par prise, ce que le message annonce.
                            val added = (result as? DetectHoldsUseCase.Result.Added)?.addedIds.orEmpty()
                            if (added.isNotEmpty()) launch { detectionScan.play(added, uiState.detectionZone) }
                            val message = if (failed) {
                                resources.getString(R.string.annotator_detect_failed)
                            } else {
                                result?.message(resources)
                            }
                            message?.let { snackbarState.show(it) }
                        }
                    },
                )
            }
        }
        TopoSnackbarHost(snackbarState, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 48.dp))
    }
}

/** Fond sous la photo : l'ardoise de nuit, quel que soit le theme. */
private val PhotoBackground = Color(0xFF15171A)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnnotationToolbar(
    mode: AnnotationMode,
    isPickingColor: Boolean,
    onPickColor: () -> Unit,
    onCancelPick: () -> Unit,
    isDrawingZone: Boolean,
    hasZone: Boolean,
    onDrawZone: () -> Unit,
    onCancelZone: () -> Unit,
    onClearZone: () -> Unit,
    selectedLimb: Limb,
    holdCount: Int,
    movementCount: Int,
    onModeChange: (AnnotationMode) -> Unit,
    onLimbChange: (Limb) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    isDetecting: Boolean,
    onDetect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Onglets soulignes, de largeur egale : les trois tiennent sur un 394 dp
        // (Xiaomi 15T Pro), ou les puces Material debordaient et rendaient le
        // dernier mode inatteignable.
        ModeTabs(
            selected = mode,
            onSelect = {
                if (it != mode) haptics.tick()
                onModeChange(it)
            },
        )

        // Mode d'emploi du mode courant.
        //
        // Ajoute apres un premier essai sur appareil : l'utilisateur a choisi un
        // membre puis APPUYE sur une prise, alors que le trace se fait en faisant
        // glisser le doigt. Rien ne le disait, et le geste attendu n'a aucune
        // affordance visuelle — c'est precisement ce qu'une ligne de texte resout
        // mieux qu'une icone.
        TopoText(
            text = stringResource(
                when {
                    isDrawingZone -> R.string.annotator_hint_draw_zone
                    isPickingColor -> R.string.annotator_hint_pick_color
                    else -> mode.hint()
                },
            ),
            style = TopoTheme.type.bodySmall,
            color = TopoTheme.colors.ink2,
        )

        // La palette de membres ne sert qu'au trace : l'afficher ailleurs occupe
        // de la hauteur au detriment de la photo, sans rien piloter.
        if (mode == AnnotationMode.TRACE_BETA) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Limb.entries.forEach { limb ->
                    Tag(
                        text = stringResource(limb.label()),
                        selected = limb == selectedLimb,
                        onClick = {
                            if (limb != selectedLimb) haptics.tick()
                            onLimbChange(limb)
                        },
                        leading = { ColorDot(limb.swatch()) },
                    )
                }
            }
        }

        if (mode != AnnotationMode.REPLAY) {
            // Desactives quand il n'y a rien a annuler. Un bouton actif qui ne
            // produit rien laisse croire a une panne : c'est exactement ce qui
            // s'est passe en mode trace, sans aucun mouvement enregistre.
            val hasSomethingToUndo = when (mode) {
                AnnotationMode.ADD_HOLD -> holdCount > 0
                else -> movementCount > 0
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // La detection PROPOSE des prises : elle vit donc avec la pose
                // manuelle, et se corrige avec le meme geste — un appui retire.
                if (mode == AnnotationMode.ADD_HOLD && isPickingColor) {
                    TopoButton(stringResource(CoreR.string.action_cancel), onCancelPick, style = ButtonStyle.Line)
                } else if (mode == AnnotationMode.ADD_HOLD && isDrawingZone) {
                    TopoButton(stringResource(CoreR.string.action_cancel), onCancelZone, style = ButtonStyle.Line)
                } else if (mode == AnnotationMode.ADD_HOLD) {
                    // Le cadrage d'abord : il decide de ce que la detection regardera.
                    if (hasZone) {
                        TopoButton(stringResource(R.string.annotator_clear_zone), onClearZone, style = ButtonStyle.Line)
                    } else {
                        TopoButton(stringResource(R.string.annotator_draw_zone), onDrawZone, style = ButtonStyle.Line)
                    }
                    TopoButton(
                        stringResource(if (isDetecting) R.string.annotator_detecting else R.string.annotator_detect_holds),
                        onDetect,
                        enabled = !isDetecting,
                    )
                    TopoButton(
                        stringResource(R.string.annotator_keep_color),
                        onPickColor,
                        style = ButtonStyle.Line,
                        enabled = holdCount > 0,
                    )
                }
                TopoButton(
                    stringResource(mode.undoLabel()),
                    onUndo,
                    style = ButtonStyle.Text,
                    icon = TopoIcons.Undo,
                    enabled = hasSomethingToUndo,
                )
                TopoButton(
                    stringResource(mode.clearLabel()),
                    onClear,
                    style = ButtonStyle.Text,
                    icon = TopoIcons.Delete,
                    enabled = hasSomethingToUndo,
                )
            }
        }
    }
}

/** Onglets des modes : capitales d'affiche, soulignees d'orange quand choisies. */
@Composable
private fun ModeTabs(selected: AnnotationMode, onSelect: (AnnotationMode) -> Unit) {
    val colors = TopoTheme.colors
    Row(Modifier.fillMaxWidth().selectableGroup()) {
        AnnotationMode.entries.forEach { candidate ->
            val isSelected = candidate == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = isSelected, onClick = { onSelect(candidate) }, role = Role.Tab)
                    .drawBehind {
                        val w = (if (isSelected) 3.dp else 1.dp).toPx()
                        drawLine(
                            if (isSelected) colors.accentFill else colors.rule,
                            Offset(0f, size.height - w / 2),
                            Offset(size.width, size.height - w / 2),
                            w,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                UppercaseText(
                    stringResource(candidate.label()),
                    style = TopoTheme.type.action.copy(fontSize = TopoTheme.type.label.fontSize * 1.1f),
                    color = if (isSelected) colors.ink else colors.ink2,
                )
            }
        }
    }
}

@Composable
private fun DifficultyStepSheet(
    circuit: List<CircuitColor>,
    selectedColorId: String?,
    gradeScale: GradeScale,
    selectedGrade: String?,
    onColorChange: (String?) -> Unit,
    onGradeChange: (String?) -> Unit,
    onDone: () -> Unit,
) {
    TopoSheet(onDismissRequest = onDone) {
        UppercaseText(stringResource(R.string.annotator_difficulty_title), style = TopoTheme.type.posterSmall, maxLines = 2)
        TopoText(
            stringResource(R.string.annotator_difficulty_hint),
            style = TopoTheme.type.body,
            color = TopoTheme.colors.ink2,
        )
        CircuitColorPicker(circuit, selectedColorId, onColorChange)
        GradePicker(gradeScale, selectedGrade, onGradeChange)
        // « Passer » tant que rien n'est choisi : l'etape est facultative, et un
        // bouton « Continuer » sans choix laisserait croire qu'il manque quelque
        // chose.
        TopoButton(
            text = stringResource(
                if (selectedColorId == null && selectedGrade == null) {
                    R.string.annotator_difficulty_skip
                } else {
                    R.string.annotator_difficulty_continue
                },
            ),
            onClick = onDone,
            style = if (selectedColorId == null && selectedGrade == null) ButtonStyle.Line else ButtonStyle.Ink,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Titre du bloc : sa difficulte, couleur puis cotation, comme on la lit au mur —
 * la couleur des fleches d'abord, l'estimation du grimpeur ensuite.
 */
@Composable
private fun blocTitle(color: String?, grade: String?): String = when {
    color != null && grade != null -> stringResource(R.string.annotator_title_color_grade, color, grade)
    color != null -> color
    grade != null -> grade
    else -> stringResource(R.string.annotator_title_fallback)
}

/**
 * Compte rendu d'une detection. Chaque cas dit quoi faire ensuite : une detection
 * qui n'ajoute rien sans expliquer pourquoi passerait pour une panne.
 */
private fun DetectHoldsUseCase.Result.message(resources: Resources): String = when (this) {
    DetectHoldsUseCase.Result.Unavailable ->
        resources.getString(R.string.annotator_detect_unavailable)
    is DetectHoldsUseCase.Result.Added -> {
        val addedText = resources.getQuantityString(R.plurals.annotator_detect_added, added, added)
        val completedText =
            resources.getQuantityString(R.plurals.annotator_detect_completed, completed, completed)
        when {
            // Les prises completees d'abord : c'est ce que l'utilisateur attend quand
            // il relance la detection sur un bloc deja annote, pour filtrer ensuite.
            completed > 0 && added > 0 ->
                resources.getString(R.string.annotator_detect_added_and_completed, addedText, completedText)
            completed > 0 ->
                resources.getString(R.string.annotator_detect_completed_only, completedText)
            added > 0 && alreadyPlaced > 0 ->
                resources.getString(
                    R.string.annotator_detect_added_and_already_placed,
                    addedText,
                    resources.getQuantityString(
                        R.plurals.annotator_detect_already_placed,
                        alreadyPlaced,
                        alreadyPlaced,
                    ),
                )
            added > 0 -> resources.getString(R.string.annotator_detect_added_only, addedText)
            alreadyPlaced > 0 -> resources.getString(R.string.annotator_detect_all_already_placed)
            else -> resources.getString(R.string.annotator_detect_none)
        }
    }
}

private fun KeepHoldColorUseCase.Result.message(resources: Resources): String = when (this) {
    KeepHoldColorUseCase.Result.NoReferenceColor ->
        resources.getString(R.string.annotator_keep_color_no_reference)
    is KeepHoldColorUseCase.Result.Filtered ->
        if (removed == 0) {
            resources.getString(R.string.annotator_keep_color_nothing_removed)
        } else {
            resources.getString(
                R.string.annotator_keep_color_filtered,
                resources.getQuantityString(R.plurals.annotator_keep_color_removed, removed, removed),
                resources.getQuantityString(R.plurals.annotator_keep_color_kept, kept, kept),
            )
        }
}

@StringRes
private fun AnnotationMode.label(): Int = when (this) {
    AnnotationMode.ADD_HOLD -> R.string.annotator_mode_add_hold
    AnnotationMode.TRACE_BETA -> R.string.annotator_mode_trace_beta
    AnnotationMode.REPLAY -> R.string.annotator_mode_replay
}

/** Mode d'emploi, une phrase par mode. Le geste attendu passe en premier. */
@StringRes
private fun AnnotationMode.hint(): Int = when (this) {
    AnnotationMode.ADD_HOLD -> R.string.annotator_hint_add_hold
    AnnotationMode.TRACE_BETA -> R.string.annotator_hint_trace_beta
    AnnotationMode.REPLAY -> R.string.annotator_hint_replay
}

/** Libelles explicites : l'action porte sur ce que le mode courant manipule. */
@StringRes
private fun AnnotationMode.undoLabel(): Int = when (this) {
    AnnotationMode.ADD_HOLD -> R.string.annotator_undo_last_hold
    else -> R.string.annotator_undo_last_movement
}

@StringRes
private fun AnnotationMode.clearLabel(): Int = when (this) {
    AnnotationMode.ADD_HOLD -> R.string.annotator_clear_holds
    else -> R.string.annotator_clear_beta
}

@StringRes
private fun Limb.label(): Int = when (this) {
    Limb.LEFT_HAND -> R.string.annotator_limb_left_hand
    Limb.RIGHT_HAND -> R.string.annotator_limb_right_hand
    Limb.LEFT_FOOT -> R.string.annotator_limb_left_foot
    Limb.RIGHT_FOOT -> R.string.annotator_limb_right_foot
}

private fun Limb.swatch(): Color = when (this) {
    Limb.LEFT_HAND -> BetaColors.LeftHand
    Limb.RIGHT_HAND -> BetaColors.RightHand
    Limb.LEFT_FOOT -> BetaColors.LeftFoot
    Limb.RIGHT_FOOT -> BetaColors.RightFoot
}

/** Reference mutable vers la coroutine de lecture, hors du systeme de snapshots. */
private class PlaybackJobHolder {
    var current: Job? = null
}
