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
    python ml/train.py [--volumes] [--epochs 100] [--batch 32]
Sortie : ml/runs/<nom>/ (poids, courbes) et ml/runs/<nom>/export/{holds.onnx,holds_int8.onnx},
<nom> valant « holds », ou « holds_volumes » avec --volumes (classes prise et volume).
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
RUNS = ROOT / "runs"

# Graine figee : deux entrainements sur les memes donnees doivent etre comparables.
SEED = 0
CALIBRATION_IMAGES = 200


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=100)
    parser.add_argument("--batch", type=int, default=32)
    parser.add_argument("--volumes", action="store_true",
                        help="deux classes, prise et volume (cf. fetch_datasets.py --volumes)")
    args = parser.parse_args()

    run_name = "holds_volumes" if args.volumes else "holds"
    dataset = ROOT / "datasets" / run_name / "holds.yaml"

    if not dataset.exists():
        raise SystemExit("Jeu fusionne absent : lancer ml/fetch_datasets.py")
    size = int(read_native_constants()["kInputSize"])

    model = YOLO("yolo11n.pt")
    model.train(
        data=str(dataset),
        epochs=args.epochs,
        batch=args.batch,
        imgsz=size,
        seed=SEED,
        deterministic=True,
        project=str(RUNS),
        name=run_name,
        exist_ok=True,
        # Arret si la validation ne progresse plus : au-dela, le modele apprend
        # les murs du jeu, pas les prises.
        patience=20,
        # Pas de retournement vertical : un mur a un haut et un bas, et une prise
        # a l'envers n'est pas une configuration que le modele verra.
        flipud=0.0,
        fliplr=0.5,
    )

    best = YOLO(str(RUNS / run_name / "weights" / "best.pt"))
    # Evaluation sur TEST : la validation a servi a choisir le meilleur epoch, sa
    # mAP est donc optimiste. Seul le test est une mesure honnete.
    metrics = best.val(data=str(dataset), split="test", imgsz=size, conf=0.001)
    # Par classe en plus du global : en mode --volumes, une mAP moyenne correcte
    # pourrait masquer des volumes mal reconnus, minoritaires dans le jeu.
    report = {
        "per_class_mAP50": {
            best.names[int(c)]: float(v) for c, v in zip(metrics.box.ap_class_index, metrics.box.ap50)
        },
        "mAP50": float(metrics.box.map50),
        "mAP50-95": float(metrics.box.map),
        "precision": float(metrics.box.mp),
        "recall": float(metrics.box.mr),
    }
    print(json.dumps(report, indent=2))

    export_dir = RUNS / run_name / "export"
    export_dir.mkdir(exist_ok=True)
    onnx_path = Path(best.export(format="onnx", imgsz=size, dynamic=False, simplify=True, nms=False))
    fp32 = export_dir / "holds.onnx"
    shutil.copy(onnx_path, fp32)

    # Calibration sur des photos de prises tirees de l'entrainement : ce sont
    # les activations de CE domaine que la quantification doit couvrir.
    train_images = sorted((dataset.parent / "train" / "images").iterdir())
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
