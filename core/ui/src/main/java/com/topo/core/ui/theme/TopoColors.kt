package com.topo.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.topo.core.domain.model.BlocStatus

/**
 * Couleurs de l'interface : un papier, une encre, un accent. C'est la metaphore
 * du topo imprime — pas d'ombre ni d'elevation, des filets d'encre separent les
 * choses.
 *
 * Ces jetons remplacent le `ColorScheme` Material, et les couleurs dynamiques du
 * fond d'ecran sont abandonnees : elles effacaient la palette de Topo sur
 * Android 12 et plus. Chaque couple texte / fond est verifie par
 * `PaletteContrastTest`.
 */
@Immutable
data class TopoColors(
    val isDark: Boolean,
    /** Fond de page. */
    val paper: Color,
    /** Zones en retrait : fiches, feuilles, pistes de barres. */
    val paper2: Color,
    /** Texte et filets principaux. */
    val ink: Color,
    /** Texte secondaire : cotes, aides. */
    val ink2: Color,
    /** Filets doux, separateurs de liste. Decoratif, jamais du texte. */
    val rule: Color,
    /** Accent lisible en texte (eyebrows, valeurs mises en avant). */
    val accent: Color,
    /** L'orange « main droite » d'origine, pour les traits et les aplats. */
    val accentFill: Color,
    /** Texte pose sur un aplat d'encre (bouton principal, etiquette choisie). */
    val onInk: Color,
    val error: Color,
    /** Voile sous une feuille ou un dialogue. */
    val scrim: Color,
    // Statuts imprimes en tampon, lisibles en petit texte sur chaque papier :
    // gris, ambre, bleu « eclair » pour le flash, vert pour le reussi. Flash et
    // reussi etaient deux verts, confondus en protanopie (cf. PaletteContrastTest).
    val statusProject: Color,
    val statusWorking: Color,
    val statusFlashed: Color,
    val statusSent: Color,
) {
    fun status(status: BlocStatus): Color = when (status) {
        BlocStatus.PROJECT -> statusProject
        BlocStatus.WORKING -> statusWorking
        BlocStatus.FLASHED -> statusFlashed
        BlocStatus.SENT -> statusSent
    }
}

/** Les palettes de Topo : papier clair (par defaut) et ardoise de nuit. */
object TopoPalettes {
    val Paper = TopoColors(
        isDark = false,
        paper = Color(0xFFEEEAE0),
        paper2 = Color(0xFFE4DFD2),
        ink = Color(0xFF1B1A17),
        ink2 = Color(0xFF55524B),
        rule = Color(0xFFC9C3B4),
        accent = Color(0xFF96490C),
        accentFill = Color(0xFFF2A65A),
        onInk = Color(0xFFEEEAE0),
        error = Color(0xFFB3261E),
        scrim = Color(0x73000000),
        statusProject = Color(0xFF5E5B55),
        statusWorking = Color(0xFF8F520B),
        statusFlashed = Color(0xFF1F5F8B),
        statusSent = Color(0xFF2E6340),
    )

    val Night = TopoColors(
        isDark = true,
        paper = Color(0xFF15171A),
        paper2 = Color(0xFF1E2125),
        ink = Color(0xFFECE6D8),
        ink2 = Color(0xFFA6A193),
        rule = Color(0xFF34383E),
        accent = Color(0xFFF2A65A),
        accentFill = Color(0xFFF2A65A),
        onInk = Color(0xFF15171A),
        error = Color(0xFFFF8A80),
        scrim = Color(0x99000000),
        statusProject = Color(0xFFA6A193),
        statusWorking = Color(0xFFE9A24A),
        statusFlashed = Color(0xFF6CB4E0),
        statusSent = Color(0xFF83C79A),
    )

    val all = listOf("paper" to Paper, "night" to Night)
}
