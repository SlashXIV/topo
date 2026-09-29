package com.topo.feature.annotator

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.topo.core.domain.model.Limb
import org.junit.Test

class EditorStateSaverTest {

    @Test
    fun mode_and_limb_survive_a_process_death() {
        val handle = SavedStateHandle()

        EditorStateSaver.save(
            AnnotatorUiState(mode = AnnotationMode.TRACE_BETA, selectedLimb = Limb.LEFT_FOOT),
            handle,
        )
        // Le processus meurt : seul le contenu du handle est rendu au nouveau.
        val restored = EditorStateSaver.restore(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))

        assertThat(restored.mode).isEqualTo(AnnotationMode.TRACE_BETA)
        assertThat(restored.selectedLimb).isEqualTo(Limb.LEFT_FOOT)
    }

    @Test
    fun a_half_done_gesture_is_not_restored() {
        val handle = SavedStateHandle()

        EditorStateSaver.save(AnnotatorUiState(isPickingColor = true, isDrawingZone = true), handle)
        val restored = EditorStateSaver.restore(handle)

        // Retrouver la pipette active a froid ferait qu'un appui ne retire pas la
        // prise que l'ecran semble proposer de retirer.
        assertThat(restored.isPickingColor).isFalse()
        assertThat(restored.isDrawingZone).isFalse()
    }

    @Test
    fun a_fresh_editor_starts_on_holds_with_the_right_hand() {
        val restored = EditorStateSaver.restore(SavedStateHandle())

        assertThat(restored.mode).isEqualTo(AnnotationMode.ADD_HOLD)
        assertThat(restored.selectedLimb).isEqualTo(Limb.RIGHT_HAND)
    }
}
