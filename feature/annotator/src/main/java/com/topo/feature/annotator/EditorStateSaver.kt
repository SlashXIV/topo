package com.topo.feature.annotator

import androidx.lifecycle.SavedStateHandle
import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.Limb

/**
 * Ce qui, de l'editeur, doit survivre a la terminaison du processus.
 *
 * Android tue une application en arriere-plan quand il manque de memoire — cas
 * banal ici, ou l'on quitte Topo pour prendre une photo ou choisir dans la
 * galerie. La navigation et la base reviennent d'elles-memes ; l'etat de
 * l'editeur, lui, etait perdu : constate sur emulateur, le mode « Tracer » et son
 * membre revenaient a « Prises ».
 *
 * Sont sauves le mode, le membre et la zone de detection. PAS la pipette ni le
 * cadrage en cours : ce sont des gestes a moitie faits, et les retrouver actifs a
 * froid ferait qu'un appui ne fait pas ce que l'ecran laisse croire.
 */
internal object EditorStateSaver {

    private const val MODE = "editor.mode"
    private const val LIMB = "editor.limb"
    private const val ZONE = "editor.zone"

    fun restore(handle: SavedStateHandle): AnnotatorUiState = AnnotatorUiState(
        mode = handle.get<String>(MODE)
            ?.let { name -> AnnotationMode.entries.firstOrNull { it.name == name } }
            ?: AnnotationMode.ADD_HOLD,
        selectedLimb = handle.get<String>(LIMB)
            ?.let { name -> Limb.entries.firstOrNull { it.name == name } }
            ?: Limb.RIGHT_HAND,
        detectionZone = handle.get<FloatArray>(ZONE)
            ?.takeIf { it.size == 4 }
            ?.let { BoundingBox(it[0], it[1], it[2], it[3]) },
    )

    fun save(state: AnnotatorUiState, handle: SavedStateHandle) {
        // Par NOM et non par ordinal : reordonner une enumeration ne doit pas faire
        // revenir l'utilisateur dans un autre mode que celui qu'il avait quitte.
        handle[MODE] = state.mode.name
        handle[LIMB] = state.selectedLimb.name
        handle[ZONE] = state.detectionZone?.let {
            floatArrayOf(it.centerX, it.centerY, it.width, it.height)
        }
    }
}
