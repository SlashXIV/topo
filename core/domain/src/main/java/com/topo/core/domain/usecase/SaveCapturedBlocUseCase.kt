package com.topo.core.domain.usecase

import com.topo.core.domain.repository.BlocImageStore
import com.topo.core.domain.repository.BlocRepository
import java.io.File
import javax.inject.Inject

/**
 * Transforme une photo capturee en bloc enregistre.
 *
 * Cette operation touche DEUX magasins de nature differente : le systeme de
 * fichiers (l'image) et SQLite (la ligne). Il n'existe pas de transaction commune
 * aux deux, donc l'ordre et le rattrapage sont explicites :
 *
 *  1. le fichier existe deja (CameraX a ecrit dedans) ;
 *  2. on lit ses dimensions sans le decoder ;
 *  3. on insere la ligne ;
 *  4. si l'insertion echoue, on SUPPRIME le fichier.
 *
 * Sans l'etape 4, chaque echec laisserait une photo orpheline de plusieurs Mo que
 * rien ne viendrait jamais nettoyer. L'inverse — une ligne pointant vers un fichier
 * absent — serait pire : l'ecran de detail planterait sans moyen de reparation.
 * D'ou cet ordre, et pas l'autre.
 */
class SaveCapturedBlocUseCase @Inject constructor(
    private val blocRepository: BlocRepository,
    private val imageStore: BlocImageStore,
) {

    suspend operator fun invoke(
        sessionId: String,
        capturedFile: File,
        grade: String? = null,
    ): Result<String> {
        val relativePath = imageStore.relativePathOf(capturedFile)
        return runCatching {
            val dimensions = imageStore.readDimensions(relativePath)
            blocRepository.createBloc(
                sessionId = sessionId,
                imagePath = relativePath,
                imageWidth = dimensions.width,
                imageHeight = dimensions.height,
                grade = grade,
            )
        }.onFailure {
            // Rattrapage : pas de photo orpheline si la base a refuse la ligne.
            imageStore.delete(relativePath)
        }
    }
}
