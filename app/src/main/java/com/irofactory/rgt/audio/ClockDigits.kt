package com.irofactory.rgt.audio

import kotlin.math.hypot

/**
 * ClockDigits
 * ───────────────────────────────────────────────────────────────────────────
 * Cada dígito 0-9 como un trazo de una sola línea (como una fuente de
 * plotter): una lista de sub-trazos, cada uno una polilínea sobre las seis
 * esquinas de una caja de siete segmentos (TL, TR, ML, MR, BL, BR). La
 * mayoría de los dígitos salen en un solo trazo continuo; 3 y 4 necesitan
 * levantar la pluma una vez.
 *
 * [digitPoints] reparte [count] puntos sobre esos trazos (proporcional a su
 * longitud) y los coloca en la matriz, para que el reposo "reloj" de
 * [AudioBlobSimulation] los use como destino del contorno de un anillo: el
 * mismo punto que hoy traza el borde de una figura, ahí termina trazando el
 * dígito.
 */
object ClockDigits {

    private const val TL = 0
    private const val TR = 1
    private const val ML = 2
    private const val MR = 3
    private const val BL = 4
    private const val BR = 5
    private val CORNER_X = floatArrayOf(0f, 2f, 0f, 2f, 0f, 2f)
    private val CORNER_Y = floatArrayOf(0f, 0f, 2f, 2f, 4f, 4f)

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

    /** Media unidad: ancho 2 y alto 4 de la caja, a celdas de la matriz. */
    const val SCALE = 1.5f
    const val WIDTH = 2f * SCALE
    const val HEIGHT = 4f * SCALE

    /**
     * Puntos de [digit] (0-9), repartidos entre sus sub-trazos por longitud,
     * con la esquina superior izquierda de su caja en ([left], [top]).
     * Regresa los puntos (x, y intercalados) y, por punto, el id de su
     * sub-trazo: dos puntos consecutivos se conectan solo si comparten id.
     */
    fun digitPoints(digit: Int, left: Float, top: Float, count: Int): Pair<FloatArray, IntArray> {
        val paths = DIGITS[digit]
        if (paths.size == 1) return resample(paths[0], left, top, count) to IntArray(count)

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
            resample(path, left, top, n).copyInto(points, at * 2)
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
    private fun resample(corners: IntArray, left: Float, top: Float, count: Int): FloatArray {
        val xs = FloatArray(corners.size) { left + CORNER_X[corners[it]] * SCALE }
        val ys = FloatArray(corners.size) { top + CORNER_Y[corners[it]] * SCALE }
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
