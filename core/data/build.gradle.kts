plugins {
    id("betaclimb.android.library")
    id("betaclimb.android.hilt")
}

android {
    namespace = "com.betaclimb.core.data"
}

// Le schema de la base est EXPORTE en JSON et versionne dans git. C'est ce qui rend
// les migrations verifiables : Room compare le schema attendu au schema reel, et un
// test de migration peut rejouer une version anterieure.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

androidComponents {
    onVariants { variant ->
        // Les schemas exportes servent d'assets aux tests de migration.
        variant.androidTest?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.common)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
