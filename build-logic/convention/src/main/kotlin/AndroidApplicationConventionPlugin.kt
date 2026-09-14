import com.android.build.api.dsl.ApplicationExtension
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
                    versionName = "0.1.0"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    // Aucune ABI que :vision ne sait pas servir (cf. SUPPORTED_ABIS).
                    ndk.abiFilters += SUPPORTED_ABIS
                }
            }
        }
    }
}
