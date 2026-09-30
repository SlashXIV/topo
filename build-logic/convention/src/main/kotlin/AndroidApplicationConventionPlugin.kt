import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            val extension = extensions.getByType<ApplicationExtension>()
            configureKotlinAndroid(extension)

            extension.apply {
                defaultConfig.apply {
                    targetSdk = libs.versionInt("targetSdk")
                    versionCode = 1
                    versionName = "1.0.0"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    // Aucune ABI que :vision ne sait pas servir (cf. SUPPORTED_ABIS).
                    ndk.abiFilters += SUPPORTED_ABIS
                }
            }

            // Le debug garde tout, pour l'emulateur ; la publication ne garde que ce qu'un
            // telephone execute (cf. EMULATOR_ONLY_ABIS).
            extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants(
                extensions.getByType<ApplicationAndroidComponentsExtension>().selector().withBuildType("release"),
            ) { variant ->
                variant.packaging.jniLibs.excludes.addAll(EMULATOR_ONLY_ABIS.map { "lib/$it/**" })
            }
        }
    }
}
