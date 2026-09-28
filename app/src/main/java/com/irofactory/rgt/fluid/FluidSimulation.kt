package com.irofactory.rgt.fluid

import kotlin.math.*

/**
 * FluidSimulation
 * ───────────────────────────────────────────────────────────────────────────
 * Motor SPH (Smoothed Particle Hydrodynamics) simplificado para simular
 * un fluido en una grilla 2D.
 *
 * Coordenadas: (0,0) = esquina superior izquierda
 * Unidades: celdas de la grilla (25x25 para la Glyph Matrix del Phone 3)
 *
 * @param cols            ancho del recipiente en celdas
 * @param rows            alto del recipiente en celdas
 * @param fillRatio       fraccion del recipiente llena de fluido (0.0-1.0)
 * @param viscosity       coeficiente de viscosidad mu
 * @param stiffness       rigidez del fluido k
 * @param restitution     coeficiente de rebote en paredes (0=absorbe, 1=elastico)
 * @param smoothingRadius radio de influencia h entre particulas
 * @param particleCount   numero de particulas (null = calculado por fillRatio)
 * @param circularBounds  true = recipiente circular (matriz redonda), false = rectangular
 */
class FluidSimulation(
    val cols:            Int     = 25,
    val rows:            Int     = 25,
    var fillRatio:       Float   = 0.30f,
    var viscosity:       Float   = 0.50f,
    var stiffness:       Float   = 1.00f,
    var restitution:     Float   = 0.40f,
    var smoothingRadius: Float   = 2.50f,
    particleCount:       Int?    = null,
    val circularBounds:  Boolean = true
) {
    // ── Particulas ────────────────────────────────────────────────────────────
    data class Particle(
        var x: Float, var y: Float,
        var vx: Float = 0f, var vy: Float = 0f,
        var density: Float = 0f,
        var pressure: Float = 0f
    )

    private val N: Int = particleCount ?: (cols * rows * fillRatio).toInt().coerceAtLeast(10)
    val particles: List<Particle>

    // ── Gravedad (actualizada desde el acelerometro) ───────────────────────────
    var gravX: Float = 0f
    var gravY: Float = 9.8f
    var gravScale: Float = 3.5f

    // ── Densidad de reposo ────────────────────────────────────────────────────
    private val restDensity: Float get() = (N.toFloat() / (cols * rows * fillRatio)).coerceAtLeast(0.5f)

    // ── Centro y radio para bounds circulares ──────────────────────────────────
    val centerX: Float = cols / 2f
    val centerY: Float = rows / 2f
    val boundsRadius: Float = minOf(cols, rows) / 2f - 0.5f

    init {
        // Inicializar particulas distribuidas uniformemente
        particles = buildList {
            repeat(N) { i ->
                val angle = (i.toFloat() / N) * 2 * PI.toFloat()
                val r     = sqrt(i.toFloat() / N) * boundsRadius * 0.8f
                val px    = if (circularBounds) centerX + r * cos(angle) else (i % cols).toFloat() + 0.5f
                val py    = if (circularBounds) centerY + r * sin(angle) else (i / cols).toFloat() + 0.5f
                add(Particle(
                    x  = px.coerceIn(0.1f, cols - 0.1f),
                    y  = py.coerceIn(0.1f, rows - 0.1f)
                ))
            }
        }
    }

    // ── Kernel de suavizado W(r, h) — Poly6 ───────────────────────────────────
    private fun kernelPoly6(r: Float, h: Float): Float {
        if (r > h) return 0f
        val coeff = 315f / (64f * PI.toFloat() * h.pow(9))
        return coeff * (h * h - r * r).pow(3)
    }

    // ── Gradiente del kernel — Spiky (para presion) ───────────────────────────
    private fun kernelSpikyGrad(r: Float, h: Float): Float {
        if (r > h || r < 1e-6f) return 0f
        val coeff = -45f / (PI.toFloat() * h.pow(6))
        return coeff * (h - r).pow(2)
    }

    // ── Kernel de viscosidad ──────────────────────────────────────────────────
    private fun kernelViscosity(r: Float, h: Float): Float {
        if (r > h) return 0f
        val coeff = 45f / (PI.toFloat() * h.pow(6))
        return coeff * (h - r)
    }

    // ── Paso de simulacion ────────────────────────────────────────────────────
    fun step(dt: Float = 0.016f) {
        val h = smoothingRadius

        // 1. Calcular densidades
        for (i in particles.indices) {
            val pi = particles[i]
            var density = 0f
            for (j in particles.indices) {
                val pj = particles[j]
                val dx = pi.x - pj.x
                val dy = pi.y - pj.y
                val r  = sqrt(dx * dx + dy * dy)
                density += kernelPoly6(r, h)
            }
            pi.density = density.coerceAtLeast(1e-6f)
            pi.pressure = stiffness * (pi.density - restDensity)
        }

        // 2. Calcular fuerzas y actualizar velocidades
        for (i in particles.indices) {
            val pi = particles[i]
            var fx = 0f
            var fy = 0f

            for (j in particles.indices) {
                if (i == j) continue
                val pj = particles[j]
                val dx = pi.x - pj.x
                val dy = pi.y - pj.y
                val r  = sqrt(dx * dx + dy * dy)
                if (r < 1e-6f || r > h) continue

                val nx = dx / r
                val ny = dy / r

                // Fuerza de presion
                val pressureForce = -(pi.pressure + pj.pressure) / (2f * pj.density) *
                        kernelSpikyGrad(r, h)
                fx += pressureForce * nx
                fy += pressureForce * ny

                // Fuerza de viscosidad
                val viscForce = viscosity / pj.density * kernelViscosity(r, h)
                fx += viscForce * (pj.vx - pi.vx)
                fy += viscForce * (pj.vy - pi.vy)
            }

            // Gravedad
            fx += gravX * gravScale
            fy += gravY * gravScale

            // Actualizar velocidad
            pi.vx += fx * dt / pi.density.coerceAtLeast(1e-6f)
            pi.vy += fy * dt / pi.density.coerceAtLeast(1e-6f)

            // Damping (evita velocidades infinitas)
            pi.vx *= 0.98f
            pi.vy *= 0.98f
        }

        // 3. Integrar posiciones y resolver colisiones
        for (pi in particles) {
            pi.x += pi.vx * dt
            pi.y += pi.vy * dt
            resolveCollision(pi)
        }
    }

    // ── Colisiones con paredes ────────────────────────────────────────────────
    private fun resolveCollision(p: Particle) {
        if (circularBounds) {
            // Recipiente circular
            val dx = p.x - centerX
            val dy = p.y - centerY
            val r  = sqrt(dx * dx + dy * dy)
            if (r > boundsRadius) {
                val nx = dx / r
                val ny = dy / r
                // Reposicionar dentro del circulo
                p.x = centerX + nx * (boundsRadius - 0.01f)
                p.y = centerY + ny * (boundsRadius - 0.01f)
                // Reflejar velocidad
                val dot = p.vx * nx + p.vy * ny
                p.vx = (p.vx - 2f * dot * nx) * restitution
                p.vy = (p.vy - 2f * dot * ny) * restitution
            }
        } else {
            // Recipiente rectangular
            if (p.x < 0.1f)         { p.x = 0.1f;             p.vx = abs(p.vx) * restitution }
            if (p.x > cols - 0.1f)  { p.x = cols - 0.1f;      p.vx = -abs(p.vx) * restitution }
            if (p.y < 0.1f)         { p.y = 0.1f;             p.vy = abs(p.vy) * restitution }
            if (p.y > rows - 0.1f)  { p.y = rows - 0.1f;      p.vy = -abs(p.vy) * restitution }
        }
    }

    // ── Rasterizar: particulas → mapa de brillo por celda ────────────────────
    /**
     * Devuelve un array [rows][cols] con valores 0.0-1.0 representando
     * la densidad normalizada en cada celda.
     */
    fun rasterize(): Array<FloatArray> {
        val grid = Array(rows) { FloatArray(cols) { 0f } }
        val h    = smoothingRadius.coerceAtMost(2.0f)  // kernel mas pequeno para rasterizacion

        for (p in particles) {
            val minC = (p.x - h).toInt().coerceAtLeast(0)
            val maxC = (p.x + h).toInt().coerceAtMost(cols - 1)
            val minR = (p.y - h).toInt().coerceAtLeast(0)
            val maxR = (p.y + h).toInt().coerceAtMost(rows - 1)

            for (r in minR..maxR) {
                for (c in minC..maxC) {
                    val dx   = p.x - (c + 0.5f)
                    val dy   = p.y - (r + 0.5f)
                    val dist = sqrt(dx * dx + dy * dy)
                    grid[r][c] += kernelPoly6(dist, h)
                }
            }
        }

        // Normalizar: el brillo maximo posible es cuando todas las particulas
        // estan concentradas en un punto
        val maxVal = grid.flatMap { it.toList() }.maxOrNull()?.takeIf { it > 0f } ?: 1f
        for (r in 0 until rows) for (c in 0 until cols) {
            grid[r][c] = (grid[r][c] / maxVal).coerceIn(0f, 1f)
        }

        return grid
    }

    // ── Mascara circular para el area util de la matriz ───────────────────────
    fun circularMask(): Array<BooleanArray> {
        val mask = Array(rows) { BooleanArray(cols) { false } }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val dx = c + 0.5f - centerX
                val dy = r + 0.5f - centerY
                mask[r][c] = sqrt(dx * dx + dy * dy) <= boundsRadius
            }
        }
        return mask
    }

    // ── Splash: impulso radial hacia afuera, para interaccion por touch ───────
    fun splash(strength: Float = 6f) {
        for (p in particles) {
            val dx   = p.x - centerX
            val dy   = p.y - centerY
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.1f)
            p.vx += (dx / dist) * strength
            p.vy += (dy / dist) * strength
        }
    }

    // ── Reinicia las particulas a su distribucion inicial ─────────────────────
    fun reset() {
        particles.forEachIndexed { i, p ->
            val angle = (i.toFloat() / N) * 2 * PI.toFloat()
            val r     = sqrt(i.toFloat() / N) * boundsRadius * 0.8f
            val px    = if (circularBounds) centerX + r * cos(angle) else (i % cols).toFloat() + 0.5f
            val py    = if (circularBounds) centerY + r * sin(angle) else (i / cols).toFloat() + 0.5f
            p.x = px.coerceIn(0.1f, cols - 0.1f)
            p.y = py.coerceIn(0.1f, rows - 0.1f)
            p.vx = 0f
            p.vy = 0f
        }
    }
}

// ── Parametros configurables ──────────────────────────────────────────────────
data class FluidParams(
    val fillRatio:       Float = 0.30f,
    val viscosity:       Float = 0.50f,
    val stiffness:       Float = 2.00f,
    val restitution:     Float = 0.30f,
    val smoothingRadius: Float = 1.80f
) {
    fun applyTo(sim: FluidSimulation) {
        sim.fillRatio       = fillRatio
        sim.viscosity       = viscosity
        sim.stiffness       = stiffness
        sim.restitution     = restitution
        sim.smoothingRadius = smoothingRadius
    }
}
