package com.topo.core.domain.usecase

import com.topo.core.domain.repository.BlocImageStore
import com.topo.core.domain.repository.BlocRepository
import java.io.InputStream
import javax.inject.Inject

/**
 * Cree un bloc a partir d'une image EXTERNE (galerie, autre application).
 *
 * Jumeau de [SaveCapturedBlocUseCase], avec une etape de plus : l'image n'est pas
 * encore dans le stockage de l'application, il faut donc la COPIER d'abord.
 *
 * La copie n'est pas une precaution excessive. Une `content://` obtenue d'un
 * selecteur externe porte une autorisation temporaire, revocable, et qui ne
 * survit ni au redemarrage ni a la suppression de la photo d'origine. Referencer
 * cette URI en base condamnerait le bloc a devenir illisible un jour, dans une
 * application qui se veut utilisable hors ligne et dans la duree.
 *
 * L'ordre et le rattrapage sont les memes que pour une capture, et pour la meme
 * raison : il n'existe aucune transaction commune au systeme de fichiers et a
 * SQLite. Une photo orpheline se rattrape ; une ligne pointant vers un fichier
 * absent laisserait l'ecran de detail sans recours.
 */
class ImportBlocUseCase @Inject constructor(
    private val blocRepository: BlocRepository,
    private val imageStore: BlocImageStore,
) {

    /**
     * @param openStream ouvre le flux de l'image source. Appele une seule fois, et
     *        ferme par [BlocImageStore.import]. Une lambda plutot qu'un
     *        [InputStream] deja ouvert : si l'import echoue avant d'avoir lu quoi
     *        que ce soit, aucun flux n'aura ete ouvert pour rien.
     */
    suspend operator fun invoke(
        sessionId: String,
        openStream: () -> InputStream,
        grade: String? = null,
    ): Result<String> {
        val relativePath = runCatching { imageStore.import(openStream) }
            .getOrElse { return Result.failure(it) }

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
            // La copie a reussi mais la suite non : on ne laisse pas la copie.
            imageStore.delete(relativePath)
        }
    }
}
