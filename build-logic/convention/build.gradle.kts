import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.betaclimb.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // compileOnly : ces plugins sont fournis par le build consommateur au runtime.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "betaclimb.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "betaclimb.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "betaclimb.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "betaclimb.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidNative") {
            id = "betaclimb.android.native"
            implementationClass = "AndroidNativeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "betaclimb.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
