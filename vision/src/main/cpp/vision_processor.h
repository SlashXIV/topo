#ifndef BETACLIMB_VISION_PROCESSOR_H
#define BETACLIMB_VISION_PROCESSOR_H

#include <cstdint>
#include <memory>
#include <vector>

namespace topo {

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

/** Format des pixels transmis au moteur. */
enum class PixelFormat : int32_t {
    kRgba8888 = 0,
    kYuv420   = 1,
};

/**
 * Moteur d'inference.
 *
 * Non copiable : il detiendra une session ONNX Runtime, ressource lourde dont la
 * duree de vie est pilotee explicitement depuis Kotlin (VisionEngine.close()).
 * Pimpl : les en-tetes d'ONNX Runtime ne fuient jamais dans cette interface, ce
 * qui garde jni_bridge.cpp compilable sans le SDK ONNX.
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
     * Detecte les prises dans une image deja en memoire.
     *
     * @param pixels pointeur DIRECT sur les pixels — ni copie, ni propriete
     *               transferee. Doit rester valide pour toute la duree de l'appel.
     * @param stride octets par ligne (!= width * bpp si l'image est paddee).
     * @return les detections, deja filtrees par NMS (implementation au sprint Vision).
     */
    std::vector<Detection> Detect(const uint8_t* pixels,
                                  int32_t width,
                                  int32_t height,
                                  int32_t stride,
                                  PixelFormat format);

    /** true des que le modele est charge et la session prete. */
    bool IsReady() const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

}  // namespace topo

#endif  // BETACLIMB_VISION_PROCESSOR_H
