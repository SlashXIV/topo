package com.betaclimb.core.domain.repository

import com.betaclimb.core.domain.model.DetectedHold

/**
 * Detection automatique des prises sur une image de bloc.
 *
 * Cette interface est la frontiere qui isole tout le reste de l'application de
 * l'existence meme du NDK, de la JNI, d'ONNX Runtime et de XNNPACK. Une couche
 * au-dessus ne sait pas si les prises viennent d'un reseau de neurones ou d'une
 * implementation vide.
 *
 * Consequence directe : l'absence de modele entraine n'est pas bloquante. Une
 * implementation qui retourne une liste vide laisse l'application parfaitement
 * fonctionnelle, l'utilisateur placant ses prises a la main.
 *
 * [AutoCloseable] parce que l'implementation reelle detiendra une session ONNX
 * Runtime allouee cote C++ : sa liberation est deterministe, jamais confiee au
 * ramasse-miettes.
 */
interface HoldDetector : AutoCloseable {

    /**
     * @param image image source, dans une representation dependante de la plateforme.
     * @return les prises detectees, deja filtrees par suppression des non-maxima.
     *         Liste vide si aucun modele n'est charge.
     */
    suspend fun detect(image: ImageSource): List<DetectedHold>

    /** true si un modele est charge et pret a inferer. */
    val isReady: Boolean

    /**
     * Source d'image opaque pour le domaine : celui-ci ne connait ni Bitmap ni
     * ByteBuffer, ce qui le garde compilable hors Android (tests unitaires JVM).
     */
    interface ImageSource
}
