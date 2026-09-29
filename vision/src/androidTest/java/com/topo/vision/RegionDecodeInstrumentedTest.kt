package com.topo.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decodage d'une zone sur un vrai JPEG, orientation EXIF comprise.
 *
 * Complete RegionMappingTest (JVM) : ici, c'est BitmapRegionDecoder, la rotation
 * appliquee et la convention EXIF elle-meme qui sont eprouves. Une photo prise en
 * portrait est stockee couchee avec l'etiquette ROTATE_90 : la zone tracee sur
 * l'affichage doit designer le meme endroit une fois le fichier lu.
 */
@RunWith(AndroidJUnit4::class)
class RegionDecodeInstrumentedTest {

    private val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
    private val file = File(directory, "region-test.jpg")

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun the_drawn_zone_is_decoded_from_a_portrait_photo_stored_sideways() {
        // Photo telle qu'AFFICHEE : 300 x 400, un carre rouge en haut a droite.
        val upright = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (y in 40 until 120) for (x in 200 until 280) setPixel(x, y, Color.RED)
        }
        // Stockee couchee : tournee d'un quart de tour ANTIHORAIRE, etiquetee
        // ROTATE_90 pour que l'affichage la redresse dans le sens horaire.
        val sideways = Bitmap.createBitmap(upright, 0, 0, 300, 400, Matrix().apply { postRotate(-90f) }, true)
        file.outputStream().use { sideways.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        ExifInterface(file.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }

        VisionEngine().use { engine ->
            // La zone du carre rouge, en coordonnees de la photo AFFICHEE.
            val zone = NormalizedRect(left = 200f / 300, top = 40f / 400, right = 280f / 300, bottom = 120f / 400)
            val decoded = engine.decodeForInference(file, zone)

            // Redressee et limitee a la zone : un carre, entierement rouge.
            assertThat(decoded.width).isWithin(2).of(80)
            assertThat(decoded.height).isWithin(2).of(80)
            val centre = decoded.getPixel(decoded.width / 2, decoded.height / 2)
            assertThat(Color.red(centre)).isGreaterThan(200)
            assertThat(Color.green(centre)).isLessThan(60)
        }
    }
}
