package com.felipe.nonograma

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Segmentación propia de los dígitos de las pistas (sin ML Kit, testeable en JVM):
 * separa glifos por proyecciones de "tinta" (píxeles distintos del fondo), los agrupa en números
 * y deja a ML Kit solo el reconocimiento de cada glifo ya aislado y normalizado.
 */
object Segment {
    /** Glifo en coordenadas de pantalla. rgb = color medio de la tinta. */
    class Glyph(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val rgb: Int) {
        /** Tinta del glifo (con 1 px de margen) calculada con el fondo de toda la banda. */
        var alpha: FloatArray? = null; var aw = 0; var ah = 0
        val cx get() = (x0 + x1) / 2f
        val cy get() = (y0 + y1) / 2f
        val h get() = y1 - y0
        val w get() = x1 - x0
    }
    /** Número de pista: uno o varios glifos. highlight = unido por color destacado (no es color de pista). */
    class Group(val glyphs: List<Glyph>, var rgb: Int, val highlight: Boolean = false) {
        val x0 get() = glyphs.minOf { it.x0 }; val x1 get() = glyphs.maxOf { it.x1 }
        val y0 get() = glyphs.minOf { it.y0 }; val y1 get() = glyphs.maxOf { it.y1 }
        val cx get() = (x0 + x1) / 2f; val cy get() = (y0 + y1) / 2f
    }

    class Ink(val w: Int, val h: Int, val left: Int, val top: Int, val a: FloatArray, val px: IntArray) {
        fun at(x: Int, y: Int) = a[(y - top) * w + (x - left)]
    }

    fun dist(a: Int, b: Int) = abs(((a shr 16) and 255) - ((b shr 16) and 255)) + abs(((a shr 8) and 255) - ((b shr 8) and 255)) + abs((a and 255) - (b and 255))

    /** Fondo = color más frecuente (cuantizado). */
    fun background(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int): Int {
        val m = HashMap<Int, Int>()
        for (y in y0 until y1 step 2) for (x in x0 until x1 step 2) { val q = px[y * w + x] and 0xF0F0F0; m[q] = (m[q] ?: 0) + 1 }
        return (m.maxByOrNull { it.value }?.key ?: 0) or 0xFF080808.toInt()
    }

    /** Mapa de tinta 0..1 normalizado por contraste (sirve para texto claro sobre oscuro y viceversa). */
    fun ink(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, loF: Float = 0.30f, hiF: Float = 0.65f): Ink? {
        val l = max(0, x0); val t = max(0, y0); val r = min(w, x1); val b = min(px.size / w, y1)
        if (r - l < 2 || b - t < 2) return null
        val bg = background(px, w, l, t, r, b)
        val iw = r - l; val ih = b - t
        val d = IntArray(iw * ih); var mx = 0
        for (y in 0 until ih) for (x in 0 until iw) { val v = dist(px[(t + y) * w + l + x], bg); d[y * iw + x] = v; if (v > mx) mx = v }
        if (mx < 150) return Ink(iw, ih, l, t, FloatArray(iw * ih), px) // sin texto
        val lo = mx * loF; val hi = mx * hiF
        val a = FloatArray(iw * ih) { ((d[it] - lo) / (hi - lo)).coerceIn(0f, 1f) }
        return Ink(iw, ih, l, t, a, px)
    }

    private fun runs(p: FloatArray, minVal: Float): List<IntArray> {
        val out = ArrayList<IntArray>(); var s = -1
        for (i in p.indices) {
            if (p[i] > minVal) { if (s < 0) s = i } else if (s >= 0) { out.add(intArrayOf(s, i)); s = -1 }
        }
        if (s >= 0) out.add(intArrayOf(s, p.size))
        return out
    }

    /** Separa runs demasiado anchos para un dígito (dígitos pegados, p. ej. "16") por el mínimo del perfil. */
    fun splitWide(p: FloatArray, rs: List<IntArray>, h: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (r in rs) {
            val wdt = r[1] - r[0]
            if (wdt <= h * 0.95f || wdt < 5) { out.add(r); continue }
            val a = r[0] + maxOf(1, (wdt * 0.2f).toInt()); val b = r[1] - maxOf(1, (wdt * 0.2f).toInt())
            var m = a; for (x in a until b) if (p[x] < p[m]) m = x
            out.add(intArrayOf(r[0], m)); out.add(intArrayOf(m + 1, r[1]))
        }
        return out
    }

    private fun glyphOf(ink: Ink, x0: Int, x1: Int, w: Int): Glyph? {
        // extensión vertical y color medio de la tinta fuerte
        var top = -1; var bot = -1; var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
        for (y in 0 until ink.h) {
            var any = false
            for (x in x0 until x1) {
                val v = ink.a[y * ink.w + x]
                if (v > 0.5f) {
                    any = true
                    val p = ink.px[(ink.top + y) * w + ink.left + x]
                    rs += (p shr 16) and 255; gs += (p shr 8) and 255; bs += p and 255; n++
                }
            }
            if (any) { if (top < 0) top = y; bot = y + 1 }
        }
        if (n < 4 || top < 0) return null
        val rgb = (0xFF shl 24) or ((rs / n).toInt() shl 16) or ((gs / n).toInt() shl 8) or (bs / n).toInt()
        val g = Glyph(ink.left + x0, ink.top + top, ink.left + x1, ink.top + bot, rgb)
        val ax0 = maxOf(0, x0 - 1); val ax1 = minOf(ink.w, x1 + 1); val ay0 = maxOf(0, top - 1); val ay1 = minOf(ink.h, bot + 1)
        g.aw = ax1 - ax0; g.ah = ay1 - ay0
        g.alpha = FloatArray(g.aw * g.ah) { ink.a[(ay0 + it / g.aw) * ink.w + ax0 + it % g.aw] }
        return g
    }

    /** Glifos de una banda de fila (izquierda→derecha). Recorta 15 % arriba/abajo para evitar separadores. */
    fun rowGlyphs(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, insetF: Float = 0.12f, loF: Float = 0.30f, hiF: Float = 0.65f): List<Glyph> {
        val inset = (y1 - y0) * insetF
        val ink = ink(px, w, x0, (y0 + inset).toInt(), x1, (y1 - inset).toInt(), loF, hiF) ?: return emptyList()
        val col = FloatArray(ink.w)
        for (y in 0 until ink.h) for (x in 0 until ink.w) col[x] += ink.a[y * ink.w + x]
        val out = ArrayList<Glyph>()
        for (r in runs(col, 0.6f)) {
            val g = glyphOf(ink, r[0], r[1], w) ?: continue
            if (g.h < (y1 - y0) * minOf(0.25f, loF * 0.8f)) continue // ruido (relativo al tamaño de dígito buscado)
            if (g.h > (y1 - y0) * 0.95f && g.w > (y1 - y0)) continue // línea/borde
            out.add(g)
        }
        // trozos de línea vertical (muy estrechos y más altos que los dígitos)
        if (out.size > 1) {
            val mh = out.map { it.h }.sorted()[out.size / 2]
            out.removeAll { it.w * 6 < it.h && it.h > mh * 1.4f }
        }
        return out
    }

    /** Glifos de una franja de columna (arriba→abajo); cada fila de texto puede tener varios dígitos. */
    fun colGroupsRaw(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, insetF: Float = 0.14f, loF: Float = 0.30f, hiF: Float = 0.65f): List<List<Glyph>> {
        // ventana interior (sin las líneas separadoras) para encontrar las líneas de texto, y ventana más
        // ancha para no recortar números que se salen de la celda (p. ej. "20" en la última columna).
        val inset = (x1 - x0) * insetF
        val outer = (x1 - x0) * minOf(insetF, 0.14f) * 0.25f
        val ink = ink(px, w, (x0 + outer).toInt(), y0, (x1 - outer).toInt(), y1, loF, hiF) ?: return emptyList()
        val inL = ((x0 + inset).toInt() - ink.left).coerceIn(0, ink.w); val inR = ((x1 - inset).toInt() - ink.left).coerceIn(inL, ink.w)
        val rowP = FloatArray(ink.h)
        for (y in 0 until ink.h) for (x in inL until inR) rowP[y] += ink.a[y * ink.w + x]
        val cellW = x1 - x0
        val out = ArrayList<List<Glyph>>()
        for (r in runs(rowP, 0.6f)) {
            val hgt = r[1] - r[0]
            if (hgt < cellW * minOf(0.2f, loF * 0.6f) || hgt > cellW * 1.5f) continue
            // sub-imagen de esa línea → glifos por columnas (solo los que tocan la ventana interior)
            val sub = Ink(ink.w, hgt, ink.left, ink.top + r[0], FloatArray(ink.w * hgt) { ink.a[(r[0] + it / ink.w) * ink.w + it % ink.w] }, px)
            val colP = FloatArray(sub.w)
            for (y in 0 until sub.h) for (x in 0 until sub.w) colP[x] += sub.a[y * sub.w + x]
            val gl = splitWide(colP, runs(colP, 0.6f), hgt).filter { it[1] > inL && it[0] < inR }.mapNotNull { glyphOf(sub, it[0], it[1], w) }
            if (gl.isNotEmpty()) out.add(gl)
        }
        return out
    }

    fun isNeutral(c: Int): Boolean {
        val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
        return max(r, max(g, b)) - min(r, min(g, b)) < 45
    }

    /**
     * Agrupa glifos de una fila en números:
     *  - si la línea usa espacios (huecos claramente distintos), une los glifos con hueco pequeño;
     *  - si los dígitos van pegados, cada glifo es un número, salvo glifos contiguos con un color
     *    destacado distinto del predominante de la línea (p. ej. "10" en amarillo).
     */
    fun groupRow(gl: List<Glyph>, domIn: Int? = null): List<Group> {
        if (gl.isEmpty()) return emptyList()
        val hgt = gl.map { it.h }.sorted()[gl.size / 2].toFloat()
        val gaps = (1 until gl.size).map { gl[it].x0 - gl[it - 1].x1 }
        val sortedGaps = gaps.sorted()
        val maxGap = sortedGaps.lastOrNull() ?: 0
        val minGap = sortedGaps.firstOrNull() ?: 0
        // solo si hay espacios claros entre números y los dígitos de un número van casi pegados
        val spaced = gaps.isNotEmpty() && maxGap > hgt * 0.6f && maxGap > 3f * max(1, minGap) && minGap <= hgt * 0.15f
        // color predominante de la línea
        val dom = domIn ?: dominant(gl)
        val out = ArrayList<Group>()
        var cur = mutableListOf(gl[0]); var hl = false
        for (i in 1 until gl.size) {
            val a = gl[i - 1]; val b = gl[i]
            // tinta de color en una línea neutra (o muy distinta del predominante): número destacado
            val chrom = !isNeutral(a.rgb) || !isNeutral(b.rgb)
            val differs = (isNeutral(dom) && chrom) || dist(a.rgb, dom) > 150
            val sameHighlight = chrom && dist(a.rgb, b.rgb) < 140 && differs && dist(b.rgb, dom) > 60 && dist(a.rgb, dom) > 60 && gaps[i - 1] <= hgt * 0.35f
            val tight = spaced && gaps[i - 1] < maxGap * 0.45f
            if (sameHighlight || tight) { cur.add(b); if (sameHighlight) hl = true }
            else { out.add(Group(cur, avg(cur), hl)); cur = mutableListOf(b); hl = false }
        }
        out.add(Group(cur, avg(cur), hl))
        return out
    }

    /** Color predominante de la tinta (preferir neutros: el texto normal suele ser blanco/negro). */
    fun dominant(gl: List<Glyph>): Int {
        if (gl.isEmpty()) return 0xFFFFFFFF.toInt()
        val neutral = gl.filter { isNeutral(it.rgb) }
        val pool = if (neutral.size * 3 >= gl.size) neutral else gl
        return pool.maxByOrNull { q -> pool.count { dist(it.rgb, q.rgb) < 90 } }!!.rgb
    }

    private fun avg(g: List<Glyph>): Int {
        var r = 0; var gg = 0; var b = 0
        for (q in g) { r += (q.rgb shr 16) and 255; gg += (q.rgb shr 8) and 255; b += q.rgb and 255 }
        val n = g.size
        return (0xFF shl 24) or ((r / n) shl 16) or ((gg / n) shl 8) or (b / n)
    }

    /** Rasgo de un glifo para clasificación por vecino más cercano: tinta remuestreada a 10x16 + proporción. */
    /** Rasgos del reconocedor propio para un glifo segmentado. */
    fun digitFeat(g: Glyph): DigitClassifier.Feat? = g.alpha?.let { DigitClassifier.features(it, g.aw, g.ah) }

    fun feature(px: IntArray, w: Int, g: Glyph): FloatArray? {
        val ink = ink(px, w, g.x0, g.y0, g.x1, g.y1) ?: return null
        val fw = 10; val fh = 16
        val f = FloatArray(fw * fh + 1)
        for (y in 0 until fh) for (x in 0 until fw) {
            val sx = (x + 0.5f) * ink.w / fw; val sy = (y + 0.5f) * ink.h / fh
            f[y * fw + x] = ink.a[min(ink.h - 1, sy.toInt()) * ink.w + min(ink.w - 1, sx.toInt())]
        }
        f[fw * fh] = 4f * g.w / max(1, g.h)
        return f
    }

    /** Dígito del ejemplo etiquetado más parecido (-1 si no hay ejemplos). */
    fun nearest(f: FloatArray, labeled: List<Pair<FloatArray, Int>>): Int {
        var best = -1; var bd = Float.MAX_VALUE
        for ((g, d) in labeled) {
            var s = 0f
            for (i in f.indices) { val e = f[i] - g[i]; s += e * e }
            if (s < bd) { bd = s; best = d }
        }
        return best
    }

    /**
     * Completa los glifos sin dígito (OCR vacío) por vecino más cercano contra los glifos ya
     * reconocidos de la misma captura (misma fuente). Devuelve cuántos se completaron.
     */
    fun fillUnknown(glyphs: List<Glyph>, digit: MutableMap<Glyph, Int>, feat: (Glyph) -> FloatArray?): Int {
        val labeled = glyphs.mapNotNull { g -> digit[g]?.takeIf { it >= 0 }?.let { d -> feat(g)?.let { it to d } } }
        if (labeled.isEmpty()) return 0
        var n = 0
        for (g in glyphs) if ((digit[g] ?: -1) < 0) {
            val f = feat(g) ?: continue
            val d = nearest(f, labeled)
            if (d >= 0) { digit[g] = d; n++ }
        }
        return n
    }

    /** Pila de números de una columna pegada a la rejilla (de abajo hacia arriba, huecos ≤ maxGap celdas). */
    fun stackCol(lines: List<List<Glyph>>, gridTop: Int, cell: Float, maxGap: Float = 2.5f): List<Group> {
        val out = ArrayList<Group>(); var last = gridTop.toFloat()
        for (l in lines.sortedByDescending { it.maxOf { g -> g.y1 } }) {
            val bottom = l.maxOf { it.y1 }.toFloat()
            if (last - bottom > maxGap * cell) break
            out.add(Group(l, avg(l))); last = l.minOf { it.y0 }.toFloat()
        }
        return out.reversed()
    }

    /** Pila de una fila pegada a la rejilla (de derecha a izquierda). */
    fun stackRow(groups: List<Group>, gridLeft: Int, cell: Float, maxGap: Float = 2.5f): List<Group> {
        val out = ArrayList<Group>(); var last = gridLeft.toFloat()
        for (g in groups.sortedByDescending { it.x1 }) {
            if (last - g.x1 > maxGap * cell) break
            out.add(g); last = g.x0.toFloat()
        }
        return out.reversed()
    }

    /**
     * Valores finales: '0' tras otro dígito se une (10, 20…); si la pista no cabe en la línea,
     * se separan los números multidígito en dígitos sueltos.
     */
    fun finalize(values: List<List<Int>>, len: Int): List<Int> {
        val merged = ArrayList<Int>()
        for (digits in values) {
            if (digits.isEmpty()) continue
            val v = digits.fold(0) { a, d -> a * 10 + d }
            if (digits.size == 1 && digits[0] == 0 && merged.isNotEmpty() && merged.last() < 10) merged[merged.size - 1] = merged.last() * 10
            else merged.add(v)
        }
        val res = merged.filter { it > 0 }
        if (res.sum() + res.size - 1 > len) {
            val singles = values.flatten().filter { it > 0 }
            if (singles.sum() + singles.size - 1 <= len) return singles
        }
        return res
    }
}
