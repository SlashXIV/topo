pluginManagement {
    // Le build composite build-logic fournit les plugins de convention "topo.*".
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Aucun module ne declare ses propres depots : tout passe par ici.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Topo"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:ui")
include(":core:common")
include(":core:domain")
include(":core:data")

include(":feature:camera")
include(":feature:annotator")
include(":feature:session")
include(":feature:settings")
include(":feature:stats")

include(":vision")
