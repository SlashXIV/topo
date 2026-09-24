package com.topo.core.domain.repository

import com.topo.core.domain.model.Bloc
import com.topo.core.domain.model.BlocDetail
import kotlinx.coroutines.flow.Flow

/** Acces aux blocs et a leur annotation complete. */
interface BlocRepository {

    fun observeBlocsForSession(sessionId: String): Flow<List<Bloc>>

    /**
     * Le bloc avec ses prises et ses mouvements, en un seul flux.
     *
     * Emettre les trois ensembles ensemble evite un etat intermediaire incoherent
     * a l'ecran (des mouvements pointant vers des prises pas encore chargees).
     * Emet `null` si le bloc a ete supprime.
     */
    fun observeBlocDetail(blocId: String): Flow<BlocDetail?>

    /**
     * Enregistre un bloc a partir d'une image DEJA copiee dans le stockage interne.
     *
     * Le repository ne prend pas en charge la copie : c'est le role de
     * [BlocImageStore], qui garantit l'atomicite du fichier avant que la base n'en
     * connaisse le chemin. Une ligne referencant un fichier inexistant serait
     * irreparable.
     *
     * @return l'identifiant du bloc cree.
     */
    suspend fun createBloc(
        sessionId: String,
        imagePath: String,
        imageWidth: Int,
        imageHeight: Int,
        grade: String? = null,
    ): String

    /**
     * Lecture ponctuelle d'un bloc.
     *
     * Distincte des flux d'observation : les cas d'usage qui modifient un bloc ont
     * besoin de son etat courant une fois, pas d'un abonnement. Collecter un Flow
     * pour en prendre le premier element ouvrirait puis fermerait un abonnement a
     * chaque appui sur un bouton.
     */
    suspend fun getBloc(blocId: String): Bloc?

    suspend fun updateBloc(bloc: Bloc)

    /** Supprime le bloc, son annotation (cascade SQLite) et son fichier image. */
    suspend fun deleteBloc(blocId: String)
}
