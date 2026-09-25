package com.topo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.topo.app.navigation.TopoNavHost
import com.topo.core.ui.theme.TopoTheme
import dagger.hilt.android.AndroidEntryPoint

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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Avant super.onCreate : la bibliotheque doit remplacer le theme de
        // demarrage par le theme applicatif pendant que la fenetre se cree.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TopoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    TopoNavHost()
                }
            }
        }
    }
}
