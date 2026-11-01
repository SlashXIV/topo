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
            registerHardcodedUiTextCheck()
        }
    }

    /**
     * Tout module Compose affiche du texte : c'est donc ici, et nulle part
     * ailleurs, que se branche la verification des textes codes en dur. Rattachee
     * a `check`, elle tourne avec `./gradlew build` sans qu'on y pense.
     */
    private fun Project.registerHardcodedUiTextCheck() {
        val checkTask = tasks.register("checkHardcodedUiText", HardcodedUiTextCheck::class.java) {
            sources.from(fileTree("src/main/java") { include("**/*.kt") })
            report.set(layout.buildDirectory.file("reports/hardcoded-ui-text.txt"))
        }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(checkTask) }
    }

    private fun Project.configureComposeDependencies(libs: VersionCatalog) {
        val bom = libs.findLibrary("androidx-compose-bom").get()
        dependencies {
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))

            add("implementation", libs.findLibrary("androidx-compose-ui").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-graphics").get())
            add("implementation", libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            // Foundation et non material3 : l'interface de Topo est un systeme maison
            // (core/ui), et aucune bibliotheque Material n'entre dans l'application.
            add("implementation", libs.findLibrary("androidx-compose-foundation").get())

            add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
            add("debugImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
            add("androidTestImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
        }
    }
}
