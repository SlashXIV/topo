package com.topo.core.domain.model

/**
 * Une barre de repartition : un niveau, et le nombre de blocs a ce niveau.
 *
 * [label] est ce qui s'affiche, [rank] ce qui ordonne. Les deux sont necessaires :
 * trier sur l'etiquette rangerait « V10 » avant « V9 ».
 */
data class DifficultyBucket(
    val label: String,
    val rank: Int,
    val argb: Int?,
    val total: Int,
    val sent: Int,
)

/**
 * Statistiques de progression.
 *
 * POURQUOI DEUX REPARTITIONS SEPAREES
 * -----------------------------------
 * Un rang de couleur (0 a 4 sur un circuit de salle) et un rang de cotation
 * (0 a 23 sur l'echelle francaise) ne sont PAS comparables : ils ne mesurent
 * pas sur la meme regle. Les melanger produirait un « niveau moyen » qui ne
 * veut rien dire. Chaque systeme garde donc sa repartition et son maximum.
 */
data class ClimbingStatistics(
    val blocCount: Int,
    val sentCount: Int,
    val flashCount: Int,
    val totalAttempts: Int,
    /** Meilleure cotation REUSSIE, pas simplement tentee. */
    val bestGrade: String?,
    /** Meilleure couleur reussie. */
    val bestColor: CircuitColor?,
    val gradeBuckets: List<DifficultyBucket>,
    val colorBuckets: List<DifficultyBucket>,
) {
    val successRate: Float get() = if (blocCount == 0) 0f else sentCount.toFloat() / blocCount

    /**
     * Part de flashs PARMI LES REUSSITES, et non parmi tous les blocs.
     * Rapporte au total, le taux chuterait a chaque projet ajoute et
     * penaliserait le fait d'essayer des blocs durs.
     */
    val flashRate: Float get() = if (sentCount == 0) 0f else flashCount.toFloat() / sentCount

    /** Essais moyens par reussite. Nul tant que rien n'est reussi. */
    val attemptsPerSend: Float? get() = if (sentCount == 0) null else totalAttempts.toFloat() / sentCount

    val isEmpty: Boolean get() = blocCount == 0

    companion object {
        val Empty = ClimbingStatistics(0, 0, 0, 0, null, null, emptyList(), emptyList())
    }
}
