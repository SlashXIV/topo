package com.topo.core.domain.model

/**
 * Ou en est le grimpeur sur un bloc.
 *
 * Remplace l'ancien booleen `isCompleted`, qui ne distinguait pas « jamais essaye »
 * de « travaille sans succes » — precisement la distinction qui interesse un
 * carnet d'entrainement. Conserver les deux cote a cote aurait cree deux sources
 * de verite pour la meme question, promises a diverger.
 *
 * ATTENTION — les ordinaux sont PERSISTES (colonne `status`) et references
 * litteralement dans l'agregation SQL des seances. Ne jamais reordonner ni inserer
 * au milieu : toute nouvelle valeur s'ajoute a la fin. Un test verrouille cet ordre.
 */
enum class BlocStatus {
    /** Repere, pas encore essaye. */
    PROJECT,

    /** Essaye, pas encore reussi. */
    WORKING,

    /** Reussi du premier coup, sans repetition prealable. */
    FLASHED,

    /** Reussi apres travail. */
    SENT,
    ;

    /** true si le bloc compte comme reussi dans les statistiques. */
    val isSent: Boolean get() = this == FLASHED || this == SENT

    companion object {
        /**
         * Ordinaux des statuts reussis, tels qu'ecrits dans la requete
         * d'agregation `observeSessionsWithCounts`. Definis ici pour que le lien
         * entre l'enumeration et le SQL soit visible d'un seul endroit.
         */
        const val SQL_SENT_ORDINALS = "2, 3"
    }
}
