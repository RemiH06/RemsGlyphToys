package com.irofactory.rgt.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** FFT y modelos de lo que entrega Visualizer.getFft, para las pruebas con audio sintetico. */
object VisualizerModel {

    /**
     * Como el Visualizer en modo normalizado (el de por defecto): el audio de
     * 16 bits a volumen [level] se sube en pasos de x2 hasta que su pico llena
     * 8 bits, con tope de x32 sobre la conversion normal; luego FFT de punto
     * fijo cuantizada a bytes. Asi un golpe y su cola conservan parte de su
     * diferencia de nivel, como en el telefono.
     */
    fun normalizedMagnitudes(block: DoubleArray, level: Double = 0.25): FloatArray {
        val size = block.size
        val pcm = IntArray(size) { (block[it] * level * 32767).roundToInt().coerceIn(-32768, 32767) }
        val peak = pcm.maxOf { abs(it) }
        if (peak == 0) return FloatArray(size / 2)
        // Un pico de escala completa tiene 17 ceros al frente y se corre 8 bits (16 → 8)
        val shift = (25 - Integer.numberOfLeadingZeros(peak)).coerceAtLeast(3)
        val re = DoubleArray(size) { ((pcm[it] shr shift).coerceIn(-128, 127)).toDouble() }
        val im = DoubleArray(size)
        fft(re, im)
        // Un seno de escala completa en 8 bits da ~127 en su bin
        val scale = 2.0 / size
        return FloatArray(size / 2) { k ->
            val qr = (re[k] * scale).roundToInt().coerceIn(-128, 127)
            val qi = (im[k] * scale).roundToInt().coerceIn(-128, 127)
            hypot(qr.toFloat(), qi.toFloat())
        }
    }

    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            for (i in 0 until n step len) for (k in 0 until len / 2) {
                val wr = cos(ang * k); val wi = sin(ang * k)
                val ur = re[i + k]; val ui = im[i + k]
                val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
                val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                re[i + k] = ur + vr; im[i + k] = ui + vi
                re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
            }
            len = len shl 1
        }
    }
}
