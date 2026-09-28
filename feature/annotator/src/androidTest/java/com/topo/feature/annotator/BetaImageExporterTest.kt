package com.topo.feature.annotator

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ecriture du PNG et exposition par FileProvider.
 *
 * Le fichier est relu par le ContentResolver, comme le ferait l'application
 * receptrice : un fichier bien ecrit mais hors des chemins declares du provider
 * passerait un test sur le [File] et echouerait au partage reel.
 */
@RunWith(AndroidJUnit4::class)
class BetaImageExporterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val exporter = BetaImageExporter(context, Dispatchers.IO)
    private val directory = File(context.cacheDir, BetaImageExporter.DIRECTORY)

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun the_exported_png_is_readable_through_the_provider_without_loss() = runTest {
        val uri = exporter.export(solidBitmap(64, 48, Color.RED).asImageBitmap())

        assertThat(uri.scheme).isEqualTo(ContentResolver.SCHEME_CONTENT)
        assertThat(uri.authority).isEqualTo(context.packageName + BetaImageExporter.AUTHORITY_SUFFIX)
        assertThat(context.contentResolver.getType(uri)).isEqualTo(BetaImageExporter.MIME_TYPE)

        val decoded = decode(uri)
        assertThat(decoded.width).isEqualTo(64)
        assertThat(decoded.height).isEqualTo(48)
        // Sans perte : le pixel relu est exactement celui ecrit.
        assertThat(decoded.getPixel(10, 10)).isEqualTo(Color.RED)
    }

    @Test
    fun a_new_export_purges_the_previous_one() = runTest {
        exporter.export(solidBitmap(8, 8, Color.RED).asImageBitmap())
        // Le nom est horodate a la seconde : deux exports dans la meme seconde
        // porteraient le meme nom et le test ne prouverait rien.
        Thread.sleep(1_100)
        exporter.export(solidBitmap(8, 8, Color.BLUE).asImageBitmap())

        val remaining = directory.listFiles().orEmpty()
        assertThat(remaining).hasLength(1)
        assertThat(BitmapFactory.decodeFile(remaining.single().path).getPixel(0, 0))
            .isEqualTo(Color.BLUE)
    }

    @Test
    fun the_share_intent_grants_read_access_to_the_image_only() = runTest {
        val uri = exporter.export(solidBitmap(8, 8, Color.RED).asImageBitmap())

        val chooser = exporter.shareIntent(uri)
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!

        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.type).isEqualTo(BetaImageExporter.MIME_TYPE)
        assertThat(send.clipData!!.getItemAt(0).uri).isEqualTo(uri)
        assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(send.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION).isEqualTo(0)
    }

    private fun decode(uri: Uri): Bitmap =
        context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)

    private fun solidBitmap(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
}
