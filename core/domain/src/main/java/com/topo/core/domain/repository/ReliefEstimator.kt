package com.topo.core.domain.repository

import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.ReliefMap
import java.io.File

/**
 * Estimation du relief d'une photo de bloc, pour la vue 3D.
 *
 * Meme frontiere que [HoldDetector] : les couches superieures ignorent qu'un
 * reseau de neurones tourne derriere. Le modele est facultatif : sans lui,
 * [isAvailable] est faux et l'application masque la vue 3D au lieu de la montrer
 * cassee.
 */
interface ReliefEstimator {

    /**
     * true si un modele est embarque. Connu sans le charger : un ecran peut
     * decider d'afficher le bouton 3D sans payer l'ouverture de la session.
     */
    val isAvailable: Boolean

    /**
     * Relief de la [region] de la photo (normalisee dans la photo REDRESSEE, telle
     * qu'affichee), ou de la photo entiere si null.
     *
     * Plusieurs centaines de millisecondes au premier appel pour une photo
     * donnee ; l'implementation peut garder le resultat pour les suivants.
     *
     * @return null si aucun modele n'est disponible.
     */
    suspend fun estimate(file: File, region: BoundingBox?): ReliefMap?
}
