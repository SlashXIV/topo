package com.topo.vision

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * Declarations des fonctions natives du moteur de vision.
 *
 * Cet objet est un DETAIL D'IMPLEMENTATION du module `:vision` : il est
 * `internal`, et rien en dehors de ce module ne doit le voir. Le reste de
 * l'application parle a [VisionEngine], qui implemente une interface du domaine.
 *
 * INVARIANTS - leur violation est un crash natif, pas une exception Kotlin :
 *
 *  - Les methodes ci-dessous sont liees par `RegisterNatives` dans `JNI_OnLoad`
 *    (voir `jni_bridge.cpp`). Leur NOM et leur SIGNATURE font partie du contrat :
 *    renommer une methode ou changer un type ici sans mettre a jour le tableau
 *    `kMethods` cote C++ fait echouer `System.loadLibrary` au chargement - ce qui
 *    est exactement le comportement voulu (echec immediat et bruyant, pas un
 *    `UnsatisfiedLinkError` au premier appel, en production).
 *
 *  - [nativeDetectFromDirectBuffer] et [nativeLoadModel] exigent un buffer
 *    DIRECT (`ByteBuffer.allocateDirect`, un `MappedByteBuffer`, ou le plan d'un
 *    `ImageProxy` CameraX en `OUTPUT_IMAGE_FORMAT_RGBA_8888`). Un
 *    `ByteBuffer.allocate()` ordinaire fait renvoyer `nullptr` a
 *    `GetDirectBufferAddress` cote natif, ce qui est converti en
 *    [IllegalArgumentException].
 *
 *  - Le `handle` est un pointeur natif. Il appartient a l'appelant, qui doit
 *    appeler [nativeDestroy] exactement une fois. Utiliser un handle libere est
 *    un use-after-free.
 *
 *  - Les erreurs d'ONNX Runtime arrivent en [IllegalArgumentException] (modele
 *    ou entree refuses) ou [IllegalStateException] (defaillance du moteur) :
 *    aucune exception C++ ne traverse la frontiere JNI.
 *
 *  - Les tableaux retournes sont APLATIS : `[cx, cy, w, h, score, nature, L, a, b]`
 *    par prise, donc `taille / DETECTION_STRIDE` detections, en coordonnees
 *    normalisees `[0, 1]` ; nature 0 = prise, 1 = volume ; couleur CIELAB. Une
 *    seule traversee JNI par inference (cf. l'en-tete de `jni_bridge.cpp`).
 */
internal object NativeBridge {

    /** Taille d'un enregistrement de detection dans le tableau aplati. */
    const val DETECTION_STRIDE: Int = 9

    init {
        System.loadLibrary("topo_vision")
    }

    /** @return un handle natif opaque, a liberer via [nativeDestroy]. */
    external fun nativeCreate(): Long

    /** Idempotent : un handle nul ou deja libere est ignore cote natif. */
    external fun nativeDestroy(handle: Long)

    /**
     * Charge un modele ONNX et ouvre la session. ONNX Runtime copie le modele :
     * [model] peut etre libere des le retour.
     */
    external fun nativeLoadModel(handle: Long, model: ByteBuffer)

    external fun nativeIsReady(handle: Long): Boolean

    /**
     * Suppression des non-maxima seule, sur des candidats aplatis. Reservee aux
     * tests : c'est le seul moyen de lui soumettre un cas limite choisi.
     */
    external fun nativeNonMaxSuppression(candidates: FloatArray, iouThreshold: Float): FloatArray

    /**
     * Couleur CIELAB `[L, a, b]` d'une boite normalisee sur un bitmap ARGB_8888.
     * Reservee aux tests, comme [nativeNonMaxSuppression].
     */
    external fun nativeMeasureColor(bitmap: Bitmap, cx: Float, cy: Float, w: Float, h: Float): FloatArray

    /**
     * Chemin CameraX : plan RGBA_8888 direct, aucune copie n'est faite.
     *
     * @param rowStride octets par ligne (superieur a `width * 4` si l'image est
     *        paddee par le materiel, ce qui est courant sur les plans CameraX).
     */
    external fun nativeDetectFromDirectBuffer(
        handle: Long,
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
    ): FloatArray

    /**
     * Chemin Bitmap : verrouillage direct des pixels via `AndroidBitmap_lockPixels`.
     * Le bitmap doit etre en [Bitmap.Config.ARGB_8888].
     */
    external fun nativeDetectFromBitmap(handle: Long, bitmap: Bitmap): FloatArray

    // --- Profondeur : moteur distinct, handle distinct (a ne pas croiser) ---

    /** @return un handle d'estimateur de profondeur, a liberer via [nativeDepthDestroy]. */
    external fun nativeDepthCreate(): Long

    external fun nativeDepthDestroy(handle: Long)

    external fun nativeDepthLoadModel(handle: Long, model: ByteBuffer)

    external fun nativeDepthIsReady(handle: Long): Boolean

    /**
     * Relief d'un bitmap ARGB_8888 : `[largeur, hauteur, valeurs ligne par ligne...]`,
     * valeurs dans `[0, 1]`, 1 = le plus proche. `[0, 0]` sans modele charge.
     */
    external fun nativeEstimateDepth(handle: Long, bitmap: Bitmap): FloatArray

    /** Tenseur d'entree du modele de relief, `[largeur, hauteur, CHW...]`. Reserve aux tests. */
    external fun nativeDepthPreprocess(bitmap: Bitmap): FloatArray
}
