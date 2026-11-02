package com.topo.core.ui.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import coil.request.ImageRequest
import coil.size.Size
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.HoldKind
import kotlin.math.roundToInt
import androidx.compose.ui.res.pluralStringResource
import com.topo.core.ui.R
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.style.TextOverflow
import com.topo.core.ui.theme.TopoTheme
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.topo.core.ui.theme.BetaColors
import java.io.File
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/**
 * Vignette d'un bloc dans une grille.
 *
 * Le ratio vient des dimensions ENREGISTREES a la creation du bloc, pas du bitmap :
 * une grille affiche des dizaines de vignettes, et decoder chaque photo rien que
 * pour connaitre sa forme provoquerait une rafale d'allocations au defilement.
 * C'est la raison d'etre des colonnes `image_width` / `image_height`.
 *
 * Avec un cadre de bloc, la vignette est RECADREE sur lui et ses prises portent
 * le halo de sa difficulte : dans une seance, on reconnait chaque bloc — et la
 * couleur de ses prises — sans l'ouvrir. La photo d'un pan embarque presque
 * toujours des blocs voisins ; sans recadrage, la vignette les montrerait tous.
 *
 * L'appui long ouvre la feuille de progression. Une corbeille permanente sur chaque
 * vignette encombrerait une grille dont l'action principale est d'ouvrir le bloc,
 * et placerait une action destructrice a portee de pouce dans une liste defilante.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BlocThumbnail(
    imageFile: File,
    aspectRatio: Float,
    grade: String?,
    modifier: Modifier = Modifier,
    /** Couleur du circuit, si le bloc en porte une. */
    circuitColor: Color? = null,
    circuitName: String? = null,
    statusLabel: String? = null,
    /** Couleur du tampon ; par defaut l'encre secondaire. */
    statusColor: Color = Color.Unspecified,
    attempts: Int = 0,
    isCompleted: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    /** Cadre du bloc sur la photo ; null pour la photo entiere. */
    zone: BoundingBox? = null,
    /** Prises du bloc, dessinees par-dessus la photo. Les volumes sont ignores. */
    holds: List<Hold> = emptyList(),
) {
    val context = LocalContext.current
    val request = remember(imageFile, zone) {
        ImageRequest.Builder(context)
            .data(imageFile)
            .apply {
                // Recadree, la vignette agrandit la photo : Coil la demanderait a la
                // taille agrandie, 15 Mo par vignette pour un bloc au tiers de la
                // photo. 1024 pixels suffisent a une vignette, meme recadree.
                if (zone != null) size(Size(MAX_DECODED_SIDE, MAX_DECODED_SIDE))
            }
            .build()
    }

    Column(
        modifier = modifier
            .fiche()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                // Forme CONSTANTE, celle de la photo, cadre ou non : une vignette
                // aussi haute que le bloc qu'elle montre casserait la grille —
                // constate avec un cadre etroit et haut.
                .aspectRatio(aspectRatio)
                .clipToBounds(),
        ) {
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = if (zone == null) Modifier.fillMaxSize() else Modifier.cropTo(zone, aspectRatio),
                contentScale = if (zone == null) ContentScale.Crop else ContentScale.FillBounds,
            )
            if (holds.isNotEmpty()) {
                HoldsOverlay(holds, zone, aspectRatio, circuitColor ?: BetaColors.Hold, Modifier.fillMaxSize())
            }
            // Le compteur n'apparait qu'a partir du premier essai : afficher « 0 »
            // sur chaque bloc jamais touche ne dirait rien et chargerait la grille.
            if (attempts > 0) {
                TopoText(
                    text = pluralStringResource(R.plurals.attempt_count, attempts, attempts),
                    style = TopoTheme.type.cote,
                    color = TopoTheme.colors.ink,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(TopoTheme.colors.paper.copy(alpha = 0.9f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Rule()
        // Legende de la fiche : ce que les reglages mettent en avant, puis le
        // statut en tampon. L'appelant decide : la vignette ne connait pas les
        // preferences, elle affiche ce qu'on lui donne.
        // En rangee qui se replie : sur une fiche etroite (deux colonnes a 320 dp),
        // le tampon passe sous le nom au lieu de le reduire a « ... ».
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 34.dp)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (circuitColor != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(circuitColor)
                    TopoText(
                        circuitName.orEmpty(),
                        style = TopoTheme.type.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (grade != null) {
                GradeChip(grade, isCompleted = isCompleted)
            }
            if (statusLabel != null) {
                StatusStamp(statusLabel, statusColor.takeOrElse { TopoTheme.colors.ink2 })
            }
        }
    }
}

/** Cotation en cote. Encadree d'encre pleine si le bloc est reussi, de filet sinon. */
@Composable
fun GradeChip(
    grade: String,
    modifier: Modifier = Modifier,
    isCompleted: Boolean = false,
) {
    val colors = TopoTheme.colors
    TopoText(
        text = grade,
        style = TopoTheme.type.cote,
        color = if (isCompleted) colors.onInk else colors.ink,
        maxLines = 1,
        modifier = modifier
            .then(if (isCompleted) Modifier.background(colors.ink) else Modifier.border(1.dp, colors.ink))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/**
 * Placement de la photo dans la vignette : largeur et hauteur affichees de la
 * photo, et son decalage.
 */
private data class PhotoPlacement(val width: Float, val height: Float, val x: Float, val y: Float) {
    fun toFrame(normalizedX: Float, normalizedY: Float) = Offset(x + normalizedX * width, y + normalizedY * height)
}

/**
 * Agrandit la photo juste assez pour que [zone] couvre la vignette, et la centre
 * sur le bloc. Sans deborder : pres d'un bord, la vignette s'arrete au bord de la
 * photo plutot que de montrer du vide. Photo et prises passent par ce seul calcul,
 * pour qu'un halo tombe toujours sur sa prise.
 */
private fun placePhoto(frameWidth: Float, frameHeight: Float, aspectRatio: Float, zone: BoundingBox?): PhotoPlacement {
    if (zone == null) return PhotoPlacement(frameWidth, frameHeight, 0f, 0f)
    val width = maxOf(frameWidth / zone.width, frameHeight * aspectRatio / zone.height)
    val height = width / aspectRatio
    val x = (frameWidth / 2f - zone.centerX * width).coerceIn(frameWidth - width, 0f)
    val y = (frameHeight / 2f - zone.centerY * height).coerceIn(frameHeight - height, 0f)
    return PhotoPlacement(width, height, x, y)
}

private fun Modifier.cropTo(zone: BoundingBox, aspectRatio: Float): Modifier = layout { measurable, constraints ->
    val placement = placePhoto(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), aspectRatio, zone)
    val placeable = measurable.measure(
        Constraints.fixed(placement.width.roundToInt(), placement.height.roundToInt()),
    )
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(placement.x.roundToInt(), placement.y.roundToInt())
    }
}

/**
 * Prises du bloc sur la vignette, au halo de sa difficulte (blanc sans couleur).
 * Les volumes sont omis : sur une vignette, ils encombreraient sans rien dire du
 * bloc.
 */
@Composable
private fun HoldsOverlay(holds: List<Hold>, zone: BoundingBox?, aspectRatio: Float, color: Color, modifier: Modifier) {
    Spacer(
        modifier = modifier.drawWithCache {
            val ring = Stroke(width = 2.dp.toPx())
            val outline = Stroke(width = 1.dp.toPx())
            val radius = 6.dp.toPx()
            val placement = placePhoto(size.width, size.height, aspectRatio, zone)
            onDrawBehind {
                for (hold in holds) {
                    if (hold.kind == HoldKind.VOLUME) continue
                    // Une prise hors du cadre appartient a un bloc voisin, meme si
                    // le recadrage laisse voir un peu de photo autour du bloc.
                    if (zone != null && !zone.containsCenterOf(hold.box)) continue
                    drawHoldHalo(placement.toFrame(hold.box.centerX, hold.box.centerY), radius, color, ring, outline)
                }
            }
        },
    )
}

private fun BoundingBox.containsCenterOf(box: BoundingBox) =
    box.centerX in left..right && box.centerY in top..bottom

private const val MAX_DECODED_SIDE = 1024
