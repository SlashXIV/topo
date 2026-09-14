import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project

/**
 * Configuration Android commune a tous les modules. Une seule definition : les
 * modules ne declarent que ce qui leur est propre.
 *
 * Note AGP 9 : aucun plugin Kotlin n'est applique ici. AGP compile lui-meme le
 * Kotlin (built-in Kotlin) et le plugin autonome org.jetbrains.kotlin.android
 * est incompatible avec le nouveau DSL. Le jvmTarget de Kotlin s'aligne
 * automatiquement sur compileOptions.targetCompatibility ci-dessous : il n'y a
 * donc plus qu'UNE valeur a maintenir au lieu de deux qui pouvaient diverger.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = libs.versionInt("compileSdk")

        defaultConfig.apply {
            minSdk = libs.versionInt("minSdk")
        }

        compileOptions.apply {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
