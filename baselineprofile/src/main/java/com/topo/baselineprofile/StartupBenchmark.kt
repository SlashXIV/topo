package com.topo.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Temps de demarrage a froid, SANS puis AVEC le baseline profile.
 *
 * C'est la mesure qui justifie le profil : sans elle, l'affirmer utile serait
 * une opinion (principe 5 de la feuille de route). Les deux modes tournent dans
 * la meme execution, sur le meme appareil, pour que seule la compilation
 * differe.
 *
 * Sur emulateur, les chiffres absolus sont indicatifs — seul l'ECART compte ; la
 * mesure de reference se fait sur telephone physique.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutProfile() = coldStart(CompilationMode.None())

    @Test
    fun coldStartWithProfile() = coldStart(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun coldStart(compilation: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        iterations = ITERATIONS,
        startupMode = StartupMode.COLD,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }

    private companion object {
        const val ITERATIONS = 10
    }
}
