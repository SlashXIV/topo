#ifndef TOPO_VISION_PROCESSOR_H
#define TOPO_VISION_PROCESSOR_H

#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

namespace topo {

// ---------------------------------------------------------------------------
//  Parametres de detection.
//
//  ml/export_placeholder.py relit ces valeurs dans CE fichier pour calculer la
//  reference des tests : les modifier ici suffit, rien n'est recopie ailleurs.
//  Garder la forme « constexpr <type> kNom = <valeur>; » sur une ligne.
// ---------------------------------------------------------------------------

/** Cote de l'entree carree du modele, en pixels. */
constexpr int32_t kInputSize = 640;

/** Niveau des bandes de letterbox : le gris d'entrainement des YOLO Ultralytics. */
constexpr float kPadValue = 114.0f;

/** Confiance minimale d'une detection conservee. */
constexpr float kScoreThreshold = 0.25f;

/** Au-dela de ce recouvrement, deux boites designent la meme prise. */
constexpr float kIouThreshold = 0.45f;

/** Borne haute : un mur depasse rarement 200 prises. */
constexpr int32_t kMaxDetections = 300;

/** Nature de l'objet detecte. Les valeurs traversent le pont JNI : ne pas renumeroter. */
enum class Kind : int32_t {
    kHold = 0,
    kVolume = 1,
};

/** Couleur CIELAB (illuminant D65) : L dans [0,100], a et b signes. */
struct LabColor {
    float l;
    float a;
    float b;
};

/**
 * Une prise detectee.
 * Coordonnees NORMALISEES dans [0,1] par rapport a l'image d'entree : le modele
 * de donnees Room stocke exactement cette forme, ce qui rend les prises
 * independantes de la resolution de la photo et du facteur d'echelle du Canvas.
 */
struct Detection {
    float cx;     // centre X
    float cy;     // centre Y
    float w;      // largeur
    float h;      // hauteur
    float score;  // confiance [0,1]
    Kind kind = Kind::kHold;
    LabColor color{0.0f, 0.0f, 0.0f};
};

/**
 * Contrat de classes d'un modele :
 *  - 1 classe  : prises seulement ;
 *  - 2 classes : [prise, volume], dans cet ordre (ml/fetch_datasets.py --volumes).
 * Au-dela (modele de substitution COCO), tout est traite comme prise.
 */
constexpr int32_t kVolumeClassCount = 2;

/**
 * Moteur d'inference.
 *
 * Non copiable : il detient une session ONNX Runtime, ressource lourde dont la
 * duree de vie est pilotee explicitement depuis Kotlin (VisionEngine.close()).
 * Pimpl : les en-tetes d'ONNX Runtime ne fuient jamais dans cette interface, ce
 * qui garde jni_bridge.cpp independant du SDK ONNX.
 *
 * Les erreurs d'ONNX Runtime remontent en exceptions derivees de
 * std::exception ; le pont JNI les convertit en exceptions Java.
 */
class VisionProcessor {
public:
    VisionProcessor();
    ~VisionProcessor();

    VisionProcessor(const VisionProcessor&) = delete;
    VisionProcessor& operator=(const VisionProcessor&) = delete;
    VisionProcessor(VisionProcessor&&) = delete;
    VisionProcessor& operator=(VisionProcessor&&) = delete;

    /**
     * Charge un modele ONNX depuis la memoire et ouvre la session.
     *
     * ONNX Runtime analyse et copie le modele : [data] peut etre libere des le
     * retour. Un second appel remplace la session precedente.
     */
    void LoadModel(const uint8_t* data, size_t size);

    /**
     * Detecte les prises dans une image RGBA_8888 deja en memoire.
     *
     * @param pixels pointeur DIRECT sur les pixels — ni copie, ni propriete
     *               transferee. Doit rester valide pour toute la duree de l'appel.
     * @param stride octets par ligne (!= width * 4 si l'image est paddee).
     * @return les detections apres suppression des non-maxima ; vide si aucun
     *         modele n'est charge.
     */
    std::vector<Detection> Detect(const uint8_t* pixels,
                                  int32_t width,
                                  int32_t height,
                                  int32_t stride);

    /** true des que le modele est charge et la session prete. */
    bool IsReady() const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

/**
 * Suppression des non-maxima, toutes classes confondues.
 *
 * Exposee pour etre testee isolement. Toutes classes confondues et non par
 * classe : deux boites de classes differentes superposees au meme endroit sont
 * une seule prise pour l'utilisateur. Une prise vissee SUR un volume n'est pas
 * pour autant supprimee : leurs tailles tres differentes donnent un IoU faible.
 */
std::vector<Detection> NonMaxSuppression(std::vector<Detection> candidates,
                                         float iou_threshold,
                                         size_t max_detections);

/**
 * Couleur d'une prise : mediane CIELAB sur le CENTRE de sa boite.
 *
 * Le centre et non la boite entiere : une boite englobante deborde sur le mur a
 * ses coins, d'autant plus que la prise est ronde. La mediane et non la moyenne :
 * le trou de la vis, une trace de magnesie ou un reflet sont des valeurs
 * aberrantes qu'une moyenne absorberait dans la couleur.
 *
 * Exposee pour etre testee isolement.
 */
LabColor MeasureColor(const uint8_t* pixels, int32_t width, int32_t height, int32_t stride,
                      const Detection& box);

}  // namespace topo

#endif  // TOPO_VISION_PROCESSOR_H
