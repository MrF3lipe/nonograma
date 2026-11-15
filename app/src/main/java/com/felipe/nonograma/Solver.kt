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

    /** Tiempo de búsqueda acorde al tamaño (puzzles duros/grandes necesitan más). */
    fun timeoutFor(h: Int, w: Int): Long = (12_000L + h.toLong() * w * 6L).coerceIn(12_000L, 90_000L)

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

    /**
     * Resuelve con líneas de pista desconocida (null = cualquier contenido). Devuelve hasta [limit]
     * soluciones distintas (para saber si la deducida es única) o null si se agota el tiempo.
     */
    fun solveUnknown(rows: List<List<Block>?>, cols: List<List<Block>?>, timeoutMs: Long = 2000, limit: Int = 2): List<IntArray>? {
        val h = rows.size; val w = cols.size
        if (h == 0 || w == 0) return emptyList()
        val R = rows.map { l -> l?.filter { it.len > 0 } }
        val C = cols.map { l -> l?.filter { it.len > 0 } }
        if (R.any { it != null && minLen(it) > w } || C.any { it != null && minLen(it) > h }) return emptyList()
        val k = (R.filterNotNull() + C.filterNotNull()).flatten().maxOfOrNull { it.color } ?: 1
        val full = (1 shl (k + 1)) - 1
        val ctx = Ctx(h, w, R, C, System.currentTimeMillis() + timeoutMs)
        val out = ArrayList<IntArray>()
        return try {
            val g = IntArray(h * w) { full }
            if (ctx.propagate(g, BooleanArray(h) { true }, BooleanArray(w) { true })) ctx.branchAll(g, out, limit)
            out.map { sol -> IntArray(h * w) { Integer.numberOfTrailingZeros(sol[it]) } }
        } catch (e: Timeout) { null }
    }

    private class Ctx(val h: Int, val w: Int, val R: List<List<Block>?>, val C: List<List<Block>?>, val deadline: Long) {
        val rowL = R.map { l -> l?.let { IntArray(it.size) { i -> it[i].len } } }
        val rowC = R.map { l -> l?.let { IntArray(it.size) { i -> it[i].color } } }
        val colL = C.map { l -> l?.let { IntArray(it.size) { i -> it[i].len } } }
        val colC = C.map { l -> l?.let { IntArray(it.size) { i -> it[i].color } } }

        /** Enumeración de soluciones (sin sondeo, para no descartar nada) hasta [limit]. */
        fun branchAll(g: IntArray, out: MutableList<IntArray>, limit: Int) {
            if (out.size >= limit) return
            if (System.currentTimeMillis() > deadline) throw Timeout()
            var best = -1; var bestBits = 99
            for (i in g.indices) { val b = Integer.bitCount(g[i]); if (b > 1 && b < bestBits) { best = i; bestBits = b; if (b == 2) break } }
            if (best < 0) { out.add(g); return }
            for (o in options(g[best])) {
                val ng = tryValue(g, best, o) ?: continue
                branchAll(ng, out, limit)
                if (out.size >= limit) return
            }
        }
        var steps = 0

        fun propagate(g: IntArray, dirtyRows: BooleanArray, dirtyCols: BooleanArray): Boolean {
            val rowBuf = IntArray(w); val colBuf = IntArray(h)
            var any = true
            while (any) {
                any = false
                for (r in 0 until h) if (dirtyRows[r]) {
                    dirtyRows[r] = false; any = true
                    for (c in 0 until w) rowBuf[c] = g[r * w + c]
                    val rl = rowL[r] ?: continue
                    val res = solveLine(rowBuf, rl, rowC[r]!!) ?: return false
                    for (c in 0 until w) if (res[c] != g[r * w + c]) { g[r * w + c] = res[c]; dirtyCols[c] = true }
                }
                for (c in 0 until w) if (dirtyCols[c]) {
                    dirtyCols[c] = false; any = true
                    for (r in 0 until h) colBuf[r] = g[r * w + c]
                    val cl = colL[c] ?: continue
                    val res = solveLine(colBuf, cl, colC[c]!!) ?: return false
                    for (r in 0 until h) if (res[r] != g[r * w + c]) { g[r * w + c] = res[r]; dirtyRows[r] = true }
                }
                if ((++steps and 63) == 0 && System.currentTimeMillis() > deadline) throw Timeout()
            }
            return true
        }

        fun search(start: IntArray): IntArray? {
            val g = start.copyOf()
            probeHelps = false; started = false; hint = -1
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

        /** Sondeo (failed-literal). Si asigna celdas, se repite en cada nodo; si no, se ramifica por impacto. */
        private var probeHelps = false
        private var started = false
        private var hint = -1

        private fun probe(g: IntArray, thorough: Boolean): Boolean {
            hint = -1
            val unknown = ArrayList<Int>()
            for (i in g.indices) if (Integer.bitCount(g[i]) > 1) unknown.add(i)
            if (unknown.isEmpty()) return true
            val budget = if (thorough) Int.MAX_VALUE else when {
                unknown.size > 2500 -> 300
                unknown.size > 900 -> 600
                else -> minOf(1400, unknown.size * 3)
            }
            var tries = 0
            var assigned = 0
            var changed = true
            var best = -1; var bestScore = -1
            while (changed) {
                changed = false
                for (i in unknown) {
                    if (Integer.bitCount(g[i]) < 2) continue
                    if (tries >= budget) break
                    var mask = 0; var maxDed = 0
                    for (o in options(g[i])) {
                        tries++
                        val ng = tryValue(g, i, o) ?: continue
                        mask = mask or o
                        var ded = 0
                        for (k in ng.indices) if (Integer.bitCount(g[k]) > 1 && Integer.bitCount(ng[k]) == 1) ded++
                        if (ded > maxDed) maxDed = ded
                    }
                    if (mask == 0) return false
                    if (mask != g[i]) {
                        g[i] = mask
                        val dr = BooleanArray(h); val dc = BooleanArray(w); dr[i / w] = true; dc[i % w] = true
                        if (!propagate(g, dr, dc)) return false
                        changed = true; assigned++
                    } else if (maxDed > bestScore) { bestScore = maxDed; best = i }
                }
                if (tries >= budget) break
            }
            if (assigned > 0) probeHelps = true
            hint = best
            return true
        }

        private fun tryLine(g: IntArray, isRow: Boolean, idx: Int, place: IntArray): IntArray? {
            val ng = g.copyOf()
            val dr = BooleanArray(h); val dc = BooleanArray(w)
            if (isRow) {
                for (c in 0 until w) {
                    val v = place[c]
                    if (ng[idx * w + c] and v == 0) return null
                    if (ng[idx * w + c] != v) { ng[idx * w + c] = v; dc[c] = true }
                }
                dr[idx] = true
            } else {
                for (r in 0 until h) {
                    val v = place[r]
                    if (ng[r * w + idx] and v == 0) return null
                    if (ng[r * w + idx] != v) { ng[r * w + idx] = v; dr[r] = true }
                }
                dc[idx] = true
            }
            return if (propagate(ng, dr, dc)) ng else null
        }

        /** Línea no resuelta con 2..[maxN] colocaciones (contadas en O(n·k)). */
        private fun fewestLine(g: IntArray, maxN: Int): Triple<Boolean, Int, ArrayList<IntArray>>? {
            var best: Triple<Boolean, Int, ArrayList<IntArray>>? = null
            var bestN = maxN + 1
            val rowBuf = IntArray(w)
            for (r in 0 until h) {
                val rl = rowL[r] ?: continue
                if ((0 until w).all { Integer.bitCount(g[r * w + it]) == 1 }) continue
                for (c in 0 until w) rowBuf[c] = g[r * w + c]
                val npl = Solver.countPlacements(rowBuf, rl, rowC[r]!!)
                if (npl !in 2 until bestN) continue
                val pls = Solver.listPlacements(rowBuf, rl, rowC[r]!!, npl.toInt()) ?: continue
                if (pls.size.toLong() != npl) continue
                bestN = pls.size; best = Triple(true, r, pls)
                if (bestN == 2) return best
            }
            val colBuf = IntArray(h)
            for (c in 0 until w) {
                val cl = colL[c] ?: continue
                if ((0 until h).all { Integer.bitCount(g[it * w + c]) == 1 }) continue
                for (r in 0 until h) colBuf[r] = g[r * w + c]
                val npl = Solver.countPlacements(colBuf, cl, colC[c]!!)
                if (npl !in 2 until bestN) continue
                val pls = Solver.listPlacements(colBuf, cl, colC[c]!!, npl.toInt()) ?: continue
                if (pls.size.toLong() != npl) continue
                bestN = pls.size; best = Triple(false, c, pls)
                if (bestN == 2) return best
            }
            return best
        }

        private fun branch(g: IntArray): IntArray? {
            if (System.currentTimeMillis() > deadline) throw Timeout()
            if (g.all { Integer.bitCount(it) == 1 }) return g
            val first = !started
            started = true
            // Raíz: siempre completo. Luego, completo si el sondeo está asignando celdas.
            val thorough = first || probeHelps
            if (!probe(g, thorough)) return null
            if (g.all { Integer.bitCount(it) == 1 }) return g
            // Si el sondeo no avanza, ramificar por línea (2..5) e impacto; si avanza, MRV clásico.
            if (!probeHelps) fewestLine(g, 5)?.let { (isRow, idx, pls) ->
                for (pl in pls) {
                    val ng = tryLine(g, isRow, idx, pl) ?: continue
                    branch(ng)?.let { return it }
                }
                return null
            }
            var best = if (!probeHelps) hint else -1
            if (best < 0 || Integer.bitCount(g[best]) < 2) {
                var bestBits = 99; best = -1
                for (i in g.indices) {
                    val b = Integer.bitCount(g[i])
                    if (b > 1 && b < bestBits) { best = i; bestBits = b; if (b == 2) break }
                }
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

    /**
     * Hasta [cap] colocaciones exactas de una línea (cada celda un solo bit).
     * Devuelve null si hay más de [cap] (demasiadas para ramificar).
     */
    /** Número de colocaciones válidas de una línea (tope 1_000_001). */
    fun countPlacements(cells: IntArray, lens: IntArray, cols: IntArray): Long {
        val n = cells.size; val k = lens.size
        val cap = 1_000_001L
        fun add(a: Long, b: Long): Long { val s = a + b; return if (s < 0L || s > cap) cap else s }
        val B = Array(k + 1) { LongArray(n + 1) }
        val E = Array(k + 1) { LongArray(n + 1) }
        B[0][0] = 1
        val badPref = Array(k) { j ->
            val bit = 1 shl cols[j]
            val p = IntArray(n + 1)
            for (i in 0 until n) p[i + 1] = p[i] + if (cells[i] and bit == 0) 1 else 0
            p
        }
        for (i in 0..n) for (j in 0..k) {
            val b = B[j][i]; val e = E[j][i]
            if (b == 0L && e == 0L) continue
            if (i < n && cells[i] and 1 != 0) B[j][i + 1] = add(B[j][i + 1], add(b, e))
            if (j < k) {
                val L = lens[j]
                if (i + L <= n && badPref[j][i + L] - badPref[j][i] == 0) {
                    var ways = b
                    if (e != 0L && (j == 0 || cols[j - 1] != cols[j])) ways = add(ways, e)
                    if (ways != 0L) E[j + 1][i + L] = add(E[j + 1][i + L], ways)
                }
            }
        }
        return add(B[k][n], E[k][n])
    }

    fun listPlacements(cells: IntArray, lens: IntArray, cols: IntArray, cap: Int): ArrayList<IntArray>? {
        val n = cells.size
        val out = ArrayList<IntArray>()
        val line = IntArray(n)
        fun rec(i: Int, j: Int, lastColor: Int): Boolean {
            if (out.size > cap) return false
            if (j == lens.size) {
                if ((i until n).all { cells[it] and 1 != 0 }) {
                    for (t in i until n) line[t] = 1
                    out.add(line.copyOf())
                }
                return true
            }
            val L = lens[j]; val c = cols[j]; val bit = 1 shl c
            val start0 = if (j > 0 && lastColor == c) i + 1 else i
            for (s in start0..n - L) {
                if ((i until s).any { cells[it] and 1 == 0 }) break
                if ((s until s + L).any { cells[it] and bit == 0 }) continue
                for (t in i until s) line[t] = 1
                for (t in s until s + L) line[t] = bit
                if (!rec(s + L, j + 1, c)) return false
            }
            return true
        }
        return if (rec(0, 0, 0) && out.size <= cap) out else null
    }
}
