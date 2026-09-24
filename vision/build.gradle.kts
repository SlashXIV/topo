// Module hybride Kotlin + C++ (NDK).
//
// Il encapsule a lui seul toute la complexite native du projet : JNI, CMake,
// conformite 16 Ko et, plus tard, ONNX Runtime / XNNPACK. Le reste de
// l'application ne depend que de l'interface HoldDetector du domaine et ignore
// totalement qu'une bibliotheque native existe.
plugins {
    id("topo.android.library")
    id("topo.android.native")
}

android {
    namespace = "com.topo.vision"
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
