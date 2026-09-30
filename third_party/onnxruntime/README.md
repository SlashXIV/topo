# ONNX Runtime reduit

L'AAR officiel d'ONNX Runtime embarque tous les operateurs ONNX, NNAPI et WebGPU : 33 Mo de
`libonnxruntime.so` en arm64. Topo n'execute que deux modeles, soit une quarantaine
d'operateurs. Ce dossier recompile la **meme version** d'ORT reduite a ces operateurs.

L'AAR produit n'est pas versionne (`dist/`, hors git). Sans lui, `:vision` retombe sur l'AAR
officiel : un clone du depot se construit toujours, simplement plus lourd.
`-Ptopo.stockOnnxRuntime=true` force l'officiel, pour comparer.

## Construire

Sous Windows, dans WSL (Ubuntu). Le chemin Linux est celui que Microsoft documente et teste pour
Android ; Windows natif demanderait Visual Studio.

```bash
sudo apt-get install build-essential git cmake ninja-build python3-venv openjdk-17-jdk-headless \
  unzip curl zip pkg-config autoconf automake libtool
bash third_party/onnxruntime/setup_android_sdk.sh     # SDK et NDK Linux, ~2 Go, une fois
bash third_party/onnxruntime/build_reduced_aar.sh     # ~1 h la premiere fois
```

Puis valider sur appareil, en release : `HoldDetectionInstrumentedTest` et
`DepthEstimationInstrumentedTest` comparent les deux modeles a leur reference Python, et
echouent si un operateur manque.

## Quand recompiler

Presque jamais. Le code de l'application et notre code natif (`vision/src/main/cpp`) se
compilent a part, contre les en-tetes de l'AAR. Un reentrainement du modele de prises sur la
meme architecture garde les memes operateurs.

Recompiler seulement si :

1. **un modele change d'operateurs** (autre architecture, autre quantification). Regenerer
   d'abord `required_operators.config` :
   ```bash
   python -m onnxruntime.tools.convert_onnx_models_to_ort <dossier_des_modeles> \
     --optimization_style Fixed Runtime
   ```
   et en faire l'union avec les deux fichiers `.config` produits : le graphe d'origine ET les
   operateurs que l'optimisation au chargement y substitue (FusedConv, SkipLayerNormalization...) ;
2. **ORT monte de version** : changer `onnxruntime` et `onnxruntime-reduced` dans
   `gradle/libs.versions.toml`, relancer avec `ORT_VERSION=...`, et REAUDITER l'alignement
   16 Ko (`scripts/check_alignment.sh`) ;
3. **les options changent** (`topo_aar_build_settings.json`).

Le dossier de compilation (`~/ort-build` dans WSL) est conserve : hors montee de version, une
recompilation reprend les dependances deja construites.
