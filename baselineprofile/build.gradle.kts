// Generation du baseline profile et mesure du demarrage de :app.
plugins {
    id("topo.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.topo.baselineprofile"
    targetProjectPath = ":app"
}

baselineProfile {
    // Genere sur l'appareil ou l'emulateur branche, plutot que sur un appareil
    // gere par Gradle : le projet a deja ses deux AVD (cf. README).
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
