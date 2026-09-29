package com.topo.feature.annotator

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.BlocDetail
import com.topo.core.domain.model.BlocStatus
import com.topo.core.domain.model.GradeScale
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.HoldKind
import com.topo.core.domain.model.HoldOrigin
import com.topo.core.domain.model.Limb
import com.topo.core.domain.model.Movement
import com.topo.core.ui.theme.BetaColors
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/**
 * Comportement gestuel du canevas d'annotation.
 *
 * Les tests pilotent de vrais evenements tactiles sur le composable, sans base de
 * donnees ni ViewModel : le canevas est volontairement sans etat metier, ce qui le
 * rend testable isolement.
 */
class AnnotationCanvasTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val leftHold = hold("gauche", x = 0.2f, y = 0.5f)
    private val rightHold = hold("droite", x = 0.8f, y = 0.5f)

    @Test
    fun tapping_in_add_hold_mode_reports_normalized_coordinates() {
        var requested: Pair<Float, Float>? = null

        setCanvas(
            holds = emptyList(),
            mode = AnnotationMode.ADD_HOLD,
            onHoldRequested = { x, y -> requested = x to y },
        )

        composeRule.onNodeWithTag(CanvasTestTag).performTouchInput {
            click(percentOffset(0.25f, 0.75f))
        }
        composeRule.waitForIdle()

        // Les coordonnees remontent NORMALISEES : le domaine ne connait pas les
        // pixels, et une prise doit rester valide a toute resolution d'affichage.
        assertThat(requested?.first).isWithin(0.02f).of(0.25f)
        assertThat(requested?.second).isWithin(0.02f).of(0.75f)
    }

    @Test
    fun tapping_in_trace_mode_does_not_add_a_hold() {
        var requested = 0

        setCanvas(
            holds = listOf(leftHold),
            mode = AnnotationMode.TRACE_BETA,
            onHoldRequested = { _, _ -> requested++ },
        )

        composeRule.onNodeWithTag(CanvasTestTag).performTouchInput {
            click(percentOffset(0.5f, 0.5f))
        }
        composeRule.waitForIdle()

        assertThat(requested).isEqualTo(0)
    }

    @Test
    fun a_swipe_across_two_holds_reports_them_in_order() {
        var chain: List<String>? = null

        setCanvas(
            holds = listOf(leftHold, rightHold),
            mode = AnnotationMode.TRACE_BETA,
            onTraceCompleted = { chain = it },
        )

        composeRule.onNodeWithTag(CanvasTestTag).performTouchInput {
            traceLine(fromX = 0.2f, toX = 0.8f, y = 0.5f)
        }
        composeRule.waitForIdle()

        // Le doigt passe a proximite des deux prises : le trace se magnetise sur
        // chacune, dans l'ordre de passage.
        assertThat(chain).containsExactly("gauche", "droite").inOrder()
    }

    @Test
    fun a_swipe_far_from_every_hold_reports_nothing() {
        var completed = 0

        setCanvas(
            holds = listOf(hold("isolee", x = 0.1f, y = 0.1f)),
            mode = AnnotationMode.TRACE_BETA,
            onTraceCompleted = { completed++ },
        )

        composeRule.onNodeWithTag(CanvasTestTag).performTouchInput {
            traceLine(fromX = 0.2f, toX = 0.8f, y = 0.9f)
        }
        composeRule.waitForIdle()

        // Aucune prise sous le rayon d'attraction : rien a enregistrer.
        assertThat(completed).isEqualTo(0)
    }

    @Test
    fun lingering_on_one_hold_does_not_duplicate_it() {
        var chain: List<String>? = null

        setCanvas(
            holds = listOf(leftHold),
            mode = AnnotationMode.TRACE_BETA,
            onTraceCompleted = { chain = it },
        )

        composeRule.onNodeWithTag(CanvasTestTag).performTouchInput {
            // Le doigt bouge assez pour franchir le seuil de glissement, mais reste
            // dans le rayon d'attraction de la meme prise (0,08 = 32 dp < 40 dp).
            traceLine(fromX = 0.2f, toX = 0.28f, y = 0.5f)
        }
        composeRule.waitForIdle()

        // Le doigt reste dans le rayon de la meme prise pendant dix evenements :
        // la chaine n'enregistre qu'un changement de prise, pas chaque evenement.
        assertThat(chain).containsExactly("gauche")
    }

    /**
     * LE test de `rememberUpdatedState`.
     *
     * Contrairement au conseil souvent recopie, le danger n'est PAS une capture
     * perimee : Compose reinitialise le gestionnaire de `pointerInput` des que la
     * lambda change d'instance, meme a cle constante. Le vrai danger est cette
     * reinitialisation elle-meme — elle ANNULE le geste en cours.
     *
     * Le scenario est banal ici : les prises arrivent d'un Flow Room, et une
     * emission peut tomber pendant que le doigt trace. Sans `rememberUpdatedState`,
     * la lambda capturerait `blocDetail.holds` et changerait d'instance a cette
     * emission, tuant le trace a mi-parcours.
     *
     * Le test maintient donc le doigt POSE pendant la mise a jour, puis verifie que
     * le geste se poursuit et que la nouvelle prise est magnetisable. Il a ete
     * valide par mutation : remplacer la lecture indirecte par une lecture directe
     * le fait echouer.
     */
    @Test
    fun a_hold_list_update_mid_gesture_neither_cancels_the_trace_nor_is_ignored() {
        var chain: List<String>? = null
        var holds by mutableStateOf(listOf(leftHold))

        composeRule.setContent {
            AnnotationCanvas(
                blocDetail = detail(holds),
                imageFile = MissingImage,
                mode = AnnotationMode.TRACE_BETA,
                selectedLimb = Limb.RIGHT_HAND,
                onHoldRequested = { _, _ -> },
                onHoldRemoved = {},
                onTraceCompleted = { chain = it },
                modifier = Modifier.size(CanvasSize),
            )
        }

        val canvas = composeRule.onNodeWithTag(CanvasTestTag)

        // Le doigt part de la prise de gauche et reste POSE.
        canvas.performTouchInput {
            down(percentOffset(0.2f, 0.5f))
            repeat(8) { step -> moveTo(percentOffset(0.2f + step * 0.025f, 0.5f)) }
        }

        // Emission du Flow en plein geste : une prise apparait.
        composeRule.runOnIdle { holds = listOf(leftHold, rightHold) }

        // Le doigt poursuit jusqu'a la nouvelle prise, puis se leve.
        canvas.performTouchInput {
            repeat(16) { step -> moveTo(percentOffset(0.4f + step * 0.025f, 0.5f)) }
            up()
        }
        composeRule.waitForIdle()

        assertThat(chain).containsExactly("gauche", "droite").inOrder()
    }

    @Test
    fun an_existing_beta_renders_without_crashing_on_a_dangling_movement() {
        // Un mouvement dont la source a ete supprimee (ON DELETE SET NULL) reste en
        // base : le rendu doit le traiter comme un depart, pas lever d'exception.
        val orphan = Movement(
            id = "mvt-1",
            blocId = "bloc-1",
            sequenceOrder = 0,
            limb = Limb.LEFT_FOOT,
            sourceHoldId = null,
            targetHoldId = leftHold.id,
        )

        composeRule.setContent {
            AnnotationCanvas(
                blocDetail = BlocDetail(bloc(), listOf(leftHold), listOf(orphan)),
                imageFile = MissingImage,
                mode = AnnotationMode.TRACE_BETA,
                selectedLimb = Limb.RIGHT_HAND,
                onHoldRequested = { _, _ -> },
                onHoldRemoved = {},
                onTraceCompleted = {},
                modifier = Modifier.size(CanvasSize),
            )
        }

        composeRule.onNodeWithTag(CanvasTestTag).assertExists()
    }

    /**
     * Le halo porte la couleur de difficulte du bloc sur ses prises, et laisse les
     * volumes en blanc neutre : ils appartiennent au mur, pas au bloc.
     */
    @Test
    fun bloc_holds_wear_the_difficulty_colour_and_volumes_stay_white() {
        val purple = Color(0xFF7B3FA0)
        val hold = hold("prise", x = 0.3f, y = 0.5f)
        val volume = hold("volume", x = 0.7f, y = 0.5f).copy(kind = HoldKind.VOLUME)
        lateinit var layer: GraphicsLayer

        composeRule.setContent {
            layer = rememberGraphicsLayer()
            AnnotationCanvas(
                blocDetail = BlocDetail(bloc(), listOf(hold, volume), emptyList()),
                imageFile = MissingImage,
                mode = AnnotationMode.ADD_HOLD,
                selectedLimb = Limb.RIGHT_HAND,
                onHoldRequested = { _, _ -> },
                onHoldRemoved = {},
                onTraceCompleted = {},
                modifier = Modifier.size(CanvasSize),
                exportLayer = layer,
                highlight = purple,
            )
        }
        composeRule.waitForIdle()

        val bitmap = runBlocking(Dispatchers.Main) { layer.toImageBitmap() }
            .asAndroidBitmap()
            .copy(Bitmap.Config.ARGB_8888, false)
        val canvasPx = with(composeRule.density) { CanvasSize.roundToPx() }
        val ringPx = with(composeRule.density) { 14.dp.roundToPx() }
        val y = canvasPx / 2

        // Sur l'anneau, a un rayon du centre de chaque prise.
        assertThat(bitmap.getPixel((canvasPx * 0.3f).toInt() + ringPx, y)).isEqualTo(purple.toArgb())
        val onVolume = bitmap.getPixel((canvasPx * 0.7f).toInt() + ringPx, y)
        assertThat(android.graphics.Color.red(onVolume)).isGreaterThan(200)
        assertThat(android.graphics.Color.blue(onVolume)).isGreaterThan(200)
    }

    /**
     * Pendant le scan, une prise tout juste detectee attend le passage de la ligne ; une
     * prise deja posee, elle, reste affichee. Le scan monte : a mi-course, la prise basse
     * est revelee, la haute pas encore.
     */
    @Test
    fun during_the_scan_a_new_hold_waits_for_the_line_to_reach_it() {
        val purple = Color(0xFF7B3FA0)
        val low = hold("basse", x = 0.3f, y = 0.8f)
        val high = hold("haute", x = 0.7f, y = 0.2f)
        val scan = DetectionScanState()
        runBlocking(Dispatchers.Main) { scan.snapTo(0.5f, holdIds = setOf(low.id, high.id)) }
        lateinit var layer: GraphicsLayer

        composeRule.setContent {
            layer = rememberGraphicsLayer()
            AnnotationCanvas(
                blocDetail = BlocDetail(bloc(), listOf(low, high), emptyList()),
                imageFile = MissingImage,
                mode = AnnotationMode.ADD_HOLD,
                selectedLimb = Limb.RIGHT_HAND,
                onHoldRequested = { _, _ -> },
                onHoldRemoved = {},
                onTraceCompleted = {},
                modifier = Modifier.size(CanvasSize),
                exportLayer = layer,
                highlight = purple,
                scan = scan,
            )
        }
        composeRule.waitForIdle()

        val bitmap = runBlocking(Dispatchers.Main) { layer.toImageBitmap() }
            .asAndroidBitmap()
            .copy(Bitmap.Config.ARGB_8888, false)
        val canvasPx = with(composeRule.density) { CanvasSize.roundToPx() }
        val ringPx = with(composeRule.density) { 14.dp.roundToPx() }

        // Ligne a 0,5 : la prise basse (0,8) est passee, la haute (0,2) pas encore.
        assertThat(bitmap.getPixel((canvasPx * 0.3f).toInt() + ringPx, (canvasPx * 0.8f).toInt()))
            .isEqualTo(purple.toArgb())
        assertThat(android.graphics.Color.alpha(bitmap.getPixel((canvasPx * 0.7f).toInt() + ringPx, (canvasPx * 0.2f).toInt())))
            .isEqualTo(0)
    }

    /**
     * Cadrage de la zone de detection : un glissement d'un coin a l'autre la
     * rend normalisee, quel que soit le sens du glissement.
     */
    @Test
    fun dragging_while_framing_reports_the_zone_normalized() {
        var zone: BoundingBox? = null
        setZoneCanvas(isDrawingZone = true, onZoneDrawn = { zone = it })

        // De bas-droite vers haut-gauche : le cadre ne depend pas du sens.
        composeRule.onNodeWithTag(CanvasTestTag).performTouchInput {
            swipe(percentOffset(0.7f, 0.8f), percentOffset(0.2f, 0.3f), durationMillis = 300)
        }
        composeRule.waitForIdle()

        val drawn = zone!!
        assertThat(drawn.left).isWithin(0.03f).of(0.2f)
        assertThat(drawn.top).isWithin(0.03f).of(0.3f)
        assertThat(drawn.right).isWithin(0.03f).of(0.7f)
        assertThat(drawn.bottom).isWithin(0.03f).of(0.8f)
    }

    @Test
    fun dragging_in_hold_mode_without_framing_draws_no_zone() {
        var zone: BoundingBox? = null
        setZoneCanvas(isDrawingZone = false, onZoneDrawn = { zone = it })

        composeRule.onNodeWithTag(CanvasTestTag).performTouchInput {
            swipe(percentOffset(0.2f, 0.3f), percentOffset(0.7f, 0.8f), durationMillis = 300)
        }
        composeRule.waitForIdle()

        // Sans demande explicite, un glissement en mode prises ne cadre rien.
        assertThat(zone).isNull()
    }

    @Test
    fun a_tiny_drag_is_not_taken_for_a_zone() {
        var zone: BoundingBox? = null
        setZoneCanvas(isDrawingZone = true, onZoneDrawn = { zone = it })

        composeRule.onNodeWithTag(CanvasTestTag).performTouchInput {
            swipe(percentOffset(0.50f, 0.50f), percentOffset(0.52f, 0.53f), durationMillis = 200)
        }
        composeRule.waitForIdle()

        // Un glissement de quelques pixels ne doit pas reduire l'analyse a rien.
        assertThat(zone).isNull()
    }

    private fun setZoneCanvas(isDrawingZone: Boolean, onZoneDrawn: (BoundingBox) -> Unit) {
        composeRule.setContent {
            AnnotationCanvas(
                blocDetail = detail(emptyList()),
                imageFile = MissingImage,
                mode = AnnotationMode.ADD_HOLD,
                selectedLimb = Limb.RIGHT_HAND,
                onHoldRequested = { _, _ -> },
                onHoldRemoved = {},
                onTraceCompleted = {},
                modifier = Modifier.size(CanvasSize),
                isDrawingZone = isDrawingZone,
                onZoneDrawn = onZoneDrawn,
            )
        }
    }

    /**
     * L'image exportee est le rendu de l'ecran, trace compris.
     *
     * Le calque d'export enregistre le contenu du canevas au moment du dessin. Si
     * seul le calque photo y etait capture — ou si le trace, dessine par un enfant,
     * y echappait — l'image partagee serait une photo nue, sans que rien ne plante.
     */
    @Test
    fun the_export_layer_captures_the_traced_beta_at_canvas_size() {
        val move = Movement(
            id = "mvt-1",
            blocId = "bloc-1",
            sequenceOrder = 0,
            limb = Limb.RIGHT_HAND,
            sourceHoldId = leftHold.id,
            targetHoldId = rightHold.id,
        )
        // Une prise au ras du bord inferieur : la troncature observee sur
        // emulateur emportait le bas de l'image, et un controle au seul centre
        // l'avait laissee passer.
        val bottomHold = hold("bas", x = 0.2f, y = 0.97f)
        val descent = Movement(
            id = "mvt-2",
            blocId = "bloc-1",
            sequenceOrder = 1,
            limb = Limb.LEFT_HAND,
            sourceHoldId = leftHold.id,
            targetHoldId = bottomHold.id,
        )
        lateinit var layer: GraphicsLayer

        composeRule.setContent {
            layer = rememberGraphicsLayer()
            AnnotationCanvas(
                blocDetail = BlocDetail(bloc(), listOf(leftHold, rightHold, bottomHold), listOf(move, descent)),
                imageFile = MissingImage,
                mode = AnnotationMode.TRACE_BETA,
                selectedLimb = Limb.RIGHT_HAND,
                onHoldRequested = { _, _ -> },
                onHoldRemoved = {},
                onTraceCompleted = {},
                modifier = Modifier.size(CanvasSize),
                exportLayer = layer,
            )
        }
        composeRule.waitForIdle()

        val bitmap = runBlocking(Dispatchers.Main) { layer.toImageBitmap() }
            .asAndroidBitmap()
            .copy(Bitmap.Config.ARGB_8888, false)
        val expectedPx = with(composeRule.density) { CanvasSize.roundToPx() }

        assertThat(bitmap.width).isEqualTo(expectedPx)
        assertThat(bitmap.height).isEqualTo(expectedPx)
        // Milieu du segment entre les deux prises, loin de tout contour de prise :
        // seul le trait de la main droite peut y avoir laisse une couleur.
        assertThat(bitmap.getPixel(expectedPx / 2, expectedPx / 2))
            .isEqualTo(BetaColors.RightHand.toArgb())
        // Sur le trait de la descente, a 2 % du bord : hors du marqueur d'arrivee
        // et du contour de la prise, seul le trait de la main gauche y passe.
        assertThat(bitmap.getPixel((expectedPx * 0.2f).toInt(), (expectedPx * 0.95f).toInt()))
            .isEqualTo(BetaColors.LeftHand.toArgb())
    }

    /**
     * Correction d'une prise mal visee.
     *
     * Signale par un premier essai sur appareil : une prise posee a cote etait
     * definitive, les boutons « annuler » de la barre d'outils ne portant que sur
     * les mouvements. L'appui sur une prise existante la retire donc desormais.
     */
    @Test
    fun tapping_an_existing_hold_removes_it_instead_of_adding_another() {
        var removed: String? = null
        var added = 0

        setCanvas(
            holds = listOf(leftHold),
            mode = AnnotationMode.ADD_HOLD,
            onHoldRequested = { _, _ -> added++ },
            onHoldRemoved = { removed = it },
        )

        composeRule.onNodeWithTag(CanvasTestTag).performTouchInput {
            click(percentOffset(0.2f, 0.5f))
        }
        composeRule.waitForIdle()

        assertThat(removed).isEqualTo("gauche")
        assertThat(added).isEqualTo(0)
    }

    /**
     * Le rayon de selection est plus petit que celui de magnetisation : sans
     * cela, poser une prise pres d'une autre deviendrait impossible, chaque appui
     * proche effacant la voisine au lieu d'ajouter.
     */
    @Test
    fun tapping_near_but_outside_a_hold_still_adds_one() {
        var removed: String? = null
        var added = 0

        setCanvas(
            holds = listOf(leftHold),
            mode = AnnotationMode.ADD_HOLD,
            onHoldRequested = { _, _ -> added++ },
            onHoldRemoved = { removed = it },
        )

        // 0,28 sur un canevas de 400 dp = 32 dp de la prise : au-dela des 22 dp
        // de selection, en deca des 40 dp de magnetisation.
        composeRule.onNodeWithTag(CanvasTestTag).performTouchInput {
            click(percentOffset(0.28f, 0.5f))
        }
        composeRule.waitForIdle()

        assertThat(removed).isNull()
        assertThat(added).isEqualTo(1)
    }

    // --- Utilitaires -------------------------------------------------------

    /**
     * Simule un balayage REALISTE : de nombreux petits deplacements plutot que
     * quelques sauts.
     *
     * Ce n'est pas cosmetique. `detectDragGestures` ne signale le debut du geste
     * qu'une fois le seuil de glissement (touch slop) franchi, et rapporte la
     * position atteinte a ce moment-la. Avec trois sauts de 20 %, le geste
     * "commencerait" tres loin du point d'appui et la premiere prise serait
     * manquee — un artefact du test, pas du produit : un vrai doigt franchit le
     * seuil a quelques dp de son point de depart, donc a l'interieur du rayon
     * d'attraction.
     */
    private fun TouchInjectionScope.traceLine(
        fromX: Float,
        toX: Float,
        y: Float,
        steps: Int = 24,
    ) {
        down(percentOffset(fromX, y))
        repeat(steps) { step ->
            val progress = (step + 1) / steps.toFloat()
            moveTo(percentOffset(fromX + (toX - fromX) * progress, y))
        }
        up()
    }

    private fun setCanvas(
        holds: List<Hold>,
        mode: AnnotationMode,
        onHoldRequested: (Float, Float) -> Unit = { _, _ -> },
        onHoldRemoved: (String) -> Unit = {},
        onTraceCompleted: (List<String>) -> Unit = {},
    ) {
        composeRule.setContent {
            AnnotationCanvas(
                blocDetail = detail(holds),
                imageFile = MissingImage,
                mode = mode,
                selectedLimb = Limb.RIGHT_HAND,
                onHoldRequested = onHoldRequested,
                onHoldRemoved = onHoldRemoved,
                onTraceCompleted = onTraceCompleted,
                modifier = Modifier.size(CanvasSize),
            )
        }
    }

    private fun detail(holds: List<Hold>) = BlocDetail(bloc(), holds, emptyList())

    /** Bloc carre : le canevas a alors exactement la taille demandee. */
    private fun bloc() = Bloc(
        id = "bloc-1",
        sessionId = "session-1",
        imagePath = "absent.jpg",
        imageWidth = 1000,
        imageHeight = 1000,
        grade = "7A",
        gradeScale = GradeScale.FRENCH,
        colorId = null,
        status = BlocStatus.PROJECT,
        attempts = 0,
        sentAt = null,
        feeling = null,
        createdAt = 0L,
    )

    private fun hold(id: String, x: Float, y: Float) = Hold(
        id = id,
        blocId = "bloc-1",
        box = BoundingBox(centerX = x, centerY = y, width = 0.08f, height = 0.08f),
        confidence = 1f,
        origin = HoldOrigin.MANUAL,
    )

    private companion object {
        val CanvasSize = 400.dp

        /** Le rendu de la photo n'est pas l'objet de ces tests. */
        val MissingImage = File("/topo/absent.jpg")
    }
}
