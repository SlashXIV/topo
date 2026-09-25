package com.topo.feature.annotator

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.topo.core.domain.model.BlocDetail
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.Limb
import com.topo.core.domain.model.Movement
import com.topo.core.domain.model.Viewport
import com.topo.core.domain.usecase.FindSnapTargetUseCase
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
) {
    Box(
        modifier = modifier
            .aspectRatio(blocDetail.bloc.aspectRatio)
            .testTag(CanvasTestTag),
    ) {
        AsyncImage(
            model = imageFile,
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

                detectDragGestures(
                    onDragStart = { offset ->
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
                        val chain = traceState.finish()
                        if (chain.isNotEmpty()) currentOnTraceCompleted(chain)
                    },
                    onDragCancel = { traceState.reset() },
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

                onDrawBehind {
                    // Toutes les lectures d'etat mutable ont lieu ICI, dans la phase
                    // de dessin : un deplacement du doigt n'invalide que le dessin,
                    // jamais la composition.
                    drawHolds(currentHolds, holdStroke, holdRadius)

                    // playback.progress.value est lu ICI et nulle part ailleurs.
                    // Une animation emet une valeur par trame : la lire en
                    // composition provoquerait 60 a 120 recompositions par
                    // seconde de tout le sous-arbre.
                    drawMovements(
                        movements = currentMovements,
                        holdsById = holdsById,
                        handStroke = movementStroke,
                        footStroke = footStroke,
                        frame = playback?.let {
                            playbackFrameAt(it.progress.value, currentMovements.size)
                        },
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

private fun DrawScope.drawHolds(holds: List<Hold>, stroke: Stroke, radius: Float) {
    for (index in holds.indices) {
        val hold = holds[index]
        drawCircle(
            color = BetaColors.Hold,
            radius = radius,
            center = hold.box.toOffset(size),
            style = stroke,
        )
    }
}

/**
 * Dessine la sequence enregistree.
 *
 * Sans [frame], toute la sequence est tracee : c'est le mode edition. Avec, seuls
 * les mouvements deja joues apparaissent, et celui en cours est interpole entre
 * sa prise de depart et sa prise d'arrivee — le trait pousse vers la prise, comme
 * une main qui va la chercher.
 */
private fun DrawScope.drawMovements(
    movements: List<Movement>,
    holdsById: Map<String, Hold>,
    handStroke: Stroke,
    footStroke: Stroke,
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
            drawCircle(
                color = color,
                radius = handStroke.width * 1.6f * advance,
                center = targetCenter,
            )
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
            drawCircle(color = color, radius = handStroke.width, center = targetCenter)
        }
    }
}

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
