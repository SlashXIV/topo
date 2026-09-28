package com.topo.feature.annotator

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import com.topo.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Ecrit l'image d'un beta en PNG et prepare son partage.
 *
 * Le fichier vit dans `cacheDir/shared`, expose par un [FileProvider] : l'application
 * receptrice obtient un droit de lecture temporaire sur CE fichier, et sur rien
 * d'autre. Les photos de `filesDir/blocs` ne sont jamais exposees.
 *
 * PNG plutot que JPEG : le trace est fait d'aplats et d'aretes franches, exactement
 * ce que la compression JPEG degrade en halos autour des traits.
 */
class BetaImageExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    private val directory: File get() = File(context.cacheDir, DIRECTORY)

    /** Autorite derivee du paquet : elle reste juste dans l'APK de test d'une bibliotheque. */
    private val authority: String get() = "${context.packageName}$AUTHORITY_SUFFIX"

    /**
     * @return l'URI `content://` du PNG ecrit.
     * @throws IOException si l'ecriture ou l'encodage echoue.
     */
    suspend fun export(image: ImageBitmap): Uri = withContext(ioDispatcher) {
        // L'export precedent est purge ici, et non juste apres le partage : on ne
        // sait pas quand l'application receptrice aura fini de lire le fichier.
        // Au prochain export, elle a fini depuis longtemps. Le repertoire ne
        // contient donc jamais plus d'une image.
        val target = directory.apply {
            deleteRecursively()
            mkdirs()
        }.resolve(fileName())

        // Une GraphicsLayer rend en bitmap HARDWARE sur l'API 28+, qui vit en
        // memoire graphique : son contenu doit etre relu en ARGB_8888 avant
        // d'etre encode par le CPU.
        val source = image.asAndroidBitmap()
        val readable = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            source
        }

        try {
            val encoded = target.outputStream().use { output ->
                readable.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY_IGNORED, output)
            }
            if (!encoded) throw IOException("Encodage PNG refuse pour ${target.name}")
        } catch (error: Throwable) {
            target.delete()
            throw error
        } finally {
            if (readable !== source) readable.recycle()
        }

        FileProvider.getUriForFile(context, authority, target)
    }

    /**
     * Selecteur systeme de partage pour [uri].
     *
     * Le droit de lecture est porte par le ClipData en plus de l'extra : c'est par
     * lui que le selecteur transmet l'autorisation a l'application choisie. Sans
     * lui, certaines cibles recoivent l'URI et echouent a l'ouvrir.
     */
    fun shareIntent(uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }

    private fun fileName(): String {
        // Horodate : le nom est ce que le destinataire voit dans sa messagerie, et
        // certaines applications mettent en cache par URI — un nom fixe leur ferait
        // afficher l'image precedente.
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        return "topo-beta-$stamp.png"
    }

    internal companion object {
        const val DIRECTORY = "shared"
        const val AUTHORITY_SUFFIX = ".betaimages"
        const val MIME_TYPE = "image/png"

        /** PNG est sans perte : le parametre de qualite est ignore par l'encodeur. */
        private const val PNG_QUALITY_IGNORED = 100
    }
}
