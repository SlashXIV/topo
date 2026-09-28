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
