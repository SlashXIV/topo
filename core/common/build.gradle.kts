plugins {
    id("topo.android.library")
}

android {
    namespace = "com.topo.core.common"
}

dependencies {
    // Les qualificateurs de dispatcher sont des annotations javax.inject exposees
    // aux autres modules : api, pas implementation.
    api(libs.javax.inject)
    api(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
