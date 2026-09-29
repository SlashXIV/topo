package com.topo.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Ecran de capture.
 *
 * Volontairement minimal : l'habillage Material 3 definitif appartient au sprint
 * d'assemblage. Ce qui compte ici, c'est le cablage — permission, liaison au cycle
 * de vie de CameraX, et remontee de l'identifiant du bloc cree.
 */
@Composable
fun CameraCaptureScreen(
    sessionId: String,
    onBlocCaptured: (blocId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraCaptureViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // rememberUpdatedState : le callback de navigation peut changer d'instance a
    // chaque recomposition. Sans cette indirection, l'effet ci-dessous capturerait
    // la toute premiere lambda et notifierait un destinataire perime.
    val currentOnBlocCaptured by rememberUpdatedState(onBlocCaptured)
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is CaptureUiState.Saved) {
            currentOnBlocCaptured(state.blocId)
            viewModel.acknowledge()
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        if (hasPermission) {
            CameraPreview(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val state = uiState) {
                CaptureUiState.Capturing -> CircularProgressIndicator()
                is CaptureUiState.Failed -> Text(
                    text = state.detail
                        ?.let { stringResource(R.string.camera_capture_error_detail, it) }
                        ?: stringResource(R.string.camera_capture_error),
                    color = MaterialTheme.colorScheme.error,
                )

                else -> Unit
            }

            Button(
                onClick = { viewModel.captureBloc(sessionId) },
                enabled = hasPermission && uiState !is CaptureUiState.Capturing,
            ) {
                Text(
                    stringResource(
                        if (hasPermission) R.string.camera_capture_action else R.string.camera_permission_required,
                    ),
                )
            }
        }
    }
}

/**
 * Liaison de CameraX au cycle de vie du composable.
 *
 * `DisposableEffect` et non `LaunchedEffect` : la camera est une ressource
 * materielle exclusive. Si elle n'etait pas deliee a la sortie de l'ecran, aucune
 * autre application — ni un autre ecran de celle-ci — ne pourrait l'ouvrir.
 */
@Composable
private fun CameraPreview(
    viewModel: CameraCaptureViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            provider = providerFuture.get().also { cameraProvider ->
                val preview = Preview.Builder().build().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                // Toujours repartir d'un etat propre : lier deux fois le meme cas
                // d'usage leve une exception cote CameraX.
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    viewModel.imageCapture,
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose { provider?.unbindAll() }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
