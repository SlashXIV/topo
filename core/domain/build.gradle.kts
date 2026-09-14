// Module JVM pur : aucune dependance au framework Android.
// C'est verifie mecaniquement par le plugin de convention, pas par convention sociale.
plugins {
    id("betaclimb.jvm.library")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
