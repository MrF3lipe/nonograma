package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Pipeline completo (rejilla → segmentación → reconocedor propio → reintentos → solver) sobre todas
 * las capturas locales de prueba. Las capturas no están en el repo: se leen de disco si existen.
 * Dirs: env NONO_DIRS (separados por ':').
 */
class PipelineAllTest {
    private val dirs = (System.getenv("NONO_DIRS") ?: "/workspace/entregas/nonograma/capturas:/home/box/telegram-relay/media").split(":")

    companion object {
        /** Nonogramas que deben salir con pistas válidas y resueltas. */
        val MUST_PASS = setOf("juego.jpg", "juego2.jpg", "tg-1526.jpg", "tg-1534.jpg", "tg-1535.jpg", "tg-1536.jpg", "tg-1537.jpg", "tg-1538.jpg", "tg-1539.jpg")
        /** Capturas que no son nonogramas: nunca deben dar pistas "válidas". */
        val NOT_PUZZLE = setOf("tg-667.jpg")
        /**
         * tg-1549: el mismo puzzle que tg-1536, pero con la superposición visible en la captura: las asas tapan
         * la pista de la fila 20 ("6 9") y la columna 20 ("20") y parte de la fila 19 y la columna 19. Con esas
         * 4 líneas desconocidas hay 2 soluciones, así que no se puede deducir: debe marcarlas como dudosas.
         */
        val OCCLUDED = mapOf("tg-1549.jpg" to (setOf(18, 19) to setOf(18, 19)))
    }

    /**
     * Simula lo que pasaba en el teléfono: la captura de MediaProjection salía con la superposición
     * (marcos y asas) aún a medio desaparecer. Mezcla tg-1536 (limpia) con tg-1549 (mismo puzzle con la
     * superposición) a 1080x2400; con transparencia parcial debe seguir leyendo pistas válidas.
     */
    @Test fun overlayRemnantAtPhoneResolution() {
        val a = File("/home/box/telegram-relay/media/tg-1536.jpg"); val b = File("/home/box/telegram-relay/media/tg-1549.jpg")
        if (!a.exists() || !b.exists()) return
        val ia = DigitEvalTest.scale(ImageIO.read(a), 1080.0 / 576); val ib = DigitEvalTest.scale(ImageIO.read(b), 1080.0 / 576)
        val w = ia.width; val h = ia.height
        val pa = ia.getRGB(0, 0, w, h, null, 0, w); val pb = ib.getRGB(0, 0, w, h, null, 0, w)
        for (t in listOf(0f, 0.3f, 0.5f)) {
            val px = IntArray(w * h) { i ->
                var o = 0xFF shl 24
                for (sh in intArrayOf(16, 8, 0)) o = o or ((((pa[i] shr sh) and 255) * (1 - t) + ((pb[i] shr sh) and 255) * t).toInt() shl sh)
                o
            }
            val g = GridDetect.detect(px, w, h, 15)!!
            assertEquals(20, g.rows); assertEquals(20, g.cols)
            val o = CluePipeline.run(px, w, h, g.left, g.top, g.right, g.bottom, 0, 0, 20, 20)!!
            println("mezcla $t: ok=${o.ok} intentos=${o.attempts}")
            assertTrue("mezcla $t", o.ok)
        }
    }

    @Test fun allImages() {
        val files = dirs.flatMap { d -> File(d).listFiles { f -> f.name.endsWith(".jpg") || f.name.endsWith(".png") }?.toList() ?: emptyList() }
            .sortedBy { it.name }
        if (files.isEmpty()) return
        val report = StringBuilder()
        val truth = DigitEvalTest.FIX
        var okImgs = 0; var puzzles = 0
        val failures = ArrayList<String>()
        for (f in files) {
            val im = ImageIO.read(f) ?: continue
            val w = im.width; val h = im.height; val px = im.getRGB(0, 0, w, h, null, 0, w)
            val minCell = if (w >= 1000) 14 else 8
            val g = GridDetect.detect(px, w, h, minCell)
            if (g == null) { report.append("${f.path}: sin rejilla\n"); continue }
            puzzles++
            val t0 = System.currentTimeMillis()
            val o = CluePipeline.run(px, w, h, g.left, g.top, g.right, g.bottom, 0, 0, g.rows, g.cols)
            val ms = System.currentTimeMillis() - t0
            if (o == null) { report.append("${f.path}: ${g.rows}x${g.cols} sin glifos\n"); continue }
            if (o.ok) okImgs++
            if (f.name in MUST_PASS && !o.ok) failures.add(f.name)
            if (f.name in NOT_PUZZLE && o.ok) failures.add("${f.name} (no es nonograma)")
            OCCLUDED[f.name]?.let { (r, c) -> if (o.ok || !o.doubtRows.containsAll(r) || !o.doubtCols.containsAll(c)) failures.add("${f.name} (tapada: debe marcar f$r c$c)") }
            var digitsTxt = "glifos=${o.lines.glyphs.size}"
            truth[f.name]?.let { (rt, ct) ->
                val all = (o.lines.rowGroups.zip(rt) + o.lines.colGroups.zip(ct)).sumOf { (gs, t) ->
                    val d = gs.flatMap { it.glyphs }.map { o.digit[it] }
                    if (d.size != t.length) 0 else d.indices.count { d[it] == t[it] - '0' }
                }
                digitsTxt = "dígitos bien=$all/${rt.sumOf { it.length } + ct.sumOf { it.length }}"
            }
            report.append("${f.path}: ${g.rows}x${g.cols} ${if (o.ok) "VALIDA+RESUELTA" else "FALLA ${o.check.problems}"} $digitsTxt intentos=${o.attempts} combos=${o.combos} ${ms}ms" +
                (if (!o.ok) " dudosas f=${o.doubtRows.map { it + 1 }} c=${o.doubtCols.map { it + 1 }}" else "") + "\n   ${o.log}\n" +
                "   filas=${o.rows.map { l -> l.joinToString(" ") { it.len.toString() } }}\n   cols=${o.cols.map { l -> l.joinToString(" ") { it.len.toString() } }}\n")
        }
        report.append("TOTAL: $okImgs/$puzzles válidas y resueltas\n")
        println(report)
        File("build/pipeline-report.txt").writeText(report.toString())
        assertTrue("fallan: $failures", failures.isEmpty())
    }
}
