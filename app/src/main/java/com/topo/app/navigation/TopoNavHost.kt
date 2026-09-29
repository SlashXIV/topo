package com.topo.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.topo.feature.annotator.AnnotatorScreen
import com.topo.feature.camera.CameraCaptureScreen
import com.topo.feature.session.SessionDetailScreen
import com.topo.feature.session.SessionListScreen
import com.topo.feature.settings.SettingsScreen
import com.topo.feature.stats.StatsScreen

/**
 * Graphe de navigation de Topo.
 *
 * C'est le SEUL endroit du depot ou les modules de fonctionnalite se rencontrent.
 * `:feature:session` ignore l'existence de `:feature:annotator`, et reciproquement :
 * chacun expose des callbacks de navigation que ce graphe relie. Ajouter un ecran
 * ne touche donc aucun module existant.
 */
@Composable
fun TopoNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = SessionListRoute,
        modifier = modifier,
    ) {
        composable<SessionListRoute> {
            SessionListScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(SessionDetailRoute(sessionId))
                },
                onSettingsClick = { navController.navigate(SettingsRoute) },
                onStatsClick = { navController.navigate(StatsRoute) },
            )
        }

        composable<SessionDetailRoute> { entry ->
            val route: SessionDetailRoute = entry.toRoute()
            SessionDetailScreen(
                sessionId = route.sessionId,
                onBack = navController::popBackStack,
                onCaptureBloc = { sessionId -> navController.navigate(CaptureRoute(sessionId)) },
                onBlocClick = { blocId -> navController.navigate(AnnotatorRoute(blocId)) },
                // Meme parcours que la photo : un bloc importe s'ouvre sur sa
                // difficulte, puis sur ses prises.
                onBlocImported = { blocId ->
                    navController.navigate(AnnotatorRoute(blocId, askDifficulty = true))
                },
            )
        }

        composable<CaptureRoute> { entry ->
            val route: CaptureRoute = entry.toRoute()
            CameraCaptureScreen(
                sessionId = route.sessionId,
                onBlocCaptured = { blocId ->
                    // L'ecran de capture est retire de la pile avant d'ouvrir
                    // l'annotation : un retour depuis l'annotation doit ramener a la
                    // seance, pas rouvrir l'appareil photo sur un bloc deja pris.
                    navController.popBackStack()
                    navController.navigate(AnnotatorRoute(blocId, askDifficulty = true))
                },
            )
        }

        composable<StatsRoute> {
            StatsScreen(onBack = navController::popBackStack)
        }

        composable<SettingsRoute> {
            SettingsScreen(onBack = navController::popBackStack)
        }

        composable<AnnotatorRoute> { entry ->
            val route: AnnotatorRoute = entry.toRoute()
            AnnotatorScreen(
                blocId = route.blocId,
                askDifficulty = route.askDifficulty,
                onBack = navController::popBackStack,
            )
        }
    }
}
