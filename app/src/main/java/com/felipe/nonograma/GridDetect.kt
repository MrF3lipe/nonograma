package com.felipe.nonograma

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Detección de la rejilla en una captura (píxeles ARGB, sin dependencias de Android → testeable).
 * Busca líneas horizontales/verticales largas (bordes de luminancia), las agrupa y encuentra la
 * secuencia regularmente espaciada más grande en cada eje; combina ambos ejes de forma consistente.
 *
 * En capturas con fondo con textura o pistas enmarcadas (celdas beige), no se une un tramo de la
 * rejilla con otro disjunto del fondo, se tolera el jitter de celdas pequeñas, y si el marco
 * incluye las pistas se recorta al área jugable (esquina vacía + bandas con dígitos).
 */
object GridDetect {
    data class Grid(val left: Int, val top: Int, val right: Int, val bottom: Int, val rows: Int, val cols: Int)
    /** Línea: posición y extensión a lo largo del eje perpendicular. */
    data class Line(val pos: Int, val a: Int, val b: Int)
    data class Chain(val start: Int, val end: Int, val cells: Int, val a: Int, val b: Int)

    private const val T = 22

    fun lum(p: Int) = (((p shr 16) and 255) * 3 + ((p shr 8) and 255) * 6 + (p and 255)) / 10

    /** Prueba varios umbrales/modos (capturas nítidas, reescaladas o con JPEG fuerte) y se queda con la mayor rejilla de interior uniforme. */
    fun detect(px: IntArray, w: Int, h: Int, minCell: Int = 6): Grid? {
        val L = IntArray(w * h) { lum(px[it]) }
        var best: Grid? = null; var bestScore = Int.MIN_VALUE
        for ((t, ridge) in listOf(22 to false, 14 to false, 12 to true)) {
            val raw = detectWith(L, w, h, minCell, t, ridge) ?: continue
            val g = shrinkToPlayable(L, w, raw)
            val score = scoreGrid(L, w, g)
            if (score > bestScore) { bestScore = score; best = g }
        }
        return best?.let { extendMissingPlayableEdge(L, w, it) }
    }

    /**
     * Si la cadena de columnas empezó un paso más adentro (línea izquierda partida al reescalar),
     * se añade esa columna solo cuando hay un borde fuerte y no parece una columna de pistas.
     */

    private fun extendMissingPlayableEdge(L: IntArray, w: Int, g: Grid): Grid {
        val h = L.size / w
        val cw = (g.right - g.left).toFloat() / g.cols
        val ch = (g.bottom - g.top).toFloat() / g.rows
        if (cw < 8f) return g
        val x = (g.left - cw).roundToInt()
        if (x < 2) return g
        val y0 = g.top.coerceAtLeast(0); val y1 = g.bottom.coerceAtMost(h)
        if (y1 - y0 < 8) return g
        var edge = 0; var n = 0; var y = y0
        while (y < y1) {
            if (x in 1 until w) {
                if (kotlin.math.abs(L[y * w + x] - L[y * w + x - 1]) >= 18) edge++
            }
            n++; y += 2
        }
        if (n == 0 || edge < n * 65 / 100) return g
        // columna de pistas: tinta tipo dígito en muchas filas (no un recorte jugable perdido)
        fun digitish(c0: Int, c1: Int): Int {
            val ix = maxOf(1, ((c1 - c0) * 0.25f).toInt())
            var rows = 0
            for (r in 0 until g.rows) {
                val a = (g.top + r * ch).toInt() + ix; val b = (g.top + (r + 1) * ch).toInt() - ix
                if (b - a < 2) continue
                var dark = 0; var tot = 0; var sum = 0
                for (yy in a until b.coerceAtMost(h)) {
                    val row = yy * w
                    for (xx in (c0 + ix).coerceAtLeast(0) until (c1 - ix).coerceAtMost(w)) {
                        sum += L[row + xx]; tot++; if (L[row + xx] < 140) dark++
                    }
                }
                if (tot > 0) {
                    val frac = dark.toFloat() / tot
                    if (frac in 0.08f..0.45f) rows++
                }
            }
            return rows
        }
        val clueHits = digitish(x, g.left)
        if (clueHits > 0.5f * g.rows) return g
        return Grid(x, g.top, g.right, g.bottom, g.rows, g.cols + 1)
    }

    private fun detectWith(L: IntArray, w: Int, h: Int, minCell: Int, t: Int, ridge: Boolean): Grid? {
        val hl = lines(L, w, h, horizontal = true, t = t, ridge = ridge)
        val vl = lines(L, w, h, horizontal = false, t = t, ridge = ridge)
        val hc = chains(hl, minCell)
        val vc = chains(vl, minCell)
        var best: Grid? = null; var bestScore = Int.MIN_VALUE
        for (r in hc) for (c in vc) {
            // r: filas (top..bottom, extensión x a..b); c: columnas (left..right, extensión y a..b)
            val tolX = maxOf(8, (c.end - c.start) / 20); val tolY = maxOf(8, (r.end - r.start) / 20)
            val xOk = r.a <= c.start + tolX && r.b >= c.end - tolX
            val yOk = c.a <= r.start + tolY && c.b >= r.end - tolY
            if (!xOk || !yOk) continue
            val cw = (c.end - c.start).toFloat() / c.cells; val ch = (r.end - r.start).toFloat() / r.cells
            if (cw / ch > 1.6f || ch / cw > 1.6f) continue // celdas aproximadamente cuadradas
            val g = Grid(c.start, r.start, c.end, r.end, r.cells, c.cells)
            val score = scoreGrid(L, w, g)
            if (score > bestScore) { bestScore = score; best = g }
        }
        return best
    }

    /**
     * Celdas de papel (varianza baja) suman; celdas con textura (fondo floral) restan más.
     * Así un recuadro que se mete en el wallpaper no gana a la rejilla real.
     */
    private fun scoreGrid(L: IntArray, w: Int, g: Grid): Int {
        val acc = cellTextures(L, w, g)
        if (acc.isEmpty()) return g.rows * g.cols
        // Solo se penaliza textura clara (wallpaper). Un poco de JPEG no debe hacer que un sub-recuadro gane.
        val bad = acc.count { it >= 800 }
        return g.rows * g.cols * 10_000 - bad * 50_000
    }

    /** Varianza de luminancia de cada celda (recortada). */
    private fun cellTextures(L: IntArray, w: Int, g: Grid): List<Int> {
        val cw = (g.right - g.left).toFloat() / g.cols
        val ch = (g.bottom - g.top).toFloat() / g.rows
        val acc = ArrayList<Int>()
        val ix = maxOf(1, (cw * 0.2f).toInt()); val iy = maxOf(1, (ch * 0.2f).toInt())
        for (r in 0 until g.rows) {
            val y0 = (g.top + r * ch).toInt() + iy; val y1 = (g.top + (r + 1) * ch).toInt() - iy
            if (y1 - y0 < 2) continue
            for (c in 0 until g.cols) {
                val x0 = (g.left + c * cw).toInt() + ix; val x1 = (g.left + (c + 1) * cw).toInt() - ix
                if (x1 - x0 < 2) continue
                var n = 0; var sum = 0; var sum2 = 0
                var y = y0
                while (y < y1) {
                    val row = y * w
                    var x = x0
                    while (x < x1) {
                        val v = L[row + x]
                        sum += v; sum2 += v * v; n++; x++
                    }
                    y++
                }
                if (n >= 4) { val mean = sum / n; acc.add(sum2 / n - mean * mean) }
            }
        }
        return acc
    }

    /**
     * Si la rejilla detectada incluye el marco de pistas (esquina vacía arriba-izq y dígitos en las
     * bandas superior e izquierda), se recorta al área jugable. Si no hay ese patrón, se deja igual.
     */
    private fun shrinkToPlayable(L: IntArray, w: Int, g: Grid): Grid {
        val cw = (g.right - g.left).toFloat() / g.cols
        val ch = (g.bottom - g.top).toFloat() / g.rows
        // inset amplio para no contar el trazo de la rejilla como tinta de dígito
        val ix = maxOf(1, (cw * 0.28f).toInt()); val iy = maxOf(1, (ch * 0.28f).toInt())
        fun ink(r: Int, c: Int): Float {
            val x0 = (g.left + c * cw).toInt() + ix; val x1 = (g.left + (c + 1) * cw).toInt() - ix
            val y0 = (g.top + r * ch).toInt() + iy; val y1 = (g.top + (r + 1) * ch).toInt() - iy
            if (x1 <= x0 || y1 <= y0) return 0f
            var n = 0; var sum = 0
            for (y in y0 until y1) {
                val row = y * w
                for (x in x0 until x1) { sum += L[row + x]; n++ }
            }
            if (n == 0) return 0f
            val thr = minOf(140, sum / n - 35)
            var dark = 0
            for (y in y0 until y1) {
                val row = y * w
                for (x in x0 until x1) if (L[row + x] <= thr) dark++
            }
            return dark.toFloat() / n
        }
        val rows = g.rows; val cols = g.cols
        val ink = Array(rows) { r -> FloatArray(cols) { c -> ink(r, c) } }
        fun digit(v: Float) = v in 0.08f..0.55f
        // Las pistas se alinean hacia la rejilla: la última fila/columna de la banda debe tener dígitos.
        fun topOk(r0: Int, c0: Int): Boolean {
            if (r0 == 0) return true
            val n = (c0 until cols).count { c -> digit(ink[r0 - 1][c]) }
            return n > 0.6f * (cols - c0)
        }
        fun leftOk(r0: Int, c0: Int): Boolean {
            if (c0 == 0) return true
            val n = (r0 until rows).count { r -> digit(ink[r][c0 - 1]) }
            return n > 0.6f * (rows - r0)
        }
        val maxR = minOf(12, rows - 2); val maxC = minOf(12, cols - 2)
        var bestR = 0
        for (r0 in 1..maxR) if (topOk(r0, 0)) { bestR = r0; break }
        var bestC = 0
        for (c0 in 1..maxC) if (leftOk(bestR, c0)) { bestC = c0; break }
        // Solo se recorta si hay banda arriba Y a la izquierda, de al menos 2 celdas cada una
        // (una sola celda suele ser la primera fila/columna jugable con bloques que parecen dígitos).
        if (bestR < 2 || bestC < 2) return g
        var cornerOk = 0
        fun empty(v: Float) = v < 0.04f
        for (r in 0 until bestR) for (c in 0 until bestC) if (empty(ink[r][c])) cornerOk++
        if (cornerOk < 0.7f * bestR * bestC) return g
        return Grid(
            (g.left + bestC * cw).roundToInt(), (g.top + bestR * ch).roundToInt(),
            g.right, g.bottom, rows - bestR, cols - bestC)
    }

    /** Líneas largas: filas (o columnas) con tramos largos de bordes. Se conservan todos los tramos (no solo el más largo) y no se fusionan extensiones disjuntas. */
    fun lines(L: IntArray, w: Int, h: Int, horizontal: Boolean, t: Int = T, ridge: Boolean = false): List<Line> {
        val n = if (horizontal) h else w          // posiciones
        val m = if (horizontal) w else h          // longitud a lo largo
        val minLen = maxOf(40, m / 6)
        val raw = ArrayList<Line>()
        for (p in 2 until n - 2) {
            var runStart = -1; var lastHit = -10
            fun flush() {
                if (runStart >= 0 && lastHit - runStart + 1 >= minLen) raw.add(Line(p, runStart, lastHit))
                runStart = -1
            }
            for (q in 0 until m) {
                val i = if (horizontal) p * w + q else q * w + p
                val j = if (horizontal) i - w else i - 1
                val j2 = if (horizontal) i - 2 * w else i - 2
                val k2 = if (horizontal) i + 2 * w else i + 2
                // borde a 1 o 2 px (capturas reescaladas/borrosas) o cresta de una línea fina
                val hit = if (ridge) abs(2 * L[i] - L[j2] - L[k2]) >= 2 * t
                          else abs(L[i] - L[j]) >= t || abs(L[i] - L[j2]) >= t + 8
                if (hit) {
                    if (runStart >= 0 && q - lastHit > 8) flush()
                    if (runStart < 0) runStart = q
                    lastHit = q
                }
            }
            flush()
        }
        // fusionar bordes contiguos de la misma familia (líneas gruesas); no unir un tramo de la rejilla con el fondo
        raw.sortWith(compareBy({ it.pos }, { it.a }))
        val out = ArrayList<Line>()
        val used = BooleanArray(raw.size)
        for (i in raw.indices) {
            if (used[i]) continue
            var a = raw[i].a; var b = raw[i].b; var posSum = raw[i].pos; var n = 1
            used[i] = true
            val pos0 = raw[i].pos
            for (j in i + 1 until raw.size) {
                if (raw[j].pos - pos0 > 4) break
                if (used[j]) continue
                if (!similar(Line(0, a, b), raw[j])) continue
                used[j] = true
                a = minOf(a, raw[j].a); b = maxOf(b, raw[j].b)
                posSum += raw[j].pos; n++
            }
            out.add(Line(posSum / n, a, b))
        }
        return out.sortedBy { it.pos }
    }

    /** Secuencias de líneas con extensión parecida y espaciado regular; devuelve las mejores. */
    fun chains(lines: List<Line>, minCell: Int): List<Chain> {
        val res = ArrayList<Chain>()
        val ls = lines.sortedBy { it.pos }
        for (i in ls.indices) for (j in i + 1 until ls.size) {
            var s = (ls[j].pos - ls[i].pos).toFloat()
            if (s < minCell) continue
            if (!similar(ls[i], ls[j])) continue
            var hits = 2; var misses = 0; var lastPos = ls[j].pos.toFloat(); var cells = 1; var lastHit = ls[j].pos
            var a = minOf(ls[i].a, ls[j].a); var b = maxOf(ls[i].b, ls[j].b)
            var k = j + 1
            while (true) {
                val exp = lastPos + s
                val tol = maxOf(s * 0.2f, 5f)
                var found: Line? = null
                while (k < ls.size && ls[k].pos < exp - tol) k++
                var kk = k
                while (kk < ls.size && ls[kk].pos <= exp + tol) { if (similar(ls[i], ls[kk])) { found = ls[kk]; break }; kk++ }
                if (found != null) {
                    hits++; cells += 1 + misses; misses = 0; lastPos = found.pos.toFloat(); lastHit = found.pos
                    a = minOf(a, found.a); b = maxOf(b, found.b); k = kk + 1
                } else {
                    misses++
                    if (misses > 2) break
                    lastPos = exp
                }
            }
            if (cells < 2) continue
            if (hits < 0.7f * (cells + 1)) continue
            res.add(Chain(ls[i].pos, lastHit, cells, a, b))
        }
        // mantener las de más celdas
        return res.sortedByDescending { it.cells }.take(80)
    }

    /** Misma familia de líneas: extensiones que se solapan al menos la mitad de la más corta... y de la más larga. */
    private fun similar(x: Line, y: Line): Boolean {
        val ov = minOf(x.b, y.b) - maxOf(x.a, y.a)
        val lx = x.b - x.a; val ly = y.b - y.a
        return ov >= 0.8f * minOf(lx, ly) && ov >= 0.45f * maxOf(lx, ly)
    }

    /**
     * Recorte automático del marco de pistas: desde el borde de la rejilla hacia afuera, se para
     * cuando el fondo deja de ser papel (baja textura, claro) y aparece wallpaper o una barra oscura.
     * [from] es el borde (izquierda o arriba de la rejilla). [a0,a1] es el tramo perpendicular.
     */
    fun paperEdge(px: IntArray, w: Int, from: Int, a0: Int, a1: Int, verticalStrip: Boolean, maxSpan: Int): Int {
        val h = px.size / w
        if (from <= 0) return 0
        val span = minOf(maxSpan, from)
        val skip = minOf(6, span)
        val start = (from - skip).coerceAtLeast(1)
        val win = 7
        val buf = IntArray(4096)
        fun collect(pos: Int): Int {
            val p0 = (pos - win / 2).coerceAtLeast(0)
            val p1 = (pos + win / 2 + 1).coerceAtMost(if (verticalStrip) w else h)
            val b0 = a0.coerceAtLeast(0)
            val b1 = a1.coerceAtMost(if (verticalStrip) h else w)
            var n = 0
            if (verticalStrip) {
                var y = b0
                while (y < b1 && n < buf.size) {
                    val row = y * w
                    var x = p0
                    while (x < p1 && n < buf.size) { buf[n++] = lum(px[row + x]); x++ }
                    y += 2
                }
            } else {
                var y = p0
                while (y < p1 && n < buf.size) {
                    val row = y * w
                    var x = b0
                    while (x < b1 && n < buf.size) { buf[n++] = lum(px[row + x]); x += 2 }
                    y++
                }
            }
            return n
        }
        fun madMed(n: Int): Pair<Int, Int> {
            if (n < 4) return 0 to 255
            java.util.Arrays.sort(buf, 0, n)
            val med = buf[n / 2]
            for (i in 0 until n) buf[i] = kotlin.math.abs(buf[i] - med)
            java.util.Arrays.sort(buf, 0, n)
            return buf[n / 2] to med
        }
        val (baseMad, baseLum) = madMed(collect(start))
        var lastGood = start
        var pos = start - 1
        val stop = (from - span).coerceAtLeast(0)
        var badStreak = 0
        val need = 8
        while (pos >= stop) {
            val (mad, med) = madMed(collect(pos))
            val bad = mad > maxOf(18, baseMad * 5) || kotlin.math.abs(med - baseLum) > 80
            if (bad) {
                badStreak++
                if (badStreak >= need) break
            } else {
                badStreak = 0
                lastGood = pos
            }
            pos--
        }
        return lastGood
    }

    /**
     * Izquierda del marco de las celdas de pista: la primera línea vertical fuerte alineada
     * con la cuadrícula (paso = celda), midiendo desde el borde izquierdo de la rejilla.
     * Si no hay marco, se deja el borde del papel. Evita leer el trazo del marco o el wallpaper como dígitos.
     */
    fun clueFrameEdge(px: IntArray, w: Int, paper: Int, gridLeft: Int, y0: Int, y1: Int, cell: Int): Int {
        // Sin borde de papel no se busca: en un wallpaper floral hay falsos filos alineados a la rejilla.
        if (paper <= 0 || cell < 4 || gridLeft - paper < cell) return paper
        val hImg = px.size / w
        val ya = y0.coerceAtLeast(0)
        val yb = y1.coerceAtMost(hImg)
        val tol = maxOf(3, (cell * 0.3f).toInt())
        val samples = maxOf(1, (yb - ya + 1) / 2)
        val need = maxOf(6, samples * 65 / 100)
        var x = paper.coerceAtLeast(0)
        // el marco está junto al borde del papel, no en medio de las pistas
        val limit = minOf(paper + cell * 2 + tol, gridLeft - cell + tol)
        while (x < limit && x + 1 < w) {
            var edge = 0
            var y = ya
            while (y < yb) {
                val lum0 = lum(px[y * w + x])
                val lum1 = lum(px[y * w + x + 1])
                if (kotlin.math.abs(lum0 - lum1) > 36) edge++
                y += 2
            }
            if (edge >= need) {
                val dist = gridLeft - x
                val k = (dist + cell / 2) / cell
                if (k >= 1 && kotlin.math.abs(dist - k * cell) <= tol) {
                    return (x + maxOf(2, cell / 12)).coerceAtMost(gridLeft - cell)
                }
            }
            x++
        }
        return paper
    }
}
