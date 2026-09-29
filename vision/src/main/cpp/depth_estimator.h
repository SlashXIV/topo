#ifndef TOPO_DEPTH_ESTIMATOR_H
#define TOPO_DEPTH_ESTIMATOR_H

#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>

namespace topo {

// ---------------------------------------------------------------------------
//  Parametres de l'estimation de profondeur (Depth Anything V2 Small).
//
//  ml/depth_model.py relit ces valeurs dans CE fichier pour calculer la
//  reference des tests. Garder la forme « constexpr <type> kNom = <valeur>; ».
// ---------------------------------------------------------------------------

/**
 * Grand cote de l'entree du modele, en pixels. 518 est la resolution
 * d'entrainement ; mesure sur le Xiaomi : ~460 ms en INT8 pour 392x518.
 */
constexpr int32_t kDepthLongSide = 518;

/** Taille des patchs du ViT : chaque cote d'entree doit en etre un multiple. */
constexpr int32_t kDepthPatch = 14;

/**
 * Percentiles de normalisation. Les extremes d'une carte de disparite sont un
 * reflet, un bord de photo ou le plafond : normaliser sur eux ecraserait le
 * relief du mur.
 */
constexpr float kDepthLowPercentile = 0.02f;
constexpr float kDepthHighPercentile = 0.98f;

/** Normalisation ImageNet, celle de l'entrainement du modele, canal par canal. */
constexpr float kDepthMeanR = 0.485f;
constexpr float kDepthMeanG = 0.456f;
constexpr float kDepthMeanB = 0.406f;
constexpr float kDepthStdR = 0.229f;
constexpr float kDepthStdG = 0.224f;
constexpr float kDepthStdB = 0.225f;

/** Relief relatif, 1 = le plus proche de l'objectif, en grille ligne par ligne. */
struct DepthMap {
    int32_t width = 0;
    int32_t height = 0;
    std::vector<float> values;
};

/** Dimensions d'entree du modele pour une image source : ratio conserve, multiples de 14. */
struct DepthInputSize {
    int32_t width;
    int32_t height;
};
DepthInputSize ComputeDepthInputSize(int32_t width, int32_t height);

/**
 * Estimation de profondeur monoculaire.
 *
 * Deuxieme session ONNX Runtime, distincte de celle de VisionProcessor : les
 * deux modeles n'ont ni la meme entree ni les memes fournisseurs d'execution.
 * XNNPACK refuse le Resize de la tete de Depth Anything (constate sur le Xiaomi,
 * ORT 1.30) : cette session reste sur le CPU d'ONNX Runtime, dont les noyaux
 * entiers servent le modele quantifie INT8.
 *
 * Meme contrat que VisionProcessor : non copiable, erreurs d'ORT en exceptions
 * std::exception, converties en exceptions Java par le pont.
 */
class DepthEstimator {
public:
    DepthEstimator();
    ~DepthEstimator();

    DepthEstimator(const DepthEstimator&) = delete;
    DepthEstimator& operator=(const DepthEstimator&) = delete;
    DepthEstimator(DepthEstimator&&) = delete;
    DepthEstimator& operator=(DepthEstimator&&) = delete;

    /** Charge le modele depuis la memoire ; [data] peut etre libere des le retour. */
    void LoadModel(const uint8_t* data, size_t size);

    /**
     * Relief d'une image RGBA_8888, a la resolution d'entree du modele.
     *
     * @return une carte normalisee dans [0, 1] ; vide si aucun modele n'est charge.
     */
    DepthMap Estimate(const uint8_t* pixels, int32_t width, int32_t height, int32_t stride);

    bool IsReady() const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

/**
 * Tenseur CHW normalise ImageNet de [size], ecrit dans [chw] (3 * largeur * hauteur).
 *
 * Un seul passage, du pixel au tenseur. Bilineaire a centres de pixels, echelles
 * independantes par axe : l'arrondi aux multiples de 14 deforme tres legerement
 * l'image, et c'est voulu — la carte doit recouvrir exactement la photo, sans
 * bande de remplissage. ml/depth_model.py en contient le miroir NumPy.
 *
 * Expose pour etre teste isolement : le relief final absorbe presque toute erreur
 * de pretraitement (canaux inverses, ecart-type oublie : moins de 0,01 d'ecart
 * moyen apres normalisation par percentiles, mesure). Seule une comparaison
 * exacte du tenseur les revele.
 */
void PreprocessDepth(const uint8_t* pixels, int32_t width, int32_t height, int32_t stride,
                     DepthInputSize size, float* chw);

/**
 * Normalise une disparite brute sur ses percentiles puis la lisse (boite 3x3,
 * bords repliques). Le lissage efface le grain que la quantification INT8
 * ajoute, sans toucher au relief des prises, qui s'etend sur des dizaines de
 * pixels.
 *
 * Exposee pour etre testee isolement.
 */
void NormalizeDepth(std::vector<float>& values, int32_t width, int32_t height);

}  // namespace topo

#endif  // TOPO_DEPTH_ESTIMATOR_H
