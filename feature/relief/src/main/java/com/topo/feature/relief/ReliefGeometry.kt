package com.topo.feature.relief

import com.topo.core.domain.model.BoundingBox
import com.topo.core.domain.model.Hold
import com.topo.core.domain.model.HoldKind
import com.topo.core.domain.model.Limb
import com.topo.core.domain.model.Movement
import com.topo.core.domain.model.ReliefMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Geometrie de la vue 3D, calculee une fois sur le CPU puis confiee telle quelle au GPU.
 *
 * Kotlin pur, sans OpenGL : elle se teste sur JVM, et le rendu n'a plus qu'a copier
 * des tableaux dans des tampons.
 *
 * Repere : le mur est centre a l'origine, large de 1, haut de [ReliefScene.aspect] ; x
 * vers la droite, y vers le HAUT (l'inverse de la photo), z vers l'objectif. Le relief
 * est RELATIF : [DEPTH_SCALE] en fixe l'amplitude visuelle, pas une mesure.
 */
internal class ReliefScene(
    /** Hauteur du mur rapportee a sa largeur. */
    val aspect: Float,
    /** Sommets du mur, [MESH_STRIDE] flottants chacun : position, uv, normale. */
    val meshVertices: FloatArray,
    val meshIndices: IntArray,
    /** Halos des prises, en rubans ([RIBBON_STRIDE] flottants par sommet). */
    val halos: Ribbons,
    /** Mouvements du beta, en arcs au-dessus du mur, dans l'ordre de la sequence. */
    val arcs: Ribbons,
    val holdCount: Int,
    val movementCount: Int,
)

/**
 * Lignes epaisses dessinees en triangles : chaque segment est un quadrilatere que le
 * shader elargit a l'ecran, d'une largeur en pixels constante quel que soit le zoom.
 * `glLineWidth` ne le permet pas : la plupart des GPU mobiles le bornent a 1 pixel.
 */
internal class Ribbons(val vertices: FloatArray, val indices: IntArray) {
    val isEmpty: Boolean get() = indices.isEmpty()
}

/** Couleur ARGB d'un membre ; injectee pour garder ce fichier hors de Compose. */
internal fun interface LimbPalette {
    fun argb(limb: Limb): Int
}

internal object ReliefGeometry {

    /** position (3), uv (2), normale (3). */
    const val MESH_STRIDE = 8

    /** position (3), autre extremite (3), cote (1), progression (1), couleur (4). */
    const val RIBBON_STRIDE = 12

    /** Amplitude du relief rapportee a la largeur du mur. */
    const val DEPTH_SCALE = 0.22f

    /** Au-dela, un maillage plus fin n'ajoute rien de visible et coute des sommets. */
    const val MAX_MESH_SIDE = 192

    /** Decollement des halos et des arcs au-dessus du mur, contre le z-fighting. */
    const val SURFACE_LIFT = 0.012f

    const val HALO_SEGMENTS = 40
    const val ARC_SEGMENTS = 28

    fun build(
        relief: ReliefMap,
        aspect: Float,
        zone: BoundingBox?,
        holds: List<Hold>,
        movements: List<Movement>,
        holdArgb: Int,
        volumeArgb: Int,
        palette: LimbPalette,
    ): ReliefScene {
        val frame = Frame.of(zone)
        val (vertices, indices) = mesh(relief, aspect)

        val placed = holds.mapNotNull { hold ->
            val u = frame.u(hold.box.centerX)
            val v = frame.v(hold.box.centerY)
            if (u !in 0f..1f || v !in 0f..1f) return@mapNotNull null
            hold.id to PlacedHold(hold, u, v, surface(relief, aspect, u, v))
        }.toMap()

        val halos = RibbonBuilder()
        for ((_, placedHold) in placed) {
            // Le rayon suit la prise, borne : une boite enorme (volume) ne doit pas
            // couvrir le mur, une minuscule rester visible.
            val boxSide = max(placedHold.hold.box.width / frame.width, placedHold.hold.box.height / frame.height)
            val radius = (boxSide * 0.5f).coerceIn(0.012f, 0.09f)
            val argb = if (placedHold.hold.kind == HoldKind.VOLUME) volumeArgb else holdArgb
            halos.ring(placedHold.position, radius, argb)
        }

        val sequence = movements.sortedBy(Movement::sequenceOrder)
        val arcs = RibbonBuilder()
        sequence.forEachIndexed { index, movement ->
            val source = movement.sourceHoldId?.let(placed::get) ?: return@forEachIndexed
            val target = placed[movement.targetHoldId] ?: return@forEachIndexed
            val start = index.toFloat() / sequence.size
            val end = (index + 1).toFloat() / sequence.size
            arcs.arc(source.position, target.position, start, end, palette.argb(movement.limb))
        }

        return ReliefScene(
            aspect = aspect,
            meshVertices = vertices,
            meshIndices = indices,
            halos = halos.build(),
            arcs = arcs.build(),
            holdCount = placed.size,
            movementCount = sequence.size,
        )
    }

    /** Point du mur a la coordonnee normalisee de la zone (u vers la droite, v vers le bas). */
    fun surface(relief: ReliefMap, aspect: Float, u: Float, v: Float): Vec3 =
        Vec3(u - 0.5f, (0.5f - v) * aspect, relief.sample(u, v) * DEPTH_SCALE)

    private fun mesh(relief: ReliefMap, aspect: Float): Pair<FloatArray, IntArray> {
        val step = max(1, (max(relief.width, relief.height) + MAX_MESH_SIDE - 1) / MAX_MESH_SIDE)
        val columns = max(2, relief.width / step)
        val rows = max(2, relief.height / step)
        val vertices = FloatArray(columns * rows * MESH_STRIDE)
        val cell = 1f / (columns - 1)

        for (row in 0 until rows) {
            val v = row.toFloat() / (rows - 1)
            for (column in 0 until columns) {
                val u = column.toFloat() / (columns - 1)
                val p = surface(relief, aspect, u, v)
                // Normale par differences centrees : le relief se lit a la lumiere
                // autant qu'a la rotation.
                val dzdx = (relief.sample(u + cell, v) - relief.sample(u - cell, v)) * DEPTH_SCALE / (2 * cell)
                val dzdy = (relief.sample(u, v - cell) - relief.sample(u, v + cell)) * DEPTH_SCALE / (2 * cell * aspect)
                val length = sqrt(dzdx * dzdx + dzdy * dzdy + 1f)
                val o = (row * columns + column) * MESH_STRIDE
                vertices[o] = p.x
                vertices[o + 1] = p.y
                vertices[o + 2] = p.z
                vertices[o + 3] = u
                vertices[o + 4] = v
                vertices[o + 5] = -dzdx / length
                vertices[o + 6] = -dzdy / length
                vertices[o + 7] = 1f / length
            }
        }

        val indices = IntArray((columns - 1) * (rows - 1) * 6)
        var i = 0
        for (row in 0 until rows - 1) {
            for (column in 0 until columns - 1) {
                val a = row * columns + column
                val b = a + 1
                val c = a + columns
                val d = c + 1
                indices[i++] = a; indices[i++] = c; indices[i++] = b
                indices[i++] = b; indices[i++] = c; indices[i++] = d
            }
        }
        return vertices to indices
    }

    private class PlacedHold(val hold: Hold, val u: Float, val v: Float, val position: Vec3)

    /** Zone rognee a la photo, comme celle qu'a lue l'estimateur de relief. */
    private class Frame(val left: Float, val top: Float, val width: Float, val height: Float) {
        fun u(x: Float) = (x - left) / width
        fun v(y: Float) = (y - top) / height

        companion object {
            fun of(zone: BoundingBox?): Frame {
                if (zone == null) return Frame(0f, 0f, 1f, 1f)
                val left = zone.left.coerceIn(0f, 1f)
                val top = zone.top.coerceIn(0f, 1f)
                return Frame(left, top, zone.right.coerceIn(0f, 1f) - left, zone.bottom.coerceIn(0f, 1f) - top)
            }
        }
    }
}

internal data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    fun distanceTo(o: Vec3): Float {
        val dx = x - o.x
        val dy = y - o.y
        val dz = z - o.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

private class RibbonBuilder {
    private val vertices = ArrayList<Float>()
    private val indices = ArrayList<Int>()
    private var count = 0

    /** Cercle dans le plan du mur, decolle de la surface. */
    fun ring(center: Vec3, radius: Float, argb: Int) {
        val lifted = center + Vec3(0f, 0f, ReliefGeometry.SURFACE_LIFT)
        val points = (0..ReliefGeometry.HALO_SEGMENTS).map { k ->
            val angle = 2 * PI.toFloat() * k / ReliefGeometry.HALO_SEGMENTS
            lifted + Vec3(cos(angle) * radius, sin(angle) * radius, 0f)
        }
        // Un halo n'attend pas le rejeu : il est la des le depart.
        polyline(points, List(points.size) { 0f }, argb)
    }

    /**
     * Arc de Bezier quadratique entre deux prises, bombe vers l'objectif d'autant
     * plus que le mouvement est long — un jete saute plus haut qu'un pas.
     */
    fun arc(from: Vec3, to: Vec3, start: Float, end: Float, argb: Int) {
        val lift = Vec3(0f, 0f, ReliefGeometry.SURFACE_LIFT)
        val a = from + lift
        val b = to + lift
        val control = (a + b) * 0.5f + Vec3(0f, 0f, 0.03f + 0.35f * a.distanceTo(b))
        val points = ArrayList<Vec3>()
        val progress = ArrayList<Float>()
        for (k in 0..ReliefGeometry.ARC_SEGMENTS) {
            val t = k.toFloat() / ReliefGeometry.ARC_SEGMENTS
            val s = 1 - t
            points += a * (s * s) + control * (2 * s * t) + b * (t * t)
            progress += start + (end - start) * t
        }
        polyline(points, progress, argb)
    }

    private fun polyline(points: List<Vec3>, progress: List<Float>, argb: Int) {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val alpha = (argb ushr 24) / 255f
        for (k in 0 until points.size - 1) {
            val p0 = points[k]
            val p1 = points[k + 1]
            // Chaque extremite connait l'autre : le shader en tire la direction du
            // segment a l'ecran. Vue depuis p1, la direction s'inverse, donc le cote
            // aussi, pour que les deux bords restent du meme cote.
            vertex(p0, p1, 1f, progress[k], r, g, b, alpha)
            vertex(p0, p1, -1f, progress[k], r, g, b, alpha)
            // Ordre du pourtour : p0 gauche, p0 droite, p1 droite, p1 gauche.
            vertex(p1, p0, 1f, progress[k + 1], r, g, b, alpha)
            vertex(p1, p0, -1f, progress[k + 1], r, g, b, alpha)
            val base = count - 4
            indices += listOf(base, base + 1, base + 2, base, base + 2, base + 3)
        }
    }

    private fun vertex(p: Vec3, other: Vec3, side: Float, t: Float, r: Float, g: Float, b: Float, a: Float) {
        vertices += listOf(p.x, p.y, p.z, other.x, other.y, other.z, side, t, r, g, b, a)
        count++
    }

    fun build() = Ribbons(vertices.toFloatArray(), indices.toIntArray())
}

/** Clampe un rapport d'aspect aberrant (photo vide, zone degeneree). */
internal fun safeAspect(width: Int, height: Int): Float =
    if (width <= 0 || height <= 0) 1f else min(4f, max(0.25f, height.toFloat() / width))
