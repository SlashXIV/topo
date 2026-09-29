package com.topo.core.domain.repository

import com.topo.core.domain.model.DetectedHold
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.Movement

/**
 * Ecriture de l'annotation d'un bloc : ses prises et sa sequence de mouvements.
 *
 * Separe de [BlocRepository] parce que le cycle de vie n'est pas le meme : un bloc
 * est cree une fois, son annotation est reecrite en continu pendant le trace.
 * La lecture, elle, reste unifiee dans `observeBlocDetail` — l'ecran d'annotation a
 * besoin des trois ensembles a la fois.
 */
interface BetaRepository {

    /**
     * Remplace l'integralite des prises d'un bloc, en UNE transaction.
     *
     * C'est l'operation qui suit une detection automatique ou un import : l'ancien
     * jeu disparait, le nouveau le remplace. Transactionnel parce qu'un etat
     * intermediaire (anciennes prises supprimees, nouvelles pas encore inserees)
     * casserait les mouvements qui les referencent.
     *
     * @return les prises persistees, avec leurs identifiants attribues.
     */
    suspend fun replaceHolds(blocId: String, holds: List<DetectedHold>): List<Hold>

    /** @return la prise creee, avec son identifiant. */
    suspend fun addHold(blocId: String, hold: DetectedHold): Hold

    /**
     * Ajoute plusieurs prises en UNE transaction, sans toucher aux existantes.
     *
     * C'est l'ecriture d'une detection automatique. Une insertion par prise ferait
     * reemettre le Flow du bloc une centaine de fois, et l'ecran se redessinerait
     * a chaque prise.
     *
     * @return les prises creees, avec leurs identifiants.
     */
    suspend fun addHolds(blocId: String, holds: List<DetectedHold>): List<Hold>

    suspend fun updateHold(hold: Hold)

    /**
     * Supprime une prise.
     *
     * Effets de bord assumes, appliques par SQLite et non par du code metier :
     *  - les mouvements qui ARRIVENT sur cette prise sont supprimes (ON DELETE
     *    CASCADE) — un mouvement vers une prise inexistante n'a pas de sens ;
     *  - les mouvements qui en PARTENT voient leur source passer a nul (ON DELETE
     *    SET NULL) — ils deviennent des mouvements de depart, ce qui reste valide.
     */
    suspend fun deleteHold(holdId: String)

    /**
     * Supprime plusieurs prises en UNE transaction, avec les memes effets de bord
     * que [deleteHold]. C'est l'ecriture du filtre par couleur : une centaine de
     * prises retirees d'un coup, qui ne doivent faire reemettre le bloc qu'une fois.
     */
    suspend fun deleteHolds(holdIds: List<String>)

    /**
     * Remplace la sequence complete de mouvements d'un bloc, en UNE transaction.
     *
     * Le trace au doigt produit une sequence entiere, pas des mutations unitaires :
     * la remplacer en bloc evite d'avoir a reconcilier des ordres de sequence
     * partiels, source classique d'incoherence.
     */
    suspend fun replaceMovements(blocId: String, movements: List<Movement>): List<Movement>

    suspend fun deleteMovement(movementId: String)
}
