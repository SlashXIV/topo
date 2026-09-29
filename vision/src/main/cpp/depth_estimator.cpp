#include "depth_estimator.h"

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

constexpr const char* kTag = "TopoDepth";

/** Valeur de rang [fraction] : l'index arrondi, sans interpolation (miroir Python). */
float Percentile(std::vector<float> values, float fraction) {
    const auto rank = static_cast<size_t>(
            std::lround(fraction * static_cast<float>(values.size() - 1)));
    auto nth = values.begin() + static_cast<std::ptrdiff_t>(rank);
    std::nth_element(values.begin(), nth, values.end());
    return *nth;
}

}  // namespace

DepthInputSize ComputeDepthInputSize(int32_t width, int32_t height) {
    if (width <= 0 || height <= 0) throw std::invalid_argument("Image vide");
    const float scale = static_cast<float>(kDepthLongSide) /
                        static_cast<float>(std::max(width, height));
    auto side = [scale](int32_t v) {
        const auto patches = static_cast<int32_t>(
                std::floor(static_cast<float>(v) * scale / kDepthPatch + 0.5f));
        return std::max(1, patches) * kDepthPatch;
    };
    return {side(width), side(height)};
}

void PreprocessDepth(const uint8_t* pixels, int32_t width, int32_t height, int32_t stride,
                     DepthInputSize size, float* chw) {
    constexpr std::array<float, 3> kMean{kDepthMeanR, kDepthMeanG, kDepthMeanB};
    constexpr std::array<float, 3> kStd{kDepthStdR, kDepthStdG, kDepthStdB};
    const float scale_x = static_cast<float>(width) / static_cast<float>(size.width);
    const float scale_y = static_cast<float>(height) / static_cast<float>(size.height);
    const size_t plane = static_cast<size_t>(size.width) * size.height;

    for (int32_t y = 0; y < size.height; ++y) {
        const float sy = std::clamp((static_cast<float>(y) + 0.5f) * scale_y - 0.5f, 0.0f,
                                    static_cast<float>(height - 1));
        const auto y0 = static_cast<int32_t>(sy);
        const int32_t y1 = std::min(y0 + 1, height - 1);
        const float fy = sy - static_cast<float>(y0);
        const uint8_t* row0 = pixels + static_cast<size_t>(y0) * stride;
        const uint8_t* row1 = pixels + static_cast<size_t>(y1) * stride;

        for (int32_t x = 0; x < size.width; ++x) {
            const float sx = std::clamp((static_cast<float>(x) + 0.5f) * scale_x - 0.5f, 0.0f,
                                        static_cast<float>(width - 1));
            const auto x0 = static_cast<int32_t>(sx);
            const int32_t x1 = std::min(x0 + 1, width - 1);
            const float fx = sx - static_cast<float>(x0);

            const size_t out = static_cast<size_t>(y) * size.width + x;
            for (int c = 0; c < 3; ++c) {
                const float top = row0[x0 * 4 + c] * (1.0f - fx) + row0[x1 * 4 + c] * fx;
                const float bottom = row1[x0 * 4 + c] * (1.0f - fx) + row1[x1 * 4 + c] * fx;
                const float value = (top * (1.0f - fy) + bottom * fy) / 255.0f;
                chw[c * plane + out] = (value - kMean[c]) / kStd[c];
            }
        }
    }
}

void NormalizeDepth(std::vector<float>& values, int32_t width, int32_t height) {
    if (values.empty()) return;
    const float low = Percentile(values, kDepthLowPercentile);
    const float high = Percentile(values, kDepthHighPercentile);
    const float range = std::max(high - low, 1e-6f);
    for (float& v : values) v = std::clamp((v - low) / range, 0.0f, 1.0f);

    const std::vector<float> source = values;
    for (int32_t y = 0; y < height; ++y) {
        for (int32_t x = 0; x < width; ++x) {
            float sum = 0.0f;
            for (int32_t dy = -1; dy <= 1; ++dy) {
                const int32_t yy = std::clamp(y + dy, 0, height - 1);
                for (int32_t dx = -1; dx <= 1; ++dx) {
                    const int32_t xx = std::clamp(x + dx, 0, width - 1);
                    sum += source[static_cast<size_t>(yy) * width + xx];
                }
            }
            values[static_cast<size_t>(y) * width + x] = sum / 9.0f;
        }
    }
}

struct DepthEstimator::Impl {
    Ort::Env env{ORT_LOGGING_LEVEL_WARNING, "topo-depth"};
    std::unique_ptr<Ort::Session> session;
    std::string input_name;
    std::string output_name;
};

DepthEstimator::DepthEstimator() : impl_(std::make_unique<Impl>()) {}

DepthEstimator::~DepthEstimator() = default;

bool DepthEstimator::IsReady() const { return impl_->session != nullptr; }

void DepthEstimator::LoadModel(const uint8_t* data, size_t size) {
    Ort::SessionOptions options;
    options.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);
    // Quatre threads, sans attente active : la configuration mesuree sur le
    // Xiaomi. Au-dela, les coeurs lents du big.LITTLE ralentissent l'ensemble.
    const unsigned cores = std::max(1u, std::min(4u, std::thread::hardware_concurrency()));
    options.SetIntraOpNumThreads(static_cast<int>(cores));
    options.AddConfigEntry("session.intra_op.allow_spinning", "0");

    std::unique_ptr<Ort::Session> session;
    try {
        session = std::make_unique<Ort::Session>(impl_->env, data, size, options);
    } catch (const Ort::Exception& error) {
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

    const auto input = session->GetInputTypeInfo(0).GetTensorTypeAndShapeInfo().GetShape();
    if (input.size() != 4 || input[1] != 3) {
        throw std::invalid_argument("Entree inattendue : [1, 3, H, W] attendu");
    }
    const auto output = session->GetOutputTypeInfo(0).GetTensorTypeAndShapeInfo().GetShape();
    if (output.size() != 3) {
        throw std::invalid_argument("Sortie inattendue : [1, H, W] attendu");
    }

    Ort::AllocatorWithDefaultOptions allocator;
    impl_->input_name = session->GetInputNameAllocated(0, allocator).get();
    impl_->output_name = session->GetOutputNameAllocated(0, allocator).get();
    impl_->session = std::move(session);
    __android_log_print(ANDROID_LOG_INFO, kTag, "Modele de profondeur charge (%zu octets, %u threads)",
                        size, cores);
}

DepthMap DepthEstimator::Estimate(const uint8_t* pixels, int32_t width, int32_t height,
                                  int32_t stride) {
    if (!impl_->session) return {};

    const auto started = std::chrono::steady_clock::now();
    const DepthInputSize size = ComputeDepthInputSize(width, height);
    const std::array<int64_t, 4> shape{1, 3, size.height, size.width};
    std::vector<float> chw(static_cast<size_t>(3) * size.width * size.height);
    PreprocessDepth(pixels, width, height, stride, size, chw.data());

    const auto memory = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    Ort::Value input = Ort::Value::CreateTensor<float>(memory, chw.data(), chw.size(),
                                                       shape.data(), shape.size());
    const char* input_names[] = {impl_->input_name.c_str()};
    const char* output_names[] = {impl_->output_name.c_str()};
    auto outputs = impl_->session->Run(Ort::RunOptions{nullptr}, input_names, &input, 1,
                                       output_names, 1);
    const auto inferred = std::chrono::steady_clock::now();

    // Le modele rend 14 * floor(cote / 14) : egal a l'entree, deja multiple de 14.
    const auto out_shape = outputs[0].GetTensorTypeAndShapeInfo().GetShape();
    DepthMap map;
    map.height = static_cast<int32_t>(out_shape[1]);
    map.width = static_cast<int32_t>(out_shape[2]);
    const float* raw = outputs[0].GetTensorData<float>();
    map.values.assign(raw, raw + static_cast<size_t>(map.width) * map.height);
    NormalizeDepth(map.values, map.width, map.height);
    const auto finished = std::chrono::steady_clock::now();

    using Ms = std::chrono::duration<double, std::milli>;
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Profondeur %dx%d -> %dx%d : inference %.0f ms, normalisation %.1f ms",
                        width, height, map.width, map.height, Ms(inferred - started).count(),
                        Ms(finished - inferred).count());
    return map;
}

}  // namespace topo
