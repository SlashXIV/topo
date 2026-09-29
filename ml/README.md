# Workstream ML (hors build Gradle)

Ce dossier accueillera la chaine Python produisant `yolov11n_climbing.onnx`.
Il est volontairement **hors du build Gradle** : le cycle de vie d'un modele
(dataset, entrainement, evaluation) n'a rien a voir avec celui d'une application.

## Etat actuel

Aucun modele. C'est assume : l'application est concue pour etre complete sans lui.
L'ajout manuel des prises est le chemin nominal du MVP ; la detection automatique
se branchera derriere l'interface `HoldDetector` de `:core:domain` sans qu'aucune
couche superieure ne change.

## Modele de substitution

En attendant, `export_placeholder.py` exporte un YOLO nano generique (COCO) au meme format de
sortie que le futur modele, et calcule la reference des tests instrumentes de `:vision` par un
miroir NumPy du code natif. Sous licence AGPL-3.0 : il reste dans les ressources de test, hors
git, et n'entre jamais dans l'APK livre.

```bash
python -m venv ml/.venv
ml/.venv/Scripts/python -m pip install ultralytics onnxruntime pillow numpy
ml/.venv/Scripts/python ml/export_placeholder.py
```

## Modele de prises

```bash
export ROBOFLOW_API_KEY=...          # compte gratuit Roboflow ; jamais dans un fichier
ml/.venv/Scripts/python ml/fetch_datasets.py   # telecharge et fusionne en une classe
ml/.venv/Scripts/python ml/train.py            # GPU, evaluation sur test, export FP32 + INT8
```

Le modele embarque distingue les prises des volumes, pour que le filtre par couleur garde les
volumes sans garder les petites prises grises :

```bash
ml/.venv/Scripts/python ml/fetch_datasets.py --volumes   # seul Blackcreed distingue les volumes
ml/.venv/Scripts/python ml/train.py --volumes            # sortie : ml/runs/holds_volumes/export/
```

L'entrainement utilise le GPU s'il est disponible : installer PyTorch depuis l'index CUDA
(`--index-url https://download.pytorch.org/whl/cu128`), la roue par defaut etant CPU.

### Jeux de donnees et attribution

Tous sous licence [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), fusionnes en une
seule classe « prise » (prises et volumes confondus), decoupages d'origine conserves :

| Jeu | Auteur | Version | Images sources |
|---|---|---|---|
| [Climbing Holds and Volumes](https://universe.roboflow.com/blackcreed-xpgxh/climbing-holds-and-volumes) | Blackcreed | 14 | 1 717 |
| [Climbing Holds Detector](https://universe.roboflow.com/gabriel-murry-hbznu/climbing-holds-detector) | Gabriel Murry | 4 | 86 |
| [Hold Detector](https://universe.roboflow.com/climb-ai/hold-detector-rnvkl) | Climb AI | 2 | 47 |

Ecarte : [Climbing Holds (Brage)](https://universe.roboflow.com/brage-drnsc/climbing-holds-mavv2),
annote de facon clairsemee — une prise non etiquetee est apprise comme du fond.

## Chaine cible

1. Dataset de prises d'escalade annotees (boites englobantes).
2. Fine-tuning d'un YOLO nano.
3. Elagage (channel pruning) + quantification post-entrainement en INT8.
4. Export ONNX (opset fige), cible ~5 Mo.
5. Validation mAP50 avant integration.

## Critere d'integration

Le modele n'entre dans `vision/src/main/assets/models/holds.onnx` que s'il tient ces trois
conditions : mAP50 mesuree sur un jeu de validation tenu a l'ecart, latence
d'inference sous 100 ms sur appareil physique ARM64, et taille compatible avec
la cible d'APK.
