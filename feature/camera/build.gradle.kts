plugins {
    id("betaclimb.android.library")
    id("betaclimb.android.compose")
}

android {
    namespace = "com.betaclimb.feature.camera"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
}
