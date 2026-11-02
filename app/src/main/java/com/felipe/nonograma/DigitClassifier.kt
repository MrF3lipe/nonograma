package com.felipe.nonograma

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Reconocedor de dígitos propio (Kotlin puro, sin ML Kit) para glifos ya segmentados.
 * Rasgos: densidad por zonas (8x12), perfil izquierdo/derecho, proporción, nº de agujeros y su altura.
 * Clasifica por vecino más cercano contra plantillas (fuentes renderizadas + glifos reales del juego)
 * y, dentro de una captura, refuerza por consistencia (glifos casi idénticos reciben la misma etiqueta).
 */
object DigitClassifier {
    const val GW = 8; const val GHt = 12
    const val FLEN = GW * GHt + GHt * 2 + 3 // zonas + perfiles + (aspecto, agujeros, y agujero)

    class Feat(val v: FloatArray) {
        val holes get() = v[FLEN - 2]
        val holeY get() = v[FLEN - 1]
    }

    /** Rasgos desde un mapa de tinta (alpha 0..1) w×h. null si no hay tinta. */
    fun features(a: FloatArray, w: Int, h: Int): Feat? {
        // recorte ajustado
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (a[y * w + x] > 0.35f) { x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y) }
        if (x1 < 0) return null
        val cw = x1 - x0 + 1; val ch = y1 - y0 + 1
        // remuestreo bilineal a una rejilla fina (para agujeros y perfiles) de 24x36
        val FW = 24; val FH = 36
        val fine = FloatArray(FW * FH)
        for (y in 0 until FH) for (x in 0 until FW) {
            val sx = x0 - 0.5f + (x + 0.5f) * cw / FW; val sy = y0 - 0.5f + (y + 0.5f) * ch / FH
            fine[y * FW + x] = bilinear(a, w, h, sx, sy)
        }
        val f = FloatArray(FLEN)
        // zonas
        for (y in 0 until FH) for (x in 0 until FW) f[(y * GHt / FH) * GW + (x * GW / FW)] += fine[y * FW + x]
        val zone = (FW / GW) * (FH / GHt).toFloat()
        var norm = 0f
        for (i in 0 until GW * GHt) { f[i] /= zone; norm += f[i] * f[i] }
        norm = sqrt(norm).coerceAtLeast(1e-3f)
        for (i in 0 until GW * GHt) f[i] /= norm
        // perfiles: primera/última tinta por fila (fracción del ancho)
        for (r in 0 until GHt) {
            var left = 0f; var right = 0f
            for (yy in 0 until FH / GHt) {
                val y = r * (FH / GHt) + yy
                var l = FW; var rr = -1
                for (x in 0 until FW) if (fine[y * FW + x] > 0.5f) { if (l == FW) l = x; rr = x }
                left += if (rr < 0) 1f else l.toFloat() / FW
                right += if (rr < 0) 1f else (FW - 1 - rr).toFloat() / FW
            }
            f[GW * GHt + r] = left / (FH / GHt); f[GW * GHt + GHt + r] = right / (FH / GHt)
        }
        f[FLEN - 3] = cw.toFloat() / ch
        val (holes, hy) = holes(fine, FW, FH)
        f[FLEN - 2] = holes.toFloat(); f[FLEN - 1] = hy
        return Feat(f)
    }

    private fun bilinear(a: FloatArray, w: Int, h: Int, x: Float, y: Float): Float {
        val xi = x.toInt().coerceIn(0, w - 1); val yi = y.toInt().coerceIn(0, h - 1)
        val x2 = min(w - 1, xi + 1); val y2 = min(h - 1, yi + 1)
        val fx = (x - xi).coerceIn(0f, 1f); val fy = (y - yi).coerceIn(0f, 1f)
        val t = a[yi * w + xi] * (1 - fx) + a[yi * w + x2] * fx
        val b = a[y2 * w + xi] * (1 - fx) + a[y2 * w + x2] * fx
        return t * (1 - fy) + b * fy
    }

    /** Agujeros (zonas de fondo no conectadas con el borde) y altura media del agujero (0 arriba, 1 abajo). */
    fun holes(a: FloatArray, w: Int, h: Int): Pair<Int, Float> {
        var best = 0 to 0.5f
        for (thr in floatArrayOf(0.45f, 0.62f)) {
            val ink = BooleanArray(w * h) { a[it] > thr }
            val lab = IntArray(w * h) // 0 sin visitar, -1 exterior, k>0 agujero
            val q = IntArray(w * h)
            fun fill(start: Int, v: Int): Pair<Int, Float> {
                var head = 0; var tail = 0; q[tail++] = start; lab[start] = v; var n = 0; var sy = 0f
                while (head < tail) {
                    val p = q[head++]; n++; sy += p / w
                    val x = p % w; val y = p / w
                    if (x > 0) { val o = p - 1; if (!ink[o] && lab[o] == 0) { lab[o] = v; q[tail++] = o } }
                    if (x < w - 1) { val o = p + 1; if (!ink[o] && lab[o] == 0) { lab[o] = v; q[tail++] = o } }
                    if (y > 0) { val o = p - w; if (!ink[o] && lab[o] == 0) { lab[o] = v; q[tail++] = o } }
                    if (y < h - 1) { val o = p + w; if (!ink[o] && lab[o] == 0) { lab[o] = v; q[tail++] = o } }
                }
                return n to sy / max(1, n)
            }
            for (x in 0 until w) { for (y in intArrayOf(0, h - 1)) { val p = y * w + x; if (!ink[p] && lab[p] == 0) fill(p, -1) } }
            for (y in 0 until h) { for (x in intArrayOf(0, w - 1)) { val p = y * w + x; if (!ink[p] && lab[p] == 0) fill(p, -1) } }
            var k = 0; var ys = 0f; var cnt = 0
            for (p in 0 until w * h) if (!ink[p] && lab[p] == 0) {
                k++
                val (n, cy) = fill(p, k)
                if (n >= w * h / 60) { cnt++; ys += cy / h }
            }
            if (cnt > best.first) best = cnt to (if (cnt > 0) ys / cnt else 0.5f)
        }
        return best
    }

    fun distance(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in 0 until GW * GHt) { val d = a[i] - b[i]; s += d * d }
        var p = 0f
        for (i in GW * GHt until GW * GHt + 2 * GHt) { val d = a[i] - b[i]; p += d * d }
        val asp = abs(a[FLEN - 3] - b[FLEN - 3])
        val holes = abs(a[FLEN - 2] - b[FLEN - 2])
        val hy = if (a[FLEN - 2] > 0 && b[FLEN - 2] > 0) abs(a[FLEN - 1] - b[FLEN - 1]) else 0f
        return s + 2.0f * p / GHt + 0.8f * asp * asp + 0.35f * holes + 0.8f * hy * hy
    }

    class Template(val digit: Int, val f: FloatArray)

    @Volatile private var builtIn: List<Template>? = null
    fun templates(): List<Template> = builtIn ?: DigitTemplates.load().also { builtIn = it }

    /** Distancia mínima a cada dígito 0..9. */
    fun scores(f: Feat, tpl: List<Template> = templates()): FloatArray {
        val s = FloatArray(10) { Float.MAX_VALUE }
        for (t in tpl) { val d = distance(f.v, t.f); if (d < s[t.digit]) s[t.digit] = d }
        return s
    }

    fun classify(f: Feat, tpl: List<Template> = templates()): Int {
        val s = scores(f, tpl); var b = 0
        for (d in 1..9) if (s[d] < s[b]) b = d
        return b
    }

    /**
     * Clasificación de todos los glifos de una captura con consistencia: agrupa glifos casi idénticos
     * (misma fuente y tamaño) y les da la etiqueta con mejor puntuación conjunta. votes: votos externos
     * opcionales (ML Kit) por glifo, que restan distancia.
     */
    fun classifyAll(feats: List<Feat?>, votes: List<Int> = emptyList(), tpl: List<Template> = templates()): IntArray {
        val n = feats.size
        val sc = feats.map { it?.let { f -> scores(f, tpl) } }
        val parent = IntArray(n) { it }
        fun find(x: Int): Int { var r = x; while (parent[r] != r) r = parent[r]; return r }
        // umbral: glifos muy parecidos entre sí
        for (i in 0 until n) for (j in i + 1 until n) {
            val a = feats[i] ?: continue; val b = feats[j] ?: continue
            if (distance(a.v, b.v) < 0.05f && a.holes == b.holes) parent[find(i)] = find(j)
        }
        val out = IntArray(n) { -1 }
        val groups = (0 until n).filter { sc[it] != null }.groupBy { find(it) }
        for ((_, mem) in groups) {
            val tot = FloatArray(10)
            for (m in mem) {
                val s = sc[m]!!
                val mn = s.minOrNull()!!
                for (d in 0..9) tot[d] += s[d] - mn
                val v = votes.getOrNull(m) ?: -1
                if (v in 0..9) tot[v] -= 0.08f
            }
            var b = 0; for (d in 1..9) if (tot[d] < tot[b]) b = d
            for (m in mem) out[m] = b
        }
        return out
    }

    /** Alternativas ordenadas por puntuación (para la reparación con el solver). */
    fun ranked(f: Feat, tpl: List<Template> = templates()): List<Int> {
        val s = scores(f, tpl); return (0..9).sortedBy { s[it] }
    }
}
