package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Capturas sintéticas tipo app de nonogramas. */
class GridDetectTest {
    private class Img(val w: Int, val h: Int, bg: Int) {
        val px = IntArray(w * h) { bg }
        fun rect(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
            for (y in maxOf(0, y0) until minOf(h, y1)) for (x in maxOf(0, x0) until minOf(w, x1)) px[y * w + x] = c
        }
    }

    private fun lineGrid(img: Img, left: Int, top: Int, cell: Int, rows: Int, cols: Int, line: Int, thick: Int, rnd: Random, fill: Int) {
        for (r in 0 until rows) for (c in 0 until cols) if (rnd.nextDouble() < 0.4)
            img.rect(left + c * cell + 2, top + r * cell + 2, left + (c + 1) * cell - 1, top + (r + 1) * cell - 1, fill)
        for (r in 0..rows) { val t = if (r % 5 == 0) thick else 1; img.rect(left, top + r * cell, left + cols * cell + t, top + r * cell + t, line) }
        for (c in 0..cols) { val t = if (c % 5 == 0) thick else 1; img.rect(left + c * cell, top, left + c * cell + t, top + rows * cell + t, line) }
    }

    /** "números" de pista: bloques pequeños oscuros. */
    private fun fakeClues(img: Img, left: Int, top: Int, cell: Int, rows: Int, cols: Int, rnd: Random) {
        for (r in 0 until rows) { var x = left - cell; repeat(1 + rnd.nextInt(3)) { img.rect(x + cell / 4, top + r * cell + cell / 4, x + cell * 3 / 4, top + r * cell + cell * 3 / 4, 0xFF222222.toInt()); x -= cell } }
        for (c in 0 until cols) { var y = top - cell; repeat(1 + rnd.nextInt(3)) { img.rect(left + c * cell + cell / 4, y + cell / 4, left + c * cell + cell * 3 / 4, y + cell * 3 / 4, 0xFF222222.toInt()); y -= cell } }
    }

    private fun check(img: Img, left: Int, top: Int, cell: Int, rows: Int, cols: Int) {
        val g = GridDetect.detect(img.px, img.w, img.h)
        assertNotNull("no detectada ${rows}x$cols", g)
        g!!
        assertEquals("filas", rows, g.rows); assertEquals("cols", cols, g.cols)
        val tol = 5
        assertTrue("left ${g.left} vs $left", kotlin.math.abs(g.left - left) <= tol)
        assertTrue("top ${g.top} vs $top", kotlin.math.abs(g.top - top) <= tol)
        assertTrue("right ${g.right}", kotlin.math.abs(g.right - (left + cols * cell)) <= tol)
        assertTrue("bottom ${g.bottom}", kotlin.math.abs(g.bottom - (top + rows * cell)) <= tol)
    }

    @Test fun classicLines() {
        for ((n, cell) in listOf(5 to 120, 10 to 70, 15 to 50, 20 to 40, 25 to 32, 30 to 27)) {
            val rnd = Random(n)
            val img = Img(1080, 2400, 0xFFFFFFFF.toInt())
            img.rect(0, 0, 1080, 140, 0xFF3949AB.toInt()) // barra superior
            val left = 1080 - n * cell - 20; val top = 900
            fakeClues(img, left, top, cell, n, n, rnd)
            lineGrid(img, left, top, cell, n, n, 0xFF9E9E9E.toInt(), 3, rnd, 0xFF303030.toInt())
            img.rect(100, 2200, 980, 2300, 0xFF4CAF50.toInt()) // botón abajo
            check(img, left, top, cell, n, n)
        }
    }

    @Test fun nonSquareDarkTheme() {
        val rnd = Random(5)
        val img = Img(1080, 2400, 0xFF121212.toInt())
        val cell = 45; val rows = 10; val cols = 15; val left = 1080 - cols * cell - 10; val top = 1000
        fakeClues(img, left, top, cell, rows, cols, rnd)
        lineGrid(img, left, top, cell, rows, cols, 0xFF5A5A5A.toInt(), 3, rnd, 0xFFE0E0E0.toInt())
        check(img, left, top, cell, rows, cols)
    }

    @Test fun cellsWithGapsNoLines() {
        // estilo "baldosas": celdas grises separadas por huecos de 3 px sobre fondo blanco
        val img = Img(1080, 2400, 0xFFFFFFFF.toInt())
        val cell = 50; val n = 15; val left = 300; val top = 800
        for (r in 0 until n) for (c in 0 until n) img.rect(left + c * cell + 3, top + r * cell + 3, left + (c + 1) * cell, top + (r + 1) * cell, 0xFFD0D0D0.toInt())
        fakeClues(img, left, top, cell, n, n, Random(1))
        val g = GridDetect.detect(img.px, img.w, img.h)!!
        assertEquals(n, g.rows); assertEquals(n, g.cols)
        assertTrue(kotlin.math.abs(g.left - left) <= 6 && kotlin.math.abs(g.top - top) <= 6)
    }

    @Test fun stripedClueRowsExtendLeft() {
        // fondo de pistas de fila alterno que se extiende a la izquierda de la rejilla
        val rnd = Random(9)
        val img = Img(1080, 2400, 0xFFFFFFFF.toInt())
        val cell = 40; val n = 20; val left = 1080 - n * cell - 10; val top = 1000
        for (r in 0 until n) if (r % 2 == 0) img.rect(0, top + r * cell, left, top + (r + 1) * cell, 0xFFEEEEFF.toInt())
        lineGrid(img, left, top, cell, n, n, 0xFF888888.toInt(), 3, rnd, 0xFF000000.toInt())
        check(img, left, top, cell, n, n)
    }

    @Test fun noGridReturnsNull() {
        val img = Img(1080, 2400, 0xFFFFFFFF.toInt())
        img.rect(100, 300, 900, 500, 0xFF3949AB.toInt())
        assertNull(GridDetect.detect(img.px, img.w, img.h))
    }

    @Test fun speed() {
        val img = Img(1080, 2400, 0xFFFFFFFF.toInt())
        lineGrid(img, 100, 800, 30, 30, 30, 0xFF888888.toInt(), 3, Random(2), 0xFF000000.toInt())
        val t = System.nanoTime(); GridDetect.detect(img.px, img.w, img.h)
        val ms = (System.nanoTime() - t) / 1e6
        println("detect 1080x2400: %.0f ms".format(ms)); assertTrue(ms < 3000)
    }

    @Test fun texturedBackgroundWithClueFrame() {
        // Cartón con pistas enmarcadas + fondo con textura, como la captura tg-1732.
        val rnd = Random(42)
        val img = Img(360, 800, 0xFFFFFFFF.toInt())
        // fondo floral: motas oscuras por toda la imagen
        for (i in 0 until 4000) {
            val x = rnd.nextInt(360); val y = rnd.nextInt(800)
            val s = 2 + rnd.nextInt(4)
            img.rect(x, y, x + s, y + s, if (rnd.nextBoolean()) 0xFF6B6B6B.toInt() else 0xFFD8D8D8.toInt())
        }
        val cell = 16; val n = 14
        val clueR = 3; val clueC = 2
        val left = 80; val top = 280
        val playL = left + clueC * cell; val playT = top + clueR * cell
        // cartón claro que tapa el fondo
        img.rect(left - 8, top - 8, left + (clueC + n) * cell + 8, top + (clueR + n) * cell + 8, 0xFFF5F5F0.toInt())
        // celdas beige de pistas
        val beige = 0xFFE8E0B0.toInt()
        for (r in 0 until clueR) for (c in 0 until n)
            img.rect(playL + c * cell + 1, top + r * cell + 1, playL + (c + 1) * cell, top + (r + 1) * cell, beige)
        for (r in 0 until n) for (c in 0 until clueC)
            img.rect(left + c * cell + 1, playT + r * cell + 1, left + (c + 1) * cell, playT + (r + 1) * cell, beige)
        // dígitos (bloques oscuros) en las pistas
        for (c in 0 until n) img.rect(playL + c * cell + 5, top + (clueR - 1) * cell + 4, playL + c * cell + 11, top + (clueR - 1) * cell + 12, 0xFF222222.toInt())
        for (r in 0 until n) img.rect(left + (clueC - 1) * cell + 5, playT + r * cell + 4, left + (clueC - 1) * cell + 11, playT + r * cell + 12, 0xFF222222.toInt())
        // rejilla completa (pistas + jugable), líneas cada 5 más gruesas medidas desde el área jugable
        val line = 0xFF444444.toInt()
        val totalR = clueR + n; val totalC = clueC + n
        for (r in 0..totalR) {
            val y = top + r * cell
            val t = if (r == 0 || r == totalR || r == clueR || (r > clueR && (r - clueR) % 5 == 0) ) 3 else 1
            img.rect(left, y, left + totalC * cell + t, y + t, line)
        }
        for (c in 0..totalC) {
            val x = left + c * cell
            val t = if (c == 0 || c == totalC || c == clueC || (c > clueC && (c - clueC) % 5 == 0) ) 3 else 1
            img.rect(x, top, x + t, top + totalR * cell + t, line)
        }
        val g = GridDetect.detect(img.px, img.w, img.h, 6)
        assertNotNull("no detectada", g)
        g!!
        assertEquals("filas jugables", n, g.rows)
        assertEquals("cols jugables", n, g.cols)
        val tol = 6
        assertTrue("left ${g.left} vs $playL", kotlin.math.abs(g.left - playL) <= tol)
        assertTrue("top ${g.top} vs $playT", kotlin.math.abs(g.top - playT) <= tol)
    }

}
