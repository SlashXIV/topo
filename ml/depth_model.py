"""
Modele de relief de la vue 3D : telechargement et reference de test.

Depth Anything V2 SMALL, Apache-2.0, dans sa version ONNX quantifiee INT8 publiee par
onnx-community (27 Mo). Les variantes Base et Large sont NON commerciales : ne pas les
substituer. Choix mesure sur le Xiaomi (DepthModelBenchmark, 392x518) : INT8 463 ms,
FP16 796 ms, q4f16 791 ms ; XNNPACK refuse les trois. Fidelite contre FP32 sur six photos
de Villenave : correlation >= 0,997.

Sorties :
  - vision/src/main/assets/models/depth.onnx : le modele embarque (hors git, *.onnx) ;
  - vision/src/androidTest/assets/depth/     : image, reference.json, input.bin (tenseur
    d'entree) et reference.bin (relief), en float32 petit-boutiste, calcules par un MIROIR
    NumPy du pretraitement et de la normalisation de depth_estimator.cpp, sur ONNX Runtime
    CPU.

Usage (depuis la racine du depot) :
    ml/.venv/Scripts/python ml/depth_model.py [--image chemin.png]
"""

import argparse
import hashlib
import json
import urllib.request
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
MODEL = ROOT / "vision" / "src" / "main" / "assets" / "models" / "depth.onnx"
OUT = ROOT / "vision" / "src" / "androidTest" / "assets" / "depth"
URL = "https://huggingface.co/onnx-community/depth-anything-v2-small/resolve/main/onnx/model_int8.onnx"
# Empreinte du fichier mesure et valide : un modele republie sous le meme nom ne doit
# pas entrer dans l'APK sans que personne ne le voie.
SHA256 = "01aa7a23de3f4a0ee1a2bb9997e6918104c85a9f95dea46d27b9b3fb0c6b9001"


def read_native_constants() -> dict:
    header = (ROOT / "vision" / "src" / "main" / "cpp" / "depth_estimator.h").read_text()
    values = {}
    for line in header.splitlines():
        if line.startswith("constexpr ") and " = " in line:
            name, value = line.split(" = ")
            values[name.split()[-1]] = float(value.split(";")[0].rstrip("f"))
    return values


def fetch_model() -> None:
    if not MODEL.exists():
        MODEL.parent.mkdir(parents=True, exist_ok=True)
        print(f"Telechargement de {URL}")
        urllib.request.urlretrieve(URL, MODEL)
    digest = hashlib.sha256(MODEL.read_bytes()).hexdigest()
    if digest != SHA256:
        MODEL.unlink()
        raise SystemExit(f"Empreinte inattendue ({digest}) : modele supprime")


def input_size(width: int, height: int, c: dict) -> tuple[int, int]:
    """Miroir de ComputeDepthInputSize."""
    scale = np.float32(c["kDepthLongSide"]) / np.float32(max(width, height))
    patch = int(c["kDepthPatch"])
    side = lambda v: max(1, int(np.floor(np.float32(v) * scale / np.float32(patch) + np.float32(0.5)))) * patch
    return side(width), side(height)


def preprocess(rgba: np.ndarray, size: tuple[int, int], c: dict) -> np.ndarray:
    """Miroir de Preprocess : bilineaire a centres de pixels, echelles par axe, ImageNet."""
    height, width = rgba.shape[:2]
    out_w, out_h = size
    rgb = rgba[..., :3].astype(np.float32)
    xs = np.clip((np.arange(out_w, dtype=np.float32) + 0.5) * np.float32(width / out_w) - 0.5, 0, width - 1)
    ys = np.clip((np.arange(out_h, dtype=np.float32) + 0.5) * np.float32(height / out_h) - 0.5, 0, height - 1)
    x0 = np.floor(xs).astype(np.int32)
    y0 = np.floor(ys).astype(np.int32)
    x1 = np.minimum(x0 + 1, width - 1)
    y1 = np.minimum(y0 + 1, height - 1)
    fx = (xs - x0)[None, :, None]
    fy = (ys - y0)[:, None, None]
    top = rgb[y0][:, x0] * (1 - fx) + rgb[y0][:, x1] * fx
    bottom = rgb[y1][:, x0] * (1 - fx) + rgb[y1][:, x1] * fx
    value = (top * (1 - fy) + bottom * fy) / 255.0
    mean = np.array([c["kDepthMeanR"], c["kDepthMeanG"], c["kDepthMeanB"]], np.float32)
    std = np.array([c["kDepthStdR"], c["kDepthStdG"], c["kDepthStdB"]], np.float32)
    return ((value - mean) / std).transpose(2, 0, 1)[None].astype(np.float32)


def normalize(depth: np.ndarray, c: dict) -> np.ndarray:
    """Miroir de NormalizeDepth : percentiles par rang arrondi, puis boite 3x3."""
    flat = np.sort(depth.ravel())
    rank = lambda p: int(np.floor(np.float32(p) * np.float32(flat.size - 1) + np.float32(0.5)))
    low, high = flat[rank(c["kDepthLowPercentile"])], flat[rank(c["kDepthHighPercentile"])]
    norm = np.clip((depth - low) / max(high - low, 1e-6), 0, 1).astype(np.float32)
    padded = np.pad(norm, 1, mode="edge")
    h, w = norm.shape
    return sum(padded[dy:dy + h, dx:dx + w] for dy in range(3) for dx in range(3)) / np.float32(9)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--image", type=Path,
                        default=ROOT / "vision" / "src" / "androidTest" / "assets" / "placeholder" / "bus.png",
                        help="image de test, PNG pour que Python et Android lisent les memes pixels")
    args = parser.parse_args()
    constants = read_native_constants()
    fetch_model()

    OUT.mkdir(parents=True, exist_ok=True)
    image = Image.open(args.image).convert("RGBA")
    image.save(OUT / "image.png")
    rgba = np.asarray(Image.open(OUT / "image.png").convert("RGBA"))
    size = input_size(rgba.shape[1], rgba.shape[0], constants)

    session = ort.InferenceSession(str(MODEL), providers=["CPUExecutionProvider"])
    tensor = preprocess(rgba, size, constants)
    tensor.astype("<f4").tofile(OUT / "input.bin")
    raw = session.run(None, {session.get_inputs()[0].name: tensor})[0][0]
    depth = normalize(raw, constants)
    depth.astype("<f4").tofile(OUT / "reference.bin")
    (OUT / "reference.json").write_text(json.dumps({
        "image": "image.png", "width": int(depth.shape[1]), "height": int(depth.shape[0]),
    }, indent=2))
    print(f"Reference {depth.shape[1]}x{depth.shape[0]}, moyenne {depth.mean():.3f}")


if __name__ == "__main__":
    main()
