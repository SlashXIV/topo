plugins {
    id("topo.android.library")
    id("topo.android.compose")
}

android {
    namespace = "com.topo.core.ui"
}

dependencies {
    // Les libelles des enumerations du domaine vivent ici (cf. text/DomainLabels.kt).
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    // api : BlocThumbnail expose un File charge par Coil, et les modules de
    // fonctionnalite reutilisent le composant tel quel.
    api(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
