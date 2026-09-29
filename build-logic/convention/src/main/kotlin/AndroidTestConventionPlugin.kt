import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/**
 * Module de test autonome (`com.android.test`), qui instrumente l'application
 * depuis l'exterieur : generation du baseline profile et mesures de demarrage.
 *
 * Meme socle que les autres modules — compileSdk, minSdk, Java 17 — pour qu'un
 * module de mesure ne compile pas contre une autre plateforme que celle qu'il
 * mesure.
 */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.test")

            val extension = extensions.getByType<TestExtension>()
            configureKotlinAndroid(extension)

            extension.apply {
                defaultConfig.apply {
                    targetSdk = libs.versionInt("targetSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
            }
        }
    }
}
