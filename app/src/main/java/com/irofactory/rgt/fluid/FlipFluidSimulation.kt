package com.irofactory.rgt.fluid

import com.irofactory.rgt.glyph.GlyphFrames
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * FlipFluidSimulation
 * ───────────────────────────────────────────────────────────────────────────
 * Agua en la Glyph Matrix con FLIP (Fluid-Implicit Particle), el mismo
 * metodo de la fluid pendant de mitxela: un port del tutorial "How to write
 * a FLIP Water Simulator" de Matthias Muller (Ten Minute Physics), adaptado a
 * un recipiente circular del tamano de la matriz y a gravedad en 2D desde el
 * acelerometro.
 *
 * Cada paso: las particulas se mueven con la gravedad, se separan entre si
 * (sin eso el fluido colapsa), chocan con el borde circular, pasan su
 * velocidad a una rejilla MAC donde se fuerza la incompresibilidad (con
 * correccion de deriva por densidad) y la rejilla les regresa la velocidad
 * mezclando PIC y FLIP.
 *
 * Para la matriz, cada LED se enciende segun cuantas particulas caen en el:
 * el cuerpo del agua se ve solido y las gotas sueltas, tenues.
 *
 * Coordenadas en LEDs, (0,0) arriba a la izquierda, y crece hacia abajo.
 * Motor puro, sin dependencias de Android.
 */
class FlipFluidSimulation(private val fill: Float = 0.38f) {

    private companion object {
        const val SIZE = 25f             // lado del dominio en LEDs
        const val CELLS = 41             // celdas de la rejilla de presion por lado
        const val GRAVITY_SCALE = 20f    // m/s² del acelerometro → LEDs/s²
        const val FLIP_RATIO = 0.9f
        const val PRESSURE_ITERS = 40
        const val SEPARATION_ITERS = 2
        const val OVER_RELAXATION = 1.9f
        const val SUBSTEPS = 2

        const val FLUID = 0
        const val AIR = 1
        const val SOLID = 2
    }

    // ── Rejilla MAC (u en caras verticales, v en horizontales) ───────────────
    private val n = CELLS
    private val h = SIZE / n
    private val invH = 1f / h
    private val numCells = n * n
    private val u = FloatArray(numCells)
    private val v = FloatArray(numCells)
    private val du = FloatArray(numCells)
    private val dv = FloatArray(numCells)
    private val prevU = FloatArray(numCells)
    private val prevV = FloatArray(numCells)
    private val s = FloatArray(numCells)          // 1 = fluido posible, 0 = pared
    private val cellType = IntArray(numCells)
    private val cellDensity = FloatArray(numCells)
    private var restDensity = 0f

    // ── Recipiente circular ──────────────────────────────────────────────────
    private val center = SIZE / 2f
    private val radius = GlyphFrames.LED_RADIUS

    // ── Particulas y rejilla hash para separarlas ────────────────────────────
    private val r = 0.3f * h
    private val pInv = 1f / (2.2f * r)
    private val pNum = floor(SIZE * pInv).toInt() + 1
    private val numCellParticles = IntArray(pNum * pNum)
    private val firstCellParticle = IntArray(pNum * pNum + 1)
    private var cellParticleIds = IntArray(0)
    private var count = 0
    private var pos = FloatArray(0)
    private var vel = FloatArray(0)
    /** Particulas por LED en reposo: referencia para el brillo. */
    private val restPerLed = 1f / (2f * r * sqrt(3f) * r)

    @Volatile private var gx = 0f
    @Volatile private var gy = 9.81f * GRAVITY_SCALE
    // Acciones pedidas desde otro hilo (boton Glyph, touch): se aplican al inicio del paso
    @Volatile private var pendingSplash = false
    @Volatile private var pendingReset = false
    private val random = Random(7)

    init {
        for (i in 0 until n) for (j in 0 until n) {
            val cx = (i + 0.5f) * h
            val cy = (j + 0.5f) * h
            val inside = hypot(cx - center, cy - center) < radius
            val border = i == 0 || j == 0 || i == n - 1 || j == n - 1
            s[i * n + j] = if (inside && !border) 1f else 0f
        }
        spawn()
    }

    /** Aceleracion del telefono en m/s² (ejes del sensor ya mapeados a la matriz). */
    fun setGravity(x: Float, y: Float) {
        gx = x * GRAVITY_SCALE
        gy = y * GRAVITY_SCALE
    }

    /**
     * Reemplaza el agua por particulas quietas en [points] (x, y intercalados,
     * en LEDs desde la esquina). Llamar desde el mismo hilo que [step]. La
     * densidad de reposo queda la del panal de [spawn], no la de estos puntos:
     * si no, unos puntos ralos se tomarian como "agua en reposo".
     */
    fun pour(points: FloatArray) {
        count = points.size / 2
        pos = points.copyOf()
        vel = FloatArray(2 * count)
        cellParticleIds = IntArray(count)
        u.fill(0f); v.fill(0f)
        restDensity = restPerLed * h * h
    }

    /** Particulas por LED con el agua en reposo. */
    val particlesPerLed: Float get() = restPerLed

    fun splash() { pendingSplash = true }
    fun reset() { pendingReset = true }

    fun step(dt: Float) {
        if (pendingReset) { pendingReset = false; spawn() }
        if (pendingSplash) { pendingSplash = false; kick() }
        val sdt = dt / SUBSTEPS
        repeat(SUBSTEPS) {
            integrate(sdt)
            separate()
            collideWithBowl()
            transferToGrid()
            updateDensity()
            solveIncompressibility()
            transferToParticles()
        }
    }

    /** Brillo por LED (grilla 25x25, 0f..1f) segun las particulas que caen en cada uno. */
    fun rasterize(): Array<FloatArray> {
        val size = GlyphFrames.SIZE
        val counts = Array(size) { FloatArray(size) }
        for (i in 0 until count) {
            val col = pos[2 * i].toInt()
            val row = pos[2 * i + 1].toInt()
            if (row in 0 until size && col in 0 until size) counts[row][col] += 1f
        }
        for (row in 0 until size) for (col in 0 until size) {
            val t = ((counts[row][col] / restPerLed - 0.1f) / 0.35f).coerceIn(0f, 1f)
            counts[row][col] = t * t * (3f - 2f * t)
        }
        return counts
    }

    // ── Estado inicial: particulas en panal, del lado hacia donde apunta la gravedad ──
    private fun spawn() {
        val dx = 2f * r
        val dy = sqrt(3f) / 2f * dx
        val points = ArrayList<FloatArray>()
        var row = 0
        var y = center - radius + r
        while (y < center + radius) {
            var x = center - radius + r + if (row % 2 == 0) 0f else r
            while (x < center + radius) {
                if (hypot(x - center, y - center) < radius - r - 0.05f) points += floatArrayOf(x, y)
                x += dx
            }
            y += dy
            row++
        }
        val g = hypot(gx, gy)
        val nx = if (g > 1e-3f) gx / g else 0f
        val ny = if (g > 1e-3f) gy / g else 1f
        points.sortByDescending { it[0] * nx + it[1] * ny }

        count = (points.size * fill).toInt()
        pos = FloatArray(2 * count)
        vel = FloatArray(2 * count)
        cellParticleIds = IntArray(count)
        for (i in 0 until count) {
            pos[2 * i] = points[i][0]
            pos[2 * i + 1] = points[i][1]
        }
        u.fill(0f); v.fill(0f)
        restDensity = 0f
    }

    /** Agita el agua: impulso contra la gravedad con algo de azar. */
    private fun kick() {
        val g = hypot(gx, gy)
        val nx = if (g > 1e-3f) -gx / g else 0f
        val ny = if (g > 1e-3f) -gy / g else -1f
        for (i in 0 until count) {
            val strength = 8f + random.nextFloat() * 10f
            vel[2 * i] += nx * strength + (random.nextFloat() - 0.5f) * 8f
            vel[2 * i + 1] += ny * strength + (random.nextFloat() - 0.5f) * 8f
        }
    }

    private fun integrate(dt: Float) {
        val ax = gx
        val ay = gy
        for (i in 0 until count) {
            vel[2 * i] += dt * ax
            vel[2 * i + 1] += dt * ay
            pos[2 * i] += vel[2 * i] * dt
            pos[2 * i + 1] += vel[2 * i + 1] * dt
        }
    }

    private fun separate() {
        numCellParticles.fill(0)
        for (i in 0 until count) {
            val xi = (pos[2 * i] * pInv).toInt().coerceIn(0, pNum - 1)
            val yi = (pos[2 * i + 1] * pInv).toInt().coerceIn(0, pNum - 1)
            numCellParticles[xi * pNum + yi]++
        }
        var first = 0
        for (c in 0 until pNum * pNum) {
            first += numCellParticles[c]
            firstCellParticle[c] = first
        }
        firstCellParticle[pNum * pNum] = first
        for (i in 0 until count) {
            val xi = (pos[2 * i] * pInv).toInt().coerceIn(0, pNum - 1)
            val yi = (pos[2 * i + 1] * pInv).toInt().coerceIn(0, pNum - 1)
            val c = xi * pNum + yi
            firstCellParticle[c]--
            cellParticleIds[firstCellParticle[c]] = i
        }

        val minDist = 2f * r
        val minDist2 = minDist * minDist
        repeat(SEPARATION_ITERS) {
            for (i in 0 until count) {
                val px = pos[2 * i]
                val py = pos[2 * i + 1]
                val pxi = (px * pInv).toInt()
                val pyi = (py * pInv).toInt()
                for (xi in maxOf(pxi - 1, 0)..minOf(pxi + 1, pNum - 1)) {
                    for (yi in maxOf(pyi - 1, 0)..minOf(pyi + 1, pNum - 1)) {
                        val c = xi * pNum + yi
                        for (k in firstCellParticle[c] until firstCellParticle[c + 1]) {
                            val id = cellParticleIds[k]
                            if (id == i) continue
                            var dx = pos[2 * id] - px
                            var dy = pos[2 * id + 1] - py
                            val d2 = dx * dx + dy * dy
                            if (d2 > minDist2 || d2 == 0f) continue
                            val d = sqrt(d2)
                            val push = 0.5f * (minDist - d) / d
                            dx *= push
                            dy *= push
                            pos[2 * i] -= dx
                            pos[2 * i + 1] -= dy
                            pos[2 * id] += dx
                            pos[2 * id + 1] += dy
                        }
                    }
                }
            }
        }
    }

    private fun collideWithBowl() {
        val limit = radius - r
        for (i in 0 until count) {
            val dx = pos[2 * i] - center
            val dy = pos[2 * i + 1] - center
            val d = hypot(dx, dy)
            if (d <= limit || d < 1e-5f) continue
            val nx = dx / d
            val ny = dy / d
            pos[2 * i] = center + nx * limit
            pos[2 * i + 1] = center + ny * limit
            val vn = vel[2 * i] * nx + vel[2 * i + 1] * ny
            if (vn > 0f) {
                vel[2 * i] -= vn * nx
                vel[2 * i + 1] -= vn * ny
            }
        }
    }

    private fun transferToGrid() {
        prevU.copyFrom(u); prevV.copyFrom(v)
        du.fill(0f); dv.fill(0f); u.fill(0f); v.fill(0f)
        for (c in 0 until numCells) cellType[c] = if (s[c] == 0f) SOLID else AIR
        for (i in 0 until count) {
            val xi = (pos[2 * i] * invH).toInt().coerceIn(0, n - 1)
            val yi = (pos[2 * i + 1] * invH).toInt().coerceIn(0, n - 1)
            val c = xi * n + yi
            if (cellType[c] == AIR) cellType[c] = FLUID
        }
        splat(component = 0)
        splat(component = 1)
    }

    private fun transferToParticles() {
        gather(component = 0)
        gather(component = 1)
    }

    private inline fun forEachWeight(i: Int, component: Int, body: (nr0: Int, nr1: Int, nr2: Int, nr3: Int, d0: Float, d1: Float, d2: Float, d3: Float) -> Unit) {
        val half = 0.5f * h
        val dx = if (component == 0) 0f else half
        val dy = if (component == 0) half else 0f
        val x = pos[2 * i].coerceIn(h, (n - 1) * h)
        val y = pos[2 * i + 1].coerceIn(h, (n - 1) * h)
        val x0 = minOf(floor((x - dx) * invH).toInt(), n - 2)
        val tx = ((x - dx) - x0 * h) * invH
        val x1 = minOf(x0 + 1, n - 2)
        val y0 = minOf(floor((y - dy) * invH).toInt(), n - 2)
        val ty = ((y - dy) - y0 * h) * invH
        val y1 = minOf(y0 + 1, n - 2)
        val sx = 1f - tx
        val sy = 1f - ty
        body(x0 * n + y0, x1 * n + y0, x1 * n + y1, x0 * n + y1, sx * sy, tx * sy, tx * ty, sx * ty)
    }

    private fun splat(component: Int) {
        val f = if (component == 0) u else v
        val d = if (component == 0) du else dv
        for (i in 0 until count) {
            val pv = vel[2 * i + component]
            forEachWeight(i, component) { nr0, nr1, nr2, nr3, d0, d1, d2, d3 ->
                f[nr0] += pv * d0; d[nr0] += d0
                f[nr1] += pv * d1; d[nr1] += d1
                f[nr2] += pv * d2; d[nr2] += d2
                f[nr3] += pv * d3; d[nr3] += d3
            }
        }
        for (c in 0 until numCells) if (d[c] > 0f) f[c] /= d[c]
        // Las paredes conservan su velocidad anterior
        val prev = if (component == 0) prevU else prevV
        for (i in 0 until n) for (j in 0 until n) {
            val c = i * n + j
            val solid = cellType[c] == SOLID
            val neighborSolid = if (component == 0) i > 0 && cellType[c - n] == SOLID else j > 0 && cellType[c - 1] == SOLID
            if (solid || neighborSolid) f[c] = prev[c]
        }
    }

    private fun gather(component: Int) {
        val f = if (component == 0) u else v
        val prev = if (component == 0) prevU else prevV
        val offset = if (component == 0) n else 1
        for (i in 0 until count) {
            forEachWeight(i, component) { nr0, nr1, nr2, nr3, d0, d1, d2, d3 ->
                val w0 = if (valid(nr0, offset)) d0 else 0f
                val w1 = if (valid(nr1, offset)) d1 else 0f
                val w2 = if (valid(nr2, offset)) d2 else 0f
                val w3 = if (valid(nr3, offset)) d3 else 0f
                val total = w0 + w1 + w2 + w3
                if (total > 0f) {
                    val pic = (w0 * f[nr0] + w1 * f[nr1] + w2 * f[nr2] + w3 * f[nr3]) / total
                    val corr = (w0 * (f[nr0] - prev[nr0]) + w1 * (f[nr1] - prev[nr1]) +
                        w2 * (f[nr2] - prev[nr2]) + w3 * (f[nr3] - prev[nr3])) / total
                    val flip = vel[2 * i + component] + corr
                    vel[2 * i + component] = (1f - FLIP_RATIO) * pic + FLIP_RATIO * flip
                }
            }
        }
    }

    /** Una cara de la rejilla es valida si toca alguna celda que no sea aire. */
    private fun valid(c: Int, offset: Int): Boolean {
        val other = c - offset
        return cellType[c] != AIR || other < 0 || cellType[other] != AIR
    }

    private fun updateDensity() {
        cellDensity.fill(0f)
        val half = 0.5f * h
        for (i in 0 until count) {
            val x = pos[2 * i].coerceIn(h, (n - 1) * h)
            val y = pos[2 * i + 1].coerceIn(h, (n - 1) * h)
            val x0 = floor((x - half) * invH).toInt()
            val tx = ((x - half) - x0 * h) * invH
            val x1 = minOf(x0 + 1, n - 2)
            val y0 = floor((y - half) * invH).toInt()
            val ty = ((y - half) - y0 * h) * invH
            val y1 = minOf(y0 + 1, n - 2)
            val sx = 1f - tx
            val sy = 1f - ty
            cellDensity[x0 * n + y0] += sx * sy
            cellDensity[x1 * n + y0] += tx * sy
            cellDensity[x1 * n + y1] += tx * ty
            cellDensity[x0 * n + y1] += sx * ty
        }
        if (restDensity == 0f) {
            var sum = 0f
            var fluidCells = 0
            for (c in 0 until numCells) if (cellType[c] == FLUID) { sum += cellDensity[c]; fluidCells++ }
            if (fluidCells > 0) restDensity = sum / fluidCells
        }
    }

    private fun solveIncompressibility() {
        prevU.copyFrom(u); prevV.copyFrom(v)
        repeat(PRESSURE_ITERS) {
            for (i in 1 until n - 1) {
                for (j in 1 until n - 1) {
                    val c = i * n + j
                    if (cellType[c] != FLUID) continue
                    val left = c - n
                    val right = c + n
                    val bottom = c - 1
                    val top = c + 1
                    val sx0 = s[left]
                    val sx1 = s[right]
                    val sy0 = s[bottom]
                    val sy1 = s[top]
                    val sum = sx0 + sx1 + sy0 + sy1
                    if (sum == 0f) continue
                    var div = u[right] - u[c] + v[top] - v[c]
                    // Correccion de deriva: empuja hacia afuera donde se amontonan particulas
                    if (restDensity > 0f) {
                        val compression = cellDensity[c] - restDensity
                        if (compression > 0f) div -= compression
                    }
                    val p = -div / sum * OVER_RELAXATION
                    u[c] -= sx0 * p
                    u[right] += sx1 * p
                    v[c] -= sy0 * p
                    v[top] += sy1 * p
                }
            }
        }
    }

    private fun FloatArray.copyFrom(other: FloatArray) = other.copyInto(this)
}
