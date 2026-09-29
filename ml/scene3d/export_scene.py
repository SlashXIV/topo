"""
Exporte un bloc de Topo en « paquet de scene » 3D, pour la visionneuse web du portfolio.

Rien ici n'entre dans l'application : c'est une chaine HORS LIGNE, qui precalcule ce que la
visionneuse affichera. La page web n'execute aucun modele, elle lit des fichiers statiques.

Entrees : une copie de la base Topo (schema v5), le dossier de ses photos, l'id d'un bloc.
Etapes :
  1. le bloc, ses prises et ses mouvements, tels que l'utilisateur les a corriges dans
     l'application — c'est SA detection, pas une nouvelle passe du modele ;
  2. la photo redressee (EXIF) et recadree sur le bloc : sa zone tracee, ou a defaut
     l'emprise de ses prises ;
  3. la profondeur, par Depth Anything V2 Small (Apache-2.0) : relief RELATIF, estime sur
     une seule photo, fait pour etre regarde et non mesure ;
  4. le contour precis de chaque prise, par SAM 2.1 tiny (Apache-2.0), guide par la boite
     que la detection de Topo a trouvee ;
  5. l'export : scene.json, photo.webp, depth.png (16 bits), et une image de controle.

Les variantes Base et Large de Depth Anything V2 sont sous licence NON commerciale : ne pas
les substituer a la Small.

Usage :
    python ml/scene3d/export_scene.py <topo.db> <dossier_photos> <id_bloc> <sortie>
"""

import json
import os
import shutil
import sqlite3
import sys
import tempfile
from pathlib import Path

import cv2
import numpy as np
import torch
from PIL import Image, ImageOps
from transformers import AutoImageProcessor, AutoModelForDepthEstimation, Sam2Model, Sam2Processor

DEPTH_MODEL = "depth-anything/Depth-Anything-V2-Small-hf"
SEGMENTATION_MODEL = "facebook/sam2.1-hiera-tiny"

# Ordinaux de Limb dans l'application (core/domain) : ne pas reordonner.
LIMBS = ["LEFT_HAND", "RIGHT_HAND", "LEFT_FOOT", "RIGHT_FOOT"]
KINDS = ["HOLD", "VOLUME"]

# Marge autour de l'emprise des prises quand le bloc n'a pas de zone tracee.
AUTO_ZONE_MARGIN = 0.08
# La photo exportee est lue par un navigateur : au-dela, du poids pour rien.
PHOTO_LONG_SIDE = 1280
# Le maillage de la visionneuse fait 256 x 256 : 512 pixels de profondeur suffisent.
DEPTH_LONG_SIDE = 512


def read_bloc(db_path: Path, bloc_id: str) -> dict:
    # Copie de travail : ouvrir la base en place ecrirait dans son journal WAL.
    work = Path(tempfile.mkdtemp())
    for name in os.listdir(db_path.parent):
        if name.startswith(db_path.name):
            shutil.copy(db_path.parent / name, work / name)
    db = sqlite3.connect(work / db_path.name)
    db.row_factory = sqlite3.Row

    bloc = db.execute(
        "SELECT b.*, s.location_name, c.name AS color_name, c.argb AS color_argb FROM blocs b "
        "JOIN sessions s ON s.id = b.session_id LEFT JOIN circuit_colors c ON c.id = b.color_id "
        "WHERE b.id = ?", (bloc_id,),
    ).fetchone()
    if bloc is None:
        raise SystemExit(f"Bloc introuvable : {bloc_id}")
    holds = db.execute("SELECT * FROM holds WHERE bloc_id = ?", (bloc_id,)).fetchall()
    moves = db.execute(
        "SELECT * FROM movements WHERE bloc_id = ? ORDER BY sequence_order", (bloc_id,),
    ).fetchall()
    return {"bloc": dict(bloc), "holds": [dict(h) for h in holds], "moves": [dict(m) for m in moves]}


def zone_of(data: dict) -> tuple[float, float, float, float]:
    """Zone (gauche, haut, droite, bas) normalisee : tracee, sinon emprise des prises."""
    b = data["bloc"]
    if b["zone_width"] is not None:
        cx, cy, w, h = b["zone_center_x"], b["zone_center_y"], b["zone_width"], b["zone_height"]
        return max(0, cx - w / 2), max(0, cy - h / 2), min(1, cx + w / 2), min(1, cy + h / 2)
    holds = [h for h in data["holds"] if h["kind"] == 0] or data["holds"]
    if not holds:
        return 0.0, 0.0, 1.0, 1.0
    left = min(h["bbox_x_center"] - h["bbox_width"] / 2 for h in holds) - AUTO_ZONE_MARGIN
    top = min(h["bbox_y_center"] - h["bbox_height"] / 2 for h in holds) - AUTO_ZONE_MARGIN
    right = max(h["bbox_x_center"] + h["bbox_width"] / 2 for h in holds) + AUTO_ZONE_MARGIN
    bottom = max(h["bbox_y_center"] + h["bbox_height"] / 2 for h in holds) + AUTO_ZONE_MARGIN
    return max(0, left), max(0, top), min(1, right), min(1, bottom)


def to_crop(x: float, y: float, zone) -> tuple[float, float]:
    left, top, right, bottom = zone
    return (x - left) / (right - left), (y - top) / (bottom - top)


def estimate_depth(image: Image.Image, device: str) -> np.ndarray:
    """Profondeur relative dans [0, 1], 1 = le plus proche de l'objectif."""
    processor = AutoImageProcessor.from_pretrained(DEPTH_MODEL)
    model = AutoModelForDepthEstimation.from_pretrained(DEPTH_MODEL).to(device).eval()
    with torch.no_grad():
        inputs = processor(images=image, return_tensors="pt").to(device)
        predicted = model(**inputs).predicted_depth
    depth = torch.nn.functional.interpolate(
        predicted.unsqueeze(1), size=image.size[::-1], mode="bicubic", align_corners=False,
    ).squeeze().cpu().numpy()
    # Depth Anything rend une DISPARITE : plus grand = plus proche. Normalisee sur les
    # percentiles 2-98 : un reflet ou un bord aberrant ne doit pas ecraser tout le relief.
    low, high = np.percentile(depth, [2, 98])
    depth = np.clip((depth - low) / max(high - low, 1e-6), 0, 1)
    # Lissage leger : la visionneuse deplace un maillage, et un bruit de pixel y devient
    # une pointe.
    return cv2.GaussianBlur(depth.astype(np.float32), (0, 0), sigmaX=2)


def segment_holds(image: Image.Image, prompts: list[tuple[str, list[float]]], device: str) -> list[np.ndarray | None]:
    """
    Un masque binaire par prise, guide par Topo ; None si aucun n'est credible.

    Chaque invite est ("box", boite) pour une prise DETECTEE, dont la boite epouse la prise,
    ou ("manual", boite) pour une prise posee A LA MAIN. La boite de cette derniere est
    arbitraire (taille fixe) : elle faisait deborder le masque sur la fleche voisine, et le
    point du doigt, legerement a cote de la prise, faisait segmenter le mur. On la remplace
    donc par une boite de taille typique, et parmi trois masques proposes on garde le plus
    sur qui ne couvre pas la moitie de la boite — au-dela, c'est du mur. Sans masque
    credible, pas de contour : la visionneuse dessine alors un cercle, plutot qu'un faux
    contour.
    """
    if not prompts:
        return []
    processor = Sam2Processor.from_pretrained(SEGMENTATION_MODEL)
    model = Sam2Model.from_pretrained(SEGMENTATION_MODEL).to(device).eval()
    boxes = [box for _, box in prompts]
    inputs = processor(images=image, input_boxes=[boxes], return_tensors="pt").to(device)
    with torch.no_grad():
        outputs = model(**inputs, multimask_output=True)
    candidates = processor.post_process_masks(outputs.pred_masks.cpu(), inputs["original_sizes"])[0]
    scores = outputs.iou_scores.cpu()[0]

    masks: list[np.ndarray | None] = []
    for index, (kind, box) in enumerate(prompts):
        options = [confine(m.numpy().astype(np.uint8), box) for m in candidates[index]]
        order = np.argsort(-scores[index].numpy())
        if kind == "box":
            masks.append(options[order[0]])
            continue
        box_area = max((box[2] - box[0]) * (box[3] - box[1]), 1.0)
        credible = [options[i] for i in order if 0 < options[i].sum() < MAX_MANUAL_MASK_SHARE * box_area]
        masks.append(credible[0] if credible else None)
    return masks


# Part maximale de sa boite qu'un masque de prise posee a la main peut couvrir.
MAX_MANUAL_MASK_SHARE = 0.5


def confine(mask: np.ndarray, box: list[float]) -> np.ndarray:
    """
    Borne le masque a la boite de detection elargie de 15 %.

    La detection sait OU est la prise ; SAM ne sert qu'a en epouser la forme. Constate sur
    une photo reelle : sur un mur uni, le masque d'une prise debordait sur le mur et sur la
    fleche voisine. Tout ce qui sort de la boite est une fuite.
    """
    x0, y0, x1, y1 = box
    mx, my = 0.15 * (x1 - x0), 0.15 * (y1 - y0)
    height, width = mask.shape
    keep = np.zeros_like(mask)
    keep[max(0, int(y0 - my)):min(height, int(y1 + my)), max(0, int(x0 - mx)):min(width, int(x1 + mx))] = 1
    return mask * keep


def polygon_of(mask: np.ndarray, width: int, height: int) -> list[list[float]]:
    """Contour principal du masque, simplifie, normalise dans le recadrage."""
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return []
    contour = max(contours, key=cv2.contourArea)
    # 1 % du perimetre : assez pour garder la silhouette d'une prise, assez peu de
    # points pour rester leger dans le JSON et dans la geometrie web.
    simplified = cv2.approxPolyDP(contour, 0.01 * cv2.arcLength(contour, True), True)
    return [[round(float(x) / width, 4), round(float(y) / height, 4)] for [[x, y]] in simplified]


def argb_to_hex(argb: int | None) -> str | None:
    return None if argb is None else "#{:06x}".format(argb & 0xFFFFFF)


def main(db_path: Path, photos: Path, bloc_id: str, out: Path) -> None:
    data = read_bloc(db_path, bloc_id)
    bloc = data["bloc"]
    device = "cuda" if torch.cuda.is_available() else "cpu"

    photo = ImageOps.exif_transpose(Image.open(photos / bloc["image_path"])).convert("RGB")
    zone = zone_of(data)
    w, h = photo.size
    crop = photo.crop((int(zone[0] * w), int(zone[1] * h), int(zone[2] * w), int(zone[3] * h)))
    crop.thumbnail((PHOTO_LONG_SIDE, PHOTO_LONG_SIDE))
    cw, ch = crop.size

    # Seules les prises du cadre : les autres appartiennent a des blocs voisins.
    holds = []
    for hold in data["holds"]:
        x, y = to_crop(hold["bbox_x_center"], hold["bbox_y_center"], zone)
        if 0 <= x <= 1 and 0 <= y <= 1:
            holds.append((hold, x, y))

    depth = estimate_depth(crop, device)
    # Taille d'une vraie prise sur CETTE photo, d'apres les prises detectees : c'est elle
    # qui borne le masque d'une prise posee a la main, dont la boite est arbitraire.
    detected = [h for h, _, _ in holds if h["origin"] == 1 and h["kind"] == 0]
    typical_w = float(np.median([h["bbox_width"] for h in detected])) if detected else 0.03
    typical_h = float(np.median([h["bbox_height"] for h in detected])) if detected else 0.03
    prompts = []
    for hold, x, y in holds:
        manual = hold["origin"] == 0
        bw = (2 * typical_w if manual else hold["bbox_width"]) / (zone[2] - zone[0]) * cw
        bh = (2 * typical_h if manual else hold["bbox_height"]) / (zone[3] - zone[1]) * ch
        box = [x * cw - bw / 2, y * ch - bh / 2, x * cw + bw / 2, y * ch + bh / 2]
        prompts.append(("manual" if manual else "box", box))
    masks = segment_holds(crop, prompts, device)

    scene_holds = []
    for (hold, x, y), mask in zip(holds, masks):
        region = depth[mask.astype(bool)] if mask is not None else np.empty(0)
        scene_holds.append({
            "id": hold["id"],
            "kind": KINDS[hold["kind"]],
            "center": [round(x, 4), round(y, 4)],
            "depth": round(float(np.median(region)) if region.size else float(depth[int(y * (ch - 1)), int(x * (cw - 1))]), 4),
            "score": round(hold["confidence_score"], 3),
            "polygon": polygon_of(mask, cw, ch) if mask is not None else [],
        })
    kept = {h["id"] for h in scene_holds}
    scene_moves = [
        {"order": m["sequence_order"], "limb": LIMBS[m["limb_type"]], "from": m["source_hold_id"], "to": m["target_hold_id"]}
        for m in data["moves"] if m["target_hold_id"] in kept
    ]

    out.mkdir(parents=True, exist_ok=True)
    crop.save(out / "photo.webp", quality=82, method=6)
    small = cv2.resize(depth, (0, 0), fx=DEPTH_LONG_SIDE / max(cw, ch), fy=DEPTH_LONG_SIDE / max(cw, ch), interpolation=cv2.INTER_AREA)
    cv2.imwrite(str(out / "depth.png"), (small * 65535).astype(np.uint16))
    scene = {
        "version": 1,
        "bloc": {
            "location": bloc["location_name"],
            "grade": bloc["grade"],
            "difficulty": {"name": bloc["color_name"], "color": argb_to_hex(bloc["color_argb"])},
        },
        "image": {"width": cw, "height": ch},
        "holds": scene_holds,
        "moves": scene_moves,
    }
    (out / "scene.json").write_text(json.dumps(scene, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")

    # Image de controle, a relire avant toute integration web : contours sur la photo,
    # profondeur en fausses couleurs a cote.
    control = cv2.cvtColor(np.asarray(crop), cv2.COLOR_RGB2BGR)
    for hold in scene_holds:
        pts = (np.array(hold["polygon"]) * [cw, ch]).astype(np.int32)
        colour = (180, 180, 180) if hold["kind"] == "VOLUME" else (255, 0, 255)
        if len(pts):
            cv2.polylines(control, [pts], True, colour, 2)
    heat = cv2.applyColorMap((depth * 255).astype(np.uint8), cv2.COLORMAP_TURBO)
    cv2.imwrite(str(out / "_controle.jpg"), np.hstack([control, heat]))

    size = sum(f.stat().st_size for f in out.iterdir() if not f.name.startswith("_"))
    print(f"{len(scene_holds)} prises, {len(scene_moves)} mouvements, {cw}x{ch}, {size / 1024:.0f} Ko -> {out}")


if __name__ == "__main__":
    if len(sys.argv) != 5:
        raise SystemExit(__doc__)
    main(Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3], Path(sys.argv[4]))
