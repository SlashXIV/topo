plugins {
    id("betaclimb.android.application")
    id("betaclimb.android.compose")
    id("betaclimb.android.hilt")
}

android {
    namespace = "com.betaclimb.app"

    defaultConfig {
        applicationId = "com.betaclimb"
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
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
