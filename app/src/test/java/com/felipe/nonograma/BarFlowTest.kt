package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test

/**
 * Barra de 1.0.11: Pistas, Auto·B/N, Leer, Editar, ⏺, ▶, 1x, 🔓, Ocultar, ⟲, ✕. Anchos aproximados en px de
 * cada botón (texto a 14sp + 9dp de relleno por lado) a 720 px (2x) y 1080 px (2.625x). En 1.0.10 todo iba en
 * una fila y a partir de «1x»/«Ocultar» se salía de la pantalla.
 */
class BarFlowTest {
    private val labels = listOf("Pistas", "Auto·B/N", "Leer", "Editar", "⏺", "▶", "1x", "🔓", "Ocultar", "⟲", "✕")
    private fun widths(d: Float) = labels.map { (18 * d + it.length * 8.2f * d).toInt() }

    @Test fun allButtonsVisibleAt720And1080() {
        for ((screen, d) in listOf(720 to 2f, 1080 to 2.625f, 1440 to 3.5f)) {
            val ws = widths(d); val maxW = screen - (16 * d).toInt()
            assertTrue("en una fila no cabe ($screen)", ws.sum() > maxW)
            val rows = BarFlow.rows(ws, maxW)
            assertEquals(labels.indices.toList(), rows.flatten())
            for (r in rows) assertTrue("$screen: fila ancha ${r.sumOf { ws[it] }} > $maxW", r.sumOf { ws[it] } <= maxW)
            assertTrue(rows.size <= 3)
            println("$screen px: " + rows.map { r -> r.map { labels[it] } })
        }
    }

    @Test fun tooWideSingleButtonStillShown() {
        assertEquals(listOf(listOf(0), listOf(1)), BarFlow.rows(listOf(500, 50), 100))
    }
}

/** La fila se calcula con el texto más largo de cada botón: si el modo crece a «Auto·Color», nada se sale. */
class BarFlowLongestTextTest {
    @Test fun modeTextGrowthDoesNotOverflow() {
        val d = 2.625f; val maxW = 1080 - (16 * d).toInt()
        fun w(t: String) = (18 * d + t.length * 8.2f * d).toInt()
        val shortLabels = listOf("Pistas", "Auto", "Leer", "Editar", "⏺", "▶", "1x", "🔓", "Ocultar", "⟲", "✕")
        val longest = shortLabels.map { if (it == "Auto") "Auto·Color" else it }
        // como en 1.0.11: filas calculadas con «Auto», mostradas con «Auto·Color» → la primera fila se sale
        val old = BarFlow.rows(shortLabels.map(::w), maxW)
        assertTrue(old.any { r -> r.sumOf { w(longest[it]) } > maxW })
        // ahora: filas con el texto más largo
        val now = BarFlow.rows(longest.map(::w), maxW)
        for (r in now) assertTrue(r.sumOf { w(longest[it]) } <= maxW)
        assertEquals(shortLabels.indices.toList(), now.flatten())
    }
}
