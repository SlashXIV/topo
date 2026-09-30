#!/usr/bin/env bash
# ONNX Runtime reduit aux operateurs des modeles de Topo, en AAR Android.
#
# A lancer dans WSL (Ubuntu), apres setup_android_sdk.sh. Paquets requis :
#   build-essential git cmake ninja-build python3-venv openjdk-17-jdk-headless
#   unzip curl zip pkg-config autoconf automake libtool
# Sans pkg-config, vcpkg echoue des la premiere dependance (abseil).
#
# Sortie : l'AAR, dans le depot Maven local third_party/onnxruntime/dist/maven, que
# Gradle prefere a l'AAR officiel s'il est present.
#
# Par rapport a l'AAR officiel (default_full_aar_build_settings.json d'ORT) : deux ABI au
# lieu de quatre, ni NNAPI ni WebGPU, pas d'operateurs ai.onnx.ml, et seuls les noyaux de
# required_operators.config. XNNPACK est garde : le modele de prises l'utilise.
# Les tests d'ORT ne sont pas compiles (onnxruntime_BUILD_UNIT_TESTS=OFF) : --skip_tests
# n'empeche que leur execution, et la reduction d'operateurs y laisse des constantes
# inutilisees que -Werror refuse.
set -euo pipefail
ORT_VERSION="${ORT_VERSION:-1.30.0}"
HERE="$(cd "$(dirname "$0")" && pwd)"
SRC="$HOME/onnxruntime"
BUILD="$HOME/ort-build"
SDK="$HOME/android-sdk"
NDK="$SDK/ndk/${NDK_VERSION:-28.2.13676358}"

if [ ! -d "$SRC" ]; then
  git clone -q --depth 1 --branch "v$ORT_VERSION" https://github.com/microsoft/onnxruntime.git "$SRC"
fi
python3 -m venv "$HOME/ort-venv"
"$HOME/ort-venv/bin/pip" -q install flatbuffers numpy packaging

# Configuration Release et non MinSizeRel : -Os ralentirait MLAS, qui porte le modele de
# relief (XNNPACK refuse son Resize).
"$HOME/ort-venv/bin/python" "$SRC/tools/ci_build/github/android/build_aar_package.py" \
  --android_sdk_path "$SDK" --android_ndk_path "$NDK" \
  --build_dir "$BUILD" --config Release \
  --include_ops_by_config "$HERE/required_operators.config" \
  "$HERE/topo_aar_build_settings.json"

# Depot Maven local, pour que Gradle resolve l'AAR comme une dependance ordinaire : une
# dependance files("*.aar") est refusee dans un module bibliotheque.
VERSION="$ORT_VERSION-topo"
REPO="$HERE/dist/maven/com/microsoft/onnxruntime/onnxruntime-android/$VERSION"
mkdir -p "$REPO"
AAR="$(find "$BUILD/aar_out" -name "onnxruntime-android-*.aar" | head -1)"
cp "$AAR" "$REPO/onnxruntime-android-$VERSION.aar"
cat > "$REPO/onnxruntime-android-$VERSION.pom" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.microsoft.onnxruntime</groupId>
  <artifactId>onnxruntime-android</artifactId>
  <version>$VERSION</version>
  <packaging>aar</packaging>
</project>
POM
echo "AAR : $REPO/onnxruntime-android-$VERSION.aar ($(du -h "$REPO/onnxruntime-android-$VERSION.aar" | cut -f1))"
