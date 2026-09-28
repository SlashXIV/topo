"""
Quantification statique INT8 d'un modele YOLO ONNX, au format QDQ.

QDQ (QuantizeLinear / DequantizeLinear autour des operateurs) plutot que les
operateurs QLinear* : c'est le format que l'Execution Provider XNNPACK d'ONNX
Runtime sait fusionner en noyaux entiers. La quantification DYNAMIQUE ne
convient pas ici : elle ne couvre pas les convolutions, qui font l'essentiel
du calcul d'un YOLO.

La calibration mesure les plages d'activation sur des images representatives,
pretraitees EXACTEMENT comme le fait vision_processor.cpp (letterbox partage
avec export_placeholder.py). Des images non representatives donneraient des
plages fausses, et un modele rapide mais aveugle.

Usage :
    python ml/quantize.py <modele.onnx> <sortie.onnx> <dossier_images_calibration>
"""

import sys
from pathlib import Path

import numpy as np
from onnxruntime.quantization import (
    CalibrationDataReader,
    CalibrationMethod,
    QuantFormat,
    QuantType,
    quantize_static,
)
from onnxruntime.quantization.shape_inference import quant_pre_process
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from export_placeholder import letterbox, read_native_constants  # noqa: E402


class LetterboxReader(CalibrationDataReader):
    def __init__(self, input_name: str, images: list[Path], size: int, pad: float):
        self._batches = iter(
            {input_name: letterbox(np.asarray(Image.open(p).convert("RGBA")), size, pad)[0][None]}
            for p in images
        )

    def get_next(self):
        return next(self._batches, None)


def main(source: Path, target: Path, calibration_dir: Path) -> None:
    import onnx

    constants = read_native_constants()
    images = sorted(p for p in calibration_dir.iterdir()
                    if p.suffix.lower() in {".jpg", ".jpeg", ".png"})
    if not images:
        raise SystemExit(f"Aucune image de calibration dans {calibration_dir}")

    # Pre-traitement recommande par ORT avant quantification : inference des
    # formes et fusion des operateurs, sans quoi certains noeuds restent en FP32.
    prepared = target.with_suffix(".prep.onnx")
    quant_pre_process(str(source), str(prepared))

    graph = onnx.load(str(prepared)).graph
    input_name = graph.input[0].name
    # La tete de detection (dernier module, « model.23 » pour YOLO11n) reste en
    # flottant : ses convolutions produisent directement boites et scores, et un
    # arrondi y coute plus cher qu'ailleurs. Elle pese peu dans le calcul total.
    head = max(int(n.name.split("/model.")[1].split("/")[0])
               for n in graph.node if n.name.startswith("/model."))
    excluded = [n.name for n in graph.node if n.name.startswith(f"/model.{head}/")]
    quantize_static(
        str(prepared),
        str(target),
        LetterboxReader(input_name, images, int(constants["kInputSize"]), constants["kPadValue"]),
        quant_format=QuantFormat.QDQ,
        activation_type=QuantType.QUInt8,
        weight_type=QuantType.QInt8,
        per_channel=True,
        calibrate_method=CalibrationMethod.Percentile,
        extra_options={"CalibPercentile": 99.999},
        # Seules les convolutions sont quantifiees — l'essentiel du calcul.
        # Quantifier TOUT le graphe detruit le modele : la sortie concatene les
        # coordonnees (0 a 640) et les scores (0 a 1) dans un seul tenseur, qui
        # recoit une seule echelle INT8 ; un pas vaut alors ~2,5 et chaque score
        # s'arrondit a zero. Constate : mAP50 = 0,000 en tout-INT8, contre 0,879
        # en FP32. La queue de la tete de detection reste donc en flottant.
        op_types_to_quantize=["Conv"],
        nodes_to_exclude=excluded,
    )
    prepared.unlink()
    print(f"{target} : {target.stat().st_size / 1e6:.1f} Mo, calibre sur {len(images)} images, "
          f"{len(excluded)} noeuds de la tete (model.{head}) en flottant")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit(__doc__)
    main(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
