package com.felipe.nonograma

/**
 * Auto-acople (puro, testeable): con la rejilla detectada en la captura, los marcos F (pistas de filas) y
 * C (pistas de columnas) se pegan a sus bordes exactos. Solo se respeta lo que el usuario elige de verdad:
 * hasta dónde llega F por la izquierda y C por arriba (si quedaron dentro de la rejilla, se abren hasta el borde
 * de la pantalla).
 */
object Snap {
    data class Box(val l: Int, val t: Int, val r: Int, val b: Int)

    fun frames(g: Box, rf: Box, cf: Box, minSide: Int = 8): Pair<Box, Box> {
        val rl = if (rf.l in 0..(g.l - 1 - minSide)) rf.l else 0
        val ct = if (cf.t in 0..(g.t - 1 - minSide)) cf.t else 0
        return Box(rl, g.t, g.l - 1, g.b) to Box(g.l, ct, g.r, g.t - 1)
    }

    /** ¿La rejilla detectada es la que el usuario está señalando? (solapa el cuadro actual o está pegada a los marcos). */
    fun near(det: Box, g: Box, rf: Box, cf: Box): Boolean {
        fun ov(a: Box, b: Box): Int { val w = minOf(a.r, b.r) - maxOf(a.l, b.l); val h = minOf(a.b, b.b) - maxOf(a.t, b.t); return if (w > 0 && h > 0) w * h else 0 }
        val area = maxOf(1, (det.r - det.l) * (det.b - det.t))
        if (ov(det, g) * 3 >= area) return true
        val tolX = (det.r - det.l) / 4; val tolY = (det.b - det.t) / 4
        val rowsOk = kotlin.math.abs(rf.r - det.l) <= tolX && minOf(rf.b, det.b) - maxOf(rf.t, det.t) > (det.b - det.t) / 2
        val colsOk = kotlin.math.abs(cf.b - det.t) <= tolY && minOf(cf.r, det.r) - maxOf(cf.l, det.l) > (det.r - det.l) / 2
        return rowsOk || colsOk
    }
}
