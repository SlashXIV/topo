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
};

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
 * classe : le modele de prises n'aura qu'une classe, et sur le modele de
 * substitution deux classes superposees au meme endroit sont de toute facon une
 * seule prise pour l'utilisateur.
 */
std::vector<Detection> NonMaxSuppression(std::vector<Detection> candidates,
                                         float iou_threshold,
                                         size_t max_detections);

}  // namespace topo

#endif  // TOPO_VISION_PROCESSOR_H
