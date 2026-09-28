# ============================================================================
#  Topo — moteur de vision natif (NDK / C++17)
# ============================================================================
cmake_minimum_required(VERSION 3.22.1)
project(topo_vision LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(topo_vision SHARED
        jni_bridge.cpp
        vision_processor.cpp
)

target_include_directories(topo_vision PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

target_compile_options(topo_vision PRIVATE
        -Wall
        -Wextra
        -Werror
        # Seuls les symboles explicitement exportes sortent de la .so. Le pont JNI
        # s'enregistre via RegisterNatives dans JNI_OnLoad : il n'a donc besoin
        # d'exporter AUCUN symbole de fonction native, ce qui reduit la table
        # dynamique et le temps de chargement.
        -fvisibility=hidden
        -fvisibility-inlines-hidden
        # Prerequis du --gc-sections ci-dessous : une section par fonction/donnee.
        -ffunction-sections
        -fdata-sections
)

target_compile_options(topo_vision PRIVATE
        $<$<CONFIG:Release>:-O3>
        $<$<CONFIG:Debug>:-O0 -g>
)

# ---------------------------------------------------------------------------
#  CONFORMITE 16 Ko — Android 15+ (obligatoire Google Play depuis nov. 2025)
# ---------------------------------------------------------------------------
#  Les constructeurs peuvent configurer le noyau avec des pages de 16 Ko. Tout
#  segment LOAD d'une .so aligne sur 4 Ko provoque alors un SIGSEGV au chargement.
#
#  ATTENTION — piege courant : on lit souvent le couple
#      -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=4096
#  La valeur 4096 pour common-page-size est INCORRECTE. Le NDK met les deux a
#  4096 par defaut ; la doc Google prescrit 16384 pour LES DEUX.
#
#  Le NDK r28+ aligne deja 16 Ko par defaut, et l'argument CMake
#  ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON (pose par le plugin de convention
#  Gradle) le rend explicite. Les flags ci-dessous sont malgre tout conserves :
#  ils rendent la propriete independante de la version de NDK utilisee, y compris
#  si quelqu'un retrograde en r27 ou compile ce CMakeLists hors du projet Gradle.
#
#  Verification : scripts/check_alignment.sh  ->  tous les LOAD en "align 2**14".
# ---------------------------------------------------------------------------
target_link_options(topo_vision PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
        # Elimine le code mort (complement de -ffunction-sections/-fdata-sections).
        "-Wl,--gc-sections"
        # build-id : indispensable a la symbolisation des tombstones natifs.
        "-Wl,--build-id=sha1"
        # Durcissement memoire standard.
        "-Wl,-z,relro"
        "-Wl,-z,now"
        # Toute dependance non resolue devient une erreur de link, pas un crash
        # au chargement sur le telephone de l'utilisateur.
        "-Wl,--no-undefined"
)

target_link_libraries(topo_vision
        android
        log
        # jnigraphics : AndroidBitmap_lockPixels / AndroidBitmap_getInfo.
        # C'est le VRAI chemin zero-copie pour un Bitmap (cf. jni_bridge.cpp).
        jnigraphics
)

# ---------------------------------------------------------------------------
#  ONNX Runtime — cible importee depuis l'AAR precompile.
#
#  L'AAR ne fournit pas de module Prefab : :vision en extrait les en-tetes et les
#  .so dans build/onnxruntime et passe ce chemin en ONNXRUNTIME_ROOT. La .so
#  elle-meme est empaquetee dans l'APK par la dependance Gradle, pas par CMake.
#
#  Conformite 16 Ko : cette .so n'est pas compilee ici, les flags de lien
#  ci-dessus ne la couvrent donc PAS. Elle a ete auditee a l'objdump avant
#  adoption (cf. libs.versions.toml), et scripts/check_alignment.sh la reverifie
#  dans l'APK final. Le binding Java libonnxruntime4j_jni.so est empaquete aussi
#  par l'AAR, mais jamais charge : ce moteur passe par l'API C++.
#
#  SYSTEM : les en-tetes d'ORT ne sont pas soumis a nos -Wall -Wextra -Werror.
# ---------------------------------------------------------------------------
if(NOT DEFINED ONNXRUNTIME_ROOT)
    message(FATAL_ERROR "ONNXRUNTIME_ROOT non defini : lancer le build par Gradle")
endif()

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
        IMPORTED_LOCATION ${ONNXRUNTIME_ROOT}/jni/${ANDROID_ABI}/libonnxruntime.so)
target_include_directories(topo_vision SYSTEM PRIVATE ${ONNXRUNTIME_ROOT}/headers)
target_link_libraries(topo_vision onnxruntime)

message(STATUS "[Topo] ABI=${ANDROID_ABI} | STL=${ANDROID_STL} | "
        "flexible-page-sizes=${ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES} | build=${CMAKE_BUILD_TYPE}")
