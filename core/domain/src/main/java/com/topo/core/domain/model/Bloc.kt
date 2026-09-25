package com.topo.core.domain.model

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
    /** Etiquette de cotation, tiree de [gradeScale]. */
    val grade: String?,
    val gradeScale: GradeScale?,
    /** Couleur du circuit. Independante de la cotation : un bloc peut porter les deux. */
    val colorId: String?,
    val status: BlocStatus,
    val attempts: Int,
    /** Instant de la reussite, nul tant que le bloc n'est pas reussi. */
    val sentAt: Long?,
    /** Ressenti libre : sensations, cle du passage, ce qui a debloque. */
    val feeling: String?,
    val createdAt: Long,
) {
    init {
        require(imagePath.isNotBlank()) { "Chemin d'image vide" }
        require(imageWidth > 0 && imageHeight > 0) {
            "Dimensions d'image invalides : ${imageWidth}x$imageHeight"
        }
        require(attempts >= 0) { "Nombre d'essais negatif : $attempts" }
    }

    val aspectRatio: Float get() = imageWidth.toFloat() / imageHeight.toFloat()

    /**
     * Rang de difficulte de la cotation chiffree, s'il y en a une.
     *
     * C'est cette valeur, et l'equivalent cote couleur ([CircuitColor.rank]),
     * que les statistiques de progression consommeront : deux systemes
     * d'affichage, une seule notion comparable.
     */
    val gradeRank: Int? get() = gradeScale?.let { scale -> grade?.let(scale::rankOf) }

    /** Conserve pour les usages d'affichage qui ne distinguent que reussi / non reussi. */
    val isCompleted: Boolean get() = status.isSent
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
