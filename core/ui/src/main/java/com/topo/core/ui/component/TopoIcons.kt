package com.topo.core.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Icones de Topo, tracees au trait sur une grille de 24, comme les croquis d'un
 * topo. Elles remplacent `material-icons-extended` : quinze icones dessinees ici
 * plutot qu'une bibliotheque de plusieurs milliers de vecteurs au style Material.
 *
 * Les cercles sont ecrits en deux arcs, le format des chemins vectoriels Android
 * n'ayant pas de primitive cercle.
 */
object TopoIcons {
    val Back = line("back", "M15 5l-7 7 7 7", "M8 12h12")
    val Close = line("close", "M6 6l12 12", "M18 6L6 18")
    val Add = line("add", "M12 5v14", "M5 12h14")
    val Check = line("check", "M5 12.5l4 4L19 7")
    val Up = line("up", "M6 14l6-6 6 6")
    val Down = line("down", "M6 10l6 6 6-6")
    val Delete = line("delete", "M5 7h14", "M9 7V4h6v3", "M7 7l1 13h8l1-13", "M10.5 11v5", "M13.5 11v5")
    val Chart = line("chart", "M4 20h16", "M7 16v-5", "M12 16V6", "M17 16v-8")
    // Des curseurs plutot qu'un engrenage : au trait fin, l'engrenage se lisait comme un soleil.
    val Settings = line(
        "settings",
        "M4 7h9", "M19 7h1", "M4 17h3", "M13 17h7",
        circle(16f, 7f, 2.5f), circle(10f, 17f, 2.5f),
    )
    val Camera = line("camera", "M4 8h3l2-3h6l2 3h3v11H4z", circle(12f, 13f, 3.5f))
    val Gallery = line("gallery", "M4 5h16v14H4z", "M4 16l5-5 4 4 2-2 5 5", circle(15.5f, 9f, 1.3f))
    val Share = line(
        "share",
        circle(6f, 12f, 2.2f), circle(18f, 6f, 2.2f), circle(18f, 18f, 2.2f),
        "M8 11l8-4", "M8 13l8 4",
    )
    val Relief = line("relief", "M12 3l8 4.5v9L12 21l-8-4.5v-9z", "M12 12l8-4.5", "M12 12v9", "M12 12L4 7.5")
    val Play = line("play", "M7 5l12 7-12 7z")
    val Pause = line("pause", "M8 5v14", "M16 5v14")
    val Replay = line("replay", "M4 12a8 8 0 1 0 2.3-5.7", "M4 4v4.5h4.5")
    val SkipNext = line("skip_next", "M6 6l9 6-9 6z", "M18 6v12")
    val SkipPrevious = line("skip_previous", "M18 6l-9 6 9 6z", "M6 6v12")
    // Pipette : on touche une prise pour en garder la couleur.
    val Pipette = line("pipette", "M14.5 5.5l4 4", "M16.5 7.5L7 17l-3 1 1-3 9.5-9.5", "M15 4l1.5-1.5a2.1 2.1 0 0 1 3 3L18 7")
    val Undo = line("undo", "M9 7L4 12l5 5", "M4 12h11a5 5 0 0 1 0 10h-2")
}

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private fun line(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }
        .build()

private const val STROKE = 1.6f
