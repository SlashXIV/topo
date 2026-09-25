package com.topo.core.domain.repository

import com.topo.core.domain.model.CircuitColor
import kotlinx.coroutines.flow.Flow

/**
 * Circuit de couleurs de la salle : la liste ordonnee du plus facile au plus dur.
 *
 * Une seule liste pour toute l'application. Des circuits propres a chaque salle
 * supposeraient de transformer le champ texte « lieu » des seances en vraie
 * entite salle — un chantier a part, avec sa migration.
 */
interface CircuitColorRepository {

    /** Couleurs triees par difficulte croissante. */
    fun observeColors(): Flow<List<CircuitColor>>

    suspend fun getColors(): List<CircuitColor>

    /**
     * Reecrit la liste entiere, en UNE transaction.
     *
     * Un index UNIQUE porte sur le rang : mettre a jour les rangs un par un
     * violerait la contrainte en cours de route. Meme patron que
     * `replaceMovements`, pour la meme raison.
     */
    suspend fun replaceColors(colors: List<CircuitColor>)

    /** @return la couleur creee, placee en fin de liste (la plus difficile). */
    suspend fun addColor(name: String, argb: Int): CircuitColor

    /**
     * Supprime une couleur.
     *
     * Les blocs qui la portaient perdent leur couleur mais SUBSISTENT
     * (ON DELETE SET NULL) : effacer une etiquette de circuit ne doit pas
     * effacer les ascensions qui l'utilisaient.
     */
    suspend fun deleteColor(colorId: String)
}
