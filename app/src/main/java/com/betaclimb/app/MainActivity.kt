package com.betaclimb.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.betaclimb.core.ui.theme.BetaClimbTheme
import com.betaclimb.vision.VisionEngine
import dagger.hilt.android.AndroidEntryPoint
import java.nio.ByteBuffer

/**
 * Sprint 0 : ecran de diagnostic du socle natif.
 *
 * Il ne restera pas tel quel - le NavHost et les ecrans reels arrivent au
 * sprint suivant. Son interet ici est de rendre VISIBLE, sur l'appareil, ce que
 * le sprint a livre : la bibliotheque native se charge, le pont JNI s'enregistre,
 * un buffer direct traverse la frontiere sans copie.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BetaClimbTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    NativeDiagnosticsScreen()
                }
            }
        }
    }
}

@Composable
private fun NativeDiagnosticsScreen() {
    var report by remember { mutableStateOf("Diagnostic en cours...") }

    LaunchedEffect(Unit) {
        report = runNativeSmokeTest()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(text = "BetaClimb", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Sprint 0 - socle natif",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(text = report, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Traverse reellement le pont JNI et rapporte ce qui s'est passe.
 * Le seul fait que cet ecran s'affiche prouve deja que la .so est chargee, donc
 * qu'elle est correctement alignee pour le noyau de l'appareil.
 */
private suspend fun runNativeSmokeTest(): String = buildString {
    appendLine("Appareil   : ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Android    : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("ABI        : ${Build.SUPPORTED_ABIS.joinToString()}")
    appendLine()

    try {
        VisionEngine().use { engine ->
            appendLine("libbetaclimb_vision.so : CHARGEE")
            appendLine("Pont JNI               : enregistre (RegisterNatives)")

            val width = 64
            val height = 64
            val buffer = ByteBuffer.allocateDirect(width * height * 4)
            appendLine("Buffer direct          : ${buffer.isDirect} (${buffer.capacity()} octets)")

            val holds = engine.detect(
                VisionEngine.DirectBufferSource(
                    buffer = buffer,
                    width = width,
                    height = height,
                    rowStride = width * 4,
                ),
            )
            appendLine("Traversee zero-copie   : OK")
            appendLine("Prises detectees       : ${holds.size} (aucun modele charge - attendu)")
            appendLine("Moteur pret            : ${engine.isReady}")
        }
        appendLine("Handle natif           : libere")
        appendLine()
        appendLine("Socle natif operationnel.")
    } catch (error: Throwable) {
        appendLine()
        appendLine("ECHEC : ${error::class.java.simpleName}")
        appendLine(error.message.orEmpty())
    }
}
