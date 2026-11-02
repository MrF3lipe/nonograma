package com.felipe.nonograma

/** Un bloque de pista: longitud y color (1..K). */
data class Block(val len: Int, val color: Int = 1)

/**
 * Solucionador de nonogramas (monocromo y multicolor).
 * Idea del original en Python (opciones/encaja/resolver) pero con:
 *  - line solving por programación dinámica (intersección de todas las colocaciones posibles
 *    sin enumerarlas), iterado filas/columnas hasta punto fijo;
 *  - backtracking sobre la celda más restringida cuando se estanca.
 * Las celdas son máscaras de bits: bit 0 = vacía, bit c = color c.
 */
object Solver {
    enum class Status { SOLVED, NO_SOLUTION, TIMEOUT, INVALID }
    class Result(val status: Status, val grid: IntArray?, val h: Int, val w: Int) {
        fun at(r: Int, c: Int) = grid!![r * w + c]
    }
    private class Timeout : RuntimeException()

    /** Equivalente a `encaja`: ¿la línea (valores de color, 0 = vacía) cumple la pista? */
    fun encaja(line: IntArray, clue: List<Block>): Boolean = runs(line) == clue.filter { it.len > 0 }

    fun runs(line: IntArray): List<Block> {
        val out = ArrayList<Block>()
        var i = 0
        while (i < line.size) {
            val c = line[i]
            if (c == 0) { i++; continue }
            var j = i
            while (j < line.size && line[j] == c) j++
            out.add(Block(j - i, c)); i = j
        }
        return out
    }

    /** Longitud mínima de una pista (bloques del mismo color consecutivos necesitan hueco). */
    fun minLen(clue: List<Block>): Int {
        var s = 0
        for (i in clue.indices) {
            s += clue[i].len
            if (i > 0 && clue[i - 1].color == clue[i].color) s++
        }
        return s
    }

    fun verify(rows: List<List<Block>>, cols: List<List<Block>>, grid: IntArray): Boolean {
        val h = rows.size; val w = cols.size
        for (r in 0 until h) if (!encaja(IntArray(w) { grid[r * w + it] }, rows[r])) return false
        for (c in 0 until w) if (!encaja(IntArray(h) { grid[it * w + c] }, cols[c])) return false
        return true
    }

    fun solve(rows: List<List<Block>>, cols: List<List<Block>>, timeoutMs: Long = 10_000): Result {
        val h = rows.size; val w = cols.size
        val R = rows.map { l -> l.filter { it.len > 0 } }
        val C = cols.map { l -> l.filter { it.len > 0 } }
        if (h == 0 || w == 0) return Result(Status.INVALID, null, h, w)
        if (R.any { minLen(it) > w } || C.any { minLen(it) > h }) return Result(Status.INVALID, null, h, w)
        val k = (R + C).flatten().maxOfOrNull { it.color } ?: 0
        // Totales por color deben coincidir entre filas y columnas.
        for (c in 1..k) {
            val a = R.flatten().filter { it.color == c }.sumOf { it.len }
            val b = C.flatten().filter { it.color == c }.sumOf { it.len }
            if (a != b) return Result(Status.INVALID, null, h, w)
        }
        val full = (1 shl (k + 1)) - 1
        val grid = IntArray(h * w) { full }
        val deadline = System.currentTimeMillis() + timeoutMs
        val ctx = Ctx(h, w, R, C, deadline)
        return try {
            val sol = ctx.search(grid)
            if (sol == null) Result(Status.NO_SOLUTION, null, h, w)
            else Result(Status.SOLVED, IntArray(h * w) { Integer.numberOfTrailingZeros(sol[it]) }, h, w)
        } catch (e: Timeout) {
            Result(Status.TIMEOUT, null, h, w)
        }
    }

    private class Ctx(val h: Int, val w: Int, val R: List<List<Block>>, val C: List<List<Block>>, val deadline: Long) {
        val rowL = R.map { l -> IntArray(l.size) { l[it].len } }
        val rowC = R.map { l -> IntArray(l.size) { l[it].color } }
        val colL = C.map { l -> IntArray(l.size) { l[it].len } }
        val colC = C.map { l -> IntArray(l.size) { l[it].color } }
        var steps = 0

        fun propagate(g: IntArray, dirtyRows: BooleanArray, dirtyCols: BooleanArray): Boolean {
            val rowBuf = IntArray(w); val colBuf = IntArray(h)
            var any = true
            while (any) {
                any = false
                for (r in 0 until h) if (dirtyRows[r]) {
                    dirtyRows[r] = false; any = true
                    for (c in 0 until w) rowBuf[c] = g[r * w + c]
                    val res = solveLine(rowBuf, rowL[r], rowC[r]) ?: return false
                    for (c in 0 until w) if (res[c] != g[r * w + c]) { g[r * w + c] = res[c]; dirtyCols[c] = true }
                }
                for (c in 0 until w) if (dirtyCols[c]) {
                    dirtyCols[c] = false; any = true
                    for (r in 0 until h) colBuf[r] = g[r * w + c]
                    val res = solveLine(colBuf, colL[c], colC[c]) ?: return false
                    for (r in 0 until h) if (res[r] != g[r * w + c]) { g[r * w + c] = res[r]; dirtyRows[r] = true }
                }
                if ((++steps and 63) == 0 && System.currentTimeMillis() > deadline) throw Timeout()
            }
            return true
        }

        fun search(start: IntArray): IntArray? {
            val g = start.copyOf()
            if (!propagate(g, BooleanArray(h) { true }, BooleanArray(w) { true })) return null
            return branch(g)
        }

        private fun options(m0: Int): List<Int> {
            val opts = ArrayList<Int>(); var m = m0; var bit = 1
            while (m != 0) { if (m and 1 != 0 && bit != 1) opts.add(bit); m = m ushr 1; bit = bit shl 1 }
            if (m0 and 1 != 0) opts.add(1)
            return opts
        }

        private fun tryValue(g: IntArray, i: Int, v: Int): IntArray? {
            val ng = g.copyOf(); ng[i] = v
            val dr = BooleanArray(h); val dc = BooleanArray(w); dr[i / w] = true; dc[i % w] = true
            return if (propagate(ng, dr, dc)) ng else null
        }

        /** Sondeo (failed-literal): descarta valores que llevan a contradicción tras propagar. */
        private fun probe(g: IntArray): Boolean {
            var changed = true
            while (changed) {
                changed = false
                for (i in g.indices) {
                    if (Integer.bitCount(g[i]) < 2) continue
                    val ok = options(g[i]).filter { tryValue(g, i, it) != null }
                    if (ok.isEmpty()) return false
                    val m = ok.fold(0) { a, b -> a or b }
                    if (m != g[i]) {
                        g[i] = m
                        val dr = BooleanArray(h); val dc = BooleanArray(w); dr[i / w] = true; dc[i % w] = true
                        if (!propagate(g, dr, dc)) return false
                        changed = true
                    }
                }
            }
            return true
        }

        private fun branch(g: IntArray): IntArray? {
            if (System.currentTimeMillis() > deadline) throw Timeout()
            if (g.all { Integer.bitCount(it) == 1 }) return g
            if (!probe(g)) return null
            var best = -1; var bestBits = 99
            for (i in g.indices) {
                val b = Integer.bitCount(g[i])
                if (b > 1 && b < bestBits) { best = i; bestBits = b; if (b == 2) break }
            }
            if (best < 0) return g
            for (o in options(g[best])) {
                val ng = tryValue(g, best, o) ?: continue
                branch(ng)?.let { return it }
            }
            return null
        }
    }

    /**
     * Line solver exacto: devuelve para cada celda la unión de valores posibles en todas las
     * colocaciones válidas (o null si no hay ninguna). O(n·k) estados.
     */
    fun solveLine(cells: IntArray, lens: IntArray, cols: IntArray): IntArray? {
        val n = cells.size; val k = lens.size
        val f = forward(cells, lens, cols)
        val rc = IntArray(n) { cells[n - 1 - it] }
        val rl = IntArray(k) { lens[k - 1 - it] }
        val rcol = IntArray(k) { cols[k - 1 - it] }
        val b = forward(rc, rl, rcol)
        val B = f.first; val E = f.second
        if (!(B[k][n] || E[k][n])) return null
        // Sufijo desde i con bloques j..k-1: SB (empieza con vacía o i==n), SE (empieza con bloque j)
        fun sb(j: Int, i: Int) = b.first[k - j][n - i]
        fun se(j: Int, i: Int) = b.second[k - j][n - i]
        val res = IntArray(n)
        for (i in 0 until n) {
            if (cells[i] and 1 == 0) continue
            for (j in 0..k) {
                if ((B[j][i] || E[j][i]) && (sb(j, i + 1) || se(j, i + 1))) { res[i] = res[i] or 1; break }
            }
        }
        val diff = IntArray(n + 1)
        for (j in 0 until k) {
            val L = lens[j]; val c = cols[j]; val bit = 1 shl c
            java.util.Arrays.fill(diff, 0)
            var bad = 0 // celdas que no admiten c dentro de la ventana [s, s+L)
            for (t in 0 until minOf(L, n)) if (cells[t] and bit == 0) bad++
            for (s in 0..n - L) {
                if (s > 0) {
                    if (cells[s - 1] and bit == 0) bad--
                    if (cells[s + L - 1] and bit == 0) bad++
                }
                if (bad != 0) continue
                val okPre = B[j][s] || (E[j][s] && cols[j - 1] != c)
                if (!okPre) continue
                val okSuf = sb(j + 1, s + L) || (se(j + 1, s + L) && cols[j + 1] != c)
                if (!okSuf) continue
                diff[s]++; diff[s + L]--
            }
            var acc = 0
            for (i in 0 until n) { acc += diff[i]; if (acc > 0) res[i] = res[i] or bit }
        }
        for (i in 0 until n) if (res[i] == 0) return null
        return res
    }

    /** B[j][i]: prefijo [0,i) cubierto por j bloques terminando en vacía (o i=0); E: terminando con el bloque j-1. */
    private fun forward(cells: IntArray, lens: IntArray, cols: IntArray): Pair<Array<BooleanArray>, Array<BooleanArray>> {
        val n = cells.size; val k = lens.size
        val B = Array(k + 1) { BooleanArray(n + 1) }
        val E = Array(k + 1) { BooleanArray(n + 1) }
        B[0][0] = true
        // prefijos de celdas que no admiten cada color de bloque
        val badPref = Array(k) { j ->
            val bit = 1 shl cols[j]
            val p = IntArray(n + 1)
            for (i in 0 until n) p[i + 1] = p[i] + if (cells[i] and bit == 0) 1 else 0
            p
        }
        for (i in 0..n) for (j in 0..k) {
            val bj = B[j][i]; val ej = E[j][i]
            if (!bj && !ej) continue
            if (i < n && cells[i] and 1 != 0) B[j][i + 1] = true
            if (j < k) {
                val L = lens[j]
                if (i + L <= n && (bj || (ej && cols[j - 1] != cols[j])) && badPref[j][i + L] - badPref[j][i] == 0)
                    E[j + 1][i + L] = true
            }
        }
        return B to E
    }
}
