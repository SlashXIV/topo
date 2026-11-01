package com.topo.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.topo.core.ui.R

// Trois polices OFL embarquees : l'appli reste hors ligne, et aucune ne depend
// des polices du telephone. Licences dans design/fonts/.

private fun variable(res: Int, weight: Int) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Affiche condensee : les titres imprimes du topo. */
val PosterFamily = FontFamily(
    variable(R.font.big_shoulders_display, 800),
    variable(R.font.big_shoulders_display, 900),
)

/** Texte courant. */
val TextFamily = FontFamily(
    variable(R.font.instrument_sans, 400),
    variable(R.font.instrument_sans, 500),
    variable(R.font.instrument_sans, 600),
    variable(R.font.instrument_sans, 700),
)

/**
 * Cotes : chiffres, dates, essais. A chasse fixe, pour qu'un compteur qui passe
 * de 9 a 10 ne fasse pas bouger ce qui l'entoure.
 */
val CoteFamily = FontFamily(
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

// Interligne rogne en haut et en bas : un titre d'affiche se cale sur son filet
// sans la marge que la police reserve aux accents.
private val Trimmed = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

/** Styles nommes par usage, pas par taille. */
@Immutable
data class TopoType(
    /** Titre d'ecran, en capitales. */
    val poster: TextStyle = TextStyle(
        fontFamily = PosterFamily, fontWeight = FontWeight(900), fontSize = 52.sp,
        lineHeight = 0.88.em, letterSpacing = (-0.005).em, lineHeightStyle = Trimmed,
    ),
    /** Titre d'affiche plus court (lieu d'une seance, nom d'un bloc). */
    val posterSmall: TextStyle = TextStyle(
        fontFamily = PosterFamily, fontWeight = FontWeight(900), fontSize = 38.sp,
        lineHeight = 0.9.em, lineHeightStyle = Trimmed,
    ),
    /** Grand chiffre (metriques, recapitulatif). */
    val figure: TextStyle = TextStyle(
        fontFamily = PosterFamily, fontWeight = FontWeight(900), fontSize = 40.sp,
        lineHeight = 0.9.em, fontFeatureSettings = "tnum", lineHeightStyle = Trimmed,
    ),
    val heading: TextStyle = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 1.25.em,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 1.45.em,
    ),
    val bodySmall: TextStyle = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 1.4.em,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = TextFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 1.25.em,
    ),
    /** Cotes en capitales : dates, compteurs, eyebrows. */
    val cote: TextStyle = TextStyle(
        fontFamily = CoteFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp,
        letterSpacing = 0.08.em, fontFeatureSettings = "tnum",
    ),
    /** Libelle de bouton, en capitales. */
    val action: TextStyle = TextStyle(
        fontFamily = PosterFamily, fontWeight = FontWeight(800), fontSize = 18.sp,
        letterSpacing = 0.05.em, lineHeight = 1.em, lineHeightStyle = Trimmed,
    ),
)
