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
        // ONNX Runtime reduit, construit par third_party/onnxruntime/build_reduced_aar.sh.
        // Limite a ses versions « -topo » : rien d'autre ne peut etre resolu ici.
        maven {
            url = uri("third_party/onnxruntime/dist/maven")
            content { includeVersionByRegex("com\\.microsoft\\.onnxruntime","onnxruntime-android", ".*-topo") }
        }
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
include(":feature:relief")

include(":vision")

// Mesure et optimisation du demarrage : instrumente :app depuis l'exterieur.
include(":baselineprofile")
