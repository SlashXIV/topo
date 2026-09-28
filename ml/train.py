"""
Affine YOLO11n sur le jeu de prises fusionne, puis exporte le modele pour Topo.

Chaine : entrainement GPU -> evaluation sur la partie test (jamais vue pendant
l'entrainement ni la selection du meilleur epoch) -> export ONNX au format
attendu par vision_processor.cpp -> quantification INT8 calibree sur des photos
de prises.

Le modele n'est PAS copie dans les assets de l'application par ce script : il ne
doit y entrer qu'apres lecture des metriques et mesure de latence sur appareil
(cf. le critere d'integration de ml/README.md).

Usage :
    python ml/train.py [--epochs 100] [--batch 32]
Sortie : ml/runs/holds/ (poids, courbes) et ml/runs/holds/export/{holds.onnx,holds_int8.onnx}
"""

import argparse
import json
import random
import shutil
import sys
from pathlib import Path

from ultralytics import YOLO

sys.path.insert(0, str(Path(__file__).resolve().parent))
from export_placeholder import read_native_constants  # noqa: E402
from quantize import main as quantize  # noqa: E402

ROOT = Path(__file__).resolve().parent
DATASET = ROOT / "datasets" / "holds" / "holds.yaml"
RUNS = ROOT / "runs"

# Graine figee : deux entrainements sur les memes donnees doivent etre comparables.
SEED = 0
CALIBRATION_IMAGES = 200


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--batch", type=int, default=32)
    args = parser.parse_args()

    if not DATASET.exists():
        raise SystemExit("Jeu fusionne absent : lancer ml/fetch_datasets.py")
    size = int(read_native_constants()["kInputSize"])

    model = YOLO("yolo11n.pt")
    model.train(
        data=str(DATASET),
        epochs=args.epochs,
        batch=args.batch,
        imgsz=size,
        seed=SEED,
        deterministic=True,
        project=str(RUNS),
        name="holds",
        exist_ok=True,
        # Arret si la validation ne progresse plus : au-dela, le modele apprend
        # les murs du jeu, pas les prises.
        patience=20,
        # Pas de retournement vertical : un mur a un haut et un bas, et une prise
        # a l'envers n'est pas une configuration que le modele verra.
        flipud=0.0,
        fliplr=0.5,
    )

    best = YOLO(str(RUNS / "holds" / "weights" / "best.pt"))
    # Evaluation sur TEST : la validation a servi a choisir le meilleur epoch, sa
    # mAP est donc optimiste. Seul le test est une mesure honnete.
    metrics = best.val(data=str(DATASET), split="test", imgsz=size, conf=0.001)
    report = {
        "mAP50": float(metrics.box.map50),
        "mAP50-95": float(metrics.box.map),
        "precision": float(metrics.box.mp),
        "recall": float(metrics.box.mr),
    }
    print(json.dumps(report, indent=2))

    export_dir = RUNS / "holds" / "export"
    export_dir.mkdir(exist_ok=True)
    onnx_path = Path(best.export(format="onnx", imgsz=size, dynamic=False, simplify=True, nms=False))
    fp32 = export_dir / "holds.onnx"
    shutil.copy(onnx_path, fp32)

    # Calibration sur des photos de prises tirees de l'entrainement : ce sont
    # les activations de CE domaine que la quantification doit couvrir.
    train_images = sorted((DATASET.parent / "train" / "images").iterdir())
    random.Random(SEED).shuffle(train_images)
    calibration = export_dir / "_calibration"
    calibration.mkdir(exist_ok=True)
    for image in train_images[:CALIBRATION_IMAGES]:
        shutil.copy(image, calibration / image.name)
    quantize(fp32, export_dir / "holds_int8.onnx", calibration)
    shutil.rmtree(calibration)

    (export_dir / "metrics.json").write_text(json.dumps(report, indent=2))
    print(f"Export : {export_dir}")


if __name__ == "__main__":
    main()
