package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import javax.imageio.ImageIO

/** Captura real de Felipe (juego.jpg 1080x2400): detección de rejilla + segmentación de dígitos. */
class ScreenshotTest {
    private val rowsTxt = listOf("132123", "14223", "142141", "231243", "12241", "1125", "4131", "32", "621", "72", "82", "15411", "26422", "2644", "32342", "2143", "36221", "12514", "21624", "10212")
    private val rowsTruth = listOf(listOf(1,3,2,1,2,3), listOf(1,4,2,2,3), listOf(1,4,2,1,4,1), listOf(2,3,1,2,4,3), listOf(1,2,2,4,1), listOf(1,1,2,5), listOf(4,1,3,1), listOf(3,2), listOf(6,2,1), listOf(7,2), listOf(8,2), listOf(1,5,4,1,1), listOf(2,6,4,2,2), listOf(2,6,4,4), listOf(3,2,3,4,2), listOf(2,1,4,3), listOf(3,6,2,2,1), listOf(1,2,5,1,4), listOf(2,1,6,2,4), listOf(10,2,1,2))
    private val colsTxt = listOf("473", "3342", "21331", "754", "51621", "4272", "2182", "5344", "3138", "1237", "12235", "13331", "2342", "137", "441", "4222", "324", "1124", "3132", "1331")

    private fun load(): Triple<IntArray, Int, Int> {
        val im = ImageIO.read(javaClass.classLoader!!.getResourceAsStream("juego.jpg"))
        val w = im.width; val h = im.height
        return Triple(im.getRGB(0, 0, w, h, null, 0, w), w, h)
    }

    @Test fun fullPipelineOnRealScreenshot() {
        val (px, w, h) = load()
        val g = GridDetect.detect(px, w, h, 14)!!
        println("rejilla: ${g.left},${g.top}-${g.right},${g.bottom} ${g.rows}x${g.cols}")
        assertEquals(20, g.rows); assertEquals(20, g.cols)
        val ch = (g.bottom - g.top) / 20f; val cw = (g.right - g.left) / 20f

        // filas
        val rows = (0 until 20).map { r ->
            val y0 = (g.top + r * ch).toInt(); val y1 = (g.top + (r + 1) * ch).toInt()
            val gl = Segment.rowGlyphs(px, w, 0, y0, g.left - 3, y1)
            val groups = Segment.stackRow(Segment.groupRow(gl), g.left, cw)
            assertEquals("glifos fila ${r + 1}", rowsTxt[r].length, groups.sumOf { it.glyphs.size })
            // OCR simulado: cada glifo recibe su dígito verdadero, en orden
            var k = 0
            val vals = groups.map { gr -> gr.glyphs.map { rowsTxt[r][k++] - '0' } }
            Segment.finalize(vals, 20)
        }
        assertEquals(rowsTruth, rows)
        // el "10" amarillo es un grupo destacado
        val gl20 = Segment.groupRow(Segment.rowGlyphs(px, w, 0, (g.top + 19 * ch).toInt(), g.left - 3, g.bottom))
        assertTrue(gl20[0].highlight && gl20[0].glyphs.size == 2)

        // columnas: franja de cada columna desde arriba de la pantalla hasta la rejilla
        val cols = (0 until 20).map { c ->
            val x0 = (g.left + c * cw).toInt(); val x1 = (g.left + (c + 1) * cw).toInt()
            val lines = Segment.colGroupsRaw(px, w, x0, 0, x1, g.top - 3)
            val groups = Segment.stackCol(lines, g.top, ch)
            assertEquals("números col ${c + 1}", colsTxt[c].length, groups.size)
            var k = 0
            Segment.finalize(groups.map { gr -> gr.glyphs.map { colsTxt[c][k++] - '0' } }, 20)
        }
        assertEquals(colsTxt.map { s -> s.map { it - '0' } }, cols)

        val r = Solver.solve(rows.map { l -> l.map { Block(it) } }, cols.map { l -> l.map { Block(it) } }, 10_000)
        assertEquals(Solver.Status.SOLVED, r.status)
        assertTrue(Solver.verify(rows.map { l -> l.map { Block(it) } }, cols.map { l -> l.map { Block(it) } }, r.grid!!))
    }

    @Test fun finalizeRules() {
        assertEquals(listOf(10, 2), Segment.finalize(listOf(listOf(1), listOf(0), listOf(2)), 20))
        assertEquals(listOf(1, 3, 2), Segment.finalize(listOf(listOf(1, 3), listOf(2)), 8)) // 13 2 no cabe en 8
        assertEquals(listOf(12, 3), Segment.finalize(listOf(listOf(1, 2), listOf(3)), 20))
    }
}
