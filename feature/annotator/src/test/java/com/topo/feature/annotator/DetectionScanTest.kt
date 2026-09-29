package com.topo.feature.annotator

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DetectionScanTest {

    @Test
    fun the_scan_climbs_from_the_foot_of_the_zone_to_its_top() {
        // Origine en haut de la photo : le pied du mur a la plus grande ordonnee.
        assertThat(scanLineY(0f, zoneTop = 0.2f, zoneBottom = 0.9f)).isWithin(1e-6f).of(0.9f)
        assertThat(scanLineY(0.5f, zoneTop = 0.2f, zoneBottom = 0.9f)).isWithin(1e-6f).of(0.55f)
        assertThat(scanLineY(1f, zoneTop = 0.2f, zoneBottom = 0.9f)).isWithin(1e-6f).of(0.2f)
    }

    @Test
    fun a_progress_out_of_range_stays_within_the_zone() {
        assertThat(scanLineY(-1f, 0f, 1f)).isEqualTo(1f)
        assertThat(scanLineY(2f, 0f, 1f)).isEqualTo(0f)
    }

    @Test
    fun a_hold_appears_once_the_line_has_passed_it() {
        // La ligne monte : une prise basse (grande ordonnee) est revelee avant une haute.
        assertThat(isRevealed(holdY = 0.8f, lineY = 0.6f)).isTrue()
        assertThat(isRevealed(holdY = 0.4f, lineY = 0.6f)).isFalse()
    }
}
