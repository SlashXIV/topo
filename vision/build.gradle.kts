// Module hybride Kotlin + C++ (NDK).
//
// Il encapsule a lui seul toute la complexite native du projet : JNI, CMake,
// conformite 16 Ko, ONNX Runtime et XNNPACK. Le reste de l'application ne depend
// que de l'interface HoldDetector du domaine et ignore totalement qu'une
// bibliotheque native existe.
plugins {
    id("topo.android.library")
    id("topo.android.native")
    id("topo.android.hilt")
}

// L'AAR d'ONNX Runtime ne publie pas de module Prefab : CMake ne sait pas y
// trouver seul les en-tetes et la .so contre laquelle lier. Une configuration
// dediee resout l'AAR brut, et une tache en extrait ce dont CMake a besoin.
// La version reste celle du catalogue : l'application empaquette exactement la
// .so contre laquelle le moteur a ete lie.
val onnxRuntimeAar: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

val onnxRuntimeRoot = layout.buildDirectory.dir("onnxruntime")

val extractOnnxRuntime by tasks.registering(Sync::class) {
    from({ zipTree(onnxRuntimeAar.singleFile) }) {
        include("headers/**", "jni/**")
    }
    into(onnxRuntimeRoot)
}

android {
    namespace = "com.topo.vision"

    // Les tests instrumentes tournent en debug, donc le code natif en -O0 : bien
    // pour deboguer, faux pour mesurer une latence. -Ptopo.testBuildType=release
    // les fait tourner contre le build optimise, celui qui sera livre.
    testBuildType = providers.gradleProperty("topo.testBuildType").getOrElse("debug")

    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += "-DONNXRUNTIME_ROOT=${onnxRuntimeRoot.get().asFile.invariantSeparatorsPath}"
            }
        }
    }
}

tasks.matching { it.name.startsWith("configureCMake") || it.name.startsWith("buildCMake") }
    .configureEach { dependsOn(extractOnnxRuntime) }

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.onnxruntime.android)
    onnxRuntimeAar(libs.onnxruntime.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
