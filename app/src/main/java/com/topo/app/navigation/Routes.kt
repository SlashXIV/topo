package com.topo.app.navigation

import kotlinx.serialization.Serializable

/**
 * Destinations de l'application.
 *
 * Routes **typees** : Navigation Compose 2.8+ serialise ces objets, ce qui
 * supprime les chaines de format (`"session/{id}"`) et leur analyse manuelle.
 * Le compilateur verifie qu'une destination recoit bien ses arguments, et
 * renommer un champ casse la compilation au lieu de produire un argument nul a
 * l'execution.
 */

@Serializable
data object SessionListRoute

@Serializable
data class SessionDetailRoute(val sessionId: String)

@Serializable
data class CaptureRoute(val sessionId: String)

@Serializable
data class AnnotatorRoute(val blocId: String)

@Serializable
data object SettingsRoute

@Serializable
data object StatsRoute
