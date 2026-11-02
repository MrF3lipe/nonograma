package com.felipe.nonograma

/** Reparto de los botones de la barra en filas que caben en [maxW] (puro, testeable). Nunca deja un botón fuera. */
object BarFlow {
    fun rows(widths: List<Int>, maxW: Int): List<List<Int>> {
        val out = ArrayList<List<Int>>(); var cur = ArrayList<Int>(); var used = 0
        for ((i, w) in widths.withIndex()) {
            if (cur.isNotEmpty() && used + w > maxW) { out.add(cur); cur = ArrayList(); used = 0 }
            cur.add(i); used += w
        }
        if (cur.isNotEmpty()) out.add(cur)
        return out
    }
}
