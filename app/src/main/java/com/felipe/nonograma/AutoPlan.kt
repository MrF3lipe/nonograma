package com.felipe.nonograma

/** Plan de toques del autoresolver (puro, testeable): centros de las casillas rellenas, en zigzag por filas. */
object AutoPlan {
    /** Velocidad válida: cualquier número positivo (0,1 … 100). */
    fun clampSpeed(speed: Float): Float = if (speed.isNaN()) 1f else speed.coerceIn(0.1f, 100f)
    /** Pausa entre toques: la base dividida por la velocidad. */
    fun interval(baseMs: Int, speed: Float): Long = maxOf(1L, (baseMs / clampSpeed(speed)).toLong())
    /** Duración del toque: 40 ms a 1x, más corto a más velocidad, nunca menos de 10 ms (sigue siendo un toque). */
    fun stroke(speed: Float): Long = (40f / clampSpeed(speed)).toLong().coerceIn(10L, 40L)

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
