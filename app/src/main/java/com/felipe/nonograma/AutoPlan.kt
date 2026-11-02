package com.felipe.nonograma

/** Plan de toques del autoresolver (puro, testeable): centros de las casillas rellenas, en zigzag por filas. */
object AutoPlan {
    /** Pausa entre toques: la base dividida por la velocidad (1x/2x/3x). */
    fun interval(baseMs: Int, speed: Int): Long = maxOf(10L, baseMs.toLong() / speed.coerceIn(1, 3))
    /** Duración del toque: 40 ms a 1x, más corto a 2x/3x (sigue siendo un toque, no una pulsación larga). */
    fun stroke(speed: Int): Long = when (speed.coerceIn(1, 3)) { 1 -> 40L; 2 -> 25L; else -> 15L }

    data class Tap(val x: Float, val y: Float, val row: Int, val col: Int)

    /** Solo si la solución verifica las pistas; si no, lista vacía (no se toca nada). */
    fun taps(rows: List<List<Block>>, cols: List<List<Block>>, solution: IntArray?, h: Int, w: Int,
             left: Int, top: Int, right: Int, bottom: Int): List<Tap> {
        if (solution == null || solution.size != h * w || rows.size != h || cols.size != w) return emptyList()
        if (right <= left || bottom <= top) return emptyList()
        if (!Solver.verify(rows, cols, solution)) return emptyList()
        val cw = (right - left).toFloat() / w; val ch = (bottom - top).toFloat() / h
        val out = ArrayList<Tap>()
        for (r in 0 until h) {
            val cs = if (r % 2 == 0) 0 until w else (w - 1 downTo 0)
            for (c in cs) if (solution[r * w + c] > 0) out.add(Tap(left + (c + 0.5f) * cw, top + (r + 0.5f) * ch, r, c))
        }
        return out
    }
}
