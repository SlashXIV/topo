plugins {
    id("topo.android.application")
    id("topo.android.compose")
    id("topo.android.hilt")
    // Routes de navigation typees : Navigation Compose serialise les objets de
    // route, ce qui supprime les chaines de format et leur analyse manuelle.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.topo.app"

    defaultConfig {
        applicationId = "com.topo"
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

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
