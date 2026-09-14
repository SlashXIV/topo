package com.betaclimb.feature.camera

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.core.content.ContextCompat
import com.betaclimb.core.common.di.IoDispatcher
import com.betaclimb.core.domain.repository.BlocImageStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Declenche une prise de vue et ecrit directement le JPEG dans le stockage prive.
 *
 * CameraX ecrit dans un [File] fourni : la photo pleine resolution ne transite
 * jamais par un tableau d'octets en memoire. Une photo de telephone depasse
 * couramment 12 Mpx, soit ~48 Mo une fois decodee en ARGB_8888 — la faire passer
 * par le heap Java serait un candidat direct a l'OutOfMemoryError.
 *
 * L'API de CameraX est a callbacks ; elle est enveloppee ici dans une coroutine
 * ANNULABLE, pour qu'une navigation arriere pendant la capture ne laisse pas une
 * continuation suspendue indefiniment.
 */
@Singleton
class BlocPhotoCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val imageStore: BlocImageStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Cas d'usage ImageCapture configure pour BetaClimb.
     *
     * La configuration vit ici, aupres de la capture, et non dans le ViewModel :
     * les tests instrumentes appellent cette meme fabrique et valident donc la
     * configuration reellement utilisee en production.
     */
    fun newImageCapture(): ImageCapture = ImageCapture.Builder()
        // On privilegie la latence a la qualite maximale : l'image sera de toute
        // facon sous-echantillonnee pour l'inference, et l'utilisateur photographie
        // debout au pied d'un bloc, pas en studio.
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        // ResolutionSelector remplace setTargetAspectRatio, deprecie : la strategie
        // de repli laisse CameraX choisir si le capteur ne sait pas produire du 4:3,
        // au lieu d'echouer.
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build(),
        )
        .build()

    /**
     * @return le fichier ecrit, dans le repertoire du [BlocImageStore].
     * @throws ImageCaptureException si le materiel refuse la capture.
     */
    suspend fun capture(imageCapture: ImageCapture): File {
        val target = imageStore.createCaptureTarget()

        return try {
            awaitCapture(imageCapture, target)
            target
        } catch (error: Throwable) {
            // Une capture echouee laisse souvent un fichier vide ou tronque :
            // on ne le laisse pas derriere nous, personne ne le nettoierait.
            withContext(ioDispatcher) { target.delete() }
            throw error
        }
    }

    private suspend fun awaitCapture(imageCapture: ImageCapture, target: File) =
        suspendCancellableCoroutine { continuation ->
            val options = ImageCapture.OutputFileOptions.Builder(target).build()

            imageCapture.takePicture(
                options,
                // L'executeur principal : les callbacks de CameraX y sont delivres,
                // et ils ne font ici que reprendre la coroutine — aucune I/O.
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        continuation.resume(Unit)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                },
            )
        }
}
