package com.felipe.nonograma

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Número reconocido con su caja en coordenadas de pantalla. */
data class Token(val value: Int, val cx: Float, val cy: Float, val box: Rect, var rgb: Int = 0xFF000000.toInt())

/** Lógica pura (sin Android) para agrupar y asignar números; testeable. */
object ClueLogic {
    fun normChar(c: Char): Char? = when (c) {
        in '0'..'9' -> c
        'l', 'I', '|', 'i', '!', 'j', 'J', ']', '[' -> '1'
        'O', 'o', 'D', 'Q' -> '0'
        'S', 's' -> '5'
        'B' -> '8'
        'Z', 'z' -> '2'
        'g', 'q' -> '9'
        'b', 'G' -> '6'
        'A' -> '4'
        'T' -> '7'
        else -> null
    }

    /** Agrupa tokens por banda (fila o columna) y los ordena. byRow: banda por y, orden por x. */
    fun assign(tokens: List<Token>, bands: Int, start: Float, size: Float, byRow: Boolean): List<List<Token>> {
        val out = List(bands) { mutableListOf<Token>() }
        val cell = size / bands
        for (t in tokens) {
            val p = if (byRow) t.cy else t.cx
            val idx = ((p - start) / cell).toInt()
            if (p >= start && idx in 0 until bands) out[idx].add(t)
        }
        return out.map { l -> l.sortedBy { if (byRow) it.cx else it.cy }.filter { it.value > 0 } }
    }

    /**
     * Mantiene solo la "pila" de números pegada a la rejilla en cada banda: desde el más cercano al
     * borde hacia fuera, mientras el hueco no supere maxGap celdas (descarta textos lejanos: título, nivel, reloj).
     * edge = borde de la rejilla (left para filas, top para columnas).
     */
    fun stack(band: List<Token>, edge: Float, cell: Float, byRow: Boolean, maxGap: Float = 3f): List<Token> {
        val near = band.sortedByDescending { if (byRow) it.cx else it.cy }
        val out = ArrayList<Token>()
        var last = edge
        for (t in near) {
            val p = if (byRow) t.cx else t.cy
            if (p > edge + cell * 0.5f) continue // dentro de la rejilla
            if (last - p > maxGap * cell) break
            out.add(t); last = p
        }
        return out.reversed()
    }

    /**
     * Estima el número de bandas (filas o columnas) a partir de los centros de los números:
     * agrupa centros cercanos y mide el paso típico. 0 si no es fiable.
     */
    fun inferCount(centers: List<Float>, size: Float, joinTol: Float): Int {
        if (centers.size < 2) return 0
        val s = centers.sorted()
        val groups = ArrayList<Float>(); var acc = s[0]; var n = 1; var last = s[0]
        for (i in 1 until s.size) {
            if (s[i] - last <= joinTol) { acc += s[i]; n++ } else { groups.add(acc / n); acc = s[i]; n = 1 }
            last = s[i]
        }
        groups.add(acc / n)
        if (groups.size < 2) return 0
        val diffs = (1 until groups.size).map { groups[it] - groups[it - 1] }.sorted()
        val pitch = diffs[diffs.size / 2]
        if (pitch <= 0f) return 0
        val est = (size / pitch).roundToInt()
        return if (est >= groups.size && est <= groups.size * 2 && est in 2..99) est else 0
    }

    private fun chroma(c: Int): Int {
        val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
        return max(r, max(g, b)) - min(r, min(g, b))
    }
    fun dist(a: Int, b: Int): Int =
        abs(((a shr 16) and 255) - ((b shr 16) and 255)) + abs(((a shr 8) and 255) - ((b shr 8) and 255)) + abs((a and 255) - (b and 255))
    fun isChromatic(c: Int) = chroma(c) >= 45

    /**
     * Agrupa colores en paleta. Los neutros (negro/gris/blanco) cuentan como un solo color.
     * Devuelve (paleta, índice 1-based por color de entrada).
     */
    fun cluster(colors: List<Int>): Pair<List<Int>, List<Int>> {
        val pal = mutableListOf<Int>(); val sums = mutableListOf<IntArray>()
        val idx = colors.map { c ->
            val key = if (isChromatic(c)) c else 0xFF000000.toInt()
            var best = -1; var bd = Int.MAX_VALUE
            for (i in pal.indices) { val d = dist(pal[i], key); if (d < bd) { bd = d; best = i } }
            if (best < 0 || bd > 90) {
                pal.add(key); sums.add(intArrayOf(0, 0, 0, 0)); best = pal.size - 1
            }
            val s = sums[best]; s[0] += (key shr 16) and 255; s[1] += (key shr 8) and 255; s[2] += key and 255; s[3]++
            pal[best] = (0xFF shl 24) or ((s[0] / s[3]) shl 16) or ((s[1] / s[3]) shl 8) or (s[2] / s[3])
            best + 1
        }
        return pal to idx
    }

    /**
     * Detecta el número de celdas a lo largo de un perfil de bordes (líneas de la rejilla).
     * Devuelve 0 si no hay confianza.
     */
    fun detectCount(profile: FloatArray): Int {
        val n = profile.size
        if (n < 40) return 0
        val mean = profile.average().toFloat().coerceAtLeast(1e-3f)
        var bestN = 0; var bestS = 0f
        val scores = FloatArray(81)
        for (k in 4..80) {
            if (n / k < 6) break
            var s = 0f
            for (i in 1 until k) {
                val p = (i.toFloat() * n / k).roundToInt()
                var m = 0f
                for (d in -2..2) { val q = p + d; if (q in 0 until n) m = max(m, profile[q]) }
                s += m
            }
            scores[k] = s / (k - 1) / mean
            if (scores[k] > bestS) { bestS = scores[k]; bestN = k }
        }
        if (bestS < 2.2f) return 0
        // preferir el mayor k con puntuación casi igual (los divisores también puntúan alto)
        for (k in 80 downTo bestN + 1) if (scores[k] >= bestS * 0.6f) return k
        return bestN
    }
}

/** Lectura de pistas desde una captura de pantalla con ML Kit (offline). */
object ClueReader {
    class Read(val rows: Int, val cols: Int, val rowClues: List<MutableList<Block>>, val colClues: List<MutableList<Block>>,
               val palette: List<Int>, val colorDetected: Boolean, val tokens: Int)

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    private fun clip(r: Rect, bmp: Bitmap): Rect? {
        val c = Rect(r); if (!c.intersect(0, 0, bmp.width, bmp.height) || c.width() < 4 || c.height() < 4) return null
        return c
    }

    /** OCR de una región; devuelve números con cajas en coordenadas de pantalla. */
    fun ocr(screen: Bitmap, region: Rect, cellPx: Float, bandRect: Rect, bands: Int, byRow: Boolean): List<Token> {
        val r = clip(region, screen) ?: return emptyList()
        val scale = (56f / cellPx).coerceIn(1f, 3f)
        var crop = Bitmap.createBitmap(screen, r.left, r.top, r.width(), r.height())
        if (scale > 1.05f) crop = Bitmap.createScaledBitmap(crop, (r.width() * scale).toInt(), (r.height() * scale).toInt(), true)
        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(crop, 0)))
        val out = ArrayList<Token>()
        val start = if (byRow) bandRect.top.toFloat() else bandRect.left.toFloat()
        val cell = (if (byRow) bandRect.height() else bandRect.width()).toFloat() / bands
        fun toScreen(b: Rect) = Rect((r.left + b.left / scale).toInt(), (r.top + b.top / scale).toInt(),
            (r.left + b.right / scale).toInt(), (r.top + b.bottom / scale).toInt())
        for (block in text.textBlocks) for (line in block.lines) for (el in line.elements) {
            // Agrupar símbolos en números: separar por no-dígito, hueco grande o cambio de banda perpendicular.
            var digits = StringBuilder(); var box: Rect? = null; var lastBox: Rect? = null; var lastBand = -1
            fun flush() {
                val b = box
                if (digits.isNotEmpty() && b != null) {
                    val v = digits.toString().toIntOrNull()
                    if (v != null && v in 0..99) {
                        val sb = toScreen(b)
                        out.add(Token(v, sb.exactCenterX(), sb.exactCenterY(), sb))
                    }
                }
                digits = StringBuilder(); box = null; lastBox = null
            }
            val syms = el.symbols
            if (syms.isEmpty()) {
                val bb = el.boundingBox ?: continue
                for (ch in el.text) { val d = ClueLogic.normChar(ch); if (d != null) { digits.append(d) } }
                box = bb; flush(); continue
            }
            for (s in syms) {
                val sb = s.boundingBox ?: continue
                val d = s.text.firstOrNull()?.let { ClueLogic.normChar(it) }
                if (d == null) { flush(); continue }
                val scr = toScreen(sb)
                // banda perpendicular: para filas, columnas de pista no importan; separar por hueco.
                val band = if (byRow) -1 else ((scr.exactCenterX() - start) / cell).toInt()
                val lb = lastBox
                val gap = if (lb == null) 0 else sb.left - lb.right
                if (lb != null && (gap > sb.height() * 0.45f || (!byRow && band != lastBand) || digits.length >= 2)) flush()
                digits.append(d)
                box = if (box == null) Rect(sb) else Rect(box).apply { union(sb) }
                lastBox = sb; lastBand = band
            }
            flush()
        }
        return out
    }

    /** Color de un número: el de la tinta si es cromático, si no el del fondo si lo es; si no, negro. */
    fun sampleColor(bmp: Bitmap, box: Rect): Int {
        val e = Rect(box.left - 3, box.top - 3, box.right + 3, box.bottom + 3)
        if (!e.intersect(0, 0, bmp.width, bmp.height) || e.width() < 3 || e.height() < 3) return 0xFF000000.toInt()
        val border = ArrayList<Int>()
        for (x in e.left until e.right) { border.add(bmp.getPixel(x, e.top)); border.add(bmp.getPixel(x, e.bottom - 1)) }
        for (y in e.top until e.bottom) { border.add(bmp.getPixel(e.left, y)); border.add(bmp.getPixel(e.right - 1, y)) }
        val bg = mode(border)
        var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
        for (y in box.top.coerceAtLeast(0) until box.bottom.coerceAtMost(bmp.height))
            for (x in box.left.coerceAtLeast(0) until box.right.coerceAtMost(bmp.width)) {
                val p = bmp.getPixel(x, y)
                if (ClueLogic.dist(p, bg) > 150) { rs += (p shr 16) and 255; gs += (p shr 8) and 255; bs += p and 255; n++ }
            }
        val fg = if (n == 0) bg else (0xFF shl 24) or ((rs / n).toInt() shl 16) or ((gs / n).toInt() shl 8) or (bs / n).toInt()
        return when {
            ClueLogic.isChromatic(fg) -> fg
            ClueLogic.isChromatic(bg) -> bg
            else -> 0xFF000000.toInt()
        }
    }

    private fun mode(px: List<Int>): Int {
        val m = HashMap<Int, Int>()
        for (p in px) { val q = p and 0xFFF0F0F0.toInt(); m[q] = (m[q] ?: 0) + 1 }
        return (m.maxByOrNull { it.value }?.key ?: 0xFFFFFFFF.toInt()) or 0xFF000000.toInt()
    }

    /**
     * Lee las pistas. Si infer=true y no se conoce el tamaño, lo estima de los grupos de números.
     */
    fun read(screen: Bitmap, grid: Rect, rowFrame: Rect, colFrame: Rect, rowsIn: Int, colsIn: Int, infer: Boolean): Read {
        var rows = rowsIn; var cols = colsIn
        val rt = ocr(screen, rowFrame, grid.height().toFloat() / rows, grid, rows, true)
        val ct = ocr(screen, colFrame, grid.width().toFloat() / cols, grid, cols, false)
        if (infer) {
            val hts = (rt + ct).map { it.box.height().toFloat() }.sorted()
            val tol = if (hts.isEmpty()) 8f else hts[hts.size / 2] * 0.6f
            ClueLogic.inferCount(rt.map { it.cy }, grid.height().toFloat(), tol).let { if (it > 0) rows = it }
            ClueLogic.inferCount(ct.map { it.cx }, grid.width().toFloat(), tol).let { if (it > 0) cols = it }
        }
        val cellH = grid.height().toFloat() / rows; val cellW = grid.width().toFloat() / cols
        val ra = ClueLogic.assign(rt.filter { it.box.height() <= cellH * 2.5f }, rows, grid.top.toFloat(), grid.height().toFloat(), true)
            .map { ClueLogic.stack(it, grid.left.toFloat(), cellW, true) }
        val ca = ClueLogic.assign(ct.filter { it.box.height() <= cellW * 2.5f }, cols, grid.left.toFloat(), grid.width().toFloat(), false)
            .map { ClueLogic.stack(it, grid.top.toFloat(), cellH, false) }
        val all = (ra + ca).flatten()
        for (t in all) t.rgb = sampleColor(screen, t.box)
        val (pal, idx) = ClueLogic.cluster(all.map { it.rgb })
        val map = java.util.IdentityHashMap<Token, Int>(); all.forEachIndexed { i, t -> map[t] = idx[i] }
        val color = pal.size > 1
        val rowClues = ra.map { l -> l.map { Block(it.value, if (color) map[it]!! else 1) }.toMutableList() }
        val colClues = ca.map { l -> l.map { Block(it.value, if (color) map[it]!! else 1) }.toMutableList() }
        return Read(rows, cols, rowClues, colClues, if (color) pal else listOf(0xFF000000.toInt()), color, rt.size + ct.size)
    }
}
