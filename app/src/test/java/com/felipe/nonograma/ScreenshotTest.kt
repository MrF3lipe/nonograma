package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import javax.imageio.ImageIO

/**
 * Capturas reales de Felipe: detección de rejilla + segmentación de dígitos + agrupación +
 * relleno por vecino más cercano cuando el OCR deja líneas vacías. El OCR se simula con la verdad.
 */
class ScreenshotTest {
    class Fixture(val file: String, val minCell: Int,
                  val rowGlyphs: List<String>, val rows: List<List<Int>>,
                  val colGlyphs: List<String>, val cols: List<List<Int>>,
                  val ocrFails: Set<String> = emptySet())

    private fun digits(s: String) = s.split(" ").map { it.toInt() }

    private val j1 = Fixture("juego.jpg", 14,
        listOf("132123", "14223", "142141", "231243", "12241", "1125", "4131", "32", "621", "72", "82", "15411", "26422", "2644", "32342", "2143", "36221", "12514", "21624", "10212"),
        listOf("1 3 2 1 2 3", "1 4 2 2 3", "1 4 2 1 4 1", "2 3 1 2 4 3", "1 2 2 4 1", "1 1 2 5", "4 1 3 1", "3 2", "6 2 1", "7 2", "8 2", "1 5 4 1 1", "2 6 4 2 2", "2 6 4 4", "3 2 3 4 2", "2 1 4 3", "3 6 2 2 1", "1 2 5 1 4", "2 1 6 2 4", "10 2 1 2").map(::digits),
        listOf("473", "3342", "21331", "754", "51621", "4272", "2182", "5344", "3138", "1237", "12235", "13331", "2342", "137", "441", "4222", "324", "1124", "3132", "1331"),
        listOf("4 7 3", "3 3 4 2", "2 1 3 3 1", "7 5 4", "5 1 6 2 1", "4 2 7 2", "2 1 8 2", "5 3 4 4", "3 1 3 8", "1 2 3 7", "1 2 2 3 5", "1 3 3 3 1", "2 3 4 2", "1 3 7", "4 4 1", "4 2 2 2", "3 2 4", "1 1 2 4", "3 1 3 2", "1 3 3 1").map(::digits),
        ocrFails = setOf("f8", "c4"))

    // juego2.jpg llega reducida (576x1280, dígitos de ~9 px): caso difícil a propósito.
    private val j2 = Fixture("juego2.jpg", 8,
        listOf("11111", "3112", "4111", "3111", "411", "28", "431", "5221", "663", "6134", "6215", "5236", "516", "236", "716", "4116", "3135", "119", "3110", "315"),
        listOf("1 11 1 1", "3 11 2", "4 11 1", "3 1 11", "4 11", "2 8", "4 3 1", "5 2 2 1", "6 6 3", "6 1 3 4", "6 2 1 5", "5 2 3 6", "5 1 6", "2 3 6", "7 1 6", "4 1 1 6", "3 1 3 5", "1 1 9", "3 1 10", "3 1 5").map(::digits),
        listOf("43", "366", "3742", "1222", "3911", "1181", "223", "331", "423", "511", "52111", "6235", "6215", "626", "638", "910", "673", "1573", "1373", "31153"),
        listOf("4 3", "3 6 6", "3 7 4 2", "12 2 2", "3 9 1 1", "1 1 8 1", "2 2 3", "3 3 1", "4 2 3", "5 1 1", "5 2 1 1 1", "6 2 3 5", "6 2 1 5", "6 2 6", "6 3 8", "9 10", "6 7 3", "1 5 7 3", "1 3 7 3", "3 1 1 5 3").map(::digits),
        // lo que Felipe vio en 1.0.2: la fila 9 y la columna 2 vuelven vacías del OCR
        ocrFails = setOf("f9", "c2"))

    private fun run(fx: Fixture) {
        val im = ImageIO.read(javaClass.classLoader!!.getResourceAsStream(fx.file))
        val w = im.width; val h = im.height; val px = im.getRGB(0, 0, w, h, null, 0, w)
        val g = GridDetect.detect(px, w, h, fx.minCell)!!
        assertEquals("${fx.file} filas", 20, g.rows); assertEquals("${fx.file} cols", 20, g.cols)
        val ch = (g.bottom - g.top) / 20f; val cw = (g.right - g.left) / 20f
        val rowGl = (0 until 20).map { r -> Segment.rowGlyphs(px, w, 0, (g.top + r * ch).toInt(), g.left - 3, (g.top + (r + 1) * ch).toInt()) }
        val dom = Segment.dominant(rowGl.flatten())
        val rowG = rowGl.map { Segment.stackRow(Segment.groupRow(it, dom), g.left, cw) }
        val colG = (0 until 20).map { c ->
            Segment.stackCol(Segment.colGroupsRaw(px, w, (g.left + c * cw).toInt(), 0, (g.left + (c + 1) * cw).toInt(), g.top - 3), g.top, ch)
        }
        // OCR simulado (verdad) salvo en las líneas que "fallan"
        val digit = HashMap<Segment.Glyph, Int>()
        fun sim(groups: List<Segment.Group>, truth: String, key: String) {
            assertEquals("${fx.file} glifos $key", truth.length, groups.sumOf { it.glyphs.size })
            var k = 0
            for (gr in groups) for (gl in gr.glyphs) { digit[gl] = if (key in fx.ocrFails) -1 else truth[k] - '0'; k++ }
        }
        rowG.forEachIndexed { i, gr -> sim(gr, fx.rowGlyphs[i], "f${i + 1}") }
        colG.forEachIndexed { i, gr -> sim(gr, fx.colGlyphs[i], "c${i + 1}") }
        val all = (rowG + colG).flatten().flatMap { it.glyphs }
        val filled = Segment.fillUnknown(all, digit) { Segment.feature(px, w, it) }
        assertTrue(filled > 0)
        val rows = rowG.map { l -> Segment.finalize(l.map { gr -> gr.glyphs.map { digit[it]!! } }, 20) }
        val cols = colG.map { l -> Segment.finalize(l.map { gr -> gr.glyphs.map { digit[it]!! } }, 20) }
        assertEquals("${fx.file} filas", fx.rows, rows)
        assertEquals("${fx.file} columnas", fx.cols, cols)
        val rb = rows.map { l -> l.map { Block(it) } }; val cb = cols.map { l -> l.map { Block(it) } }
        val res = Solver.solve(rb, cb, 10_000)
        assertEquals(Solver.Status.SOLVED, res.status)
        assertTrue(Solver.verify(rb, cb, res.grid!!))
    }

    @Test fun juego1() = run(j1)
    @Test fun juego2() = run(j2)

    @Test fun finalizeRules() {
        assertEquals(listOf(10, 2), Segment.finalize(listOf(listOf(1), listOf(0), listOf(2)), 20))
        assertEquals(listOf(1, 3, 2), Segment.finalize(listOf(listOf(1, 3), listOf(2)), 8)) // 13 2 no cabe en 8
        assertEquals(listOf(12, 3), Segment.finalize(listOf(listOf(1, 2), listOf(3)), 20))
    }
}
