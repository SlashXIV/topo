package com.topo.app.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.topo.feature.annotator.AnnotatorScreen
import com.topo.feature.camera.CameraCaptureScreen
import com.topo.feature.relief.ReliefScreen
import com.topo.feature.session.SessionDetailScreen
import com.topo.feature.session.SessionListScreen
import com.topo.feature.settings.SettingsScreen
import com.topo.feature.stats.StatsScreen
import com.topo.core.ui.motion.LocalMotionLevel
import com.topo.core.ui.motion.Motion
import com.topo.core.ui.motion.MotionLevel
import com.topo.core.ui.motion.pageTurn
import com.topo.core.ui.theme.TopoTheme

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
    // Sens de la navigation, lu pendant la composition : la page doit le connaitre
    // des la premiere image. Retour = l'ecran affiche a deja ete visite.
    val current by navController.currentBackStackEntryAsState()
    val visited = remember { mutableListOf<String>() }
    // Un State, pas une valeur : l'ecran qui part garde les lambdas de son ancien
    // graphe, elles doivent pourtant lire le sens de la navigation en cours.
    val isPop = remember { mutableStateOf(false) }
    current?.id?.let { id ->
        if (visited.lastOrNull() != id) {
            val index = visited.indexOf(id)
            isPop.value = index >= 0
            if (index >= 0) visited.subList(index + 1, visited.size).clear() else visited += id
        }
    }
    // La page tournee se dessine elle-meme (pageTurn) ; les transitions du graphe
    // ne font que garder les deux ecrans composes le temps qu'elle passe.
    val level = LocalMotionLevel.current
    val enter: EnterTransition = when (level) {
        MotionLevel.FULL -> fadeIn(tween(Motion.TRANSITION_MS), initialAlpha = 1f)
        MotionLevel.REDUCED -> fadeIn(tween(Motion.CALM_MS))
        MotionLevel.NONE -> EnterTransition.None
    }
    val exit: ExitTransition = when (level) {
        MotionLevel.FULL -> fadeOut(tween(Motion.TRANSITION_MS), targetAlpha = 1f)
        MotionLevel.REDUCED -> fadeOut(tween(Motion.CALM_MS))
        MotionLevel.NONE -> ExitTransition.None
    }

    NavHost(
        navController = navController,
        startDestination = SessionListRoute,
        modifier = modifier,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { enter },
        popExitTransition = { exit },
    ) {
        page<SessionListRoute>(isPop) {
            SessionListScreen(
                onSessionClick = { sessionId ->
                    navController.navigate(SessionDetailRoute(sessionId))
                },
                onSettingsClick = { navController.navigate(SettingsRoute) },
                onStatsClick = { navController.navigate(StatsRoute) },
            )
        }

        page<SessionDetailRoute>(isPop) { entry ->
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

        page<CaptureRoute>(isPop) { entry ->
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

        page<StatsRoute>(isPop) {
            StatsScreen(onBack = navController::popBackStack)
        }

        page<SettingsRoute>(isPop) {
            SettingsScreen(onBack = navController::popBackStack)
        }

        page<AnnotatorRoute>(isPop) { entry ->
            val route: AnnotatorRoute = entry.toRoute()
            AnnotatorScreen(
                blocId = route.blocId,
                askDifficulty = route.askDifficulty,
                onBack = navController::popBackStack,
                onOpenRelief = { blocId -> navController.navigate(ReliefRoute(blocId)) },
            )
        }

        page<ReliefRoute>(isPop) { entry ->
            val route: ReliefRoute = entry.toRoute()
            ReliefScreen(blocId = route.blocId, onBack = navController::popBackStack)
        }
    }
}

/** Une destination dont la page se tourne a l'arrivee et se replie au retour. */
private inline fun <reified T : Any> NavGraphBuilder.page(
    isPop: State<Boolean>,
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        Box(Modifier.fillMaxSize().pageTurn(this, isPop.value).background(TopoTheme.colors.paper)) { content(entry) }
    }
}
