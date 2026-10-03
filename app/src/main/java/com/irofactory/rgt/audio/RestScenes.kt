package com.irofactory.rgt.audio

import com.irofactory.rgt.glyph.GlyphFrames
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * RestScenes
 * ───────────────────────────────────────────────────────────────────────────
 * Escenas de reposo de pulse que no salen de las figuras: cada una dibuja su
 * propia grilla 25x25 (brillo 0..1) y [AudioBlobSimulation] la mezcla con las
 * figuras segun el volumen. Celdas con el centro de la matriz en 0 y la y
 * hacia abajo. Motor puro, sin dependencias de Android.
 *
 *   - medusa: nada a pulsos como una medusa real. La campana se contrae rapido
 *     (empuja hacia arriba) y se relaja lento mientras se hunde poco a poco;
 *     es translucida (mas brillante en el borde) y se le ven las gonadas. Los
 *     tentaculos son cadenas con inercia que cuelgan del margen y se quedan
 *     atras al subir; los brazos orales, mas cortos y ondulados, al centro.
 *   - pez: visto de lado, nada hacia puntos al azar. La aleta de la cola late
 *     mas rapido entre mas rapido nada y se acorta al barrer de lado a lado
 *     (escorzo); al dar la vuelta el cuerpo se adelgaza hasta ponerse de
 *     frente y reaparece del otro lado. Se inclina al subir o bajar.
 *   - burbujas: suben sin parar en contra de la gravedad (acelerometro): al
 *     girar el telefono cada una cambia de rumbo desde donde esta. Las grandes
 *     suben mas rapido y se bambolean mas; crecen un poco al subir (menos
 *     presion) y revientan al llegar arriba. Dos chorritos de burbujas chicas
 *     salen del fondo, como en una pecera.
 */
internal class RestScenes {

    private companion object {
        const val PI_F = PI.toFloat()
        const val TWO_PI = 2f * PI_F

        // Medusa: periodo del pulso (s), campana relajada (radio y alto), empuje
        // durante la contraccion, hundimiento, altura media y tentaculos
        const val JELLY_PERIOD = 2.6f
        const val JELLY_CONTRACT = 0.22f
        const val JELLY_RADIUS = 5.6f
        const val JELLY_HEIGHT = 4.4f
        const val JELLY_THRUST = 11f
        const val JELLY_SINK = 2.2f
        const val JELLY_REST_Y = -2.5f
        const val TENTACLES = 9
        const val TENTACLE_NODES = 12
        const val TENTACLE_SEGMENT = 0.8f

        // Pez: largo del cuerpo sin la cola, medio alto maximo, rapidez y
        // hasta donde puede ir su centro
        const val FISH_LENGTH = 11f
        const val FISH_HEIGHT = 2.4f
        const val FISH_SPEED = 3.2f
        const val FISH_ROAM_X = 5f
        const val FISH_ROAM_Y = 3.5f

        // Burbujas: cuantas grandes a la vez, cada cuanto sale una chica de
        // cada chorrito (s) y cuanto dura un reventon
        const val BIG_BUBBLES = 7
        const val STREAM_INTERVAL = 0.45f
        const val POP_TIME = 0.25f
    }

    private val n = GlyphFrames.SIZE
    private val grid = Array(n) { FloatArray(n) }
    private val random = Random(17)
    private var time = 0f

    /** [gx], [gy]: gravedad en m/s², ejes de la matriz (y hacia abajo); la usan las burbujas. */
    fun step(dt: Float, pose: RestPose, gx: Float = 0f, gy: Float = 9.81f) {
        time += dt
        when (pose) {
            RestPose.JELLYFISH -> stepJelly(dt)
            RestPose.FISH -> stepFish(dt)
            RestPose.BUBBLES -> stepBubbles(dt, gx, gy)
            else -> {}
        }
    }

    fun render(pose: RestPose): Array<FloatArray> {
        for (row in grid) row.fill(0f)
        when (pose) {
            RestPose.JELLYFISH -> drawJelly()
            RestPose.FISH -> drawFish()
            RestPose.BUBBLES -> drawBubbles()
            else -> {}
        }
        return grid
    }

    // ── Medusa ──────────────────────────────────────────────────────────────

    private var jx = 0f
    private var jy = JELLY_REST_Y
    private var jvx = 0f
    private var jvy = 0f
    private var jellyPhase = 0f
    private var jellyReady = false
    private val tentX = Array(TENTACLES) { FloatArray(TENTACLE_NODES) }
    private val tentY = Array(TENTACLES) { FloatArray(TENTACLE_NODES) }
    private val tentPrevX = Array(TENTACLES) { FloatArray(TENTACLE_NODES) }
    private val tentPrevY = Array(TENTACLES) { FloatArray(TENTACLE_NODES) }

    /** 0 relajada .. 1 contraida: se cierra rapido y se abre lento, con una pausa al final. */
    private fun contraction(): Float =
        if (jellyPhase < JELLY_CONTRACT) smooth(0f, JELLY_CONTRACT, jellyPhase)
        else 1f - smooth(JELLY_CONTRACT, 0.75f, jellyPhase)

    private val bellRadius get() = JELLY_RADIUS * (1f - 0.22f * contraction())
    private val bellHeight get() = JELLY_HEIGHT * (1f + 0.15f * contraction())

    private fun tentacleRoot(i: Int): Pair<Float, Float> =
        jx + bellRadius * (-0.85f + 1.7f * i / (TENTACLES - 1)) to jy + 0.4f

    private fun initJelly() {
        for (i in 0 until TENTACLES) {
            val (rx, ry) = tentacleRoot(i)
            for (j in 0 until TENTACLE_NODES) {
                tentX[i][j] = rx; tentY[i][j] = ry + j * TENTACLE_SEGMENT
                tentPrevX[i][j] = tentX[i][j]; tentPrevY[i][j] = tentY[i][j]
            }
        }
        jellyReady = true
    }

    private fun stepJelly(dt: Float) {
        if (!jellyReady) initJelly()
        jellyPhase = (jellyPhase + dt / JELLY_PERIOD) % 1f
        // Empuje solo al contraerse; entre pulsos se hunde. Un resorte suave la
        // mantiene a la vista y una corriente lenta la lleva de lado a lado
        var ay = JELLY_SINK + 0.35f * (JELLY_REST_Y - jy)
        if (jellyPhase < JELLY_CONTRACT) ay -= JELLY_THRUST
        val targetX = 3f * sin(time * 0.11f) + 1.2f * sin(time * 0.29f)
        jvx += 0.4f * (targetX - jx) * dt
        jvy += ay * dt
        val drag = exp(-2.2f * dt)
        jvx *= drag; jvy *= drag
        jx += jvx * dt; jy += jvy * dt

        // Tentaculos: verlet con arrastre del agua; cada nodo sigue al anterior
        val dt2 = dt * dt
        for (i in 0 until TENTACLES) {
            val x = tentX[i]; val y = tentY[i]; val px = tentPrevX[i]; val py = tentPrevY[i]
            val (rx, ry) = tentacleRoot(i)
            x[0] = rx; y[0] = ry
            for (j in 1 until TENTACLE_NODES) {
                val vx = (x[j] - px[j]) * 0.9f
                val vy = (y[j] - py[j]) * 0.9f
                px[j] = x[j]; py[j] = y[j]
                x[j] += vx + 0.8f * sin(time * 0.6f + j * 0.35f + i) * dt2
                y[j] += vy + 3f * dt2
            }
            for (j in 1 until TENTACLE_NODES) {
                val dx = x[j] - x[j - 1]; val dy = y[j] - y[j - 1]
                val d = hypot(dx, dy).coerceAtLeast(1e-4f)
                x[j] = x[j - 1] + dx / d * TENTACLE_SEGMENT
                y[j] = y[j - 1] + dy / d * TENTACLE_SEGMENT
            }
        }
    }

    private fun drawJelly() {
        if (!jellyReady) initJelly()
        val rx = bellRadius
        val ry = bellHeight

        // Tentaculos: delgados, mas tenues hacia la punta
        for (i in 0 until TENTACLES) {
            for (j in 1 until TENTACLE_NODES) {
                val level = 0.55f * (1f - 0.6f * j / TENTACLE_NODES)
                line(tentX[i][j - 1], tentY[i][j - 1], tentX[i][j], tentY[i][j], 0.6f, level)
            }
        }
        // Brazos orales: cortos, gruesos y ondulados; se quedan atras si nada de lado
        for (k in 0 until 4) {
            val baseX = jx + rx * 0.22f * (-1.5f + k)
            var lastX = baseX
            var lastY = jy + 0.3f
            for (j in 1..10) {
                val s = j / 10f
                val x = baseX + 0.8f * s * sin(6f * s - time * 1.3f + k * 1.7f) - jvx * 0.3f * s
                val y = jy + 0.3f + s * 5f
                val frill = 0.75f + 0.25f * sin(s * 14f + k)
                line(lastX, lastY, x, y, 0.85f, 0.5f * frill)
                lastX = x; lastY = y
            }
        }
        // Campana translucida: relleno tenue que se aclara hacia el borde
        cells(jx - rx - 1f, jy - ry - 1f, jx + rx + 1f, jy + 0.5f) { col, row, x, y ->
            if (y > jy) return@cells
            val ex = (x - jx) / rx
            val ey = (y - jy) / ry
            val r2 = ex * ex + ey * ey
            if (r2 < 1f) put(col, row, 0.14f + 0.22f * r2)
        }
        // Gonadas: cuatro herraduras tenues dentro de la campana
        for (k in 0 until 4) {
            val gx = jx + rx * (-0.42f + 0.28f * k)
            val gy = jy - ry * (if (k == 0 || k == 3) 0.32f else 0.48f)
            ring(gx, gy, 0.7f, 0.45f, 0.5f)
        }
        // Contorno de la campana y el margen
        var lastX = jx + rx
        var lastY = jy
        for (i in 1..24) {
            val a = PI_F * i / 24f
            val x = jx + rx * cos(a)
            val y = jy - ry * sin(a)
            line(lastX, lastY, x, y, 0.7f, 0.9f)
            lastX = x; lastY = y
        }
        line(jx - rx, jy, jx + rx, jy, 0.55f, 0.55f)
    }

    // ── Pez ─────────────────────────────────────────────────────────────────

    private var fx = 0f
    private var fy = 0f
    private var fvx = FISH_SPEED
    private var fvy = 0f
    private var targetX = FISH_ROAM_X
    private var targetY = 0f
    private var retargetIn = 0f
    private var facing = 1f
    private var facingTarget = 1f
    private var tailPhase = 0f

    private fun stepFish(dt: Float) {
        retargetIn -= dt
        if (retargetIn <= 0f || hypot(targetX - fx, targetY - fy) < 1.5f) {
            // Dos de cada tres veces al otro lado (da la vuelta); si no, sigue de su lado
            val opposite = if (fx > 0f) -1f else 1f
            val side = if (random.nextFloat() < 0.65f) opposite else -opposite
            targetX = side * FISH_ROAM_X * (0.4f + 0.6f * random.nextFloat())
            targetY = FISH_ROAM_Y * (2f * random.nextFloat() - 1f)
            retargetIn = 3f + 3f * random.nextFloat()
        }
        val dx = targetX - fx
        val dy = targetY - fy
        val dist = hypot(dx, dy).coerceAtLeast(1e-3f)
        val speed = FISH_SPEED * min(1f, dist / 3f + 0.35f)
        val follow = 1f - exp(-dt / 0.6f)
        fvx += (dx / dist * speed - fvx) * follow
        fvy += (dy / dist * speed - fvy) * follow
        fx += fvx * dt
        fy += fvy * dt

        // La cola late mas rapido entre mas rapido nada
        tailPhase += dt * (4f + 2.5f * hypot(fvx, fvy))
        if (abs(fvx) > 0.35f) facingTarget = if (fvx > 0f) 1f else -1f
        facing += (facingTarget - facing) * (1f - exp(-dt / 0.3f))
    }

    /** Medio alto del cuerpo: [s] 0 en la nariz .. 1 en la base de la cola. */
    private fun bodyHalf(s: Float): Float {
        val profile = s.coerceIn(0f, 1f).pow(0.5f) * (1f - s.coerceIn(0f, 1f)).pow(0.9f) / 0.4016f
        return FISH_HEIGHT * profile + 0.35f * smooth(0.6f, 1f, s)
    }

    private fun drawFish() {
        val sign = if (facing >= 0f) 1f else -1f
        val scale = sign * max(abs(facing), 0.2f)
        // Se inclina hacia donde nada: nariz arriba al subir
        val pitch = (atan2(fvy, abs(fvx) + 0.6f) * 0.6f).coerceIn(-0.45f, 0.45f) * sign
        val c = cos(pitch)
        val s = sin(pitch)
        val reach = FISH_LENGTH / 2f + 4f
        cells(fx - reach, fy - reach, fx + reach, fy + reach) { col, row, x, y ->
            val dx = x - fx
            val dy = y - fy
            val u = (c * dx + s * dy) / scale
            val v = -s * dx + c * dy
            val value = fishShade(u, v)
            if (value > 0f) put(col, row, value)
        }
    }

    /** Brillo del pez en sus coordenadas propias: [u] a lo largo (nariz en +), [v] hacia abajo. */
    private fun fishShade(u: Float, v: Float): Float {
        val half = FISH_LENGTH / 2f
        val s = (half - u) / FISH_LENGTH
        var value = 0f

        // Cuerpo: relleno tenue (el lomo un poco mas claro) y contorno
        if (s in 0f..1f) {
            val h = bodyHalf(s)
            val d = abs(v) - h
            if (d < 0f) value = max(value, if (v < 0f) 0.32f else 0.24f)
            value = max(value, (1f - abs(d) / 0.65f) * 0.9f)
            // Linea lateral
            if (s in 0.3f..0.92f) value = max(value, (1f - abs(v) / 0.35f) * 0.42f)
            // Opérculo: arco detras del ojo
            val hg = bodyHalf(0.27f)
            if (abs(v) < 0.75f * hg) {
                val ug = half - 0.27f * FISH_LENGTH - 0.45f * (v / hg) * (v / hg)
                value = max(value, (1f - abs(u - ug) / 0.4f) * 0.5f)
            }
        } else if (s < 0f) {
            value = max(value, (1f - hypot(u - half, v) / 0.65f) * 0.9f)
        }

        // Ojo
        val eyeU = half - 0.13f * FISH_LENGTH
        val eyeV = -0.28f * FISH_HEIGHT
        value = max(value, (0.55f + 0.5f - hypot(u - eyeU, v - eyeV)).coerceIn(0f, 1f))

        // Aletas translucidas: cola (bifurcada, se acorta al barrer), dorsal, anal y pectoral
        val ub = -half
        val k = 0.5f + 0.5f * abs(cos(tailPhase))
        val flick = 0.35f * sin(tailPhase)
        value = max(value, polygonShade(u, v, floatArrayOf(
            ub + 0.3f, -0.45f,
            ub - 3f * k, -2.1f + flick,
            ub - 1.6f * k, 0.5f * flick,
            ub - 3f * k, 2.1f + flick,
            ub + 0.3f, 0.45f
        )))
        val u28 = half - 0.28f * FISH_LENGTH
        val u46 = half - 0.48f * FISH_LENGTH
        val u60 = half - 0.6f * FISH_LENGTH
        value = max(value, polygonShade(u, v, floatArrayOf(
            u28, -bodyHalf(0.28f) + 0.2f,
            u46, -bodyHalf(0.46f) - 1.4f,
            u60, -bodyHalf(0.6f) + 0.15f
        )))
        val u62 = half - 0.62f * FISH_LENGTH
        val u74 = half - 0.76f * FISH_LENGTH
        val u80 = half - 0.8f * FISH_LENGTH
        value = max(value, polygonShade(u, v, floatArrayOf(
            u62, bodyHalf(0.62f) - 0.15f,
            u74, bodyHalf(0.74f) + 0.9f,
            u80, bodyHalf(0.8f) - 0.1f
        )))
        val pecU = half - 0.3f * FISH_LENGTH
        val flap = 0.5f + 0.3f * sin(tailPhase * 0.8f)
        value = max(value, (1f - segmentDistance(u, v, pecU, 0.6f,
            pecU - 1.6f * cos(flap), 0.6f + 1.6f * sin(flap)) / 0.5f) * 0.6f)
        return value
    }

    /** Aleta: relleno tenue dentro del poligono y borde mas claro. */
    private fun polygonShade(u: Float, v: Float, pts: FloatArray): Float {
        val count = pts.size / 2
        var inside = false
        var best = Float.MAX_VALUE
        var j = count - 1
        for (i in 0 until count) {
            val ax = pts[2 * j]; val ay = pts[2 * j + 1]
            val bx = pts[2 * i]; val by = pts[2 * i + 1]
            if ((ay > v) != (by > v) && u < ax + (v - ay) / (by - ay) * (bx - ax)) inside = !inside
            best = min(best, segmentDistance(u, v, ax, ay, bx, by))
            j = i
        }
        val edge = (1f - best / 0.55f).coerceIn(0f, 1f) * 0.7f
        return if (inside) max(0.18f, edge) else edge
    }

    // ── Burbujas ────────────────────────────────────────────────────────────

    /** Burbuja: centro sin bamboleo ([bx], [by]); el bamboleo va de lado respecto a "arriba". */
    private class Bubble(var bx: Float, var by: Float, var r: Float, val speed: Float,
                         var phase: Float, val amp: Float, val freq: Float, val big: Boolean)

    private val bubbles = ArrayList<Bubble>()
    private val pops = ArrayList<FloatArray>()   // x, y, radio, edad
    private val streamOffset = floatArrayOf(-4.5f, 5f)
    private val streamTimer = floatArrayOf(0f, 0.2f)
    private var bubblesReady = false
    // "Arriba" (contra la gravedad), suavizado para que un temblor no las sacuda
    private var upX = 0f
    private var upY = -1f

    private fun bubbleX(b: Bubble) = b.bx - upY * b.amp * sin(b.phase)
    private fun bubbleY(b: Bubble) = b.by + upX * b.amp * sin(b.phase)

    /** Nace abajo (del lado contrario a "arriba"), a [depth] celdas del centro y [side] de lado. */
    private fun bubbleAt(depth: Float, side: Float, r: Float, speed: Float, amp: Float, freq: Float, big: Boolean) =
        Bubble(-upX * depth - upY * side, -upY * depth + upX * side, r, speed,
            TWO_PI * random.nextFloat(), amp, freq, big)

    private fun bigBubble(depth: Float): Bubble {
        val r = 0.9f + 1.4f * random.nextFloat()
        return bubbleAt(depth, -8f + 16f * random.nextFloat(), r, 1.6f + 1.6f * r,
            0.2f + 0.25f * r, 2.2f + random.nextFloat(), big = true)
    }

    private fun initBubbles() {
        repeat(BIG_BUBBLES) { bubbles += bigBubble(-12f + 25f * random.nextFloat()) }
        bubblesReady = true
    }

    private fun stepBubbles(dt: Float, gx: Float, gy: Float) {
        val g = hypot(gx, gy)
        if (g > 0.5f) {
            val follow = 1f - exp(-dt / 0.15f)
            upX += (-gx / g - upX) * follow
            upY += (-gy / g - upY) * follow
            val len = hypot(upX, upY).coerceAtLeast(1e-3f)
            upX /= len; upY /= len
        }
        if (!bubblesReady) initBubbles()
        for (k in streamOffset.indices) {
            streamTimer[k] -= dt
            if (streamTimer[k] <= 0f) {
                streamTimer[k] = STREAM_INTERVAL * (0.6f + 0.8f * random.nextFloat())
                bubbles += bubbleAt(13f, streamOffset[k] + 0.6f * (random.nextFloat() - 0.5f),
                    0.35f + 0.25f * random.nextFloat(), 2.2f + 0.6f * random.nextFloat(), 0.2f, 6f, big = false)
            }
        }
        val iterator = bubbles.listIterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            b.phase += b.freq * dt
            b.bx += upX * b.speed * dt
            b.by += upY * b.speed * dt
            b.r *= 1f + 0.015f * dt
            // Revienta al salir por el lado de arriba; las grandes vuelven a salir abajo.
            // Si el telefono gira, las que quedaron lejos y yendose se quitan sin reventar
            val x = bubbleX(b)
            val y = bubbleY(b)
            val outward = x * upX + y * upY > 0f
            val d = hypot(x, y)
            if (outward && d > 12.5f - b.r * 0.5f) {
                if (d < 13f + b.r) pops += floatArrayOf(x, y, b.r, 0f)
                iterator.remove()
                if (b.big) iterator.add(bigBubble(13f + b.r + 3f * random.nextFloat()))
            }
        }
        pops.removeAll { it[3] += dt; it[3] > POP_TIME }
    }

    private fun drawBubbles() {
        if (!bubblesReady) initBubbles()
        for (b in bubbles) {
            val x = bubbleX(b)
            val y = bubbleY(b)
            if (b.r < 0.8f) {
                disc(x, y, b.r + 0.1f, 0.75f)
            } else {
                cells(x - b.r, y - b.r, x + b.r, y + b.r) { col, row, cx, cy ->
                    if (hypot(cx - x, cy - y) < b.r) put(col, row, 0.1f)
                }
                ring(x, y, b.r, 0.55f, 0.85f)
                // El brillo, arriba a la izquierda respecto a "arriba"
                disc(x + (upX * 0.4f + upY * 0.4f) * b.r, y + (upY * 0.4f - upX * 0.4f) * b.r, 0.35f, 1f)
            }
        }
        for (p in pops) {
            val t = p[3] / POP_TIME
            ring(p[0], p[1], p[2] * (1f + 1.5f * t), 0.45f, 0.7f * (1f - t))
        }
    }

    // ── Dibujo ──────────────────────────────────────────────────────────────

    /** Recorre las celdas de la caja dada; la celda (col, row) tiene su centro en (col − 12, row − 12). */
    private inline fun cells(x0: Float, y0: Float, x1: Float, y1: Float, body: (col: Int, row: Int, x: Float, y: Float) -> Unit) {
        val c0 = max(0, floor(x0 + 12f).toInt())
        val c1 = min(n - 1, ceil(x1 + 12f).toInt())
        val r0 = max(0, floor(y0 + 12f).toInt())
        val r1 = min(n - 1, ceil(y1 + 12f).toInt())
        for (row in r0..r1) for (col in c0..c1) body(col, row, col - 12f, row - 12f)
    }

    /** Mezcla por maximo: las uniones de las lineas no brillan de mas. */
    private fun put(col: Int, row: Int, value: Float) {
        if (value > grid[row][col]) grid[row][col] = value.coerceAtMost(1f)
    }

    private fun line(ax: Float, ay: Float, bx: Float, by: Float, width: Float, level: Float) {
        cells(min(ax, bx) - width, min(ay, by) - width, max(ax, bx) + width, max(ay, by) + width) { col, row, x, y ->
            val v = 1f - segmentDistance(x, y, ax, ay, bx, by) / width
            if (v > 0f) put(col, row, v * level)
        }
    }

    private fun disc(cx: Float, cy: Float, r: Float, level: Float) {
        cells(cx - r - 1f, cy - r - 1f, cx + r + 1f, cy + r + 1f) { col, row, x, y ->
            val v = (r + 0.5f - hypot(x - cx, y - cy)).coerceIn(0f, 1f)
            if (v > 0f) put(col, row, v * level)
        }
    }

    private fun ring(cx: Float, cy: Float, r: Float, width: Float, level: Float) {
        cells(cx - r - width, cy - r - width, cx + r + width, cy + r + width) { col, row, x, y ->
            val v = 1f - abs(hypot(x - cx, y - cy) - r) / width
            if (v > 0f) put(col, row, v * level)
        }
    }

    private fun segmentDistance(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val ex = bx - ax
        val ey = by - ay
        val t = (((x - ax) * ex + (y - ay) * ey) / (ex * ex + ey * ey + 1e-6f)).coerceIn(0f, 1f)
        return hypot(x - ax - t * ex, y - ay - t * ey)
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
