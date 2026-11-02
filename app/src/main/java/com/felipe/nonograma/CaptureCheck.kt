package com.felipe.nonograma

/**
 * ¿Se coló la superposición (marcos F/C, cuadro de solución) en la captura?
 *
 * Las asas de redimensionar son triángulos opacos de color conocido en la esquina inferior derecha de cada
 * marco. Si en la captura esa zona sigue teniendo ese color, el fotograma se compuso antes de ocultar las
 * vistas: sus bordes discontinuos, el tinte y las asas tapan/ensucian las pistas (p. ej. un "4" pegado al borde
 * del marco se lee "2" y las dos últimas filas quedan bajo el asa). Es Kotlin puro para probarlo en la JVM.
 */
object CaptureCheck {
    /** Asa visible: triángulo inferior derecho del cuadrado [x0,x1)×[y0,y1) pintado con [rgb]. */
    data class Handle(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val rgb: Int)

    /** Fracción de píxeles del interior del triángulo con el color del asa. */
    fun coverage(px: IntArray, w: Int, h: Int, m: Handle): Float {
        val s = minOf(m.x1 - m.x0, m.y1 - m.y0)
        if (s < 6) return 0f
        val cr = (m.rgb shr 16) and 255; val cg = (m.rgb shr 8) and 255; val cb = m.rgb and 255
        var n = 0; var hit = 0
        // solo la parte bien dentro del triángulo (lejos de la diagonal y de los bordes)
        for (j in s / 6 until s - s / 10) for (i in s / 6 until s - s / 10) {
            if (i + j < s + s / 4) continue
            val x = m.x0 + i; val y = m.y0 + j
            if (x < 0 || y < 0 || x >= w || y >= h) continue
            n++
            val p = px[y * w + x]
            val dr = kotlin.math.abs(((p shr 16) and 255) - cr); val dg = kotlin.math.abs(((p shr 8) and 255) - cg); val db = kotlin.math.abs((p and 255) - cb)
            if (dr < 48 && dg < 48 && db < 48) hit++
        }
        return if (n < 8) 0f else hit.toFloat() / n
    }

    /** true si alguna asa sigue visible en la captura (fotograma sucio: hay que esperar a otro). */
    fun overlayVisible(px: IntArray, w: Int, h: Int, handles: List<Handle>): Boolean =
        handles.any { coverage(px, w, h, it) >= 0.5f }
}
