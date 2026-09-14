package com.betaclimb.vision

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
 *  - [nativeDetectFromDirectBuffer] exige un buffer DIRECT
 *    (`ByteBuffer.allocateDirect`, ou un plan d'`ImageProxy` CameraX). Un
 *    `ByteBuffer.allocate()` ordinaire fait renvoyer `nullptr` a
 *    `GetDirectBufferAddress` cote natif, ce qui est converti en
 *    [IllegalArgumentException].
 *
 *  - Le `handle` est un pointeur natif. Il appartient a l'appelant, qui doit
 *    appeler [nativeDestroy] exactement une fois. Utiliser un handle libere est
 *    un use-after-free.
 *
 *  - Les tableaux retournes sont APLATIS : `[cx, cy, w, h, score]` par prise,
 *    donc `taille / 5` detections, en coordonnees normalisees `[0, 1]`. Une
 *    seule traversee JNI par inference (cf. l'en-tete de `jni_bridge.cpp`).
 */
internal object NativeBridge {

    /** Taille d'un enregistrement de detection dans le tableau aplati. */
    const val DETECTION_STRIDE: Int = 5

    init {
        System.loadLibrary("betaclimb_vision")
    }

    /** @return un handle natif opaque, a liberer via [nativeDestroy]. */
    external fun nativeCreate(): Long

    /** Idempotent : un handle nul ou deja libere est ignore cote natif. */
    external fun nativeDestroy(handle: Long)

    /**
     * Chemin CameraX : le buffer doit etre direct, aucune copie n'est faite.
     *
     * @param rowStride octets par ligne (different de `width * bpp` si l'image
     *        est paddee par le materiel - c'est le cas courant sur les plans YUV).
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
}
