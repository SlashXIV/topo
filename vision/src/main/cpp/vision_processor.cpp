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

    for (int64_t i = 0; i < anchors; ++i) {
        float score = 0.0f;
        for (int64_t c = 4; c < attributes; ++c) {
            score = std::max(score, rows[c * anchors + i]);
        }
        if (score < kScoreThreshold) continue;

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
                              (x1 - x0) / w_img, (y1 - y0) / h_img, score});
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
    const auto finished = std::chrono::steady_clock::now();

    using Ms = std::chrono::duration<double, std::milli>;
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Detect %dx%d : %zu prises | pretraitement %.1f ms, inference %.1f ms, "
                        "NMS %.1f ms",
                        width, height, detections.size(), Ms(preprocessed - started).count(),
                        Ms(inferred - preprocessed).count(), Ms(finished - inferred).count());
    return detections;
}

}  // namespace topo
