package com.felipe.nonograma

/** Plan de toques/trazos del autoresolver (puro, testeable): centros de casillas o bloques contiguos, en zigzag por filas. */
object AutoPlan {
    /** Velocidad válida: cualquier número positivo (0,1 … 100). */
    fun clampSpeed(speed: Float): Float = if (speed.isNaN()) 1f else speed.coerceIn(0.1f, 100f)
    /** Pausa entre gestos: la base dividida por la velocidad. */
    fun interval(baseMs: Int, speed: Float): Long = maxOf(1L, (baseMs / clampSpeed(speed)).toLong())
    /** Duración del toque corto: 40 ms a 1x, más corto a más velocidad, nunca menos de 10 ms. */
    fun stroke(speed: Float): Long = (40f / clampSpeed(speed)).toLong().coerceIn(10L, 40L)
    /** Duración de un trazo por bloque: ~30 ms por celda, acotado y dividido por velocidad. */
    fun strokeFor(cells: Int, speed: Float): Long {
        val n = cells.coerceAtLeast(1)
        val base = (30f * n).coerceIn(40f, 400f)
        return (base / clampSpeed(speed)).toLong().coerceIn(40L, 400L)
    }

    data class Tap(val x: Float, val y: Float, val row: Int, val col: Int)
    /** Trazo de relleno: del centro de la primera celda al de la última (misma fila, mismo color). */
    data class Stroke(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val row: Int, val c0: Int, val c1: Int)

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

    /**
     * Un trazo por bloque contiguo del mismo color (>0) en cada fila.
     * Orden zigzag: filas pares izquierda→derecha, impares derecha→izquierda.
     * Celda suelta = trazo corto (como un toque).
     */
    fun strokes(rows: List<List<Block>>, cols: List<List<Block>>, solution: IntArray?, h: Int, w: Int,
                left: Int, top: Int, right: Int, bottom: Int): List<Stroke> {
        if (solution == null || solution.size != h * w || rows.size != h || cols.size != w) return emptyList()
        if (right <= left || bottom <= top) return emptyList()
        if (!Solver.verify(rows, cols, solution)) return emptyList()
        val cw = (right - left).toFloat() / w; val ch = (bottom - top).toFloat() / h
        val out = ArrayList<Stroke>()
        for (r in 0 until h) {
            val runs = ArrayList<Pair<Int, Int>>() // c0, c1 inclusive
            var c = 0
            while (c < w) {
                val v = solution[r * w + c]
                if (v > 0) {
                    val c0 = c
                    while (c + 1 < w && solution[r * w + c + 1] == v) c++
                    runs.add(c0 to c)
                }
                c++
            }
            val ordered = if (r % 2 == 0) runs else runs.asReversed()
            val y = top + (r + 0.5f) * ch
            for ((c0, c1) in ordered) {
                val x0 = left + (c0 + 0.5f) * cw
                val x1 = left + (c1 + 0.5f) * cw
                if (c0 == c1) out.add(Stroke(x0, y, x0 + 0.5f, y + 0.5f, r, c0, c1))
                else out.add(Stroke(x0, y, x1, y, r, c0, c1))
            }
        }
        return out
    }
}
