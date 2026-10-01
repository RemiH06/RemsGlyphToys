package com.irofactory.rgt.audio

import com.irofactory.rgt.glyph.GlyphFrames
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/**
 * SolidPieces
 * ───────────────────────────────────────────────────────────────────────────
 * Reposo "piezas" de pulse: hexagono, diamante y triangulo como poligonos
 * regulares solidos dentro del disco de la matriz, que caen hacia donde
 * apunta la gravedad y chocan entre si.
 *
 * Celdas con el centro de la matriz en 0 y la y hacia abajo. Contra la pared
 * choca cada vertice, con impulso y friccion, asi las piezas ruedan y se
 * asientan sobre un lado. Entre piezas se usa un circulo por pieza (a medio
 * camino entre su radio interior y el exterior): aproximado, pero estable.
 * Como un poligono apoyado en un circulo podria girar sin nada que lo frene,
 * el contacto entre piezas lleva friccion con los giros de ambas y frena el
 * giro mientras se tocan.
 * Motor puro, sin dependencias de Android.
 */
internal class SolidPieces {

    private companion object {
        const val PI_F = PI.toFloat()
        // Hasta donde llega un vertice: el borde de la matriz menos medio grosor de la linea
        const val WALL = GlyphFrames.LED_RADIUS - 0.6f
        // m/s² del acelerometro → celdas/s²
        const val GRAVITY_SCALE = 8f
        const val RESTITUTION = 0.25f
        const val FRICTION = 0.4f
        const val SUBSTEPS = 4
        const val THICKNESS = 0.9f
        // Freno del giro (1/s) mientras dos piezas se tocan
        const val CONTACT_SPIN_DAMPING = 6f
    }

    private class Body(var sides: Int, val radius: Float, val startX: Float, val startY: Float) {
        var x = startX
        var y = startY
        var angle = 0f
        var vx = 0f
        var vy = 0f
        var spin = 0f
        val mass = radius * radius
        val inertia = 0.4f * mass * radius * radius
        val reach get() = radius * (1f + cos(PI_F / sides)) / 2f
    }

    private val bodies = arrayOf(
        Body(sides = 6, radius = 5.5f, startX = 0f, startY = 2.5f),
        Body(sides = 4, radius = 4.5f, startX = -3.5f, startY = -4f),
        Body(sides = 3, radius = 4.2f, startX = 3.5f, startY = -4f)
    )

    /**
     * De vuelta a su lugar de salida, quietas, con los angulos y lados dados
     * (graves, voces, agudos): cada pieza es la figura que se eligio.
     */
    fun reset(angles: FloatArray, sides: IntArray) {
        for ((i, b) in bodies.withIndex()) {
            b.x = b.startX; b.y = b.startY
            b.vx = 0f; b.vy = 0f; b.spin = 0f
            b.angle = angles[i]
            b.sides = sides[i]
        }
    }

    /** [gx], [gy]: aceleracion en m/s², ya en ejes de la matriz (y hacia abajo). */
    fun step(dt: Float, gx: Float, gy: Float) {
        val h = dt / SUBSTEPS
        repeat(SUBSTEPS) {
            for (b in bodies) {
                b.vx += gx * GRAVITY_SCALE * h
                b.vy += gy * GRAVITY_SCALE * h
                val drag = exp(-0.3f * h)
                b.vx *= drag; b.vy *= drag
                b.spin *= exp(-0.6f * h)
                b.x += b.vx * h
                b.y += b.vy * h
                b.angle += b.spin * h
            }
            for (i in bodies.indices) for (j in i + 1 until bodies.size) collide(bodies[i], bodies[j], h)
            for (b in bodies) hitWall(b)
        }
    }

    /** Estado de cada pieza (hexagono, diamante, triangulo): x, y, angulo, radio y lados. */
    fun snapshot(): Array<FloatArray> =
        Array(bodies.size) { i -> bodies[i].let { floatArrayOf(it.x, it.y, it.angle, it.radius, it.sides.toFloat()) } }

    /** Brillo 0..1 de las tres siluetas en (x, y), mezcladas tipo "screen". */
    fun brightness(x: Float, y: Float): Float {
        var dark = 1f
        for (b in bodies) dark *= 1f - (1f - outlineDistance(b, x, y) / THICKNESS).coerceIn(0f, 1f)
        return 1f - dark
    }

    private fun hitWall(b: Body) {
        for (k in 0 until b.sides) {
            val a = b.angle + 2f * PI_F * k / b.sides
            val rx = b.radius * cos(a)
            val ry = b.radius * sin(a)
            val px = b.x + rx
            val py = b.y + ry
            val d = hypot(px, py)
            if (d <= WALL) continue
            val nx = px / d
            val ny = py / d
            b.x -= nx * (d - WALL)
            b.y -= ny * (d - WALL)

            // Velocidad del vertice: la del centro mas el giro
            val vn = (b.vx - b.spin * ry) * nx + (b.vy + b.spin * rx) * ny
            if (vn <= 0f) continue
            val rn = rx * ny - ry * nx
            val jn = (1f + RESTITUTION) * vn / (1f / b.mass + rn * rn / b.inertia)
            b.vx -= jn * nx / b.mass
            b.vy -= jn * ny / b.mass
            b.spin -= jn * rn / b.inertia

            // Friccion a lo largo de la pared: es lo que las hace rodar
            val tx = -ny
            val ty = nx
            val vt = (b.vx - b.spin * ry) * tx + (b.vy + b.spin * rx) * ty
            val rt = rx * ty - ry * tx
            val jt = (vt / (1f / b.mass + rt * rt / b.inertia)).coerceIn(-FRICTION * jn, FRICTION * jn)
            b.vx -= jt * tx / b.mass
            b.vy -= jt * ty / b.mass
            b.spin -= jt * rt / b.inertia
        }
    }

    private fun collide(a: Body, b: Body, h: Float) {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val d = hypot(dx, dy)
        val reach = a.reach + b.reach
        if (d >= reach || d < 1e-4f) return
        val nx = dx / d
        val ny = dy / d
        val ia = 1f / a.mass
        val ib = 1f / b.mass
        val overlap = reach - d
        a.x -= nx * overlap * ia / (ia + ib)
        a.y -= ny * overlap * ia / (ia + ib)
        b.x += nx * overlap * ib / (ia + ib)
        b.y += ny * overlap * ib / (ia + ib)
        val vRel = (b.vx - a.vx) * nx + (b.vy - a.vy) * ny
        val jn = if (vRel < 0f) -(1f + RESTITUTION) * vRel / (ia + ib) else 0f
        a.vx -= jn * nx * ia; a.vy -= jn * ny * ia
        b.vx += jn * nx * ib; b.vy += jn * ny * ib

        // Friccion en el punto de contacto, con el giro de las dos. En reposo no
        // hay impulso normal: el limite usa el del peso en este subpaso
        val tx = -ny
        val ty = nx
        val ra = a.reach
        val rb = -b.reach
        val vt = (b.vx - a.vx) * tx + (b.vy - a.vy) * ty + b.spin * rb - a.spin * ra
        val k = ia + ib + ra * ra / a.inertia + rb * rb / b.inertia
        val reducedMass = 1f / (ia + ib)
        val limit = FRICTION * maxOf(jn, reducedMass * 9.81f * GRAVITY_SCALE * h)
        val jt = (vt / k).coerceIn(-limit, limit)
        a.vx += jt * tx * ia; a.vy += jt * ty * ia
        b.vx -= jt * tx * ib; b.vy -= jt * ty * ib
        a.spin += jt * ra / a.inertia
        b.spin -= jt * rb / b.inertia

        val brake = exp(-CONTACT_SPIN_DAMPING * h)
        a.spin *= brake
        b.spin *= brake
    }

    /** Distancia de (x, y) a la silueta del poligono. */
    private fun outlineDistance(b: Body, x: Float, y: Float): Float {
        if (abs(x - b.x) > b.radius + THICKNESS || abs(y - b.y) > b.radius + THICKNESS) return Float.MAX_VALUE
        var best = Float.MAX_VALUE
        for (k in 0 until b.sides) {
            val a0 = b.angle + 2f * PI_F * k / b.sides
            val a1 = b.angle + 2f * PI_F * (k + 1) / b.sides
            val ax = b.x + b.radius * cos(a0); val ay = b.y + b.radius * sin(a0)
            val ex = b.x + b.radius * cos(a1) - ax; val ey = b.y + b.radius * sin(a1) - ay
            val t = (((x - ax) * ex + (y - ay) * ey) / (ex * ex + ey * ey)).coerceIn(0f, 1f)
            best = minOf(best, hypot(x - ax - t * ex, y - ay - t * ey))
        }
        return best
    }
}
