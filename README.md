# BetaClimb

Application Android native **hors-ligne** pour grimpeurs de bloc : photographier un pan de mur,
identifier les prises, tracer sa sequence de mouvements ("beta") au doigt, tout conserver en local.

> **Etat : Sprint 0 termine.** Le socle multi-modules, le pont JNI et la conformite 16 Ko sont
> livres et valides sur materiel. Le reste est a construire (voir [Feuille de route](#feuille-de-route)).

---

## Ce que le Sprint 0 prouve

Le sprint ne livre pas de fonctionnalite utilisateur. Il elimine le risque le plus cher a corriger
tardivement : celui qui touche le build systeme, le NDK et le packaging.

| Propriete | Preuve |
|---|---|
| Alignement ELF 16 Ko | `llvm-objdump -p` : tous les segments LOAD en `2**14`, sur les 2 ABI |
| Packaging 16 Ko | `zipalign -c -P 16 4` sur l'APK |
| Chargement sur noyau 16 Ko | 6 tests instrumentes verts sur emulateur `PAGE_SIZE=16384`, API 37 |
| Pont JNI zero-copie | Le pointeur recu en C++ est l'adresse meme du `DirectByteBuffer` |
| Liberation deterministe | Handle natif libere a la fermeture, `close()` idempotent |

```
$ scripts/check_alignment.sh app/build/outputs/apk/debug/app-debug.apk
Source : app-debug.apk (bibliotheques empaquetees)

  arm64-v8a    libandroidx.graphics.path.so   3 LOAD, tous en 2**14 — CONFORME
  arm64-v8a    libbetaclimb_vision.so         3 LOAD, tous en 2**14 — CONFORME
  arm64-v8a    libc++_shared.so               4 LOAD, tous en 2**14 — CONFORME
  x86_64       libandroidx.graphics.path.so   3 LOAD, tous en 2**14 — CONFORME
  x86_64       libbetaclimb_vision.so         3 LOAD, tous en 2**14 — CONFORME
  x86_64       libc++_shared.so               4 LOAD, tous en 2**14 — CONFORME

Packaging : APK aligne 16 Ko — CONFORME
```

```
$ adb logcat -s BetaClimbJNI BetaClimbVision
I BetaClimbJNI    : 4 methodes natives enregistrees
I BetaClimbVision : VisionProcessor cree (sans modele)
D BetaClimbJNI    : DirectBuffer @0x2608c010 capacite=16384 (64x64, stride=256) - zero copie
D BetaClimbVision : Detect(pixels=0x2608c010, 64x64, stride=256, format=1)
I BetaClimbJNI    : Moteur natif detruit
```

L'adresse `0x2608c010` est identique des deux cotes : aucune copie n'a eu lieu entre le buffer
alloue en Kotlin et le moteur C++.

---

## Architecture

```
app/                  Assemblage, navigation, point d'entree Hilt
core/
  ui/                 Theme Material 3, composants partages
  common/             Utilitaires transverses
  domain/             Logique metier — module JVM PUR, zero dependance Android
  data/               Room (WAL), DAO, implementations des repositories
feature/
  camera/             Acquisition CameraX
  annotator/          Canvas Compose, gestes, magnetisation
  session/            Historique des sessions et des blocs
vision/               Module hybride Kotlin + C++ (NDK, JNI, ONNX Runtime)
build-logic/          Plugins de convention Gradle
ml/                   Chaine Python du modele (hors build Gradle)
scripts/              Audit de conformite 16 Ko
```

Le decoupage n'est pas decoratif, il pose des **contraintes mecaniques** :

- `:core:domain` est un module JVM pur. Il ne PEUT pas dependre d'Android : la regle est
  appliquee par le plugin de convention, pas par discipline.
- `:vision` encapsule a lui seul JNI, CMake et (plus tard) ONNX Runtime. Les autres modules ne
  connaissent que l'interface `HoldDetector` du domaine et ignorent qu'un moteur natif existe.
- Les ABI sont declarees une seule fois (`SUPPORTED_ABIS`) et partagees entre `:vision` et `:app` :
  l'APK ne peut pas annoncer une ABI que le moteur natif ne sait pas servir.

### Decisions techniques notables

**Conformite 16 Ko (Android 15+).** Les segments LOAD des `.so` doivent etre alignes sur 16384
octets, sinon `System.loadLibrary` echoue en SIGSEGV sur les noyaux recents et Google Play rejette
l'APK. Attention au couple de flags souvent recopie tel quel :
`max-page-size=16384` va avec **`common-page-size=16384`**, pas `4096` — le NDK met les deux a 4096
par defaut. Le NDK r28+ aligne deja correctement, mais les flags restent poses explicitement dans
`CMakeLists.txt` pour que la propriete ne depende pas de la version de NDK.

L'exigence porte sur **toutes** les `.so` de l'APK, dependances tierces comprises — c'est pourquoi
le script d'audit inspecte le contenu de l'APK et non nos seuls artefacts.

**Zero-copie, reellement.** `ByteBuffer.allocateDirect` supprime la copie JNI mais pas la copie
`Bitmap -> ByteBuffer` cote Kotlin. Le pont expose donc deux entrees :
`AndroidBitmap_lockPixels` pour les `Bitmap` (le seul chemin vraiment sans copie) et
`GetDirectBufferAddress` pour les plans d'`ImageProxy` CameraX, qui sont deja directs.

**Une seule traversee JNI par inference.** Les resultats reviennent aplatis
(`[cx, cy, w, h, score]` par prise). Retourner des objets Java couterait ~900 appels JNI pour
150 prises.

**RegisterNatives plutot que symboles exportes.** Les signatures sont validees au chargement de la
bibliotheque, pas au premier appel : une desynchronisation Kotlin/C++ echoue immediatement.

**Pas de modele ML en dependance bloquante.** Le placement manuel des prises est le chemin nominal
du MVP. La detection automatique se branchera derriere `HoldDetector` sans qu'aucune couche
superieure ne change.

---

## Socle technique

| | |
|---|---|
| Gradle / AGP | 9.3.1 / 9.1.1 (built-in Kotlin, nouveau DSL) |
| Kotlin / KSP | 2.4.20 / 2.3.12 |
| SDK | compile 37, target 37, min 26 |
| NDK / CMake | 28.2.13676358 / 3.31.6, C++17 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3 |
| DI | Hilt 2.60.1 |
| Persistance | Room 2.8.5 en mode WAL *(Sprint 1)* |
| Inference | ONNX Runtime + XNNPACK *(sprint Vision)* |

---

## Construire et valider

Prerequis : JDK 17+ (le JBR d'Android Studio convient), NDK 28.2 et CMake 3.31.6 installes via
`sdkmanager`.

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # AGP 9 exige un JDK 17+

./gradlew build                                   # les 10 modules, debug + release
./gradlew :app:assembleDebug

scripts/check_alignment.sh \
  app/build/outputs/apk/debug/app-debug.apk       # audit 16 Ko (statique + packaging)

./gradlew :vision:connectedDebugAndroidTest       # preuve d'execution — AVD 16 Ko requis
```

L'AVD de validation doit utiliser une image systeme **`..._ps16k`** (page size 16 Ko). Verification :

```bash
adb shell getconf PAGE_SIZE   # doit renvoyer 16384
```

---

## Feuille de route

L'ordre suit une reduction progressive du risque, et non l'ordre des couches.

| Sprint | Contenu | Etat |
|---|---|---|
| **S0** | Socle multi-modules, pont JNI, conformite 16 Ko | **termine** |
| S1 | Room (WAL), CameraX, enregistrement et historique des blocs | a faire |
| S2 | Canvas Compose, prises manuelles, magnetisation du trace | a faire |
| S3 | Inference ONNX Runtime / XNNPACK, NMS | depend du modele |
| S4 | Profiling, R8, audit d'alignement final, AAB | a faire |
| ∥ | `ml/` : dataset, fine-tuning, quantification INT8, export ONNX | en parallele |

Le modele de detection arrive tard **volontairement** : c'est la seule dependance externe du
projet, et l'application est concue pour etre complete sans lui.
