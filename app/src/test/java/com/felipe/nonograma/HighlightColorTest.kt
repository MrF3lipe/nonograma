package com.felipe.nonograma

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * tg-1557 (y los generados) son B/N con los números ≥10 en amarillo, también en las columnas apiladas. La paleta
 * debe quedar con un solo color: antes las columnas amarillas daban "Auto·Color" y la validación con colores fallaba.
 */
class HighlightColorTest {
    private fun palette(f: File): Int? {
        val im = ImageIO.read(f) ?: return null; val w = im.width; val h = im.height; val px = im.getRGB(0, 0, w, h, null, 0, w)
        val g = GridDetect.detect(px, w, h, if (w >= 1000) 14 else 8) ?: return null
        val o = CluePipeline.run(px, w, h, g.left, g.top, g.right, g.bottom, 0, 0, g.rows, g.cols) ?: return null
        fun values(l: List<Segment.Group>, len: Int): Pair<List<Int>, List<Segment.Group>> {
            val keep = l.filter { gr -> gr.glyphs.any { (o.digit[it] ?: -1) >= 0 } }
            return Segment.finalize(keep.map { gr -> gr.glyphs.mapNotNull { o.digit[it]?.takeIf { d -> d >= 0 } } }, len) to keep
        }
        val lines = o.lines.rowGroups.map { values(it, g.cols) } + o.lines.colGroups.map { values(it, g.rows) }
        val all = lines.flatMap { it.second }
        val base = all.filter { !it.highlight }.map { it.rgb }.firstOrNull { Segment.isNeutral(it) } ?: 0xFF000000.toInt()
        for (gr in all) if (gr.highlight) gr.rgb = base
        ClueLogic.highlightOnlyMultiDigit(lines).forEach { it.rgb = base }
        return ClueLogic.cluster(all.map { it.rgb }).first.size
    }

    @Test fun yellowTensAreNotAColor() {
        val files = listOf(File("/home/box/telegram-relay/media/tg-1557.jpg")) +
            (File("/workspace/nonotest/caps").listFiles()?.filter { it.name.contains("20x20") }?.sortedBy { it.name } ?: emptyList())
        for (f in files.filter { it.exists() }) assertEquals(f.name, 1, palette(f))
    }

    @Test fun realColorStillDetected() {
        // un color que aparece en números de una cifra sí es color de pista
        val red = 0xFFD03030.toInt(); val wht = 0xFFFFFFFF.toInt()
        fun gr(c: Int) = Segment.Group(listOf(Segment.Glyph(0, 0, 1, 1, c)), c)
        val lines = listOf(listOf(3, 12) to listOf(gr(red), gr(wht)), listOf(2) to listOf(gr(red)))
        assertEquals(0, ClueLogic.highlightOnlyMultiDigit(lines).size)
    }
}
