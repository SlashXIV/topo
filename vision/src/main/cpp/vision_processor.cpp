#include "vision_processor.h"

#include <android/log.h>

namespace topo {
namespace {
constexpr const char* kTag = "TopoVision";
}  // namespace

/**
 * Sprint 0 : coquille vide. Portera la session ONNX Runtime + l'allocateur +
 * le provider d'execution XNNPACK au sprint Vision.
 */
struct VisionProcessor::Impl {
    bool ready = false;
    // Ort::Env env;
    // Ort::Session session;
    // Ort::MemoryInfo memory_info;
};

VisionProcessor::VisionProcessor() : impl_(std::make_unique<Impl>()) {
    __android_log_print(ANDROID_LOG_INFO, kTag, "VisionProcessor cree (sans modele)");
}

VisionProcessor::~VisionProcessor() {
    __android_log_print(ANDROID_LOG_INFO, kTag, "VisionProcessor detruit");
}

bool VisionProcessor::IsReady() const { return impl_->ready; }

std::vector<Detection> VisionProcessor::Detect(const uint8_t* pixels,
                                               int32_t width,
                                               int32_t height,
                                               int32_t stride,
                                               PixelFormat format) {
    // Sprint 0 : on prouve seulement que le pointeur arrive intact cote natif.
    // Le pretraitement (resize 640x640, RGBA->RGB, normalisation, NCHW),
    // l'inference et le NMS arrivent au sprint Vision.
    __android_log_print(ANDROID_LOG_DEBUG, kTag,
                        "Detect(pixels=%p, %dx%d, stride=%d, format=%d) — moteur non charge",
                        static_cast<const void*>(pixels), width, height, stride,
                        static_cast<int>(format));
    return {};
}

}  // namespace topo
