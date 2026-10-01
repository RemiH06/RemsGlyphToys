package com.irofactory.rgt.audio

import kotlin.math.hypot

/**
 * ClockDigits
 * ───────────────────────────────────────────────────────────────────────────
 * Cada dígito 0-9, y las letras A/P del indicador am/pm, como un trazo de
 * una sola línea (como una fuente de plotter): una lista de sub-trazos, cada
 * uno una polilínea sobre las esquinas de una caja de siete segmentos (TL,
 * TR, ML, MR, BL, BR, mas TC arriba al centro para las letras). La mayoría
 * de los dígitos salen en un solo trazo continuo; 3 y 4 necesitan levantar
 * la pluma una vez, igual que la A (el trazo y el travesaño).
 *
 * [digitPoints] y [letterPoints] reparten puntos sobre esos trazos
 * (proporcional a su longitud) y los colocan en la matriz, para que el
 * reposo "reloj" de [AudioBlobSimulation] los use como destino del contorno
 * de un anillo: el mismo punto que hoy traza el borde de una figura, ahí
 * termina trazando el dígito.
 */
object ClockDigits {

    private const val TL = 0
    private const val TR = 1
    private const val ML = 2
    private const val MR = 3
    private const val BL = 4
    private const val BR = 5
    private const val TC = 6
    private const val MC = 7
    private val CORNER_X = floatArrayOf(0f, 2f, 0f, 2f, 0f, 2f, 1f, 1f)
    private val CORNER_Y = floatArrayOf(0f, 0f, 2f, 2f, 4f, 4f, 0f, 2f)

    /** Caja de 2x4 unidades; cada dígito es 1 o 2 sub-trazos por esas esquinas. */
    private val DIGITS: Array<Array<IntArray>> = arrayOf(
        arrayOf(intArrayOf(TL, TR, MR, BR, BL, ML, TL)),             // 0: rectangulo cerrado
        arrayOf(intArrayOf(TR, MR, BR)),                             // 1: barra derecha
        arrayOf(intArrayOf(TL, TR, MR, ML, BL, BR)),                 // 2
        arrayOf(intArrayOf(TL, TR, MR), intArrayOf(ML, MR, BR, BL)), // 3: dos trazos
        arrayOf(intArrayOf(TL, ML, MR, BR), intArrayOf(TR, MR)),     // 4: dos trazos
        arrayOf(intArrayOf(TR, TL, ML, MR, BR, BL)),                 // 5
        arrayOf(intArrayOf(TR, TL, ML, MR, BR, BL, ML)),             // 6
        arrayOf(intArrayOf(TL, TR, MR, BR)),                         // 7
        arrayOf(intArrayOf(MR, TR, TL, ML, MR, BR, BL, ML)),         // 8
        arrayOf(intArrayOf(MR, TR, TL, ML, MR, BR, BL))              // 9
    )

    /**
     * "A" (dos diagonales al vertice de arriba, mas el travesaño), "P" (palo
     * con una vuelta arriba) y "M" (palo, baja al centro, sube, palo).
     */
    private val LETTER_A: Array<IntArray> = arrayOf(intArrayOf(BL, TC, BR), intArrayOf(ML, MR))
    private val LETTER_P: Array<IntArray> = arrayOf(intArrayOf(BL, TL, TR, MR, ML))
    private val LETTER_M: Array<IntArray> = arrayOf(intArrayOf(BL, TL, MC, TR, BR))

    /** Media unidad: ancho 2 y alto 4 de la caja, a celdas de la matriz, para los digitos. */
    const val SCALE = 1.5f
    const val WIDTH = 2f * SCALE
    const val HEIGHT = 4f * SCALE

    /**
     * Puntos de [digit] (0-9), repartidos entre sus sub-trazos por longitud,
     * con la esquina superior izquierda de su caja en ([left], [top]).
     * Regresa los puntos (x, y intercalados) y, por punto, el id de su
     * sub-trazo: dos puntos consecutivos se conectan solo si comparten id.
     */
    fun digitPoints(digit: Int, left: Float, top: Float, count: Int): Pair<FloatArray, IntArray> =
        pathPoints(DIGITS[digit], left, top, SCALE, count)

    /** Puntos de la letra "P" (si [pm]) o "A", a la escala [scale] (mas chica que la de los digitos). */
    fun letterPoints(pm: Boolean, left: Float, top: Float, scale: Float, count: Int): Pair<FloatArray, IntArray> =
        pathPoints(if (pm) LETTER_P else LETTER_A, left, top, scale, count)

    /** Puntos de la letra "M", la segunda del indicador am/pm. */
    fun letterMPoints(left: Float, top: Float, scale: Float, count: Int): Pair<FloatArray, IntArray> =
        pathPoints(LETTER_M, left, top, scale, count)

    private fun pathPoints(paths: Array<IntArray>, left: Float, top: Float, scale: Float, count: Int): Pair<FloatArray, IntArray> {
        if (paths.size == 1) return resample(paths[0], left, top, scale, count) to IntArray(count)

        val lengths = paths.map { pathLength(it) }
        val total = lengths.sum().coerceAtLeast(1e-6f)
        val counts = IntArray(paths.size) { i ->
            if (i == paths.lastIndex) count - (0 until i).sumOf { (count * lengths[it] / total).toInt() }
            else (count * lengths[i] / total).toInt()
        }
        val points = FloatArray(count * 2)
        val subIds = IntArray(count)
        var at = 0
        for ((i, path) in paths.withIndex()) {
            val n = counts[i].coerceAtLeast(1)
            resample(path, left, top, scale, n).copyInto(points, at * 2)
            for (k in at until at + n) subIds[k] = i
            at += n
        }
        return points to subIds
    }

    private fun pathLength(corners: IntArray): Float {
        var len = 0f
        for (i in 1 until corners.size) {
            len += hypot(
                (CORNER_X[corners[i]] - CORNER_X[corners[i - 1]]).toDouble(),
                (CORNER_Y[corners[i]] - CORNER_Y[corners[i - 1]]).toDouble()
            ).toFloat()
        }
        return len
    }

    /** [count] puntos parejos por longitud de arco sobre la polilinea [corners], ya en coordenadas de la matriz. */
    private fun resample(corners: IntArray, left: Float, top: Float, scale: Float, count: Int): FloatArray {
        val xs = FloatArray(corners.size) { left + CORNER_X[corners[it]] * scale }
        val ys = FloatArray(corners.size) { top + CORNER_Y[corners[it]] * scale }
        val segLen = FloatArray(corners.size - 1) { hypot((xs[it + 1] - xs[it]).toDouble(), (ys[it + 1] - ys[it]).toDouble()).toFloat() }
        val total = segLen.sum()
        val out = FloatArray(count * 2)
        for (k in 0 until count) {
            val target = if (count <= 1) 0f else total * k / (count - 1)
            var acc = 0f
            var seg = 0
            while (seg < segLen.lastIndex && acc + segLen[seg] < target) { acc += segLen[seg]; seg++ }
            val t = if (segLen[seg] > 1e-6f) ((target - acc) / segLen[seg]).coerceIn(0f, 1f) else 0f
            out[2 * k] = xs[seg] + t * (xs[seg + 1] - xs[seg])
            out[2 * k + 1] = ys[seg] + t * (ys[seg + 1] - ys[seg])
        }
        return out
    }
}
