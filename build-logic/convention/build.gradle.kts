import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.topo.buildlogic"

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
            id = "topo.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "topo.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "topo.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "topo.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidTest") {
            id = "topo.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("androidNative") {
            id = "topo.android.native"
            implementationClass = "AndroidNativeConventionPlugin"
        }
        register("jvmLibrary") {
            id = "topo.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
