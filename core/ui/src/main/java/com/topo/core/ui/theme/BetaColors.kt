package com.topo.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette du trace d'annotation.
 *
 * Volontairement hors du `ColorScheme` Material : ces couleurs ne sont pas
 * thematiques mais SEMANTIQUES — elles codent quel membre est engage, et doivent
 * rester identiques en theme clair comme en theme sombre. Un grimpeur qui relit
 * son beta doit reconnaitre "main gauche" a la couleur, pas la deduire du contexte.
 *
 * Les mains s'opposent sur l'axe bleu / orange, qui reste discriminable pour les
 * deficiences de la vision des couleurs les plus courantes (deuteranopie,
 * protanopie). Les pieds utilisent violet / vert, moins sûrs sur cet axe : leurs
 * luminosites sont donc nettement separees, et la forme du trace (trait continu
 * pour une main, pointille pour un pied) porte l'information en redondance.
 */
object BetaColors {
    val LeftHand = Color(0xFF2E86AB)
    val RightHand = Color(0xFFF2A65A)
    val LeftFoot = Color(0xFF8E6C99)
    val RightFoot = Color(0xFF5FAD56)

    /** Contour des prises non engagees dans le trace. */
    val Hold = Color(0xCCFFFFFF)

    /** Halo de la prise actuellement magnetisee. */
    val SnapHalo = Color(0xFFFFE066)

    /** Ligne du scan de detection : un cyan froid, absent des prises comme des membres. */
    val Scan = Color(0xFF5FE3FF)

    // Statuts de progression. Progression volontairement lisible sans lire le
    // texte : gris (repere) -> ambre (en travail) -> vert (reussi), le flash se
    // distinguant du reussi par une teinte plus vive.
    val StatusProject = Color(0xFF6E6E6E)
    val StatusWorking = Color(0xFFD98C2B)
    val StatusFlashed = Color(0xFF21A179)
    val StatusSent = Color(0xFF3F7D55)
}
