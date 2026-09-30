package com.topo.feature.relief

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * Surface GL de la vue 3D : un doigt fait tourner le mur, deux doigts zooment, un
 * double appui rejoue le beta.
 */
@SuppressLint("ViewConstructor")
internal class ReliefSurfaceView(context: Context, val renderer: ReliefRenderer) : GLSurfaceView(context) {

    private val scale = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                renderer.zoomBy(detector.scaleFactor)
                return true
            }
        },
    )

    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onScroll(first: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
                // Pendant un pincement, les deux doigts glissent aussi : ne pas tourner.
                if (!scale.isInProgress) renderer.rotateBy(-dx, -dy)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                renderer.replay()
                return true
            }
        },
    )

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        // Garder le contexte en arriere-plan evite de televerser a nouveau le maillage et
        // la texture a chaque retour sur l'ecran, quand le GPU le permet.
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        // Continu : le balancement au repos et le rejeu animent chaque trame. Mis en
        // pause avec l'ecran (cf. ReliefScreen), jamais en arriere-plan.
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Une SurfaceView cree sa surface au pre-dessin de la PROCHAINE traversee de la
        // fenetre. Ajoutee par Compose sur un ecran immobile (fin du calcul du relief),
        // aucune traversee ne venait : la surface attendait le prochain geste ou la sortie
        // de l'ecran — 3 s constatees sur le Xiaomi, indefiniment sur l'emulateur.
        post { requestLayout() }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scale.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }
}
