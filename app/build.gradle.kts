plugins {
    id("topo.android.application")
    id("topo.android.compose")
    id("topo.android.hilt")
    // Routes de navigation typees : Navigation Compose serialise les objets de
    // route, ce qui supprime les chaines de format et leur analyse manuelle.
    alias(libs.plugins.kotlin.serialization)
    // Embarque le baseline profile genere par :baselineprofile dans le build de
    // publication : les chemins de demarrage arrivent precompiles.
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.topo.app"

    defaultConfig {
        applicationId = "com.topo"
    }

    // Le modele de detection reste non compresse dans l'APK : :vision le projette
    // alors en memoire (openFd + map) au lieu d'en recopier ~10 Mo dans le tas
    // Java. Regle posee ici : c'est l'application qui empaquette les assets de
    // ses bibliotheques, le reglage d'un module library ne s'y propage pas.
    androidResources {
        noCompress += "onnx"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.vision)
    implementation(projects.feature.camera)
    implementation(projects.feature.annotator)
    implementation(projects.feature.session)
    implementation(projects.feature.settings)
    implementation(projects.feature.stats)

    // Detection des fuites en DEBUG uniquement : LeakCanary s'installe tout seul
    // et n'entre jamais dans le build de publication.
    debugImplementation(libs.leakcanary.android)

    // Installe le profil a la premiere execution quand le magasin ne l'a pas fait
    // (installation par adb, magasins tiers).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.baselineprofile)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
