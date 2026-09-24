import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Applique Compose a un module deja Android (application ou library).
 * Le BOM pilote toutes les versions Compose : aucune version Compose en dur ailleurs.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val extension = extensions.getByType<CommonExtension>()
            extension.buildFeatures.compose = true

            configureComposeDependencies(libs)
        }
    }

    private fun Project.configureComposeDependencies(libs: VersionCatalog) {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        dependencies {
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))

            add("implementation", libs.findLibrary("androidx-compose-ui").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            add("implementation", libs.findLibrary("androidx-compose-material3").get())
        // Les icones ne sont plus transitives depuis material3. Declarees ici et
        // non module par module : c'est une regle de build, elle vit dans
        // build-logic. R8 elimine les vecteurs inutilises au packaging.
        add("implementation", libs.findLibrary("androidx-compose-material-icons-extended").get())

            add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
            add("androidTestImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
        }
    }
}
