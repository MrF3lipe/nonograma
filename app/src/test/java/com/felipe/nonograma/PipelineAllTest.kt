package com.felipe.nonograma

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

    @Test fun allImages() {
        val files = dirs.flatMap { d -> File(d).listFiles { f -> f.name.endsWith(".jpg") || f.name.endsWith(".png") }?.toList() ?: emptyList() }
            .sortedBy { it.name }
        if (files.isEmpty()) return
        val report = StringBuilder()
        val truth = DigitEvalTest.FIX
        var okImgs = 0; var puzzles = 0
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
    }
}
