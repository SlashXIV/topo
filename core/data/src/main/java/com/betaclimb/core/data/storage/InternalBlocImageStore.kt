package com.betaclimb.core.data.storage

import android.content.Context
import android.graphics.BitmapFactory
import com.betaclimb.core.common.di.IoDispatcher
import com.betaclimb.core.domain.repository.BlocImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Stockage des photos dans l'espace prive de l'application (`filesDir/blocs`).
 *
 * Aucune permission requise, aucune image visible des autres applications ni de la
 * galerie : le projet est hors-ligne et prive par construction. Le repertoire est
 * efface avec l'application, ce qui est le comportement attendu.
 *
 * Toutes les operations basculent sur le dispatcher d'entrees-sorties INJECTE : une
 * ecriture de plusieurs megaoctets sur le fil principal provoquerait un ANR, et un
 * `Dispatchers.IO` code en dur rendrait la classe intestable.
 */
@Singleton
internal class InternalBlocImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BlocImageStore {

    private val directory: File by lazy {
        File(context.filesDir, DIRECTORY).apply { mkdirs() }
    }

    override suspend fun createCaptureTarget(): File = withContext(ioDispatcher) {
        directory.mkdirs()
        File(directory, "${UUID.randomUUID()}.jpg")
    }

    override fun relativePathOf(file: File): String = file.name

    override fun resolve(relativePath: String): File = File(directory, relativePath)

    override suspend fun import(source: () -> InputStream): String = withContext(ioDispatcher) {
        val target = File(directory, "${UUID.randomUUID()}.jpg")
        // Ecriture dans un fichier temporaire puis renommage atomique : un import
        // interrompu (batterie, kill systeme) ne doit jamais laisser un JPEG
        // tronque que la base referencerait comme valide.
        val staging = File(directory, "${target.name}.part")
        try {
            source().use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            }
            check(staging.renameTo(target)) { "Renommage atomique impossible" }
            target.name
        } catch (error: Throwable) {
            staging.delete()
            throw error
        }
    }

    override suspend fun delete(relativePath: String) {
        withContext(ioDispatcher) { resolve(relativePath).delete() }
    }

    override suspend fun readDimensions(
        relativePath: String,
    ): BlocImageStore.ImageDimensions = withContext(ioDispatcher) {
        val file = resolve(relativePath)
        require(file.exists()) { "Image introuvable : $relativePath" }

        // inJustDecodeBounds : lit uniquement l'en-tete, n'alloue aucun pixel. Une
        // photo de telephone decodee en entier pese ~48 Mo en ARGB_8888, ce qui
        // serait un candidat direct a l'OutOfMemoryError pour deux entiers.
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)

        check(options.outWidth > 0 && options.outHeight > 0) {
            "Image illisible ou corrompue : $relativePath"
        }
        BlocImageStore.ImageDimensions(options.outWidth, options.outHeight)
    }

    private companion object {
        const val DIRECTORY = "blocs"
    }
}
