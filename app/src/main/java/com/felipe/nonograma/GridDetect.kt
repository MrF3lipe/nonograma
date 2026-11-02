package com.felipe.nonograma

import kotlin.math.abs

/**
 * Detección de la rejilla en una captura (píxeles ARGB, sin dependencias de Android → testeable).
 * Busca líneas horizontales/verticales largas (bordes de luminancia), las agrupa y encuentra la
 * secuencia regularmente espaciada más grande en cada eje; combina ambos ejes de forma consistente.
 */
object GridDetect {
    data class Grid(val left: Int, val top: Int, val right: Int, val bottom: Int, val rows: Int, val cols: Int)
    /** Línea: posición y extensión a lo largo del eje perpendicular. */
    data class Line(val pos: Int, val a: Int, val b: Int)
    data class Chain(val start: Int, val end: Int, val cells: Int, val a: Int, val b: Int)

    private const val T = 22

    fun lum(p: Int) = (((p shr 16) and 255) * 3 + ((p shr 8) and 255) * 6 + (p and 255)) / 10

    fun detect(px: IntArray, w: Int, h: Int, minCell: Int = 6): Grid? {
        val L = IntArray(w * h) { lum(px[it]) }
        val hl = lines(L, w, h, horizontal = true)
        val vl = lines(L, w, h, horizontal = false)
        val hc = chains(hl, minCell)
        val vc = chains(vl, minCell)
        var best: Grid? = null; var bestScore = 0
        for (r in hc) for (c in vc) {
            // r: filas (top..bottom, extensión x a..b); c: columnas (left..right, extensión y a..b)
            val tolX = maxOf(8, (c.end - c.start) / 20); val tolY = maxOf(8, (r.end - r.start) / 20)
            val xOk = r.a <= c.start + tolX && r.b >= c.end - tolX
            val yOk = c.a <= r.start + tolY && c.b >= r.end - tolY
            if (!xOk || !yOk) continue
            val cw = (c.end - c.start).toFloat() / c.cells; val ch = (r.end - r.start).toFloat() / r.cells
            if (cw / ch > 1.6f || ch / cw > 1.6f) continue // celdas aproximadamente cuadradas
            val score = r.cells * c.cells
            if (score > bestScore) { bestScore = score; best = Grid(c.start, r.start, c.end, r.end, r.cells, c.cells) }
        }
        return best
    }

    /** Líneas largas: filas (o columnas) con un tramo largo de bordes, fusionando posiciones cercanas. */
    fun lines(L: IntArray, w: Int, h: Int, horizontal: Boolean): List<Line> {
        val n = if (horizontal) h else w          // posiciones
        val m = if (horizontal) w else h          // longitud a lo largo
        val minLen = maxOf(40, m / 6)
        val raw = ArrayList<Line>()
        for (p in 1 until n) {
            var bestLen = 0; var bestA = 0; var bestB = 0
            var runStart = -1; var lastHit = -10
            for (q in 0 until m) {
                val i = if (horizontal) p * w + q else q * w + p
                val j = if (horizontal) i - w else i - 1
                if (abs(L[i] - L[j]) >= T) {
                    if (q - lastHit > 8) runStart = q
                    lastHit = q
                    val len = q - runStart + 1
                    if (len > bestLen) { bestLen = len; bestA = runStart; bestB = q }
                }
            }
            if (bestLen >= minLen) raw.add(Line(p, bestA, bestB))
        }
        // fusionar bordes contiguos (líneas gruesas, huecos entre celdas)
        val out = ArrayList<Line>()
        var i = 0
        while (i < raw.size) {
            var j = i; var a = raw[i].a; var b = raw[i].b
            while (j + 1 < raw.size && raw[j + 1].pos - raw[j].pos <= 4) { j++; a = minOf(a, raw[j].a); b = maxOf(b, raw[j].b) }
            out.add(Line((raw[i].pos + raw[j].pos) / 2, a, b)); i = j + 1
        }
        return out
    }

    /** Secuencias de líneas con extensión parecida y espaciado regular; devuelve las mejores. */
    fun chains(lines: List<Line>, minCell: Int): List<Chain> {
        val res = ArrayList<Chain>()
        val ls = lines.sortedBy { it.pos }
        for (i in ls.indices) for (j in i + 1 until ls.size) {
            val s = (ls[j].pos - ls[i].pos).toFloat()
            if (s < minCell) continue
            if (!similar(ls[i], ls[j])) continue
            var hits = 2; var misses = 0; var lastPos = ls[j].pos.toFloat(); var cells = 1; var lastHit = ls[j].pos
            var a = minOf(ls[i].a, ls[j].a); var b = maxOf(ls[i].b, ls[j].b)
            var k = j + 1
            while (true) {
                val exp = lastPos + s
                var found: Line? = null
                while (k < ls.size && ls[k].pos < exp - s * 0.2f) k++
                var kk = k
                while (kk < ls.size && ls[kk].pos <= exp + s * 0.2f) { if (similar(ls[i], ls[kk])) { found = ls[kk]; break }; kk++ }
                if (found != null) {
                    hits++; cells += 1 + misses; misses = 0; lastPos = found.pos.toFloat(); lastHit = found.pos
                    a = minOf(a, found.a); b = maxOf(b, found.b); k = kk + 1
                } else {
                    misses++
                    if (misses > 1) break
                    lastPos = exp
                }
            }
            if (cells < 2) continue
            if (hits < 0.8f * (cells + 1)) continue
            res.add(Chain(ls[i].pos, lastHit, cells, a, b))
        }
        // mantener las de más celdas
        return res.sortedByDescending { it.cells }.take(40)
    }

    private fun similar(x: Line, y: Line): Boolean {
        val len = maxOf(x.b - x.a, y.b - y.a)
        val tol = maxOf(10, len / 12)
        return abs(x.a - y.a) <= tol && abs(x.b - y.b) <= tol
    }
}
