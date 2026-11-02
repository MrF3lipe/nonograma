package com.felipe.nonograma

import kotlin.math.roundToInt

/**
 * Lectura de pistas con reintentos (Kotlin puro, testeable en JVM).
 * Si las pistas leídas no son válidas, reintenta con otra escala, otro umbral de tinta y otro recorte
 * de celda; en cada variante prueba combinaciones de dígitos dudosos (de la más a la menos probable)
 * hasta que el solucionador valide. Con tope de variantes, combinaciones y tiempo.
 */
object CluePipeline {
    /** Variante de preprocesado: escala de la imagen, umbrales de tinta (fracción del contraste) y recorte de celda. */
    data class Variant(val scale: Float, val lo: Float, val hi: Float, val rowInset: Float, val colInset: Float) {
        override fun toString() = "x%.2f u%.2f-%.2f r%.2f/%.2f".format(scale, lo, hi, rowInset, colInset)
    }
    val VARIANTS = listOf(
        Variant(1f, 0.30f, 0.65f, 0.12f, 0.14f),
        Variant(1f, 0.22f, 0.50f, 0.08f, 0.10f),
        Variant(1.5f, 0.30f, 0.65f, 0.12f, 0.14f),
        Variant(1f, 0.40f, 0.75f, 0.16f, 0.18f),
        Variant(2f, 0.25f, 0.55f, 0.10f, 0.12f),
        Variant(0.75f, 0.30f, 0.65f, 0.14f, 0.16f),
        Variant(1.5f, 0.45f, 0.80f, 0.06f, 0.08f),
        Variant(1.25f, 0.35f, 0.60f, 0.18f, 0.20f),
        // celdas grandes (5x5, 10x10) con números de tamaño fijo: el dígito ocupa poco de la celda
        Variant(1f, 0.12f, 0.45f, 0.05f, 0.06f),
        Variant(1f, 0.06f, 0.30f, 0.03f, 0.04f),
    )
    /** Pares que el reconocedor confunde a menudo: se prueban antes. */
    private val CONFUSABLE = setOf(6 to 8, 8 to 6, 3 to 8, 8 to 3, 1 to 7, 7 to 1, 0 to 8, 8 to 0, 5 to 6, 6 to 5, 9 to 8, 8 to 9)

    class Limits(val maxVariants: Int = VARIANTS.size, val maxCombos: Int = 400, val timeMs: Long = 9000, val solveMs: Long = 600, val deduce: Boolean = true)

    enum class Problem { SUMA, NO_CABE, VACIA, SIN_SOLUCION, CONTRADICCION }
    class Check(val problems: Set<Problem>, val badRows: Set<Int>, val badCols: Set<Int>) { val ok get() = problems.isEmpty() }

    /** Resultado de la lectura de una variante. */
    class Lines(val rowGroups: List<List<Segment.Group>>, val colGroups: List<List<Segment.Group>>, val rowInk: BooleanArray, val colInk: BooleanArray) {
        val glyphs get() = (rowGroups + colGroups).flatten().flatMap { it.glyphs }
    }

    class Outcome(val lines: Lines, val digit: Map<Segment.Glyph, Int>, val rows: List<List<Block>>, val cols: List<List<Block>>,
                  val check: Check, val attempts: Int, val combos: Int, val variant: Variant, val log: String, val doubtRows: Set<Int>, val doubtCols: Set<Int>) {
        val ok get() = check.ok
    }

    /** Validación: sumas, que cada línea quepa, líneas vacías con tinta y solución del solver verificada. */
    fun validate(rows: List<List<Block>>, cols: List<List<Block>>, rowInk: BooleanArray?, colInk: BooleanArray?, solveMs: Long): Check {
        val p = HashSet<Problem>(); val br = HashSet<Int>(); val bc = HashSet<Int>()
        val w = cols.size; val h = rows.size
        rows.forEachIndexed { i, l -> if (Solver.minLen(l) > w) { p.add(Problem.NO_CABE); br.add(i) } }
        cols.forEachIndexed { i, l -> if (Solver.minLen(l) > h) { p.add(Problem.NO_CABE); bc.add(i) } }
        rows.forEachIndexed { i, l -> if (l.isEmpty() && rowInk?.getOrNull(i) == true) { p.add(Problem.VACIA); br.add(i) } }
        cols.forEachIndexed { i, l -> if (l.isEmpty() && colInk?.getOrNull(i) == true) { p.add(Problem.VACIA); bc.add(i) } }
        if (rows.all { it.isEmpty() } || cols.all { it.isEmpty() }) p.add(Problem.VACIA)
        val colors = (rows + cols).flatten().map { it.color }.toSet()
        for (c in colors) {
            if (rows.flatten().filter { it.color == c }.sumOf { it.len } != cols.flatten().filter { it.color == c }.sumOf { it.len }) p.add(Problem.SUMA)
        }
        if (p.isEmpty()) {
            val r = Solver.solve(rows, cols, solveMs)
            if (r.status != Solver.Status.SOLVED) p.add(Problem.SIN_SOLUCION)
            else if (!Solver.verify(rows, cols, r.grid!!)) p.add(Problem.CONTRADICCION)
        }
        return Check(p, br, bc)
    }

    /** Remuestreo bilineal de una región (para la variante de escala). */
    fun resample(px: IntArray, w: Int, x0: Int, y0: Int, x1: Int, y1: Int, s: Float): Triple<IntArray, Int, Int> {
        val rw = x1 - x0; val rh = y1 - y0
        val ow = maxOf(1, (rw * s).roundToInt()); val oh = maxOf(1, (rh * s).roundToInt())
        val out = IntArray(ow * oh)
        for (y in 0 until oh) {
            val sy = ((y + 0.5f) / s - 0.5f).coerceIn(0f, rh - 1f); val yi = sy.toInt(); val y2 = minOf(rh - 1, yi + 1); val fy = sy - yi
            for (x in 0 until ow) {
                val sx = ((x + 0.5f) / s - 0.5f).coerceIn(0f, rw - 1f); val xi = sx.toInt(); val x2 = minOf(rw - 1, xi + 1); val fx = sx - xi
                val a = px[(y0 + yi) * w + x0 + xi]; val b = px[(y0 + yi) * w + x0 + x2]; val c = px[(y0 + y2) * w + x0 + xi]; val d = px[(y0 + y2) * w + x0 + x2]
                var o = 0xFF shl 24
                for (sh in intArrayOf(16, 8, 0)) {
                    val v = ((a shr sh and 255) * (1 - fx) + (b shr sh and 255) * fx) * (1 - fy) + ((c shr sh and 255) * (1 - fx) + (d shr sh and 255) * fx) * fy
                    o = o or (v.roundToInt().coerceIn(0, 255) shl sh)
                }
                out[y * ow + x] = o
            }
        }
        return Triple(out, ow, oh)
    }

    /** Segmenta pistas con una variante. Coordenadas de rejilla/marcos en píxeles de pantalla. */
    fun segment(px: IntArray, w: Int, h: Int, gl: Int, gt: Int, gr: Int, gb: Int, rfLeft: Int, cfTop: Int, rows: Int, cols: Int, v: Variant): Lines {
        // región que contiene pistas + rejilla
        val x0 = maxOf(0, rfLeft); val y0 = maxOf(0, cfTop); val x1 = minOf(w, gr + 2); val y1 = minOf(h, gb + 2)
        val s = v.scale
        val (p, pw) = if (s == 1f) px to w else resample(px, w, x0, y0, x1, y1, s).let { it.first to it.second }
        val ox = if (s == 1f) 0 else x0; val oy = if (s == 1f) 0 else y0
        fun X(x: Int) = ((x - ox) * s).roundToInt()
        fun Y(y: Int) = ((y - oy) * s).roundToInt()
        val L = X(gl); val T = Y(gt); val R = X(gr); val B = Y(gb)
        val ch = (B - T).toFloat() / rows; val cw = (R - L).toFloat() / cols
        val rowGl = (0 until rows).map { r ->
            Segment.rowGlyphs(p, pw, maxOf(0, X(x0)), (T + r * ch).toInt(), L - 3, (T + (r + 1) * ch).toInt(), v.rowInset, v.lo, v.hi)
        }
        val dom = Segment.dominant(rowGl.flatten())
        val rowG = rowGl.map { Segment.stackRow(Segment.groupRow(it, dom), L, cw) }
        val colRaw = (0 until cols).map { c ->
            Segment.colGroupsRaw(p, pw, (L + c * cw).toInt(), maxOf(0, Y(y0)), (L + (c + 1) * cw).toInt(), T - 3, v.colInset, v.lo, v.hi)
        }
        val colG = colRaw.map { Segment.stackCol(it, T, ch) }
        return Lines(rowG, colG, BooleanArray(rows) { rowG[it].isNotEmpty() }, BooleanArray(cols) { colG[it].isNotEmpty() })
    }

    /** Línea con tinta que no es un "0" explícito (una línea vacía con un 0 es válida). */
    /** Pistas tal como se leyeron, para errores.log: "f1=1 1 4|f2=…" y "c1=…". */
    fun dump(rows: List<List<Block>>, cols: List<List<Block>>): String {
        fun l(p: String, x: List<List<Block>>) = x.mapIndexed { i, b -> "$p${i + 1}=" + b.joinToString(" ") { if (it.color > 1) "${it.len}:${it.color}" else "${it.len}" } }.joinToString("|")
        return "filas[" + l("f", rows) + "] columnas[" + l("c", cols) + "]"
    }

    fun inkRows(groups: List<List<Segment.Group>>, digit: Map<Segment.Glyph, Int>) =
        BooleanArray(groups.size) { i -> groups[i].any { g -> g.glyphs.any { (digit[it] ?: -1) != 0 } } }

    fun bw(lines: Lines, digit: Map<Segment.Glyph, Int>, rows: Int, cols: Int): Pair<List<List<Block>>, List<List<Block>>> {
        fun vals(l: List<Segment.Group>, len: Int) =
            Segment.finalize(l.map { g -> g.glyphs.mapNotNull { digit[it]?.takeIf { d -> d >= 0 } } }, len).map { Block(it) }
        return lines.rowGroups.map { vals(it, cols) } to lines.colGroups.map { vals(it, rows) }
    }

    /** Alternativa dudosa: glifo, dígito alternativo y coste (diferencia de puntuación). */
    class Alt(val glyph: Int, val digit: Int, val cost: Float)

    fun alternatives(feats: List<DigitClassifier.Feat?>, pred: IntArray, maxAlts: Int = 16): List<Alt> {
        val out = ArrayList<Alt>()
        feats.forEachIndexed { i, f ->
            f ?: return@forEachIndexed
            val sc = DigitClassifier.scores(f)
            val ranked = (0..9).filter { it != pred[i] }.sortedBy { sc[it] }.take(2)
            for (a in ranked) {
                var c = (sc[a] - sc[pred[i]]).coerceAtLeast(0f)
                if ((pred[i] to a) in CONFUSABLE) c *= 0.5f
                out.add(Alt(i, a, c))
            }
        }
        return out.sortedBy { it.cost }.take(maxAlts)
    }

    /** Combinaciones de 1 a 3 alternativas (glifos distintos), ordenadas de más a menos probable. */
    fun combos(alts: List<Alt>, max: Int): List<List<Alt>> {
        val out = ArrayList<List<Alt>>()
        for (i in alts.indices) {
            out.add(listOf(alts[i]))
            for (j in i + 1 until alts.size) {
                if (alts[j].glyph == alts[i].glyph) continue
                out.add(listOf(alts[i], alts[j]))
                for (k in j + 1 until alts.size) {
                    if (alts[k].glyph == alts[i].glyph || alts[k].glyph == alts[j].glyph) continue
                    out.add(listOf(alts[i], alts[j], alts[k]))
                }
            }
        }
        return out.sortedBy { c -> c.sumOf { it.cost.toDouble() } + 0.01 * c.size }.take(max)
    }

    /**
     * Bucle completo. votes: votos externos (ML Kit) por variante, opcional.
     * build: de dígitos a pistas (por defecto B/N); el lector Android pasa su versión con colores.
     */
    fun run(px: IntArray, w: Int, h: Int, gl: Int, gt: Int, gr: Int, gb: Int, rfLeft: Int, cfTop: Int, rows: Int, cols: Int,
            limits: Limits = Limits(),
            votes: ((Lines, Variant) -> Map<Segment.Glyph, Int>)? = null,
            build: (Lines, Map<Segment.Glyph, Int>) -> Pair<List<List<Block>>, List<List<Block>>> = { l, d -> bw(l, d, rows, cols) },
            now: () -> Long = { System.currentTimeMillis() }): Outcome? {
        val t0 = now(); val deadline = t0 + limits.timeMs
        val log = StringBuilder()
        var best: Outcome? = null; var bestScore = Int.MAX_VALUE
        var attempts = 0; var totalCombos = 0
        for (v in VARIANTS.take(limits.maxVariants)) {
            if (attempts > 0 && now() > deadline) { log.append(" tiempo"); break }
            attempts++
            val lines = segment(px, w, h, gl, gt, gr, gb, rfLeft, cfTop, rows, cols, v)
            val glyphs = lines.glyphs
            if (glyphs.isEmpty()) { log.append(" [$v] sin glifos"); continue }
            val ext = try { votes?.invoke(lines, v) ?: emptyMap() } catch (e: Throwable) { emptyMap() }
            val feats = glyphs.map { Segment.digitFeat(it) }
            val pred = DigitClassifier.classifyAll(feats, glyphs.map { ext[it] ?: -1 })
            val digit = HashMap<Segment.Glyph, Int>(); glyphs.forEachIndexed { i, g -> digit[g] = pred[i] }
            var (rc, cc) = build(lines, digit)
            var chk = validate(rc, cc, inkRows(lines.rowGroups, digit), inkRows(lines.colGroups, digit), limits.solveMs)
            val alts = alternatives(feats, pred)
            var tried = 0
            if (!chk.ok) {
                for (combo in combos(alts, limits.maxCombos)) {
                    if (now() > deadline) break
                    tried++
                    for (a in combo) digit[glyphs[a.glyph]] = a.digit
                    val (r2, c2) = build(lines, digit)
                    val k = validate(r2, c2, inkRows(lines.rowGroups, digit), inkRows(lines.colGroups, digit), limits.solveMs)
                    if (k.ok) { rc = r2; cc = c2; chk = k; break }
                    for (a in combo) digit[glyphs[a.glyph]] = pred[a.glyph]
                }
            }
            totalCombos += tried
            log.append(" [$v] glifos=${glyphs.size} combos=$tried ${if (chk.ok) "ok" else chk.problems.joinToString(",")}")
            // líneas dudosas: las que fallan + las que contienen los glifos más dudosos
            val gi = java.util.IdentityHashMap<Segment.Glyph, Int>(); glyphs.forEachIndexed { i, g -> gi[g] = i }
            val doubtful = alts.filter { it.cost < 0.15f }.map { it.glyph }.toSet()
            val dr = HashSet(chk.badRows); val dc = HashSet(chk.badCols)
            lines.rowGroups.forEachIndexed { i, l -> if (l.any { g -> g.glyphs.any { gi[it] in doubtful } }) dr.add(i) }
            lines.colGroups.forEachIndexed { i, l -> if (l.any { g -> g.glyphs.any { gi[it] in doubtful } }) dc.add(i) }
            val o = Outcome(lines, HashMap(digit), rc, cc, chk, attempts, totalCombos, v, log.toString().trim(),
                if (chk.ok) emptySet() else dr, if (chk.ok) emptySet() else dc)
            if (chk.ok) return o
            val score = chk.badRows.size + chk.badCols.size + chk.problems.size * 3
            if (score < bestScore) { best = o; bestScore = score }
        }
        val b = best ?: return null
        // Último recurso: líneas tapadas o ilegibles. Se dejan como desconocidas y, si el resto de pistas
        // determina una solución única, sus pistas se deducen de ella.
        if (limits.deduce) deduceHidden(b.rows, b.cols, b.doubtRows + b.check.badRows, b.doubtCols + b.check.badCols, deadline + limits.timeMs / 3, now)?.let { (r, c, hidden) ->
            log.append(" deducidas=${hidden}")
            return Outcome(b.lines, b.digit, r, c, Check(emptySet(), emptySet(), emptySet()), attempts, totalCombos, b.variant, log.toString().trim(), emptySet(), emptySet())
        }
        return Outcome(b.lines, b.digit, b.rows, b.cols, b.check, attempts, totalCombos, b.variant, log.toString().trim(), b.doubtRows, b.doubtCols)
    }

    /**
     * Prueba conjuntos pequeños de líneas dudosas como desconocidas (de menos a más líneas).
     * Acepta solo si hay exactamente una solución; devuelve pistas completas y qué líneas se dedujeron.
     */
    fun deduceHidden(rows: List<List<Block>>, cols: List<List<Block>>, candRows: Set<Int>, candCols: Set<Int>,
                     deadline: Long, now: () -> Long = { System.currentTimeMillis() }, maxPerSide: Int = 2): Triple<List<List<Block>>, List<List<Block>>, String>? {
        if ((rows + cols).flatten().any { it.color != 1 }) return null // solo B/N
        val h = rows.size; val w = cols.size
        if (h < 5 || w < 5) return null // muy pocas pistas para deducir nada con seguridad
        fun subsets(c: List<Int>): List<List<Int>> {
            val out = mutableListOf(emptyList<Int>())
            for (i in c.indices) { out.add(listOf(c[i])); if (maxPerSide >= 2) for (j in i + 1 until c.size) out.add(listOf(c[i], c[j])) }
            return out
        }
        // las líneas en los bordes (zona de pistas más expuesta a tapas) primero
        fun order(c: Set<Int>, n: Int) = c.filter { it in 0 until n }.sortedBy { minOf(it, n - 1 - it) }.take(7)
        val sr = subsets(order(candRows, h)); val sc = subsets(order(candCols, w))
        val pairs = sr.flatMap { a -> sc.map { bb -> a to bb } }.filter { it.first.size + it.second.size > 0 }.sortedBy { it.first.size + it.second.size }
        for ((ur, uc) in pairs) {
            if (now() > deadline) return null
            val R = rows.mapIndexed { i, l -> if (i in ur) null else l }
            val C = cols.mapIndexed { i, l -> if (i in uc) null else l }
            val sols = Solver.solveUnknown(R, C, 400, 2) ?: continue
            if (sols.size != 1) continue
            val g = sols[0]
            fun clue(line: IntArray) = Solver.runs(line).map { Block(it.len) }
            val nr = (0 until h).map { r -> if (r in ur) clue(IntArray(w) { g[r * w + it] }) else rows[r] }
            val nc = (0 until w).map { c -> if (c in uc) clue(IntArray(h) { g[it * w + c] }) else cols[c] }
            if (!Solver.verify(nr, nc, g)) continue
            return Triple(nr, nc, "f${ur.map { it + 1 }} c${uc.map { it + 1 }}")
        }
        return null
    }
}
