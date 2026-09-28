"""
Genere le modele de SUBSTITUTION du Sprint 10 et sa reference de test.

Aucun modele de prises n'est encore entraine. Pour construire et tester la chaine
d'inference native sans l'attendre, on exporte un YOLO nano generique (COCO) au
meme format de sortie que le futur modele : [1, 4 + nc, N].

Licence : Ultralytics est sous AGPL-3.0. Le modele produit ici va UNIQUEMENT dans
les ressources de test instrumente de :vision, jamais dans l'APK livre ; il est
de toute facon exclu de git (*.onnx dans .gitignore) et se regenere par ce script.

Sorties, dans vision/src/androidTest/assets/placeholder/ :
  - yolo11n_coco.onnx : le modele ;
  - bus.png           : image de test, en PNG pour que Python et Android decodent
                        EXACTEMENT les memes pixels (deux decodeurs JPEG peuvent
                        diverger d'un niveau de gris) ;
  - reference.json    : detections attendues.

La reference est calculee par un MIROIR NumPy du pretraitement et du NMS de
vision_processor.cpp, sur ONNX Runtime CPU. Le test Android ne compare donc pas
le code natif a lui-meme : une erreur de letterbox, d'ordre des canaux ou de
decodage des boites le ferait echouer.

Usage (depuis la racine du depot) :
    python -m venv ml/.venv
    ml/.venv/Scripts/python -m pip install ultralytics onnxruntime pillow numpy
    ml/.venv/Scripts/python ml/export_placeholder.py
"""

import json
import shutil
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image
from ultralytics import YOLO
from ultralytics.utils import ASSETS

# Constantes partagees avec vision_processor.cpp : toute divergence invalide la
# reference. Elles sont relues depuis l'en-tete pour ne pas etre recopiees.
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "vision" / "src" / "androidTest" / "assets" / "placeholder"


def read_native_constants() -> dict:
    header = (ROOT / "vision" / "src" / "main" / "cpp" / "vision_processor.h").read_text()
    wanted = ("kInputSize", "kScoreThreshold", "kIouThreshold", "kMaxDetections", "kPadValue")
    values = {}
    for line in header.splitlines():
        for name in wanted:
            token = f" {name} = "
            if token in line:
                values[name] = float(line.split(token)[1].split(";")[0].rstrip("f"))
    missing = set(wanted) - values.keys()
    if missing:
        raise SystemExit(f"Constantes introuvables dans vision_processor.h : {missing}")
    return values


def letterbox(rgba: np.ndarray, size: int, pad: float) -> tuple[np.ndarray, float, int, int]:
    """Miroir exact de Letterbox() : bilineaire a centres de pixels, bordure gris."""
    height, width = rgba.shape[:2]
    scale = min(size / width, size / height)
    new_w = int(round(width * scale))
    new_h = int(round(height * scale))
    pad_x = (size - new_w) // 2
    pad_y = (size - new_h) // 2

    out = np.full((3, size, size), pad / 255.0, dtype=np.float32)
    rgb = rgba[..., :3].astype(np.float32)

    xs = (np.arange(new_w, dtype=np.float32) + 0.5) / scale - 0.5
    ys = (np.arange(new_h, dtype=np.float32) + 0.5) / scale - 0.5
    xs = np.clip(xs, 0, width - 1)
    ys = np.clip(ys, 0, height - 1)
    x0 = np.floor(xs).astype(np.int32)
    y0 = np.floor(ys).astype(np.int32)
    x1 = np.minimum(x0 + 1, width - 1)
    y1 = np.minimum(y0 + 1, height - 1)
    fx = (xs - x0)[None, :, None]
    fy = (ys - y0)[:, None, None]

    top = rgb[y0][:, x0] * (1 - fx) + rgb[y0][:, x1] * fx
    bottom = rgb[y1][:, x0] * (1 - fx) + rgb[y1][:, x1] * fx
    resized = (top * (1 - fy) + bottom * fy) / 255.0

    out[:, pad_y:pad_y + new_h, pad_x:pad_x + new_w] = resized.transpose(2, 0, 1)
    return out, scale, pad_x, pad_y


def iou(a, b) -> float:
    ax0, ay0, ax1, ay1 = a[0] - a[2] / 2, a[1] - a[3] / 2, a[0] + a[2] / 2, a[1] + a[3] / 2
    bx0, by0, bx1, by1 = b[0] - b[2] / 2, b[1] - b[3] / 2, b[0] + b[2] / 2, b[1] + b[3] / 2
    iw = max(0.0, min(ax1, bx1) - max(ax0, bx0))
    ih = max(0.0, min(ay1, by1) - max(ay0, by0))
    inter = iw * ih
    union = a[2] * a[3] + b[2] * b[3] - inter
    return inter / union if union > 0 else 0.0


def decode(output: np.ndarray, width: int, height: int, scale: float, pad_x: int, pad_y: int,
           c: dict) -> list[list[float]]:
    """Miroir de Decode() puis NonMaxSuppression() : NMS toutes classes confondues."""
    rows = output[0]  # [4 + nc, N]
    scores = rows[4:].max(axis=0)
    candidates = []
    for i in np.nonzero(scores >= c["kScoreThreshold"])[0]:
        cx, cy, w, h = rows[:4, i]
        x0 = np.clip(((cx - w / 2) - pad_x) / scale, 0, width)
        x1 = np.clip(((cx + w / 2) - pad_x) / scale, 0, width)
        y0 = np.clip(((cy - h / 2) - pad_y) / scale, 0, height)
        y1 = np.clip(((cy + h / 2) - pad_y) / scale, 0, height)
        if x1 <= x0 or y1 <= y0:
            continue
        candidates.append([
            float((x0 + x1) / 2 / width), float((y0 + y1) / 2 / height),
            float((x1 - x0) / width), float((y1 - y0) / height), float(scores[i]),
        ])

    candidates.sort(key=lambda d: d[4], reverse=True)
    kept = []
    for d in candidates:
        if all(iou(d, k) <= c["kIouThreshold"] for k in kept):
            kept.append(d)
            if len(kept) == int(c["kMaxDetections"]):
                break
    return kept


def main() -> None:
    constants = read_native_constants()
    OUT.mkdir(parents=True, exist_ok=True)

    work = OUT / "_work"
    work.mkdir(exist_ok=True)
    model = YOLO(str(work / "yolo11n.pt"))
    # Entree fixe, sans NMS integre : le NMS est la responsabilite du code natif.
    exported = Path(model.export(format="onnx", imgsz=int(constants["kInputSize"]),
                                 dynamic=False, simplify=True, nms=False))
    shutil.copy(exported, OUT / "yolo11n_coco.onnx")
    shutil.rmtree(work, ignore_errors=True)

    image = Image.open(ASSETS / "bus.jpg").convert("RGBA")
    image.save(OUT / "bus.png")
    rgba = np.asarray(Image.open(OUT / "bus.png").convert("RGBA"))
    height, width = rgba.shape[:2]

    tensor, scale, pad_x, pad_y = letterbox(rgba, int(constants["kInputSize"]), constants["kPadValue"])
    session = ort.InferenceSession(str(OUT / "yolo11n_coco.onnx"), providers=["CPUExecutionProvider"])
    output = session.run(None, {session.get_inputs()[0].name: tensor[None]})[0]
    detections = decode(output, width, height, scale, pad_x, pad_y, constants)

    (OUT / "reference.json").write_text(json.dumps({
        "image": "bus.png",
        "width": width,
        "height": height,
        "output_shape": list(output.shape),
        "detections": detections,
    }, indent=2))
    print(f"{len(detections)} detections de reference, sortie {list(output.shape)}")
    for d in detections:
        print("  cx=%.3f cy=%.3f w=%.3f h=%.3f score=%.3f" % tuple(d))


if __name__ == "__main__":
    main()
