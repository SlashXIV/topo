package com.betaclimb.core.domain.model

/**
 * Un bloc photographie : une voie, au sein d'une session.
 *
 * [imagePath] est un chemin RELATIF au repertoire d'images de l'application, jamais
 * un chemin absolu ni un `content://`. Deux raisons :
 *  - le repertoire de donnees d'une application change de chemin absolu lors d'une
 *    restauration ou d'un transfert d'appareil ;
 *  - une URI de fournisseur de contenu externe peut voir sa permission revoquee,
 *    ce qui casserait l'acces hors ligne. L'image est donc COPIEE dans le stockage
 *    interne prive a l'import.
 *
 * [imageWidth] / [imageHeight] sont conserves pour denormaliser les prises sans
 * avoir a decoder le bitmap, y compris dans un ecran de liste.
 */
data class Bloc(
    val id: String,
    val sessionId: String,
    val imagePath: String,
    val imageWidth: Int,
    val imageHeight: Int,
    val grade: String?,
    val isCompleted: Boolean,
    val createdAt: Long,
) {
    init {
        require(imagePath.isNotBlank()) { "Chemin d'image vide" }
        require(imageWidth > 0 && imageHeight > 0) {
            "Dimensions d'image invalides : ${imageWidth}x$imageHeight"
        }
    }

    val aspectRatio: Float get() = imageWidth.toFloat() / imageHeight.toFloat()
}

/**
 * Un bloc et l'integralite de son annotation.
 *
 * Agregat charge en une seule requete reactive : l'ecran d'annotation a besoin des
 * trois ensembles simultanement, et les emettre separement ferait clignoter l'UI
 * (prises affichees avant les mouvements qui les relient).
 */
data class BlocDetail(
    val bloc: Bloc,
    val holds: List<Hold>,
    val movements: List<Movement>,
) {
    /** Index par identifiant : le rendu du trace resout des prises par id a chaque trame. */
    val holdsById: Map<String, Hold> by lazy { holds.associateBy(Hold::id) }
}
