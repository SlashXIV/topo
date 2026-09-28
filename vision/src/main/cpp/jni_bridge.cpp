// ============================================================================
//  Topo - pont JNI Kotlin <-> C++
//
//  CONTRAT DE TRAVERSEE (lire avant toute modification)
//  ---------------------------------------------------
//  1. UNE SEULE traversee JNI par inference. Les resultats reviennent dans un
//     unique tableau de float aplati :
//         [cx0, cy0, w0, h0, score0, cx1, cy1, w1, h1, score1, ...]
//     longueur = 5 * nombre de detections (auto-descriptive, pas d'en-tete).
//     Coordonnees normalisees [0,1]. Retourner une List<Detection> d'objets Java
//     couterait un NewObject + 5 SetFloatField par prise, soit ~900 appels JNI
//     pour 150 prises : c'est precisement ce que cette architecture refuse.
//
//  2. ZERO COPIE - deux chemins, choisis selon la source de l'image :
//     a) AndroidBitmap_lockPixels : chemin des Bitmap (photo capturee, galerie).
//        Rend un pointeur direct sur les pixels du heap graphique. C'est le seul
//        chemin reellement sans copie pour un Bitmap : passer par un
//        ByteBuffer.allocateDirect obligerait a un bitmap.copyPixelsToBuffer()
//        cote Kotlin, soit ~1,6 Mo de memcpy pour un 640x640 ARGB_8888.
//     b) GetDirectBufferAddress : chemin des ImageProxy CameraX configures en
//        OUTPUT_IMAGE_FORMAT_RGBA_8888, dont l'unique plan EST deja un
//        DirectByteBuffer - la, aucune copie n'a lieu. Le meme chemin sert au
//        modele, lu en MappedByteBuffer depuis les assets.
//     Aucun GetByteArrayElements nulle part : ce serait la duplication que toute
//     l'architecture cherche a eviter.
//
//  3. Le handle natif est un jlong opaque. Aucun etat global, aucun singleton :
//     la duree de vie est pilotee depuis Kotlin (VisionEngine implemente
//     Closeable) et plusieurs moteurs peuvent coexister.
//
//  4. Enregistrement par RegisterNatives dans JNI_OnLoad plutot que par symboles
//     exportes Java_com_topo_... : les signatures sont validees au
//     chargement (et non au premier appel), la .so n'exporte aucun symbole de
//     fonction, et un renommage cote Kotlin echoue bruyamment tout de suite.
//
//  5. AUCUNE exception C++ ne traverse la frontiere JNI : c'est un comportement
//     indefini, en pratique un abort() du processus. ONNX Runtime signale ses
//     erreurs par exception, chaque point d'entree les convertit donc en
//     exception Java.
// ============================================================================

#include <jni.h>

#include <android/bitmap.h>
#include <android/log.h>

#include <cstdint>
#include <exception>
#include <memory>
#include <stdexcept>
#include <vector>

#include "vision_processor.h"

namespace {

constexpr const char* kTag = "TopoJNI";
constexpr const char* kBridgeClass = "com/topo/vision/NativeBridge";

void ThrowJava(JNIEnv* env, const char* class_name, const char* message) {
    jclass clazz = env->FindClass(class_name);
    if (clazz != nullptr) {
        env->ThrowNew(clazz, message);
        env->DeleteLocalRef(clazz);
    }
}

/** Garde RAII : AndroidBitmap_unlockPixels survit a toute sortie anticipee. */
class ScopedBitmapLock {
public:
    ScopedBitmapLock(JNIEnv* env, jobject bitmap) : env_(env), bitmap_(bitmap) {
        status_ = AndroidBitmap_lockPixels(env_, bitmap_, &pixels_);
    }

    ~ScopedBitmapLock() {
        if (status_ == ANDROID_BITMAP_RESULT_SUCCESS) {
            AndroidBitmap_unlockPixels(env_, bitmap_);
        }
    }

    ScopedBitmapLock(const ScopedBitmapLock&) = delete;
    ScopedBitmapLock& operator=(const ScopedBitmapLock&) = delete;

    bool ok() const {
        return status_ == ANDROID_BITMAP_RESULT_SUCCESS && pixels_ != nullptr;
    }
    const uint8_t* pixels() const { return static_cast<const uint8_t*>(pixels_); }

private:
    JNIEnv* env_;
    jobject bitmap_;
    void* pixels_ = nullptr;
    int status_ = ANDROID_BITMAP_RESULT_BAD_PARAMETER;
};

/**
 * Execute [body] et convertit toute exception C++ en exception Java (point 5).
 * invalid_argument designe un modele ou une entree refuses, le reste une
 * defaillance du moteur.
 */
template <typename Body>
auto Guarded(JNIEnv* env, Body body) -> decltype(body()) {
    try {
        return body();
    } catch (const std::invalid_argument& error) {
        ThrowJava(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception& error) {
        ThrowJava(env, "java/lang/IllegalStateException", error.what());
    }
    return decltype(body()){};
}

/** Adresse et capacite d'un buffer direct, ou nullptr avec l'exception Java levee. */
void* DirectAddress(JNIEnv* env, jobject buffer, jlong* capacity) {
    if (buffer == nullptr) {
        ThrowJava(env, "java/lang/NullPointerException", "buffer == null");
        return nullptr;
    }
    void* address = env->GetDirectBufferAddress(buffer);
    *capacity = env->GetDirectBufferCapacity(buffer);
    if (address == nullptr || *capacity < 0) {
        ThrowJava(env, "java/lang/IllegalArgumentException",
                  "ByteBuffer non direct : utiliser ByteBuffer.allocateDirect(), un "
                  "MappedByteBuffer ou un plan d'ImageProxy CameraX");
        return nullptr;
    }
    return address;
}

topo::VisionProcessor* FromHandle(JNIEnv* env, jlong handle) {
    if (handle == 0L) {
        ThrowJava(env, "java/lang/IllegalStateException",
                  "Handle natif nul : moteur deja libere ou jamais cree");
        return nullptr;
    }
    return reinterpret_cast<topo::VisionProcessor*>(handle);
}

/** Aplatit les detections en un unique jfloatArray (cf. contrat, point 1). */
jfloatArray ToFlatArray(JNIEnv* env, const std::vector<topo::Detection>& detections) {
    if (env->ExceptionCheck()) {
        return nullptr;  // Guarded a deja leve l'exception Java.
    }
    const jsize length = static_cast<jsize>(detections.size() * 5);
    jfloatArray result = env->NewFloatArray(length);
    if (result == nullptr) {
        return nullptr;  // OutOfMemoryError deja levee par la JVM.
    }
    if (length == 0) {
        return result;
    }

    std::vector<jfloat> flat;
    flat.reserve(static_cast<size_t>(length));
    for (const auto& d : detections) {
        flat.push_back(d.cx);
        flat.push_back(d.cy);
        flat.push_back(d.w);
        flat.push_back(d.h);
        flat.push_back(d.score);
    }
    env->SetFloatArrayRegion(result, 0, length, flat.data());
    return result;
}

// --------------------------------------------------------------------------
//  Implementations natives
// --------------------------------------------------------------------------

jlong NativeCreate(JNIEnv* env, jobject /* thiz */) {
    (void)env;
    auto processor = std::make_unique<topo::VisionProcessor>();
    __android_log_print(ANDROID_LOG_INFO, kTag, "Moteur natif cree");
    return reinterpret_cast<jlong>(processor.release());
}

void NativeDestroy(JNIEnv* env, jobject /* thiz */, jlong handle) {
    (void)env;
    if (handle == 0L) {
        return;  // Idempotent : un double close() ne doit pas crasher.
    }
    delete reinterpret_cast<topo::VisionProcessor*>(handle);
    __android_log_print(ANDROID_LOG_INFO, kTag, "Moteur natif detruit");
}

/**
 * Chargement du modele depuis un buffer direct : le MappedByteBuffer d'un asset
 * non compresse n'est jamais recopie dans le heap Java.
 */
void NativeLoadModel(JNIEnv* env, jobject /* thiz */, jlong handle, jobject model) {
    auto* processor = FromHandle(env, handle);
    if (processor == nullptr) {
        return;
    }
    jlong capacity = 0;
    void* address = DirectAddress(env, model, &capacity);
    if (address == nullptr) {
        return;
    }
    Guarded(env, [&] {
        processor->LoadModel(static_cast<const uint8_t*>(address),
                             static_cast<size_t>(capacity));
        return 0;
    });
}

jboolean NativeIsReady(JNIEnv* env, jobject /* thiz */, jlong handle) {
    auto* processor = FromHandle(env, handle);
    return processor != nullptr && processor->IsReady() ? JNI_TRUE : JNI_FALSE;
}

/**
 * Point d'entree de TEST de la suppression des non-maxima.
 *
 * Le NMS n'est autrement observable qu'a travers un modele reel, dont les
 * sorties ne permettent pas de construire un cas limite choisi.
 */
jfloatArray NativeNonMaxSuppression(JNIEnv* env, jobject /* thiz */, jfloatArray flat,
                                    jfloat iou_threshold) {
    const jsize length = env->GetArrayLength(flat);
    if (length % 5 != 0) {
        ThrowJava(env, "java/lang/IllegalArgumentException", "taille non multiple de 5");
        return nullptr;
    }
    std::vector<jfloat> values(static_cast<size_t>(length));
    env->GetFloatArrayRegion(flat, 0, length, values.data());

    std::vector<topo::Detection> candidates;
    for (size_t i = 0; i < values.size(); i += 5) {
        candidates.push_back({values[i], values[i + 1], values[i + 2], values[i + 3],
                              values[i + 4]});
    }
    return ToFlatArray(env, topo::NonMaxSuppression(std::move(candidates), iou_threshold,
                                                    static_cast<size_t>(topo::kMaxDetections)));
}

/**
 * Chemin DirectByteBuffer (plan RGBA_8888 d'un ImageProxy CameraX).
 * GetDirectBufferAddress renvoie nullptr si le buffer n'est PAS direct : c'est
 * le piege classique, donc il est transforme ici en exception explicite plutot
 * qu'en dereferencement de pointeur nul.
 */
jfloatArray NativeDetectFromDirectBuffer(JNIEnv* env,
                                         jobject /* thiz */,
                                         jlong handle,
                                         jobject buffer,
                                         jint width,
                                         jint height,
                                         jint row_stride) {
    auto* processor = FromHandle(env, handle);
    if (processor == nullptr) {
        return nullptr;
    }
    jlong capacity = 0;
    void* address = DirectAddress(env, buffer, &capacity);
    if (address == nullptr) {
        return nullptr;
    }
    if (width <= 0 || height <= 0 || row_stride < width * 4) {
        ThrowJava(env, "java/lang/IllegalArgumentException",
                  "Dimensions invalides : rowStride doit couvrir width * 4 octets (RGBA_8888)");
        return nullptr;
    }

    const jlong required = static_cast<jlong>(row_stride) * static_cast<jlong>(height);
    if (capacity < required) {
        ThrowJava(env, "java/lang/IllegalArgumentException",
                  "Capacite du buffer inferieure a rowStride * height");
        return nullptr;
    }

    __android_log_print(ANDROID_LOG_DEBUG, kTag,
                        "DirectBuffer @%p capacite=%lld (%dx%d, stride=%d) - zero copie",
                        address, static_cast<long long>(capacity), width, height, row_stride);

    const auto detections = Guarded(env, [&] {
        return processor->Detect(static_cast<const uint8_t*>(address), width, height,
                                 row_stride);
    });
    return ToFlatArray(env, detections);
}

/** Chemin Bitmap : AndroidBitmap_lockPixels, sans aucune copie intermediaire. */
jfloatArray NativeDetectFromBitmap(JNIEnv* env,
                                   jobject /* thiz */,
                                   jlong handle,
                                   jobject bitmap) {
    auto* processor = FromHandle(env, handle);
    if (processor == nullptr) {
        return nullptr;
    }
    if (bitmap == nullptr) {
        ThrowJava(env, "java/lang/NullPointerException", "bitmap == null");
        return nullptr;
    }

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        ThrowJava(env, "java/lang/IllegalArgumentException",
                  "AndroidBitmap_getInfo a echoue");
        return nullptr;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        ThrowJava(env, "java/lang/IllegalArgumentException",
                  "Format de Bitmap non supporte : ARGB_8888 attendu");
        return nullptr;
    }

    ScopedBitmapLock lock(env, bitmap);
    if (!lock.ok()) {
        ThrowJava(env, "java/lang/IllegalStateException",
                  "AndroidBitmap_lockPixels a echoue");
        return nullptr;
    }

    __android_log_print(ANDROID_LOG_DEBUG, kTag,
                        "Bitmap verrouille @%p (%ux%u, stride=%u) - zero copie",
                        static_cast<const void*>(lock.pixels()), info.width, info.height,
                        info.stride);

    const auto detections = Guarded(env, [&] {
        return processor->Detect(lock.pixels(), static_cast<int32_t>(info.width),
                                 static_cast<int32_t>(info.height),
                                 static_cast<int32_t>(info.stride));
    });
    return ToFlatArray(env, detections);
}

const JNINativeMethod kMethods[] = {
        {"nativeCreate", "()J", reinterpret_cast<void*>(NativeCreate)},
        {"nativeDestroy", "(J)V", reinterpret_cast<void*>(NativeDestroy)},
        {"nativeLoadModel", "(JLjava/nio/ByteBuffer;)V", reinterpret_cast<void*>(NativeLoadModel)},
        {"nativeIsReady", "(J)Z", reinterpret_cast<void*>(NativeIsReady)},
        {"nativeNonMaxSuppression", "([FF)[F",
         reinterpret_cast<void*>(NativeNonMaxSuppression)},
        {"nativeDetectFromDirectBuffer", "(JLjava/nio/ByteBuffer;III)[F",
         reinterpret_cast<void*>(NativeDetectFromDirectBuffer)},
        {"nativeDetectFromBitmap", "(JLandroid/graphics/Bitmap;)[F",
         reinterpret_cast<void*>(NativeDetectFromBitmap)},
};

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /* reserved */) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "GetEnv a echoue");
        return JNI_ERR;
    }

    jclass bridge = env->FindClass(kBridgeClass);
    if (bridge == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "Classe introuvable : %s", kBridgeClass);
        return JNI_ERR;
    }

    const jint count = static_cast<jint>(sizeof(kMethods) / sizeof(kMethods[0]));
    if (env->RegisterNatives(bridge, kMethods, count) != JNI_OK) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "RegisterNatives a echoue");
        env->DeleteLocalRef(bridge);
        return JNI_ERR;
    }
    env->DeleteLocalRef(bridge);

    __android_log_print(ANDROID_LOG_INFO, kTag, "%d methodes natives enregistrees", count);
    return JNI_VERSION_1_6;
}
