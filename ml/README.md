# Workstream ML (hors build Gradle)

Ce dossier accueillera la chaine Python produisant `yolov11n_climbing.onnx`.
Il est volontairement **hors du build Gradle** : le cycle de vie d'un modele
(dataset, entrainement, evaluation) n'a rien a voir avec celui d'une application.

## Etat actuel

Aucun modele. C'est assume : l'application est concue pour etre complete sans lui.
L'ajout manuel des prises est le chemin nominal du MVP ; la detection automatique
se branchera derriere l'interface `HoldDetector` de `:core:domain` sans qu'aucune
couche superieure ne change.

## Chaine cible

1. Dataset de prises d'escalade annotees (boites englobantes).
2. Fine-tuning d'un YOLO nano.
3. Elagage (channel pruning) + quantification post-entrainement en INT8.
4. Export ONNX (opset fige), cible ~5 Mo.
5. Validation mAP50 avant integration.

## Critere d'integration

Le modele n'entre dans `vision/src/main/assets/` que s'il tient ces trois
conditions : mAP50 mesuree sur un jeu de validation tenu a l'ecart, latence
d'inference sous 100 ms sur appareil physique ARM64, et taille compatible avec
la cible d'APK.
