package com.topo.core.domain.model

/**
 * Ce qu'on met en avant sur un bloc.
 *
 * Un bloc peut porter les DEUX : la salle l'etiquette « vert », et le grimpeur
 * l'estime a 6B. Ce sont deux informations distinctes et complementaires, pas
 * deux facons de dire la meme chose. Ce reglage ne choisit donc pas ce qui est
 * enregistre, seulement ce qui s'affiche en premier.
 *
 * Ordinaux persistes : ne jamais reordonner.
 */
enum class DifficultyDisplay(val label: String) {
    COLOR("Couleur du circuit"),
    GRADE("Cotation chiffree"),
}

/**
 * Reglages de l'application.
 *
 * Stockes dans Room, pas dans DataStore. Le premier principe du projet est que
 * SQLite est l'unique source de verite ; ajouter un second magasin pour deux
 * enumerations apporterait sa propre histoire de sauvegarde et de migration,
 * alors qu'en table ces reglages suivent le mecanisme de migration deja teste.
 */
data class AppSettings(
    val difficultyDisplay: DifficultyDisplay,
    val gradeScale: GradeScale,
) {
    companion object {
        /**
         * Valeurs initiales.
         *
         * Couleur par defaut : c'est ce que le grimpeur lit sur le mur, alors
         * que la cotation chiffree releve de son appreciation.
         */
        val Default = AppSettings(
            difficultyDisplay = DifficultyDisplay.COLOR,
            gradeScale = GradeScale.FRENCH,
        )
    }
}
