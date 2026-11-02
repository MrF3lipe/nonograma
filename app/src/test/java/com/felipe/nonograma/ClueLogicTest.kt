package com.felipe.nonograma

import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test

class ClueLogicTest {
    private fun t(v: Int, x: Float, y: Float) = Token(v, x, y, Rect())

    @Test fun assignRowsAndCols() {
        // rejilla 3 filas de 0..300 px; pistas de filas a la izquierda
        val toks = listOf(t(10, 80f, 40f), t(1, 40f, 45f), t(3, 50f, 150f), t(2, 60f, 260f), t(4, 90f, 255f), t(7, 50f, 400f))
        val r = ClueLogic.assign(toks, 3, 0f, 300f, true)
        assertEquals(listOf(listOf(1, 10), listOf(3), listOf(2, 4)), r.map { l -> l.map { it.value } })
        // columnas: orden por y
        val c = ClueLogic.assign(listOf(t(5, 20f, -10f), t(2, 22f, -50f), t(0, 150f, -20f)), 2, 0f, 200f, false)
        assertEquals(listOf(listOf(2, 5), listOf()), c.map { l -> l.map { it.value } })
    }

    @Test fun normalizesLookalikes() {
        assertEquals('1', ClueLogic.normChar('l')); assertEquals('0', ClueLogic.normChar('O'))
        assertEquals('5', ClueLogic.normChar('S')); assertNull(ClueLogic.normChar('-'))
    }

    @Test fun clustersColorsNeutralsTogether() {
        val red = 0xFFE53935.toInt(); val red2 = 0xFFD83A30.toInt(); val blue = 0xFF1E88E5.toInt()
        val (pal, idx) = ClueLogic.cluster(listOf(0xFF000000.toInt(), 0xFF555555.toInt(), red, red2, blue, 0xFFFFFFFF.toInt()))
        assertEquals(3, pal.size)
        assertEquals(listOf(1, 1, 2, 2, 3, 1), idx)
    }

    @Test fun detectsGridLineCount() {
        for (n in listOf(5, 10, 15, 20, 25, 30)) {
            val px = 600
            val prof = FloatArray(px) { 2f }
            for (i in 0..n) { val p = (i * px / n).coerceAtMost(px - 1); prof[p] = if (i % 5 == 0) 60f else 40f }
            assertEquals("n=$n", n, ClueLogic.detectCount(prof))
        }
        assertEquals(0, ClueLogic.detectCount(FloatArray(600) { 3f }))
    }
}

class ClueLogicStackTest {
    private fun t(v: Int, x: Float, y: Float) = Token(v, x, y, android.graphics.Rect())

    @Test fun stackDropsFarText() {
        // fila: rejilla empieza en x=500, celda 40; "12" del título lejos a la izquierda
        val band = listOf(t(12, 60f, 0f), t(3, 470f, 0f), t(1, 430f, 0f), t(2, 390f, 0f))
        assertEquals(listOf(2, 1, 3), ClueLogic.stack(band, 500f, 40f, true).map { it.value })
    }

    @Test fun inferCountFromClueGroups() {
        // 20 filas de 30 px (600 px); algunas sin pista detectada
        val ys = (0 until 20).filter { it % 7 != 3 }.flatMap { r -> listOf(r * 30f + 15f, r * 30f + 16f) }
        assertEquals(20, ClueLogic.inferCount(ys, 600f, 6f))
        assertEquals(0, ClueLogic.inferCount(listOf(10f), 600f, 6f))
    }
}
