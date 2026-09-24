plugins {
    id("topo.android.library")
    id("topo.android.compose")
}

android {
    namespace = "com.topo.core.ui"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // api : BlocThumbnail expose un File charge par Coil, et les modules de
    // fonctionnalite reutilisent le composant tel quel.
    api(libs.coil.compose)
}
