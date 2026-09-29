package com.topo.core.domain.model

/**
 * Echelle de cotation chiffree.
 *
 * POURQUOI UNE LISTE FIGEE PLUTOT QU'UN CHAMP TEXTE
 * -------------------------------------------------
 * La colonne `grade` existait depuis le premier sprint en texte libre, et n'a
 * jamais ete alimentee. C'est une chance : une cotation saisie librement est
 * inexploitable statistiquement. « 7A », « 7a » et « 7A ' » deviennent trois
 * niveaux distincts, et rien ne se trie — or trier est precisement ce que
 * demandent les statistiques de progression.
 *
 * Chaque echelle est donc une liste ORDONNEE d'etiquettes canoniques, et la
 * position dans cette liste donne le rang de difficulte. Un circuit de couleurs
 * fonctionne exactement pareil, a ceci pres que son ordre est defini par
 * l'utilisateur au lieu d'etre connu d'avance : les deux systemes se ramenent a
 * la meme abstraction, et les statistiques n'auront qu'un rang a consommer.
 *
 * L'ordre des ENUMERATIONS est persiste (colonne `grade_scale`) : ne jamais le
 * reordonner. Les etiquettes, elles, sont persistees telles quelles dans
 * `grade` — en ajouter est sans danger, en renommer une orphelinerait les blocs
 * existants.
 */
enum class GradeScale(val grades: List<String>) {

    /** Echelle francaise, celle des salles et topos francophones. */
    FRENCH(
        grades = listOf(
            "3", "4", "5A", "5B", "5C",
            "6A", "6A+", "6B", "6B+", "6C", "6C+",
            "7A", "7A+", "7B", "7B+", "7C", "7C+",
            "8A", "8A+", "8B", "8B+", "8C", "8C+",
            "9A",
        ),
    ),

    /** Echelle V (Hueco), repandue dans le monde anglophone. */
    V_SCALE(
        grades = listOf(
            "V0", "V1", "V2", "V3", "V4", "V5", "V6", "V7", "V8",
            "V9", "V10", "V11", "V12", "V13", "V14", "V15", "V16", "V17",
        ),
    ),
    ;

    /**
     * Rang de difficulte d'une etiquette, ou `null` si elle n'appartient pas a
     * cette echelle.
     *
     * Retourner `null` plutot que -1 ou 0 : un bloc cote dans une echelle puis
     * relu dans l'autre ne doit pas se retrouver silencieusement classe le plus
     * facile de la liste.
     */
    fun rankOf(grade: String): Int? = grades.indexOf(grade).takeIf { it >= 0 }

    /** true si l'etiquette appartient a cette echelle. */
    fun contains(grade: String): Boolean = rankOf(grade) != null

    companion object {
        /** Echelle a laquelle appartient une etiquette, si une seule la reconnait. */
        fun scaleOf(grade: String): GradeScale? = entries.singleOrNull { it.contains(grade) }
    }
}
