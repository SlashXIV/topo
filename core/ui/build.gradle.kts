plugins {
    id("betaclimb.android.library")
    id("betaclimb.android.compose")
}

android {
    namespace = "com.betaclimb.core.ui"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
