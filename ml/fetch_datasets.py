"""
Telecharge les jeux de prises d'escalade publics et les fusionne en un seul jeu
YOLO a UNE classe : « prise ».

Une seule classe : l'application ne distingue ni reglettes ni plats ni volumes,
elle pose des prises. Garder les categories d'origine obligerait le modele a
apprendre des distinctions que personne ne consomme, et chaque jeu a de toute
facon sa propre taxonomie — incompatibles entre elles.

La cle Roboflow est lue dans la variable d'environnement ROBOFLOW_API_KEY, jamais
dans un fichier : elle ne doit pas finir dans l'historique git.

Separation des jeux : les decoupages train/valid/test d'origine sont CONSERVES.
Re-melanger apres fusion ferait fuiter des photos quasi identiques (meme mur,
angle voisin) entre entrainement et validation, et la mAP mesuree serait
optimiste sans raison.

Usage :
    ROBOFLOW_API_KEY=... python ml/fetch_datasets.py [--list]
Sortie : ml/datasets/holds/{train,valid,test}/{images,labels} et holds.yaml
"""

import json
import os
import shutil
import sys
from pathlib import Path

from roboflow import Roboflow

ROOT = Path(__file__).resolve().parent
DOWNLOADS = ROOT / "datasets" / "_downloads"
MERGED = ROOT / "datasets" / "holds"

# (espace de travail, projet) sur Roboflow Universe, tous sous CC BY 4.0 (verifie
# le 2026-09-28 par l'API REST) : l'attribution figure dans ml/README.md. La
# version retenue est la plus recente publiee ; elle est consignee dans le
# manifeste pour la reproductibilite.
#
# Ecarte : brage-drnsc/climbing-holds-mavv2. Ses etiquettes sont surtout des
# marqueurs de depart (« start-washer »), et ses prises sont annotees de facon
# CLAIRSEMEE — 59 prises sur 42 photos de mur. Une prise non etiquetee est
# apprise comme du fond : ce jeu enseignerait au modele a en manquer.
SOURCES = [
    ("blackcreed-xpgxh", "climbing-holds-and-volumes"),
    ("gabriel-murry-hbznu", "climbing-holds-detector"),
    ("climb-ai", "hold-detector-rnvkl"),
]

SPLITS = ("train", "valid", "test")


def polygon_to_box(values: list[float]) -> list[float]:
    """Etiquette de segmentation YOLO (polygone) -> boite englobante normalisee."""
    xs, ys = values[0::2], values[1::2]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    return [(x0 + x1) / 2, (y0 + y1) / 2, x1 - x0, y1 - y0]


def to_single_class(label: Path) -> list[str]:
    lines = []
    for raw in label.read_text().split("\n"):
        parts = raw.split()
        if len(parts) < 5:
            continue
        values = [float(v) for v in parts[1:]]
        # 4 valeurs : une boite ; davantage : un polygone de segmentation.
        box = values if len(values) == 4 else polygon_to_box(values)
        if box[2] > 0 and box[3] > 0:
            lines.append("0 " + " ".join(f"{v:.6f}" for v in box))
    return lines


def describe(rf: Roboflow) -> list[dict]:
    described = []
    for workspace, name in SOURCES:
        project = rf.workspace(workspace).project(name)
        versions = project.versions()
        latest = max(versions, key=lambda v: int(str(v.version).split("/")[-1]))
        described.append({
            "workspace": workspace,
            "project": name,
            "version": int(str(latest.version).split("/")[-1]),
            "type": project.type,
            "classes": list(project.classes.keys()) if project.classes else [],
            "images": project.images,
            "license": "CC BY 4.0",
        })
    return described


def main() -> None:
    key = os.environ.get("ROBOFLOW_API_KEY")
    if not key:
        raise SystemExit("ROBOFLOW_API_KEY non definie")
    rf = Roboflow(api_key=key)

    sources = describe(rf)
    for s in sources:
        print(f"{s['workspace']}/{s['project']} v{s['version']} : {s['images']} images, "
              f"{s['type']}, licence {s['license']}, classes {s['classes']}")
    if "--list" in sys.argv:
        return

    if MERGED.exists():
        shutil.rmtree(MERGED)
    counts = {split: 0 for split in SPLITS}

    for s in sources:
        target = DOWNLOADS / f"{s['project']}-v{s['version']}"
        if not target.exists():
            # Meme format pour detection et segmentation : les polygones de la
            # seconde sont ramenes a des boites par to_single_class.
            rf.workspace(s["workspace"]).project(s["project"]).version(s["version"]) \
                .download("yolov8", location=str(target), overwrite=True)

        for split in SPLITS:
            images = target / split / "images"
            if not images.exists():
                continue
            out_images = MERGED / split / "images"
            out_labels = MERGED / split / "labels"
            out_images.mkdir(parents=True, exist_ok=True)
            out_labels.mkdir(parents=True, exist_ok=True)
            for image in images.iterdir():
                label = target / split / "labels" / (image.stem + ".txt")
                if not label.exists():
                    continue
                lines = to_single_class(label)
                # Prefixe par le projet : deux jeux peuvent reutiliser un nom.
                stem = f"{s['project']}__{image.stem}"
                shutil.copy(image, out_images / (stem + image.suffix))
                (out_labels / (stem + ".txt")).write_text("\n".join(lines))
                counts[split] += 1

    (MERGED / "holds.yaml").write_text(
        f"path: {MERGED.as_posix()}\n"
        "train: train/images\nval: valid/images\ntest: test/images\n"
        "names:\n  0: hold\n"
    )
    (MERGED / "manifest.json").write_text(json.dumps(
        {"sources": sources, "images": counts}, indent=2, ensure_ascii=False))
    print(f"Jeu fusionne : {counts}")


if __name__ == "__main__":
    main()
