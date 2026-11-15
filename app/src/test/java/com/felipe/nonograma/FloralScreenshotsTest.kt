package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import javax.imageio.ImageIO

/**
 * Capturas de Felipe (t1732/t1735): nonogramas impresos sobre fondo floral, pistas en celdas beige.
 * En 1.0.15 la rejilla se confundía con el wallpaper y los dígitos de dos cifras / el marco se leían mal.
 */
@RunWith(Parameterized::class)
class FloralScreenshotsTest(val name: String, val rows: Int, val cols: Int, val minCell: Int,
                            val expectedRows: List<List<Int>>, val expectedCols: List<List<Int>>) {

    companion object {
        private fun L(vararg v: Int) = v.toList()

        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(
            arrayOf("floral-1107", 14, 14, 8,
                listOf(L(1), L(1), L(2), L(3, 2), L(3, 3), L(3, 3), L(3, 2), L(2, 1), L(3), L(3, 4), L(8), L(5, 2), L(3, 1), L(4)),
                listOf(L(1), L(1), L(2), L(2), L(3), L(5), L(6), L(5), L(4), L(5), L(2, 3, 1), L(4, 2, 1), L(3, 4, 4), L(2, 2))),
            arrayOf("floral-1112", 16, 13, 14,
                listOf(L(3), L(2, 1), L(7), L(1), L(5), L(2, 4), L(1, 1, 3), L(2, 1, 1, 1, 4), L(1, 1, 3), L(1, 4, 1, 3), L(1, 3), L(2, 1, 1, 4), L(1, 1, 3), L(2, 4), L(5), L(8)),
                listOf(L(5), L(2, 2), L(1, 1, 1, 1), L(1, 1, 1, 1, 1, 1, 1), L(2, 1, 1, 2), L(3, 1, 4, 1, 2), L(1, 3, 2), L(1, 1, 2, 1, 1, 3), L(2, 2, 1, 3), L(1, 3, 3, 1), L(9), L(7), L(5))),
            arrayOf("floral-1113", 10, 20, 14,
                listOf(L(1, 3), L(3, 3, 5), L(4, 7, 2, 1), L(9, 5, 1), L(9, 2, 2), L(9, 5), L(7, 3), L(7), L(3), L(5)),
                listOf(L(1), L(2), L(3), L(3), L(4), L(4), L(4), L(6), L(6, 1), L(9), L(10), L(2, 6), L(1, 1, 1, 1), L(2, 2), L(4), L(6), L(4, 1), L(2, 1, 1), L(2, 2), L(4))),
            arrayOf("floral-1114", 12, 17, 14,
                listOf(L(5), L(1, 1, 2, 3), L(1, 1, 2, 3, 2), L(3, 1, 2, 2, 2), L(1, 1, 1, 1, 3, 2, 2), L(5, 2, 3, 2), L(4, 7, 2), L(3, 5, 2), L(3, 3, 3), L(12), L(13), L(7)),
                listOf(L(2), L(3, 2), L(8), L(3, 6), L(8), L(3), L(5, 4), L(2, 7), L(2, 2, 6), L(1, 3, 2, 3), L(1, 1, 1, 2, 3), L(1, 2, 3, 2), L(2, 4, 3), L(1, 2, 3), L(3, 2, 1), L(6), L(3))),
            arrayOf("floral-1115", 14, 14, 14,
                listOf(L(9), L(8, 3), L(14), L(11, 1), L(12), L(10), L(11), L(10), L(11), L(4, 2, 4), L(2, 1, 3), L(2, 2), L(1, 1), L(1)),
                listOf(L(1, 4), L(2, 3), L(4, 2), L(4, 1, 2), L(10), L(9), L(10), L(11), L(9), L(11), L(1, 11), L(12), L(3, 6), L(7))),
            arrayOf("floral-1116", 10, 16, 14,
                listOf(L(4), L(6), L(8), L(2, 8), L(12, 1), L(2, 8, 2), L(12), L(3, 4), L(2, 2), L(1)),
                listOf(L(2), L(3), L(1, 2), L(3, 1), L(3, 1), L(6), L(7), L(7), L(9), L(7, 1), L(2, 4), L(1, 3), L(3), L(2), L(4), L(2, 3))),
            arrayOf("floral-1117", 15, 14, 14,
                listOf(L(2), L(4), L(4), L(10), L(12), L(14), L(14), L(2, 8, 2), L(12), L(14), L(14), L(4, 4, 4), L(2, 2), L(2, 2), L(3, 3)),
                listOf(L(3, 3), L(9, 1), L(4, 7), L(9, 2), L(8), L(11), L(12), L(12), L(11), L(8), L(9, 2), L(4, 7), L(9, 1), L(3, 3))),
        )
    }

    private fun load(): Triple<IntArray, Int, Int> {
        val im = ImageIO.read(javaClass.classLoader!!.getResourceAsStream("$name.jpg"))
        val w = im.width; val h = im.height
        return Triple(im.getRGB(0, 0, w, h, null, 0, w), w, h)
    }

    private fun fmt(lines: List<List<Block>>) = lines.map { l -> l.map { it.len } }

    @Test fun readsCluesAndSolves() {
        val (px, w, h) = load()
        val g = GridDetect.detect(px, w, h, minCell)
        assertNotNull("$name sin rejilla", g)
        g!!
        assertEquals("$name filas", rows, g.rows)
        assertEquals("$name cols", cols, g.cols)
        val o = CluePipeline.run(px, w, h, g.left, g.top, g.right, g.bottom, 0, 0, g.rows, g.cols)
        assertNotNull("$name sin glifos", o)
        o!!
        assertEquals("$name pistas filas", expectedRows, fmt(o.rows))
        assertEquals("$name pistas cols", expectedCols, fmt(o.cols))
        assertTrue("$name pistas inválidas: ${o.check.problems} ${o.log}", o.ok)
        val res = Solver.solve(o.rows, o.cols, 10_000)
        assertEquals("$name solver", Solver.Status.SOLVED, res.status)
        assertTrue(Solver.verify(o.rows, o.cols, res.grid!!))
    }
}
