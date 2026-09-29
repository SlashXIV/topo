#include "vision_processor.h"

#include <android/log.h>
#include <onnxruntime_cxx_api.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <stdexcept>
#include <string>
#include <thread>

namespace topo {
namespace {

constexpr const char* kTag = "TopoVision";

/** sRGB 8 bits -> CIELAB D65, par les formules de la norme (sans table). */
LabColor SrgbToLab(uint8_t r8, uint8_t g8, uint8_t b8) {
    auto linear = [](uint8_t v) {
        const float c = static_cast<float>(v) / 255.0f;
        return c <= 0.04045f ? c / 12.92f : std::pow((c + 0.055f) / 1.055f, 2.4f);
    };
    const float r = linear(r8), g = linear(g8), b = linear(b8);
    // Matrice sRGB -> XYZ, normalisee par le blanc de reference D65.
    const float x = (0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.95047f;
    const float y = (0.2126f * r + 0.7152f * g + 0.0722f * b);
    const float z = (0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.08883f;
    auto f = [](float t) {
        return t > 0.008856f ? std::cbrt(t) : (7.787f * t + 16.0f / 116.0f);
    };
    const float fx = f(x), fy = f(y), fz = f(z);
    return {116.0f * fy - 16.0f, 500.0f * (fx - fy), 200.0f * (fy - fz)};
}

/** Parametres du letterbox, necessaires pour ramener les boites dans l'image source. */
struct Letterbox {
    float scale;
    int32_t pad_x;
    int32_t pad_y;
};

/**
 * Redimensionne en conservant le ratio, centre dans un carre gris, et ecrit
 * directement le tenseur CHW normalise [0,1] attendu par le modele.
 *
 * Bilineaire a centres de pixels : c'est la convention d'OpenCV, donc celle de
 * l'entrainement. ml/export_placeholder.py en contient le miroir NumPy exact ;
 * modifier l'un sans l'autre invalide la reference des tests.
 *
 * Un seul passage, du pixel RGBA au flottant : aucune image intermediaire
 * redimensionnee n'est allouee.
 */
Letterbox Preprocess(const uint8_t* pixels, int32_t width, int32_t height, int32_t stride,
                     float* chw) {
    const float scale = std::min(static_cast<float>(kInputSize) / static_cast<float>(width),
                                 static_cast<float>(kInputSize) / static_cast<float>(height));
    const auto new_w = static_cast<int32_t>(std::lround(static_cast<float>(width) * scale));
    const auto new_h = static_cast<int32_t>(std::lround(static_cast<float>(height) * scale));
    const int32_t pad_x = (kInputSize - new_w) / 2;
    const int32_t pad_y = (kInputSize - new_h) / 2;

    const size_t plane = static_cast<size_t>(kInputSize) * kInputSize;
    std::fill(chw, chw + 3 * plane, kPadValue / 255.0f);

    for (int32_t y = 0; y < new_h; ++y) {
        const float sy = std::clamp((static_cast<float>(y) + 0.5f) / scale - 0.5f, 0.0f,
                                    static_cast<float>(height - 1));
        const auto y0 = static_cast<int32_t>(sy);
        const int32_t y1 = std::min(y0 + 1, height - 1);
        const float fy = sy - static_cast<float>(y0);
        const uint8_t* row0 = pixels + static_cast<size_t>(y0) * stride;
        const uint8_t* row1 = pixels + static_cast<size_t>(y1) * stride;

        for (int32_t x = 0; x < new_w; ++x) {
            const float sx = std::clamp((static_cast<float>(x) + 0.5f) / scale - 0.5f, 0.0f,
                                        static_cast<float>(width - 1));
            const auto x0 = static_cast<int32_t>(sx);
            const int32_t x1 = std::min(x0 + 1, width - 1);
            const float fx = sx - static_cast<float>(x0);

            const size_t out = static_cast<size_t>(y + pad_y) * kInputSize + (x + pad_x);
            // Memoire RGBA_8888 : R, G, B, A. Le canal alpha est ignore.
            for (int c = 0; c < 3; ++c) {
                const float top = row0[x0 * 4 + c] * (1.0f - fx) + row0[x1 * 4 + c] * fx;
                const float bottom = row1[x0 * 4 + c] * (1.0f - fx) + row1[x1 * 4 + c] * fx;
                chw[c * plane + out] = (top * (1.0f - fy) + bottom * fy) / 255.0f;
            }
        }
    }
    return {scale, pad_x, pad_y};
}

/**
 * Sortie YOLO [1, 4 + nc, N] -> candidats normalises dans l'image SOURCE.
 *
 * La disposition est « par attribut » : les N centres X d'abord, puis les N
 * centres Y, etc. Lire une detection revient donc a sauter de N en N.
 */
std::vector<Detection> Decode(const float* rows, int64_t attributes, int64_t anchors,
                              const Letterbox& box, int32_t width, int32_t height) {
    std::vector<Detection> candidates;
    const auto w_img = static_cast<float>(width);
    const auto h_img = static_cast<float>(height);

    const int64_t classes = attributes - 4;
    for (int64_t i = 0; i < anchors; ++i) {
        float score = 0.0f;
        int64_t best = 0;
        for (int64_t c = 0; c < classes; ++c) {
            const float value = rows[(4 + c) * anchors + i];
            if (value > score) {
                score = value;
                best = c;
            }
        }
        if (score < kScoreThreshold) continue;
        const Kind kind =
                classes == kVolumeClassCount && best == 1 ? Kind::kVolume : Kind::kHold;

        const float cx = rows[0 * anchors + i];
        const float cy = rows[1 * anchors + i];
        const float bw = rows[2 * anchors + i];
        const float bh = rows[3 * anchors + i];

        // Retrait du letterbox, puis rognage aux bords : une boite qui deborde
        // sur la bande grise ne doit pas donner un centre hors de la photo.
        const float x0 = std::clamp((cx - bw / 2 - box.pad_x) / box.scale, 0.0f, w_img);
        const float x1 = std::clamp((cx + bw / 2 - box.pad_x) / box.scale, 0.0f, w_img);
        const float y0 = std::clamp((cy - bh / 2 - box.pad_y) / box.scale, 0.0f, h_img);
        const float y1 = std::clamp((cy + bh / 2 - box.pad_y) / box.scale, 0.0f, h_img);
        if (x1 <= x0 || y1 <= y0) continue;

        candidates.push_back({(x0 + x1) / 2 / w_img, (y0 + y1) / 2 / h_img,
                              (x1 - x0) / w_img, (y1 - y0) / h_img, score, kind});
    }
    return candidates;
}

/**
 * Recouvrement de deux boites normalisees.
 *
 * Calcule en coordonnees normalisees sans correction d'aspect, et c'est exact :
 * etirer un axe multiplie intersection et union par le meme facteur.
 */
float IntersectionOverUnion(const Detection& a, const Detection& b) {
    const float iw = std::max(0.0f, std::min(a.cx + a.w / 2, b.cx + b.w / 2) -
                                        std::max(a.cx - a.w / 2, b.cx - b.w / 2));
    const float ih = std::max(0.0f, std::min(a.cy + a.h / 2, b.cy + b.h / 2) -
                                        std::max(a.cy - a.h / 2, b.cy - b.h / 2));
    const float inter = iw * ih;
    const float uni = a.w * a.h + b.w * b.h - inter;
    return uni > 0.0f ? inter / uni : 0.0f;
}

}  // namespace

std::vector<Detection> NonMaxSuppression(std::vector<Detection> candidates,
                                         float iou_threshold,
                                         size_t max_detections) {
    // stable_sort : a score egal, l'ordre du modele departage, comme le miroir
    // Python dont le tri est lui aussi stable. Sans cela, deux ex aequo
    // pourraient etre gardes dans un ordre different de la reference.
    std::stable_sort(candidates.begin(), candidates.end(),
                     [](const Detection& a, const Detection& b) { return a.score > b.score; });

    // Glouton en O(n * k) : quelques centaines de candidats au-dessus du seuil,
    // quelques dizaines de gardes. Une structure spatiale couterait plus a
    // construire qu'elle ne ferait gagner.
    std::vector<Detection> kept;
    for (const auto& candidate : candidates) {
        const bool overlaps = std::any_of(kept.begin(), kept.end(), [&](const Detection& k) {
            return IntersectionOverUnion(candidate, k) > iou_threshold;
        });
        if (overlaps) continue;
        kept.push_back(candidate);
        if (kept.size() == max_detections) break;
    }
    return kept;
}

LabColor MeasureColor(const uint8_t* pixels, int32_t width, int32_t height, int32_t stride,
                      const Detection& box) {
    // Moitie centrale de la boite, echantillonnee sur une grille bornee : une
    // centaine de prises par photo, quelques centaines de points chacune, le
    // cout reste negligeable devant l'inference.
    constexpr int kGrid = 12;
    constexpr float kCentralFraction = 0.5f;
    std::vector<float> ls, as, bs;
    ls.reserve(kGrid * kGrid);
    as.reserve(kGrid * kGrid);
    bs.reserve(kGrid * kGrid);

    for (int gy = 0; gy < kGrid; ++gy) {
        for (int gx = 0; gx < kGrid; ++gx) {
            const float u = (static_cast<float>(gx) + 0.5f) / kGrid - 0.5f;
            const float v = (static_cast<float>(gy) + 0.5f) / kGrid - 0.5f;
            const float nx = box.cx + u * box.w * kCentralFraction;
            const float ny = box.cy + v * box.h * kCentralFraction;
            const auto px = std::clamp(static_cast<int32_t>(nx * static_cast<float>(width)), 0,
                                       width - 1);
            const auto py = std::clamp(static_cast<int32_t>(ny * static_cast<float>(height)), 0,
                                       height - 1);
            const uint8_t* p = pixels + static_cast<size_t>(py) * stride + px * 4;
            const LabColor lab = SrgbToLab(p[0], p[1], p[2]);
            ls.push_back(lab.l);
            as.push_back(lab.a);
            bs.push_back(lab.b);
        }
    }
    // Mediane par canal. Une mediane vectorielle serait plus rigoureuse, mais sur
    // une prise unie les trois medianes tombent sur le meme pixel type.
    auto median = [](std::vector<float>& values) {
        auto middle = values.begin() + static_cast<std::ptrdiff_t>(values.size() / 2);
        std::nth_element(values.begin(), middle, values.end());
        return *middle;
    };
    return {median(ls), median(as), median(bs)};
}

struct VisionProcessor::Impl {
    // Ort::Env est compte en references par ONNX Runtime : plusieurs moteurs
    // partagent en realite le meme environnement, sans etat global de notre cote.
    Ort::Env env{ORT_LOGGING_LEVEL_WARNING, "topo"};
    std::unique_ptr<Ort::Session> session;
    std::string input_name;
    std::string output_name;
};

VisionProcessor::VisionProcessor() : impl_(std::make_unique<Impl>()) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "VisionProcessor cree (sans modele)");
}

VisionProcessor::~VisionProcessor() {
    __android_log_print(ANDROID_LOG_INFO, kTag, "VisionProcessor detruit");
}

bool VisionProcessor::IsReady() const { return impl_->session != nullptr; }

void VisionProcessor::LoadModel(const uint8_t* data, size_t size) {
    Ort::SessionOptions options;
    options.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

    // XNNPACK porte les calculs sur ses propres threads. Le pool intra-operateur
    // d'ONNX Runtime est donc ramene a un seul thread, sans attente active : deux
    // pools concurrents se disputeraient les coeurs, et la documentation d'ORT
    // prescrit exactement cette configuration pour XNNPACK.
    const unsigned cores = std::max(1u, std::min(4u, std::thread::hardware_concurrency()));
    options.SetIntraOpNumThreads(1);
    options.AddConfigEntry("session.intra_op.allow_spinning", "0");
    options.AppendExecutionProvider("XNNPACK", {{"intra_op_num_threads", std::to_string(cores)}});

    std::unique_ptr<Ort::Session> session;
    try {
        session = std::make_unique<Ort::Session>(impl_->env, data, size, options);
    } catch (const Ort::Exception& error) {
        // ORT signale un modele inutilisable par une exception generique : son
        // code d'erreur distingue le modele refuse (a signaler a l'appelant) de
        // la defaillance du moteur. Constate au test : un fichier corrompu sort
        // en ORT_INVALID_PROTOBUF, pas en invalid_argument.
        switch (error.GetOrtErrorCode()) {
            case ORT_INVALID_PROTOBUF:
            case ORT_INVALID_GRAPH:
            case ORT_INVALID_ARGUMENT:
            case ORT_NO_MODEL:
            case ORT_NOT_IMPLEMENTED:
                throw std::invalid_argument(error.what());
            default:
                throw;
        }
    }

    // Le contrat de sortie est verifie au chargement, pas a la premiere image :
    // un modele exporte au mauvais format doit echouer ici, bruyamment.
    const auto shape = session->GetOutputTypeInfo(0).GetTensorTypeAndShapeInfo().GetShape();
    if (shape.size() != 3 || shape[1] < 5) {
        throw std::invalid_argument("Sortie inattendue : [1, 4 + classes, N] attendu");
    }
    const auto input = session->GetInputTypeInfo(0).GetTensorTypeAndShapeInfo().GetShape();
    if (input.size() != 4 || input[2] != kInputSize || input[3] != kInputSize) {
        throw std::invalid_argument("Entree inattendue : [1, 3, 640, 640] attendu");
    }

    Ort::AllocatorWithDefaultOptions allocator;
    impl_->input_name = session->GetInputNameAllocated(0, allocator).get();
    impl_->output_name = session->GetOutputNameAllocated(0, allocator).get();
    impl_->session = std::move(session);

    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Modele charge (%zu octets, sortie [1, %lld, %lld], XNNPACK %u threads)",
                        size, static_cast<long long>(shape[1]),
                        static_cast<long long>(shape[2]), cores);
}

std::vector<Detection> VisionProcessor::Detect(const uint8_t* pixels,
                                               int32_t width,
                                               int32_t height,
                                               int32_t stride) {
    if (!impl_->session) {
        __android_log_print(ANDROID_LOG_DEBUG, kTag,
                            "Detect(pixels=%p, %dx%d, stride=%d) — moteur non charge",
                            static_cast<const void*>(pixels), width, height, stride);
        return {};
    }

    const auto started = std::chrono::steady_clock::now();

    // Tampon d'entree propre a l'appel : Session::Run est sur en concurrence, et
    // un tampon membre partage ne le serait plus. 4,9 Mo alloues par inference,
    // negligeable devant l'inference elle-meme.
    constexpr std::array<int64_t, 4> kShape{1, 3, kInputSize, kInputSize};
    std::vector<float> chw(static_cast<size_t>(3) * kInputSize * kInputSize);
    const Letterbox box = Preprocess(pixels, width, height, stride, chw.data());
    const auto preprocessed = std::chrono::steady_clock::now();

    const auto memory = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    Ort::Value input = Ort::Value::CreateTensor<float>(memory, chw.data(), chw.size(),
                                                       kShape.data(), kShape.size());
    const char* input_names[] = {impl_->input_name.c_str()};
    const char* output_names[] = {impl_->output_name.c_str()};
    auto outputs = impl_->session->Run(Ort::RunOptions{nullptr}, input_names, &input, 1,
                                       output_names, 1);
    const auto inferred = std::chrono::steady_clock::now();

    const auto shape = outputs[0].GetTensorTypeAndShapeInfo().GetShape();
    auto detections = NonMaxSuppression(
            Decode(outputs[0].GetTensorData<float>(), shape[1], shape[2], box, width, height),
            kIouThreshold, static_cast<size_t>(kMaxDetections));
    // Mesuree APRES le NMS : seules les detections conservees la meritent, soit
    // quelques dizaines au lieu de plusieurs centaines de candidats.
    for (auto& detection : detections) {
        detection.color = MeasureColor(pixels, width, height, stride, detection);
    }
    const auto finished = std::chrono::steady_clock::now();

    using Ms = std::chrono::duration<double, std::milli>;
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Detect %dx%d : %zu prises | pretraitement %.1f ms, inference %.1f ms, "
                        "NMS et couleurs %.1f ms",
                        width, height, detections.size(), Ms(preprocessed - started).count(),
                        Ms(inferred - preprocessed).count(), Ms(finished - inferred).count());
    return detections;
}

}  // namespace topo
