package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import javax.imageio.ImageIO

/**
 * tg-1732 (1107.jpg): captura 360×800 de un 14×14 sobre fondo floral, pistas en celdas beige
 * y dígitos pequeños. En 1.0.15 GridDetect devolvía 23×8 y las pistas salían ilegibles.
 */
class Tg1732Test {
    private val expectedRows = listOf(
        listOf(1), listOf(1), listOf(2), listOf(3, 2), listOf(3, 3),
        listOf(3, 3), listOf(3, 2), listOf(2, 1), listOf(3), listOf(3, 4),
        listOf(8), listOf(5, 2), listOf(3, 1), listOf(4)
    )
    private val expectedCols = listOf(
        listOf(1), listOf(1), listOf(2), listOf(2), listOf(3),
        listOf(5), listOf(6), listOf(5), listOf(4), listOf(5),
        listOf(2, 3, 1), listOf(4, 2, 1), listOf(3, 4, 4), listOf(2, 2)
    )

    private fun load(): Triple<IntArray, Int, Int> {
        val im = ImageIO.read(javaClass.classLoader!!.getResourceAsStream("floral-1107.jpg"))
        val w = im.width; val h = im.height
        return Triple(im.getRGB(0, 0, w, h, null, 0, w), w, h)
    }

    private fun fmt(lines: List<List<Block>>) = lines.map { l -> l.map { it.len } }

    @Test fun detectsPlayable14x14() {
        val (px, w, h) = load()
        val g = GridDetect.detect(px, w, h, 8)
        assertNotNull("sin rejilla", g)
        g!!
        assertEquals("filas", 14, g.rows)
        assertEquals("cols", 14, g.cols)
        // área jugable medida en la captura: izq 96, arriba 317, der 312, abajo 533
        assertTrue("left ${g.left}", kotlin.math.abs(g.left - 96) <= 6)
        assertTrue("top ${g.top}", kotlin.math.abs(g.top - 317) <= 6)
        assertTrue("right ${g.right}", kotlin.math.abs(g.right - 312) <= 8)
        assertTrue("bottom ${g.bottom}", kotlin.math.abs(g.bottom - 533) <= 8)
    }

    @Test fun readsCluesAndSolves() {
        val (px, w, h) = load()
        val g = GridDetect.detect(px, w, h, 8)!!
        val o = CluePipeline.run(px, w, h, g.left, g.top, g.right, g.bottom, 0, 0, g.rows, g.cols)
        assertNotNull("sin glifos", o)
        o!!
        assertEquals("filas", expectedRows, fmt(o.rows))
        assertEquals("columnas", expectedCols, fmt(o.cols))
        assertTrue("pistas inválidas: ${o.check.problems} ${o.log}", o.ok)
        val rb = o.rows; val cb = o.cols
        val res = Solver.solve(rb, cb, 10_000)
        assertEquals(Solver.Status.SOLVED, res.status)
        assertTrue(Solver.verify(rb, cb, res.grid!!))
    }
}
