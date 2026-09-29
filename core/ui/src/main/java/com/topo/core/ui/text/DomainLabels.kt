package com.topo.core.ui.text

import androidx.annotation.StringRes
import com.topo.core.domain.model.DifficultyDisplay
import com.topo.core.domain.model.GradeScale
import com.topo.core.ui.R

/**
 * Libelles affiches des enumerations du domaine.
 *
 * Ici et non dans le domaine : un module JVM pur n'a pas acces aux ressources
 * Android, et un texte d'interface code en dur dans le domaine ne pourrait etre
 * ni accentue proprement ni traduit. Un seul endroit pour tous les ecrans qui
 * les affichent.
 */
@get:StringRes
val GradeScale.labelRes: Int
    get() = when (this) {
        GradeScale.FRENCH -> R.string.grade_scale_french
        GradeScale.V_SCALE -> R.string.grade_scale_v
    }

@get:StringRes
val DifficultyDisplay.labelRes: Int
    get() = when (this) {
        DifficultyDisplay.COLOR -> R.string.difficulty_display_color
        DifficultyDisplay.GRADE -> R.string.difficulty_display_grade
    }
