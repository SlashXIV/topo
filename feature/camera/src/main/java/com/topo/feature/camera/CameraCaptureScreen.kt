package com.topo.feature.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.annotation.SuppressLint
import android.view.ScaleGestureDetector
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.topo.core.ui.component.TopoButton
import com.topo.core.ui.component.TopoIcons
import com.topo.core.ui.component.TopoProgress
import com.topo.core.ui.component.TopoText
import com.topo.core.ui.component.cropMarks
import com.topo.core.ui.motion.pressScale
import com.topo.core.ui.theme.LocalContentColor
import com.topo.core.ui.theme.LocalTopoColors
import com.topo.core.ui.theme.TopoPalettes
import com.topo.core.ui.theme.TopoTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
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
 * Une chambre noire : apercu de toute la photo entre des reperes de cadrage, un
 * declencheur rond. Le cablage — permission, liaison au cycle de vie de CameraX,
 * remontee de l'identifiant du bloc cree — est l'essentiel de ce fichier.
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

    // L'appareil photo est une chambre noire : l'ardoise de nuit, quel que soit le theme.
    CompositionLocalProvider(
        LocalTopoColors provides TopoPalettes.Night,
        LocalContentColor provides TopoPalettes.Night.ink,
    ) {
        val colors = TopoTheme.colors
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (hasPermission) {
                CameraPreview(
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .padding(bottom = 128.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val state = uiState) {
                    CaptureUiState.Capturing -> TopoProgress()
                    is CaptureUiState.Failed -> TopoText(
                        text = state.detail
                            ?.let { stringResource(R.string.camera_capture_error_detail, it) }
                            ?: stringResource(R.string.camera_capture_error),
                        color = colors.error,
                        style = TopoTheme.type.label,
                    )

                    else -> Unit
                }

                if (hasPermission) {
                    Shutter(
                        label = stringResource(R.string.camera_capture_action),
                        enabled = uiState !is CaptureUiState.Capturing,
                        onClick = { viewModel.captureBloc(sessionId) },
                    )
                } else {
                    // Refusee une fois, l'autorisation se redemande d'ici plutot que
                    // de laisser un bouton mort.
                    TopoButton(
                        text = stringResource(R.string.camera_permission_required),
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        icon = TopoIcons.Camera,
                    )
                }
            }
        }
    }
}

/**
 * Declencheur : un disque papier dans un anneau, sans libelle visible ; son nom
 * est donne a TalkBack. Il s'enfonce a la pression comme les autres boutons.
 */
@Composable
private fun Shutter(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = TopoTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(76.dp)
            .pressScale(interaction)
            .border(3.dp, colors.ink, CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.ink else colors.ink2)
            .clickable(interaction, LocalIndication.current, enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
    )
}

/**
 * Liaison de CameraX au cycle de vie du composable.
 *
 * `DisposableEffect` et non `LaunchedEffect` : la camera est une ressource
 * materielle exclusive. Si elle n'etait pas deliee a la sortie de l'ecran, aucune
 * autre application — ni un autre ecran de celle-ci — ne pourrait l'ouvrir.
 */
@SuppressLint("ClickableViewAccessibility") // pincement seul : aucun clic a annoncer
@Composable
private fun CameraPreview(
    viewModel: CameraCaptureViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    // Porteur hors snapshot : la camera change a chaque liaison, sans rien a recomposer.
    val boundCamera = remember { arrayOfNulls<Camera>(1) }

    val previewView = remember {
        PreviewView(context).apply {
            // L'apercu montre TOUTE la photo, bandes comprises, plutot que de
            // remplir l'ecran en rognant : ce qui est vu est ce qui est capture.
            scaleType = PreviewView.ScaleType.FIT_CENTER

            // Pincer pour zoomer : resserrer le cadre sur le bloc, a la prise de
            // vue plutot qu'en recadrant apres. Au-dela de 2x environ, la camera
            // logique du telephone bascule d'elle-meme sur le teleobjectif, plus
            // net qu'un recadrage de la photo grand angle.
            val pinch = ScaleGestureDetector(
                context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val camera = boundCamera[0] ?: return false
                        val state = camera.cameraInfo.zoomState.value ?: return false
                        camera.cameraControl.setZoomRatio(
                            (state.zoomRatio * detector.scaleFactor)
                                .coerceIn(state.minZoomRatio, state.maxZoomRatio),
                        )
                        return true
                    }
                },
            )
            setOnTouchListener { _, event -> pinch.onTouchEvent(event) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null

        providerFuture.addListener({
            provider = providerFuture.get().also { cameraProvider ->
                val preview = viewModel.newPreview().apply {
                    surfaceProvider = previewView.surfaceProvider
                }

                // Toujours repartir d'un etat propre : lier deux fois le meme cas
                // d'usage leve une exception cote CameraX.
                cameraProvider.unbindAll()
                boundCamera[0] = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    viewModel.imageCapture,
                ).also { camera ->
                    camera.cameraInfo.zoomState.observe(lifecycleOwner) { zoomRatio = it.zoomRatio }
                }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            boundCamera[0] = null
            provider?.unbindAll()
        }
    }

    Box(modifier = modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        // Reperes de cadrage aux coins, comme ceux d'une page de topo : ils disent
        // ou s'arrete la photo sans masquer le mur.
        Box(Modifier.fillMaxSize().padding(16.dp).cropMarks(TopoTheme.colors.ink, length = 22.dp, stroke = 2.dp))
        // Le zoom affiche : sans lui, rien ne dit qu'un pincement a eu lieu, ni
        // qu'on est revenu a 1x.
        TopoText(
            text = stringResource(R.string.camera_zoom, zoomRatio),
            style = TopoTheme.type.cote,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(24.dp)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
