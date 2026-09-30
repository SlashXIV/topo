package com.topo.feature.relief

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Rendu OpenGL ES 3 de la vue 3D : le mur texture et eclaire, les halos des prises,
 * les arcs du beta.
 *
 * Toute la geometrie arrive deja construite ([ReliefScene]) : une trame ne fait que
 * poser des uniformes et lancer quatre dessins. Aucune allocation par trame.
 *
 * Fils : [setScene], [rotateBy], [zoomBy] et [replay] viennent du fil principal ; le
 * reste s'execute sur le fil GL. Les valeurs partagees sont @Volatile, et la scene
 * n'est televersee que sur le fil GL, a la trame suivante.
 */
internal class ReliefRenderer(private val clearColor: Int) : GLSurfaceView.Renderer {

    @Volatile private var pendingScene: Pair<ReliefScene, Bitmap>? = null

    // Conservee apres televersement : une perte de contexte EGL oblige a tout recreer.
    private var scene: ReliefScene? = null
    private var texture: Bitmap? = null

    @Volatile private var yaw = 0f
    @Volatile private var pitch = DEFAULT_PITCH
    @Volatile private var zoom = 1f
    @Volatile private var lastTouchNanos = 0L
    @Volatile private var replayRequested = false

    private var meshProgram = 0
    private var ribbonProgram = 0
    private var meshVao = 0
    private var meshIndexCount = 0
    private var haloVao = 0
    private var haloIndexCount = 0
    private var arcVao = 0
    private var arcIndexCount = 0
    private var textureId = 0
    private val buffers = IntArray(6)
    private val meshUniforms = HashMap<String, Int>()
    private val ribbonUniforms = HashMap<String, Int>()

    private var viewportWidth = 1
    private var viewportHeight = 1
    private var sceneStartNanos = 0L
    private var replayStartNanos = 0L

    private val frameStats = FrameStats()

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)

    @Volatile private var handedOverNanos = 0L

    fun setScene(scene: ReliefScene, texture: Bitmap) {
        handedOverNanos = System.nanoTime()
        pendingScene = scene to texture
    }

    /** Glissement du doigt, en pixels. */
    fun rotateBy(dx: Float, dy: Float) {
        // Le mur suit le doigt : c'est la camera qui part dans l'autre sens.
        yaw = (yaw - dx * DEGREES_PER_PIXEL).coerceIn(-MAX_YAW, MAX_YAW)
        pitch = (pitch + dy * DEGREES_PER_PIXEL).coerceIn(-MAX_PITCH, MAX_PITCH)
        lastTouchNanos = System.nanoTime()
    }

    fun zoomBy(factor: Float) {
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        lastTouchNanos = System.nanoTime()
    }

    fun replay() {
        replayRequested = true
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        meshProgram = program(MESH_VERTEX, MESH_FRAGMENT)
        ribbonProgram = program(RIBBON_VERTEX, RIBBON_FRAGMENT)
        meshUniforms.clear()
        ribbonUniforms.clear()
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        // Contexte neuf (premier affichage ou contexte perdu) : la scene deja connue
        // est televersee a nouveau, sans rejouer l'introduction.
        val current = scene
        val bitmap = texture
        if (current != null && bitmap != null && pendingScene == null) upload(current, bitmap)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = max(1, width)
        viewportHeight = max(1, height)
        GLES30.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        pendingScene?.let { (next, bitmap) ->
            pendingScene = null
            scene = next
            texture = bitmap
            upload(next, bitmap)
            // Delai entre la scene prete et sa premiere trame : c'est lui qui mesure
            // l'attente de la surface GL, pas l'inference.
            Log.i(TAG, "Premiere trame %.0f ms apres la scene".format((now - handedOverNanos) / 1e6))
            sceneStartNanos = now
            replayStartNanos = now + INTRO_NANOS
        }
        if (replayRequested) {
            replayRequested = false
            replayStartNanos = now
        }

        GLES30.glClearColor(
            (clearColor shr 16 and 0xFF) / 255f,
            (clearColor shr 8 and 0xFF) / 255f,
            (clearColor and 0xFF) / 255f,
            1f,
        )
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        val current = scene ?: return

        val relief = easeOut(((now - sceneStartNanos) / INTRO_NANOS.toFloat()).coerceIn(0f, 1f))
        val replayDuration = max(MIN_REPLAY_NANOS, current.movementCount * NANOS_PER_MOVE)
        val progress = ((now - replayStartNanos) / replayDuration.toFloat()).coerceIn(-1f, 1f)
        idleSway(now)
        updateCamera(current)

        GLES30.glUseProgram(meshProgram)
        GLES30.glUniformMatrix4fv(uniform(meshProgram, "uMvp"), 1, false, mvp, 0)
        GLES30.glUniform1f(uniform(meshProgram, "uRelief"), relief)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glUniform1i(uniform(meshProgram, "uTexture"), 0)
        GLES30.glBindVertexArray(meshVao)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, meshIndexCount, GLES30.GL_UNSIGNED_INT, 0)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glUseProgram(ribbonProgram)
        GLES30.glUniformMatrix4fv(uniform(ribbonProgram, "uMvp"), 1, false, mvp, 0)
        GLES30.glUniform2f(uniform(ribbonProgram, "uViewport"), viewportWidth.toFloat(), viewportHeight.toFloat())
        GLES30.glUniform1f(uniform(ribbonProgram, "uRelief"), relief)
        GLES30.glUniform1f(uniform(ribbonProgram, "uLift"), ReliefGeometry.SURFACE_LIFT)
        val density = viewportWidth / REFERENCE_WIDTH_PX

        if (haloIndexCount > 0) {
            GLES30.glBindVertexArray(haloVao)
            GLES30.glUniform1f(uniform(ribbonProgram, "uProgress"), 1f)
            // Deux passes, comme les halos 2D (drawHoldHalo) : un liseré sombre sous
            // la couleur, pour qu'un halo blanc reste lisible sur un mur blanc.
            ribbonPass(HALO_OUTLINE_PX * density, OUTLINE_RGBA, haloIndexCount)
            ribbonPass(HALO_PX * density, null, haloIndexCount)
        }
        if (arcIndexCount > 0 && progress > 0f) {
            GLES30.glBindVertexArray(arcVao)
            GLES30.glUniform1f(uniform(ribbonProgram, "uProgress"), progress)
            ribbonPass(ARC_OUTLINE_PX * density, OUTLINE_RGBA, arcIndexCount)
            ribbonPass(ARC_PX * density, null, arcIndexCount)
        }
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(0)
        frameStats.record(now, System.nanoTime() - now)?.let { Log.i(TAG, it) }
    }

    private fun ribbonPass(widthPx: Float, override: FloatArray?, count: Int) {
        GLES30.glUniform1f(uniform(ribbonProgram, "uWidth"), widthPx)
        val color = override ?: NO_OVERRIDE
        GLES30.glUniform4f(uniform(ribbonProgram, "uOverride"), color[0], color[1], color[2], color[3])
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, count, GLES30.GL_UNSIGNED_INT, 0)
    }

    /** Sans geste depuis un moment, la vue se balance doucement : le relief se lit au mouvement. */
    private fun idleSway(now: Long) {
        if (now - lastTouchNanos < IDLE_NANOS) return
        val t = (now - sceneStartNanos) / 1e9f
        val targetYaw = sin(t * 0.45f) * SWAY_YAW
        val targetPitch = DEFAULT_PITCH + sin(t * 0.31f) * SWAY_PITCH
        yaw += (targetYaw - yaw) * SWAY_EASING
        pitch += (targetPitch - pitch) * SWAY_EASING
    }

    private fun updateCamera(current: ReliefScene) {
        val viewAspect = viewportWidth.toFloat() / viewportHeight
        Matrix.perspectiveM(projection, 0, FOV_Y, viewAspect, 0.05f, 20f)
        // Recul qui fait tenir le mur entier, en largeur comme en hauteur.
        val halfHeight = max(current.aspect / 2f, 0.5f / viewAspect) * FRAME_MARGIN
        val distance = (halfHeight / tan(FOV_Y / 2f * PI.toFloat() / 180f) + ReliefGeometry.DEPTH_SCALE) / zoom
        val yawRad = yaw * PI.toFloat() / 180f
        val pitchRad = pitch * PI.toFloat() / 180f
        val targetZ = ReliefGeometry.DEPTH_SCALE * 0.4f
        Matrix.setLookAtM(
            view, 0,
            distance * sin(yawRad) * cos(pitchRad),
            distance * sin(pitchRad),
            targetZ + distance * cos(yawRad) * cos(pitchRad),
            0f, 0f, targetZ,
            0f, 1f, 0f,
        )
        Matrix.multiplyMM(mvp, 0, projection, 0, view, 0)
    }

    private fun upload(current: ReliefScene, bitmap: Bitmap) {
        if (meshVao != 0) {
            GLES30.glDeleteVertexArrays(3, intArrayOf(meshVao, haloVao, arcVao), 0)
            GLES30.glDeleteBuffers(buffers.size, buffers, 0)
            GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
        val vaos = IntArray(3)
        GLES30.glGenVertexArrays(3, vaos, 0)
        GLES30.glGenBuffers(buffers.size, buffers, 0)
        meshVao = vaos[0]
        haloVao = vaos[1]
        arcVao = vaos[2]

        GLES30.glBindVertexArray(meshVao)
        fill(buffers[0], buffers[1], current.meshVertices, current.meshIndices)
        attribute(0, 3, ReliefGeometry.MESH_STRIDE, 0)
        attribute(1, 2, ReliefGeometry.MESH_STRIDE, 3)
        attribute(2, 3, ReliefGeometry.MESH_STRIDE, 5)
        meshIndexCount = current.meshIndices.size

        haloIndexCount = ribbonVao(haloVao, buffers[2], buffers[3], current.halos)
        arcIndexCount = ribbonVao(arcVao, buffers[4], buffers[5], current.arcs)
        GLES30.glBindVertexArray(0)

        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
    }

    private fun ribbonVao(vao: Int, vertexBuffer: Int, indexBuffer: Int, ribbons: Ribbons): Int {
        if (ribbons.isEmpty) return 0
        GLES30.glBindVertexArray(vao)
        fill(vertexBuffer, indexBuffer, ribbons.vertices, ribbons.indices)
        val stride = ReliefGeometry.RIBBON_STRIDE
        attribute(0, 3, stride, 0)
        attribute(1, 3, stride, 3)
        attribute(2, 1, stride, 6)
        attribute(3, 1, stride, 7)
        attribute(4, 4, stride, 8)
        return ribbons.indices.size
    }

    private fun fill(vertexBuffer: Int, indexBuffer: Int, vertices: FloatArray, indices: IntArray) {
        val vertexData = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(vertices).apply { position(0) }
        val indexData = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder())
            .asIntBuffer().put(indices).apply { position(0) }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vertexBuffer)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vertexData, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, indexData, GLES30.GL_STATIC_DRAW)
    }

    private fun attribute(location: Int, size: Int, stride: Int, offset: Int) {
        GLES30.glEnableVertexAttribArray(location)
        GLES30.glVertexAttribPointer(location, size, GLES30.GL_FLOAT, false, stride * 4, offset * 4)
    }

    /** Emplacement d'uniforme, cherche une fois par programme puis relu : pas d'appel GL par trame. */
    private fun uniform(program: Int, name: String): Int {
        val cache = if (program == meshProgram) meshUniforms else ribbonUniforms
        return cache.getOrPut(name) { GLES30.glGetUniformLocation(program, name) }
    }

    private fun program(vertex: String, fragment: String): Int {
        val id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, shader(GLES30.GL_VERTEX_SHADER, vertex))
        GLES30.glAttachShader(id, shader(GLES30.GL_FRAGMENT_SHADER, fragment))
        GLES30.glLinkProgram(id)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        check(status[0] != 0) { "Edition de liens du shader : ${GLES30.glGetProgramInfoLog(id)}" }
        return id
    }

    private fun shader(type: Int, source: String): Int {
        val id = GLES30.glCreateShader(type)
        GLES30.glShaderSource(id, source)
        GLES30.glCompileShader(id)
        val status = IntArray(1)
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(id)
            Log.e(TAG, "Compilation du shader : $log")
            error("Compilation du shader : $log")
        }
        return id
    }

    private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

    private companion object {
        const val TAG = "TopoRelief"

        const val FOV_Y = 38f
        const val FRAME_MARGIN = 1.08f
        const val DEFAULT_PITCH = -6f
        const val MAX_YAW = 55f
        const val MAX_PITCH = 40f
        const val MIN_ZOOM = 0.8f
        const val MAX_ZOOM = 2.5f
        const val DEGREES_PER_PIXEL = 0.18f

        const val SWAY_YAW = 16f
        const val SWAY_PITCH = 5f
        const val SWAY_EASING = 0.025f
        const val IDLE_NANOS = 2_500_000_000L

        /** Le relief « pousse » depuis la photo plate, puis le beta se trace. */
        const val INTRO_NANOS = 1_100_000_000L
        const val NANOS_PER_MOVE = 650_000_000L
        const val MIN_REPLAY_NANOS = 1_500_000_000L

        /** Largeurs de trait donnees pour un ecran de 1080 pixels, mises a l'echelle. */
        const val REFERENCE_WIDTH_PX = 1080f
        const val HALO_PX = 7f
        const val HALO_OUTLINE_PX = 12f
        const val ARC_PX = 9f
        const val ARC_OUTLINE_PX = 14f

        val OUTLINE_RGBA = floatArrayOf(0f, 0f, 0f, 0.55f)
        val NO_OVERRIDE = floatArrayOf(0f, 0f, 0f, 0f)

        val MESH_VERTEX = """
            #version 300 es
            uniform mat4 uMvp;
            uniform float uRelief;
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec2 aUv;
            layout(location = 2) in vec3 aNormal;
            out vec2 vUv;
            out vec3 vNormal;
            void main() {
                vUv = aUv;
                // Pendant l'introduction, relief et eclairage montent ensemble depuis la photo plate.
                vNormal = normalize(mix(vec3(0.0, 0.0, 1.0), aNormal, uRelief));
                gl_Position = uMvp * vec4(aPosition.xy, aPosition.z * uRelief, 1.0);
            }
        """.trimIndent()

        val MESH_FRAGMENT = """
            #version 300 es
            precision mediump float;
            uniform sampler2D uTexture;
            in vec2 vUv;
            in vec3 vNormal;
            out vec4 fragColor;
            void main() {
                // Lumiere fixe au mur, venue d'en haut a gauche : face a l'objectif, le
                // facteur vaut 1 et la photo garde ses couleurs.
                vec3 light = normalize(vec3(-0.45, 0.55, 0.7));
                float diffuse = max(dot(normalize(vNormal), light), 0.0);
                float shade = 0.35 + 0.65 * diffuse / 0.7;
                fragColor = vec4(texture(uTexture, vUv).rgb * shade, 1.0);
            }
        """.trimIndent()

        val RIBBON_VERTEX = """
            #version 300 es
            uniform mat4 uMvp;
            uniform vec2 uViewport;
            uniform float uWidth;
            uniform float uRelief;
            uniform float uLift;
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aOther;
            layout(location = 2) in float aSide;
            layout(location = 3) in float aProgress;
            layout(location = 4) in vec4 aColor;
            out vec4 vColor;
            out float vProgress;
            vec4 project(vec3 p) {
                // Le decollement ne suit pas l'introduction : un halo ne passe jamais sous le mur.
                return uMvp * vec4(p.xy, (p.z - uLift) * uRelief + uLift, 1.0);
            }
            void main() {
                vec4 clip = project(aPosition);
                vec4 other = project(aOther);
                vec2 here = clip.xy / clip.w * uViewport * 0.5;
                vec2 there = other.xy / other.w * uViewport * 0.5;
                vec2 direction = there - here;
                float len = length(direction);
                direction = len > 1e-4 ? direction / len : vec2(1.0, 0.0);
                vec2 normal = vec2(-direction.y, direction.x);
                clip.xy += normal * aSide * uWidth * 0.5 / (uViewport * 0.5) * clip.w;
                gl_Position = clip;
                vColor = aColor;
                vProgress = aProgress;
            }
        """.trimIndent()

        val RIBBON_FRAGMENT = """
            #version 300 es
            precision mediump float;
            uniform float uProgress;
            uniform vec4 uOverride;
            in vec4 vColor;
            in float vProgress;
            out vec4 fragColor;
            void main() {
                if (vProgress > uProgress) discard;
                fragColor = uOverride.a > 0.0 ? vec4(uOverride.rgb, uOverride.a * vColor.a) : vColor;
            }
        """.trimIndent()
    }
}
