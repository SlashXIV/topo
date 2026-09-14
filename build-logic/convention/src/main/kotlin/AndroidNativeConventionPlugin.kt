import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/**
 * Configuration NDK/CMake mutualisee.
 *
 * Conformite 16 Ko (Android 15+) — la regle se joue a DEUX endroits, pas un :
 *  1. Compilation : les segments LOAD de la .so doivent etre alignes sur 2**14.
 *     - NDK r28+ le fait par defaut ;
 *     - `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON` le rend explicite ;
 *     - les flags `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384` sont
 *       poses dans CMakeLists.txt (leur place naturelle) et survivent a une
 *       retrogradation vers NDK r27.
 *  2. Packaging : `useLegacyPackaging = false` — une .so compressee dans l'APK est
 *     extraite a l'installation et perd son alignement. Une .so mal aligne se solde
 *     par un SIGSEGV au System.loadLibrary sur un noyau 16 Ko.
 *
 * x86_64 est conserve dans les ABI : sans lui, aucun test instrumente ne tourne sur
 * emulateur, donc aucune validation d'alignement en execution en CI.
 */
class AndroidNativeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val extension = extensions.getByType<LibraryExtension>()

            extension.apply {
                ndkVersion = libs.versionString("ndk")

                defaultConfig.apply {
                    externalNativeBuild.cmake.apply {
                        cppFlags += listOf("-std=c++17")
                        arguments += listOf(
                            "-DANDROID_STL=c++_shared",
                            "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                        )
                    }
                    ndk.abiFilters += SUPPORTED_ABIS
                }

                externalNativeBuild.cmake.apply {
                    path = file("src/main/cpp/CMakeLists.txt")
                    version = libs.versionString("cmake")
                }

                packaging.jniLibs.useLegacyPackaging = false
            }
        }
    }
}
