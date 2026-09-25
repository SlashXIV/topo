package com.topo.core.domain.model

/**
 * Une couleur du circuit de la salle.
 *
 * [rank] N'EST PAS decoratif : c'est la difficulte. Une salle range ses couleurs
 * du plus facile au plus dur, et cet ordre varie d'une salle a l'autre — jaune
 * peut etre le niveau d'entree ici et le plus dur ailleurs. C'est pour cela que
 * l'ordre est defini par l'utilisateur et non code en dur.
 *
 * Le rang est contigu et unique dans la liste : c'est ce qui permet aux
 * statistiques de traiter une couleur exactement comme une cotation chiffree.
 *
 * [argb] est la couleur au format ARGB, stockee en entier plutot qu'en chaine
 * hexadecimale : c'est ce que manipulent a la fois SQLite et Compose, sans
 * analyse de texte au milieu.
 */
data class CircuitColor(
    val id: String,
    val name: String,
    val argb: Int,
    val rank: Int,
) {
    init {
        require(name.isNotBlank()) { "Nom de couleur vide" }
        require(rank >= 0) { "Rang negatif : $rank" }
    }
}
