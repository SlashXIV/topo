package com.topo.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genere le baseline profile de Topo : les classes et methodes parcourues ici
 * seront precompilees a l'installation au lieu d'etre interpretees au premier
 * lancement.
 *
 * Le parcours est celui d'un grimpeur qui ouvre l'application au pied du mur :
 * demarrage, liste des seances, puis une seance. Ne rien ajouter qui ne soit pas
 * sur ce chemin : un profil trop large precompile du code froid pour rien.
 *
 * Lancement : ./gradlew :app:generateBaselineProfile (appareil ou emulateur
 * branche, API 33+, avec au moins une seance enregistree).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()

        // La liste des seances est le premier ecran : on attend qu'elle soit
        // dessinee, puis on ouvre la premiere seance si l'appareil en a une.
        device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS)
        device.findObject(By.scrollable(true))?.children?.firstOrNull()?.let { session ->
            session.click()
            device.waitForIdle()
            device.pressBack()
        }
    }
}

internal const val TARGET_PACKAGE = "com.topo"
private const val WAIT_MS = 5_000L
