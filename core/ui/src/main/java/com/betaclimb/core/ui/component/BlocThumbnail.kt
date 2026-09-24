package com.betaclimb.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.betaclimb.core.ui.theme.BetaColors
import java.io.File

/**
 * Vignette d'un bloc dans une grille.
 *
 * Le ratio vient des dimensions ENREGISTREES a la creation du bloc, pas du bitmap :
 * une grille affiche des dizaines de vignettes, et decoder chaque photo rien que
 * pour connaitre sa forme provoquerait une rafale d'allocations au defilement.
 * C'est la raison d'etre des colonnes `image_width` / `image_height`.
 *
 * L'appui long ouvre la fiche de progression. Une corbeille permanente sur chaque
 * vignette encombrerait une grille dont l'action principale est d'ouvrir le bloc,
 * et placerait une action destructrice a portee de pouce dans une liste defilante.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BlocThumbnail(
    imageFile: File,
    aspectRatio: Float,
    grade: String?,
    modifier: Modifier = Modifier,
    statusLabel: String? = null,
    statusColor: Color = BetaColors.StatusProject,
    attempts: Int = 0,
    isCompleted: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(aspectRatio)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            AsyncImage(
                model = imageFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            if (grade != null) {
                GradeChip(
                    grade = grade,
                    isCompleted = isCompleted,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                )
            }

            if (statusLabel != null) {
                Badge(
                    text = statusLabel,
                    background = statusColor,
                    foreground = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }

            // Le compteur n'apparait qu'a partir du premier essai : afficher « 0 »
            // sur chaque bloc jamais touche ne dirait rien et chargerait la grille.
            if (attempts > 0) {
                Badge(
                    text = "$attempts essai${if (attempts > 1) "s" else ""}",
                    background = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    foreground = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                )
            }
        }
    }
}

/** Pastille de cotation. Pleine si le bloc est reussi, contour sinon. */
@Composable
fun GradeChip(
    grade: String,
    modifier: Modifier = Modifier,
    isCompleted: Boolean = false,
) {
    Badge(
        text = grade,
        background = if (isCompleted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        },
        foreground = if (isCompleted) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier,
    )
}

@Composable
private fun Badge(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = background,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = foreground,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
