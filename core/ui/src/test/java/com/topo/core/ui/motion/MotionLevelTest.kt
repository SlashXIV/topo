package com.topo.core.ui.motion

import com.google.common.truth.Truth.assertThat
import com.topo.core.domain.model.MotionPreference
import org.junit.Test

class MotionLevelTest {

    @Test
    fun `supprimer les animations prime sur l'ambiance expressive`() {
        assertThat(motionLevelOf(0f, MotionPreference.EXPRESSIVE)).isEqualTo(MotionLevel.NONE)
        assertThat(motionLevelOf(0f, MotionPreference.CALM)).isEqualTo(MotionLevel.NONE)
    }

    @Test
    fun `l'ambiance decide quand le systeme anime`() {
        assertThat(motionLevelOf(1f, MotionPreference.EXPRESSIVE)).isEqualTo(MotionLevel.FULL)
        assertThat(motionLevelOf(0.5f, MotionPreference.CALM)).isEqualTo(MotionLevel.REDUCED)
    }
}
