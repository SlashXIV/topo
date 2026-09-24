package com.topo.core.domain.model

/**
 * Prise issue d'une detection, pas encore persistee : elle n'a donc pas d'identite.
 *
 * C'est la sortie du moteur de vision et l'entree du placement manuel. Elle devient
 * un [Hold] au moment de l'enregistrement, quand la couche data lui attribue un id.
 */
data class DetectedHold(
    val box: BoundingBox,
    val confidence: Float,
    val origin: HoldOrigin,
) {
    init {
        require(confidence in 0f..1f) { "Score de confiance hors bornes : $confidence" }
    }

    companion object {
        /**
         * Prise posee au doigt par l'utilisateur : confiance maximale par
         * definition, puisqu'un humain l'a designee.
         */
        fun manual(box: BoundingBox): DetectedHold =
            DetectedHold(box = box, confidence = 1f, origin = HoldOrigin.MANUAL)
    }
}
