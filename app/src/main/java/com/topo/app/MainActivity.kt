package com.topo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.topo.app.navigation.TopoNavHost
import com.topo.core.domain.repository.SettingsRepository
import com.topo.core.ui.theme.TopoTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Activite unique de l'application : elle porte le theme et le graphe de
 * navigation, rien d'autre.
 *
 * L'ecran de diagnostic natif du Sprint 0 a disparu d'ici. Sa valeur de preuve
 * est portee par `NativeBridgeInstrumentedTest` et par le README : du code de
 * validation n'a pas a vivre dans l'application livree a l'utilisateur.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Volatile
    private var settingsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Avant super.onCreate : la bibliotheque doit remplacer le theme de
        // demarrage par le theme applicatif pendant que la fenetre se cree. Il reste
        // affiche jusqu'a la lecture des reglages : l'ambiance du mouvement doit etre
        // connue avant la premiere entree animee.
        installSplashScreen().setKeepOnScreenCondition { !settingsLoaded }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by remember { settingsRepository.observeSettings() }.collectAsState(initial = null)
            val loaded = settings ?: return@setContent
            settingsLoaded = true
            TopoTheme(motion = loaded.motion) {
                Box(Modifier.fillMaxSize().background(TopoTheme.colors.paper)) {
                    TopoNavHost()
                }
            }
        }
    }
}
