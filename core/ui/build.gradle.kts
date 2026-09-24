plugins {
    id("betaclimb.android.library")
    id("betaclimb.android.compose")
}

android {
    namespace = "com.betaclimb.core.ui"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // api : BlocThumbnail expose un File charge par Coil, et les modules de
    // fonctionnalite reutilisent le composant tel quel.
    api(libs.coil.compose)
}
