plugins {
    id("betaclimb.android.library")
}

android {
    namespace = "com.betaclimb.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
