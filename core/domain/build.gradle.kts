// Module JVM pur : aucune dependance au framework Android.
// C'est verifie mecaniquement par le plugin de convention, pas par convention sociale.
plugins {
    id("topo.jvm.library")
}

dependencies {
    // api : Flow et @Inject apparaissent dans la signature PUBLIQUE des interfaces
    // de ce module. Les exposer en implementation obligerait chaque consommateur a
    // les redeclarer.
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
