package com.topo.feature.annotator

import android.os.Build
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import com.topo.core.ui.theme.CoteFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.topo.core.domain.model.BlocDetail
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.HoldKind
import com.topo.core.domain.model.Limb
import com.topo.core.domain.model.Movement
import com.topo.core.domain.model.Viewport
import com.topo.core.domain.usecase.FindSnapTargetUseCase
import com.topo.core.ui.component.drawHoldHalo
import com.topo.core.ui.theme.BetaColors
import java.io.File

/** Ce que l'utilisateur fait avec son doigt. */
enum class AnnotationMode {
    /** Un appui pose une prise. Chemin nominal du MVP, aucun modele n'etant embarque. */
    ADD_HOLD,

    /** Un balayage trace la sequence de mouvements, magnetisee sur les prises. */
    TRACE_BETA,

    /** La sequence se rejoue mouvement par mouvement. Les gestes sont inertes. */
    REPLAY,
}

/** Rayon d'attraction par defaut, en dp : environ la pulpe d'un doigt. */
val DefaultSnapRadius = 40.dp

/**
 * Rayon de selection d'une prise au doigt, pour la retirer.
 *
 * Plus petit que [DefaultSnapRadius], et c'est delibere : au rayon de
 * magnetisation, poser une prise pres d'une autre deviendrait impossible car
 * chaque appui effacerait la voisine. 22 dp couvre le cercle dessine (14 dp de
 * rayon) avec un peu de tolerance.
 */
private val HoldTapRadius = 22.dp

const val CanvasTestTag = "annotation-canvas"

/**
 * Canevas d'annotation : photo du bloc, prises, et trace du beta.
 *
 * Composable SANS ETAT metier : il recoit le [BlocDetail] et remonte les gestes.
 * Il est donc affichable et testable sans base de donnees ni ViewModel.
 *
 * Structure en deux calques, et c'est le point d'architecture graphique du sprint :
 *
 *  - le calque PHOTO est isole dans son propre `graphicsLayer`. Ses instructions de
 *    dessin sont enregistrees dans un RenderNode et mises en cache ; redessiner le
 *    trace par-dessus ne fait donc PAS repasser le systeme sur le bitmap
 *    photographique, qui pese plusieurs megapixels ;
 *  - le calque VECTORIEL, au-dessus, est le seul redessine a chaque trame.
 *
 * Avec un [exportLayer], le rendu complet — photo et trace — y est enregistre a
 * chaque dessin, puis affiche depuis lui : l'image exportee est donc exactement
 * ce que l'ecran montre, produite par le meme code de dessin, sans second moteur.
 */
@Composable
fun AnnotationCanvas(
    blocDetail: BlocDetail,
    imageFile: File,
    mode: AnnotationMode,
    selectedLimb: Limb,
    onHoldRequested: (normalizedX: Float, normalizedY: Float) -> Unit,
    onHoldRemoved: (holdId: String) -> Unit,
    onTraceCompleted: (holdIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    snapRadius: androidx.compose.ui.unit.Dp = DefaultSnapRadius,
    traceState: BetaTraceState = remember { BetaTraceState() },
    findSnapTarget: FindSnapTargetUseCase = remember { FindSnapTargetUseCase() },
    playback: BetaPlaybackState? = null,
    exportLayer: GraphicsLayer? = null,
    detectionZone: BoundingBox? = null,
    isDrawingZone: Boolean = false,
    onZoneDrawn: (BoundingBox) -> Unit = {},
    highlight: Color? = null,
    scan: DetectionScanState? = null,
    /**
     * Progression de l'encrage a l'ouverture, en mouvements traces (0 a leur
     * nombre). Lue au dessin seulement ; null ou au-dela de la sequence, tout est
     * trace.
     */
    inking: (() -> Float)? = null,
) {
    val context = LocalContext.current
    val imageRequest = remember(imageFile) {
        ImageRequest.Builder(context)
            .data(imageFile)
            // Sous l'API 28, GraphicsLayer.toImageBitmap rejoue le calque sur un
            // canevas logiciel, qui refuse de lire un bitmap HARDWARE : l'export
            // planterait. Au-dessus, le bitmap materiel reste le bon choix.
            .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            .build()
    }

    Box(
        modifier = modifier
            .aspectRatio(blocDetail.bloc.aspectRatio)
            .testTag(CanvasTestTag)
            .then(
                if (exportLayer == null) {
                    Modifier
                } else {
                    Modifier.drawWithContent {
                        exportLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(exportLayer)
                    }
                },
            ),
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                // Calque d'isolement : la photo est pixellisee une fois dans son
                // propre RenderNode. Sans cela, chaque trame du trace obligerait le
                // systeme a reexecuter les instructions de dessin du bitmap.
                .graphicsLayer(),
        )

        BetaTraceLayer(
            blocDetail = blocDetail,
            mode = mode,
            selectedLimb = selectedLimb,
            snapRadius = snapRadius,
            traceState = traceState,
            findSnapTarget = findSnapTarget,
            playback = playback,
            onHoldRequested = onHoldRequested,
            onHoldRemoved = onHoldRemoved,
            onTraceCompleted = onTraceCompleted,
            detectionZone = detectionZone,
            isDrawingZone = isDrawingZone,
            onZoneDrawn = onZoneDrawn,
            highlight = highlight,
            scan = scan,
            inking = inking,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BetaTraceLayer(
    blocDetail: BlocDetail,
    mode: AnnotationMode,
    selectedLimb: Limb,
    snapRadius: androidx.compose.ui.unit.Dp,
    traceState: BetaTraceState,
    findSnapTarget: FindSnapTargetUseCase,
    playback: BetaPlaybackState?,
    onHoldRequested: (Float, Float) -> Unit,
    onHoldRemoved: (String) -> Unit,
    onTraceCompleted: (List<String>) -> Unit,
    detectionZone: BoundingBox?,
    isDrawingZone: Boolean,
    onZoneDrawn: (BoundingBox) -> Unit,
    highlight: Color?,
    scan: DetectionScanState?,
    inking: (() -> Float)?,
    modifier: Modifier = Modifier,
) {
    // ------------------------------------------------------------------------
    //  rememberUpdatedState — la raison reelle, verifiee par mutation.
    //
    //  Le conseil habituel dit que `pointerInput(Unit)` figerait ses captures a la
    //  premiere composition. Ce n'est PAS ce qui se produit : Compose reinitialise
    //  le gestionnaire de pointeur des que la lambda change d'instance, meme a cle
    //  constante. Le danger est cette reinitialisation elle-meme — elle ANNULE le
    //  geste en cours.
    //
    //  Le scenario est banal ici : les prises viennent d'un Flow Room, et une
    //  emission peut tomber pendant que le doigt trace. Si la lambda capturait
    //  directement `blocDetail.holds`, elle changerait d'instance a cette emission
    //  et le trace mourrait a mi-parcours.
    //
    //  En capturant des objets State dont l'IDENTITE ne change jamais, la lambda
    //  reste stable : la coroutine de detection n'est pas reinitialisee, et les
    //  lectures restent fraiches. Le test
    //  `a_hold_list_update_mid_gesture_neither_cancels_the_trace_nor_is_ignored`
    //  echoue si l'on remplace ces lectures indirectes par des lectures directes.
    // ------------------------------------------------------------------------
    val currentHolds by rememberUpdatedState(blocDetail.holds)
    val currentMode by rememberUpdatedState(mode)
    val currentSnapRadius by rememberUpdatedState(snapRadius)
    val currentOnHoldRequested by rememberUpdatedState(onHoldRequested)
    val currentOnHoldRemoved by rememberUpdatedState(onHoldRemoved)
    val currentOnTraceCompleted by rememberUpdatedState(onTraceCompleted)

    val currentZone by rememberUpdatedState(detectionZone)
    val currentHighlight by rememberUpdatedState(highlight)
    val currentScan by rememberUpdatedState(scan)
    val currentInking by rememberUpdatedState(inking)
    val textMeasurer = rememberTextMeasurer()
    val currentIsDrawingZone by rememberUpdatedState(isDrawingZone)
    val currentOnZoneDrawn by rememberUpdatedState(onZoneDrawn)
    // Cadre en cours de trace, en pixels : lu seulement au dessin, comme le trace.
    var liveZone by remember { mutableStateOf<Rect?>(null) }

    val currentMovements by rememberUpdatedState(blocDetail.movements)
    val currentLimb by rememberUpdatedState(selectedLimb)
    val holdsById by rememberUpdatedState(blocDetail.holdsById)

    Box(
        modifier = modifier
            // pointerInput(Unit) : la coroutine de detection n'est JAMAIS redemarree.
            // Toutes les valeurs variables passent par rememberUpdatedState.
            .pointerInput(Unit) {
                val tapDensity = Density(this.density, this.fontScale)

                detectTapGestures { offset ->
                    // Le rejeu n'accepte aucun geste : un appui pendant la lecture
                    // poserait une prise sans que l'utilisateur l'ait voulu.
                    if (currentMode != AnnotationMode.ADD_HOLD) return@detectTapGestures

                    // Un appui SUR une prise la retire, un appui a cote en pose une.
                    // C'est le seul moyen de corriger une prise mal visee, et il ne
                    // coute aucun bouton supplementaire dans une barre d'outils
                    // deja dense.
                    //
                    // Le rayon de selection est volontairement plus petit que celui
                    // de magnetisation : avec 40 dp, poser une prise a cote d'une
                    // autre deviendrait impossible, chaque appui proche effacant la
                    // voisine au lieu d'ajouter.
                    val hit = snapAt(
                        offset, size.width, size.height,
                        currentHolds, findSnapTarget,
                        with(tapDensity) { HoldTapRadius.toPx() },
                    )
                    if (hit != null) {
                        currentOnHoldRemoved(hit)
                    } else {
                        currentOnHoldRequested(
                            offset.x / size.width,
                            offset.y / size.height,
                        )
                    }
                }
            }
            .pointerInput(Unit) {
                val density = Density(this.density, this.fontScale)
                // Coin d'ancrage du cadre de zone en cours de trace.
                var zoneAnchor = Offset.Zero

                // UN SEUL detecteur de glissement pour le trace ET le cadrage de la
                // zone. Deux detecteurs sur le meme noeud se disputent le geste : le
                // premier a franchir le seuil de glissement le consomme, et l'autre
                // ne recoit plus rien — c'est ce que le test du cadrage a revele.
                detectDragGestures(
                    onDragStart = { offset ->
                        // Cadrage : actif seulement sur demande explicite, sinon un
                        // glissement en mode prises tracerait un cadre par megarde.
                        if (currentIsDrawingZone) {
                            zoneAnchor = offset
                            liveZone = Rect(offset, offset)
                            return@detectDragGestures
                        }
                        if (currentMode != AnnotationMode.TRACE_BETA) return@detectDragGestures
                        traceState.begin(offset)
                        traceState.update(
                            position = offset,
                            snappedHoldId = snapAt(
                                offset, size.width, size.height,
                                currentHolds, findSnapTarget,
                                with(density) { currentSnapRadius.toPx() },
                            ),
                        )
                    },
                    onDrag = { change, _ ->
                        if (liveZone != null) {
                            change.consume()
                            liveZone = Rect(
                                topLeft = Offset(
                                    minOf(zoneAnchor.x, change.position.x),
                                    minOf(zoneAnchor.y, change.position.y),
                                ),
                                bottomRight = Offset(
                                    maxOf(zoneAnchor.x, change.position.x),
                                    maxOf(zoneAnchor.y, change.position.y),
                                ),
                            )
                            return@detectDragGestures
                        }
                        if (currentMode != AnnotationMode.TRACE_BETA) return@detectDragGestures
                        // consume() : le geste appartient au canevas. Sans cela, un
                        // conteneur defilant parent volerait le trace des que le
                        // doigt depasse le seuil de defilement.
                        change.consume()
                        traceState.update(
                            position = change.position,
                            snappedHoldId = snapAt(
                                change.position, size.width, size.height,
                                currentHolds, findSnapTarget,
                                with(density) { currentSnapRadius.toPx() },
                            ),
                        )
                    },
                    onDragEnd = {
                        val drawn = liveZone
                        if (drawn != null) {
                            liveZone = null
                            drawn.toZone(size.width.toFloat(), size.height.toFloat())
                                ?.let(currentOnZoneDrawn)
                            return@detectDragGestures
                        }
                        val chain = traceState.finish()
                        if (chain.isNotEmpty()) currentOnTraceCompleted(chain)
                    },
                    onDragCancel = {
                        liveZone = null
                        traceState.reset()
                    },
                )
            }
            // ----------------------------------------------------------------
            //  drawWithCache : tout objet graphique lourd (Path, Stroke, PathEffect)
            //  est instancie UNE fois, dans le bloc de cache, et non a chaque appel
            //  de dessin. Le bloc de cache n'est reexecute que si la taille du
            //  canevas change.
            //
            //  Instancier un Path ou un Stroke dans la lambda de dessin creerait un
            //  objet par trame, soit 60 a 120 allocations par seconde pendant tout
            //  le trace : le ramasse-miettes s'en chargerait par a-coups, et ce sont
            //  precisement ces micro-pauses qui se voient a l'ecran.
            // ----------------------------------------------------------------
            .drawWithCache {
                val tracePath = Path()
                val holdStroke = Stroke(width = 2.dp.toPx())
                val highlightStroke = Stroke(width = 3.dp.toPx())
                val outlineStroke = Stroke(width = 1.dp.toPx())
                val zoneStroke = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
                )
                val movementStroke = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                val footStroke = Stroke(
                    width = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    // Redondance avec la couleur : un pied se lit aussi a la forme
                    // du trait, ce qui reste vrai en cas de daltonisme.
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(10.dp.toPx(), 8.dp.toPx()),
                    ),
                )
                val liveStroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                val holdRadius = 14.dp.toPx()
                val snapHaloRadius = 22.dp.toPx()
                val markerRadius = 9.dp.toPx()
                val markerRing = Stroke(width = 2.dp.toPx())
                // Numeros des mouvements, mesures une fois chacun : TextMeasurer
                // alloue a chaque mesure, et le rejeu redessine a chaque trame.
                val numberLabels = HashMap<Int, TextLayoutResult>()
                val numberOf: (Int) -> TextLayoutResult = { n ->
                    numberLabels.getOrPut(n) { textMeasurer.measure(n.toString(), NumberStyle) }
                }

                onDrawBehind {
                    // Toutes les lectures d'etat mutable ont lieu ICI, dans la phase
                    // de dessin : un deplacement du doigt n'invalide que le dessin,
                    // jamais la composition.

                    // La zone n'a de sens qu'en edition des prises : ni le trace, ni
                    // le rejeu, ni l'image partagee depuis ces modes ne la montrent.
                    if (currentMode == AnnotationMode.ADD_HOLD) {
                        (liveZone ?: currentZone?.toPixels(size))?.let { drawZone(it, zoneStroke) }
                    }

                    // scan.progress.value est lu ICI, comme la progression du rejeu :
                    // l'animation n'invalide que le dessin.
                    val scanState = currentScan?.takeIf { it.isRunning }
                    val scanZone = scanState?.zone
                    val zoneTop = scanZone?.top?.coerceAtLeast(0f) ?: 0f
                    val zoneBottom = scanZone?.bottom?.coerceAtMost(1f) ?: 1f
                    val lineY = scanState?.let { scanLineY(it.progress.value, zoneTop, zoneBottom) }

                    drawHolds(
                        holds = currentHolds,
                        radius = holdRadius,
                        neutralStroke = holdStroke,
                        highlight = currentHighlight,
                        highlightStroke = highlightStroke,
                        outlineStroke = outlineStroke,
                        pending = if (lineY != null) scanState.revealing else emptySet(),
                        lineY = lineY ?: 0f,
                        pulseSpan = (zoneBottom - zoneTop) * DetectionScanState.PULSE_SPAN,
                    )

                    if (scanState != null && lineY != null) {
                        drawScan(
                            state = scanState,
                            holds = currentHolds,
                            lineY = lineY,
                            left = scanZone?.left?.coerceAtLeast(0f) ?: 0f,
                            right = scanZone?.right?.coerceAtMost(1f) ?: 1f,
                            labelSpan = (zoneBottom - zoneTop) * DetectionScanState.LABEL_SPAN,
                            textMeasurer = textMeasurer,
                            holdRadius = holdRadius,
                        )
                    }

                    // playback.progress.value est lu ICI et nulle part ailleurs.
                    // Une animation emet une valeur par trame : la lire en
                    // composition provoquerait 60 a 120 recompositions par
                    // seconde de tout le sous-arbre.
                    drawMovements(
                        movements = currentMovements,
                        holdsById = holdsById,
                        handStroke = movementStroke,
                        footStroke = footStroke,
                        markerRadius = markerRadius,
                        markerRing = markerRing,
                        numberOf = numberOf,
                        frame = playback?.let {
                            playbackFrameAt(it.progress.value, currentMovements.size)
                        } ?: currentInking?.invoke()
                            ?.takeIf { it < currentMovements.size }
                            ?.let { playbackFrameAt(it, currentMovements.size) },
                    )

                    drawLiveTrace(
                        traceState = traceState,
                        holdsById = holdsById,
                        limb = currentLimb,
                        path = tracePath,
                        stroke = liveStroke,
                    )

                    traceState.snappedHoldId?.let { id ->
                        holdsById[id]?.let { hold ->
                            drawSnapHalo(hold, snapHaloRadius)
                        }
                    }
                }
            },
    )
}

// ---------------------------------------------------------------------------
//  Primitives de dessin.
//  Chacune recoit ses objets graphiques deja alloues : aucune n'en cree.
// ---------------------------------------------------------------------------

/**
 * Dessine les prises. Avec [highlight], celles du bloc portent un halo de sa
 * couleur de difficulte.
 *
 * Sur le terrain, la difficulte n'est portee que par les fleches de depart : une
 * fois au pied du mur, rien sur la photo ne disait a quel niveau appartiennent
 * les prises gardees. Le halo le dit, a l'ecran comme sur l'image partagee.
 *
 * Le halo lui-meme est dessine par [drawHoldHalo], partage avec la vignette de
 * la seance. Les VOLUMES restent en blanc neutre : ils appartiennent au mur, pas
 * au bloc.
 */
private fun DrawScope.drawHolds(
    holds: List<Hold>,
    radius: Float,
    neutralStroke: Stroke,
    highlight: Color?,
    highlightStroke: Stroke,
    outlineStroke: Stroke,
    pending: Set<String>,
    lineY: Float,
    pulseSpan: Float,
) {
    for (index in holds.indices) {
        val hold = holds[index]
        var holdRadius = radius
        if (hold.id in pending) {
            // Prise tout juste detectee : cachee tant que le scan ne l'a pas atteinte,
            // puis une breve pulsation a son allumage.
            if (!isRevealed(hold.box.centerY, lineY)) continue
            val sinceLine = hold.box.centerY - lineY
            if (pulseSpan > 0f && sinceLine < pulseSpan) {
                holdRadius *= 1f + PULSE_GAIN * (1f - sinceLine / pulseSpan)
            }
        }
        val center = hold.box.toOffset(size)
        if (highlight == null || hold.kind == HoldKind.VOLUME) {
            drawCircle(color = BetaColors.Hold, radius = holdRadius, center = center, style = neutralStroke)
            continue
        }
        drawHoldHalo(center, holdRadius, highlight, highlightStroke, outlineStroke)
    }
}

/**
 * Ligne du scan et scores des prises qu'elle vient de reveler.
 *
 * La ligne est faite de trois traits superposes, du plus large et transparent au plus
 * fin et opaque : une lueur sans degrade, donc sans Brush a allouer a chaque trame.
 */
private fun DrawScope.drawScan(
    state: DetectionScanState,
    holds: List<Hold>,
    lineY: Float,
    left: Float,
    right: Float,
    labelSpan: Float,
    textMeasurer: TextMeasurer,
    holdRadius: Float,
) {
    val y = lineY * size.height
    val start = Offset(left * size.width, y)
    val end = Offset(right * size.width, y)
    drawLine(BetaColors.Scan.copy(alpha = 0.18f), start, end, strokeWidth = 14.dp.toPx())
    drawLine(BetaColors.Scan.copy(alpha = 0.35f), start, end, strokeWidth = 6.dp.toPx())
    drawLine(BetaColors.Scan, start, end, strokeWidth = 1.5.dp.toPx())

    for (index in holds.indices) {
        val hold = holds[index]
        if (hold.id !in state.revealing) continue
        val sinceLine = hold.box.centerY - lineY
        if (sinceLine < 0f || sinceLine > labelSpan) continue
        // Mesure une fois par prise, puis relue : TextMeasurer alloue a chaque mesure.
        val label = state.labels.getOrPut(hold.id) {
            textMeasurer.measure(
                text = "%.2f".format(hold.confidence), // texte-technique : format numerique du score
                style = ScoreStyle,
            )
        }
        val center = hold.box.toOffset(size)
        drawText(
            textLayoutResult = label,
            color = Color.White.copy(alpha = 1f - sinceLine / labelSpan),
            topLeft = Offset(center.x + holdRadius + 4.dp.toPx(), center.y - label.size.height / 2f),
        )
    }
}

private const val PULSE_GAIN = 0.6f
private val ScoreStyle = TextStyle(fontFamily = CoteFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp)

/**
 * Dessine la sequence enregistree.
 *
 * Sans [frame], toute la sequence est tracee : c'est le mode edition. Avec, seuls
 * les mouvements deja joues apparaissent, et celui en cours est interpole entre
 * sa prise de depart et sa prise d'arrivee — le trait pousse vers la prise, comme
 * une main qui va la chercher.
 *
 * Chaque prise atteinte porte le numero du mouvement, cercle de la couleur du
 * membre : c'est la convention des topos imprimes, et l'ordre se lit sans
 * rejouer la sequence — sur l'ecran comme sur l'image partagee.
 */
private fun DrawScope.drawMovements(
    movements: List<Movement>,
    holdsById: Map<String, Hold>,
    handStroke: Stroke,
    footStroke: Stroke,
    markerRadius: Float,
    markerRing: Stroke,
    numberOf: (Int) -> TextLayoutResult,
    frame: PlaybackFrame? = null,
) {
    val lastFullyDrawn = frame?.completedMovements ?: movements.size

    for (index in movements.indices) {
        if (index >= lastFullyDrawn && index != frame?.activeIndex) continue

        val movement = movements[index]
        val target = holdsById[movement.targetHoldId] ?: continue
        val targetCenter = target.box.toOffset(size)
        val color = movement.limb.color()
        val isActive = index == frame?.activeIndex
        val advance = if (isActive) frame.activeProgress else 1f

        val source = movement.sourceHoldId?.let(holdsById::get)
        if (source == null) {
            // Mouvement de depart : pas de segment, seulement un marqueur qui
            // grandit jusqu'a sa taille finale.
            if (isActive) {
                drawCircle(color = color, radius = markerRadius * advance, center = targetCenter)
            } else {
                drawNumberMarker(index + 1, targetCenter, color, markerRadius, markerRing, numberOf)
            }
            continue
        }

        val start = source.box.toOffset(size)
        val end = lerp(start, targetCenter, advance)

        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = handStroke.width,
            cap = handStroke.cap,
            pathEffect = if (movement.limb.isHand) null else footStroke.pathEffect,
        )
        // Le marqueur d'arrivee n'apparait qu'une fois la prise atteinte : le
        // voir avant donnerait l'impression que le mouvement est deja fini.
        if (!isActive) {
            drawNumberMarker(index + 1, targetCenter, color, markerRadius, markerRing, numberOf)
        }
    }
}

/** Numero cercle d'un mouvement : pastille papier, anneau du membre, chiffre a l'encre. */
private fun DrawScope.drawNumberMarker(
    number: Int,
    center: Offset,
    ring: Color,
    radius: Float,
    ringStroke: Stroke,
    numberOf: (Int) -> TextLayoutResult,
) {
    // Decale vers le haut a droite : la prise elle-meme reste visible sous le numero.
    val at = Offset(center.x + radius * MARKER_OFFSET, center.y - radius * MARKER_OFFSET)
    drawCircle(MarkerPaper, radius = radius, center = at)
    drawCircle(ring, radius = radius, center = at, style = ringStroke)
    val label = numberOf(number)
    drawText(
        textLayoutResult = label,
        color = MarkerInk,
        topLeft = Offset(at.x - label.size.width / 2f, at.y - label.size.height / 2f),
    )
}

// Assez loin pour que le numero ne mange ni la prise ni le trait qui y arrive.
private const val MARKER_OFFSET = 1.4f

// Le papier et l'encre de TopoPalettes.Paper, fixes : la photo ne change pas de theme.
private val MarkerPaper = Color(0xFFEEEAE0)
private val MarkerInk = Color(0xFF1B1A17)
private val NumberStyle = TextStyle(fontFamily = CoteFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)

/** Interpolation lineaire entre deux points du canevas. */
private fun lerp(start: Offset, end: Offset, fraction: Float): Offset = Offset(
    x = start.x + (end.x - start.x) * fraction,
    y = start.y + (end.y - start.y) * fraction,
)

/**
 * Trace en cours : la polyligne des prises deja magnetisees, prolongee jusqu'au
 * doigt. Le [Path] est REUTILISE d'une trame a l'autre.
 */
private fun DrawScope.drawLiveTrace(
    traceState: BetaTraceState,
    holdsById: Map<String, Hold>,
    limb: Limb,
    path: Path,
    stroke: Stroke,
) {
    if (!traceState.isTracing) return

    // rewind() et non reset() : rewind conserve la memoire deja allouee par le
    // chemin et ne fait que vider ses segments. C'est ce qui rend la reutilisation
    // du Path reellement gratuite.
    path.rewind()

    var started = false
    for (index in traceState.chain.indices) {
        val hold = holdsById[traceState.chain[index]] ?: continue
        val center = hold.box.toOffset(size)
        if (started) path.lineTo(center.x, center.y) else path.moveTo(center.x, center.y)
        started = true
    }

    val pointer = traceState.pointer
    // Le segment vif s'arrete au CENTRE de la prise magnetisee, pas sous le doigt :
    // c'est la magnetisation rendue visible. Sinon l'utilisateur ne voit l'ancrage
    // qu'au relachement.
    val endPoint = traceState.snappedHoldId
        ?.let(holdsById::get)
        ?.box?.toOffset(size)
        ?: pointer

    if (started) path.lineTo(endPoint.x, endPoint.y) else path.moveTo(endPoint.x, endPoint.y)

    drawPath(path = path, color = limb.color(), style = stroke)
}

/**
 * Assombrit tout ce qui est HORS de la zone, puis la cerne : ce qui reste clair
 * est ce qui sera analyse. Quatre bandes plutot qu'un chemin evide : aucune
 * allocation, et le calcul tient en quatre rectangles.
 */
private fun DrawScope.drawZone(zone: Rect, stroke: Stroke) {
    val scrim = Color.Black.copy(alpha = 0.45f)
    drawRect(scrim, Offset.Zero, Size(size.width, zone.top))
    drawRect(scrim, Offset(0f, zone.bottom), Size(size.width, size.height - zone.bottom))
    drawRect(scrim, Offset(0f, zone.top), Size(zone.left, zone.height))
    drawRect(scrim, Offset(zone.right, zone.top), Size(size.width - zone.right, zone.height))
    drawRect(Color.White, zone.topLeft, zone.size, style = stroke)
}

/**
 * Cadre en pixels -> zone normalisee, rognee au canevas.
 *
 * @return null pour un cadre trop petit pour etre voulu : un appui leger ou un
 *         glissement de quelques pixels ne doit pas reduire l'analyse a rien.
 */
internal fun Rect.toZone(widthPx: Float, heightPx: Float): BoundingBox? {
    if (widthPx <= 0f || heightPx <= 0f) return null
    val left = (left / widthPx).coerceIn(0f, 1f)
    val right = (right / widthPx).coerceIn(0f, 1f)
    val top = (top / heightPx).coerceIn(0f, 1f)
    val bottom = (bottom / heightPx).coerceIn(0f, 1f)
    if (right - left < MIN_ZONE_FRACTION || bottom - top < MIN_ZONE_FRACTION) return null
    return BoundingBox((left + right) / 2f, (top + bottom) / 2f, right - left, bottom - top)
}

/** Cote minimal d'une zone, en fraction de la photo. */
private const val MIN_ZONE_FRACTION = 0.05f

private fun BoundingBox.toPixels(size: Size): Rect =
    Rect(left * size.width, top * size.height, right * size.width, bottom * size.height)

private fun DrawScope.drawSnapHalo(hold: Hold, radius: Float) {
    drawCircle(
        color = BetaColors.SnapHalo.copy(alpha = 0.35f),
        radius = radius,
        center = hold.box.toOffset(size),
    )
}

// ---------------------------------------------------------------------------
//  Conversions
// ---------------------------------------------------------------------------

/** Centre normalise -> pixels du canevas. */
private fun com.topo.core.domain.model.BoundingBox.toOffset(size: Size): Offset =
    Offset(centerX * size.width, centerY * size.height)

private fun Limb.color(): Color = when (this) {
    Limb.LEFT_HAND -> BetaColors.LeftHand
    Limb.RIGHT_HAND -> BetaColors.RightHand
    Limb.LEFT_FOOT -> BetaColors.LeftFoot
    Limb.RIGHT_FOOT -> BetaColors.RightFoot
}

/**
 * Magnetisation : conversion en coordonnees normalisees, puis delegation au
 * domaine. Le calcul de distance lui-meme se fait en pixels (cf. [Viewport]).
 */
private fun snapAt(
    position: Offset,
    widthPx: Int,
    heightPx: Int,
    holds: List<Hold>,
    findSnapTarget: FindSnapTargetUseCase,
    thresholdPx: Float,
): String? {
    if (widthPx <= 0 || heightPx <= 0) return null
    return findSnapTarget(
        pointerX = position.x / widthPx,
        pointerY = position.y / heightPx,
        holds = holds,
        viewport = Viewport(widthPx.toFloat(), heightPx.toFloat()),
        thresholdPx = thresholdPx,
    )?.hold?.id
}
