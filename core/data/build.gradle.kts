// Sprint 1 : Room (mode WAL), DAO, implementations des repositories du domaine.
plugins {
    id("betaclimb.android.library")
}

android {
    namespace = "com.betaclimb.core.data"
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.android)
}
