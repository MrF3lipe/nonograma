package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import javax.imageio.ImageIO

/** Reintentos de lectura: validación, combinaciones de dígitos dudosos, cambio de variante y topes. */
class CluePipelineTest {
    private fun b(vararg l: Int) = l.map { Block(it) }

    private class Shot(val px: IntArray, val w: Int, val h: Int, val g: GridDetect.Grid)
    private fun shot(name: String): Shot {
        val im = ImageIO.read(javaClass.classLoader!!.getResourceAsStream(name))
        val px = im.getRGB(0, 0, im.width, im.height, null, 0, im.width)
        return Shot(px, im.width, im.height, GridDetect.detect(px, im.width, im.height, if (im.width >= 1000) 14 else 8)!!)
    }
    private fun run(s: Shot, limits: CluePipeline.Limits = CluePipeline.Limits(),
                    build: (CluePipeline.Lines, Map<Segment.Glyph, Int>) -> Pair<List<List<Block>>, List<List<Block>>>) =
        CluePipeline.run(s.px, s.w, s.h, s.g.left, s.g.top, s.g.right, s.g.bottom, 0, 0, s.g.rows, s.g.cols, limits, build = build)!!

    @Test fun validateDetectsEachProblem() {
        val ok = CluePipeline.validate(listOf(b(1), b(1)), listOf(b(1), b(1)), null, null, 500)
        assertTrue(ok.ok)
        // suma de filas != suma de columnas
        assertTrue(CluePipeline.Problem.SUMA in CluePipeline.validate(listOf(b(2), b(1)), listOf(b(1), b(1)), null, null, 500).problems)
        // no cabe
        val nc = CluePipeline.validate(listOf(b(1, 2), b()), listOf(b(1), b(1), b(1)), null, null, 500)
        assertTrue(CluePipeline.Problem.NO_CABE in nc.problems); assertTrue(0 in nc.badRows)
        // vacía con tinta
        val v = CluePipeline.validate(listOf(b(1), b()), listOf(b(1), b()), null, booleanArrayOf(false, true), 500)
        assertTrue(CluePipeline.Problem.VACIA in v.problems); assertEquals(setOf(1), v.badCols)
        // sumas iguales pero sin solución: filas [2],[0] vs columnas [1,],[1] no; usar 2x2 contradictorio
        val ns = CluePipeline.validate(listOf(b(2), b()), listOf(b(), b(2)), null, null, 500)
        assertTrue(CluePipeline.Problem.SIN_SOLUCION in ns.problems)
    }

    @Test fun combosOrderedByProbability() {
        val alts = listOf(CluePipeline.Alt(0, 8, 0.01f), CluePipeline.Alt(1, 3, 0.02f), CluePipeline.Alt(0, 9, 0.5f))
        val c = CluePipeline.combos(alts, 100)
        assertEquals(listOf(0), c[0].map { it.glyph })
        assertTrue(c.zipWithNext().all { (a, b) -> a.sumOf { it.cost.toDouble() } <= b.sumOf { it.cost.toDouble() } + 0.011 })
        assertTrue(c.none { combo -> combo.map { it.glyph }.toSet().size != combo.size })
        assertTrue(CluePipeline.combos(alts, 2).size == 2)
    }

    @Test fun cleanReadPassesFirstTry() {
        val s = shot("juego.jpg")
        val o = run(s) { l, d -> CluePipeline.bw(l, d, 20, 20) }
        assertTrue(o.ok); assertEquals(1, o.attempts); assertEquals(0, o.combos)
    }

    /** Un 8 mal leído como 6 (fila 11 "8 2"): la búsqueda de combinaciones lo corrige. */
    @Test fun misreadDigitRepairedByCombos() {
        val s = shot("juego.jpg")
        val o = run(s) { l, d ->
            val t = l.rowGroups[10][0].glyphs[0]
            val d2 = HashMap(d); d2[t] = when (d[t]) { 8 -> 6; 6 -> 8; else -> d[t]!! }
            CluePipeline.bw(l, d2, 20, 20)
        }
        assertTrue(o.ok); assertEquals(1, o.attempts); assertTrue(o.combos > 0)
        assertEquals(listOf(8, 2), o.rows[10].map { it.len })
    }

    /** La primera variante es irreparable (fila vacía): se reintenta con otra variante. */
    @Test fun badVariantRetriesWithAnother() {
        val s = shot("juego2.jpg")
        var first: CluePipeline.Lines? = null
        val o = run(s, CluePipeline.Limits(maxCombos = 20)) { l, d ->
            if (first == null) first = l
            val (r, c) = CluePipeline.bw(l, d, 20, 20)
            if (l === first) (r.mapIndexed { i, x -> if (i == 5) emptyList() else x }) to c else r to c
        }
        assertTrue(o.ok); assertTrue(o.attempts >= 2)
        assertNotSame(first, o.lines)
    }

    /** Nunca válido: termina dentro del tope, no da por buenas las pistas y marca líneas dudosas. */
    @Test fun givesUpWithinLimitsAndMarksDoubtful() {
        val s = shot("juego2.jpg")
        val t0 = System.currentTimeMillis()
        val o = run(s, CluePipeline.Limits(maxVariants = 3, maxCombos = 30, timeMs = 4000)) { l, d ->
            val (r, c) = CluePipeline.bw(l, d, 20, 20)
            r.mapIndexed { i, x -> if (i == 2) x + Block(19) else x } to c
        }
        assertFalse(o.ok); assertTrue(o.attempts <= 3)
        assertTrue(System.currentTimeMillis() - t0 < 8000)
        assertTrue(2 in o.doubtRows)
    }

    @Test fun timeCapStopsRetries() {
        val s = shot("juego2.jpg")
        var clock = 0L
        val o = CluePipeline.run(s.px, s.w, s.h, s.g.left, s.g.top, s.g.right, s.g.bottom, 0, 0, 20, 20,
            CluePipeline.Limits(timeMs = 100), build = { l, d -> CluePipeline.bw(l, d, 20, 20).let { (r, c) -> (r + listOf()) .mapIndexed { i, x -> if (i == 0) x + Block(30) else x } to c } },
            now = { clock += 60; clock })!!
        assertFalse(o.ok); assertTrue("intentos ${o.attempts}", o.attempts <= 2)
    }

    @Test fun lonelyZeroLineIsValidEmpty() {
        val s = shot("juego.jpg")
        val lines = CluePipeline.segment(s.px, s.w, s.h, s.g.left, s.g.top, s.g.right, s.g.bottom, 0, 0, 20, 20, CluePipeline.VARIANTS[0])
        val gl = lines.colGroups[0].first().glyphs.first()
        val d = HashMap<Segment.Glyph, Int>(); d[gl] = 0
        assertFalse(CluePipeline.inkRows(listOf(listOf(Segment.Group(listOf(gl), 0))), d)[0])
        d[gl] = 4
        assertTrue(CluePipeline.inkRows(listOf(listOf(Segment.Group(listOf(gl), 0))), d)[0])
    }

    @Test fun autoPlanTapsOnlyVerifiedSolution() {
        val rows = listOf(b(1), b(2)); val cols = listOf(b(2), b(1))
        val sol = intArrayOf(1, 0, 1, 1)
        val t = AutoPlan.taps(rows, cols, sol, 2, 2, 100, 200, 300, 400)
        assertEquals(3, t.size)
        assertEquals(150f, t[0].x); assertEquals(250f, t[0].y)
        // zigzag: fila 2 de derecha a izquierda
        assertEquals(listOf(1 to 1, 1 to 0), t.drop(1).map { it.row to it.col })
        // solución que no cumple las pistas o sin solución: no se toca nada
        assertTrue(AutoPlan.taps(rows, cols, intArrayOf(1, 1, 0, 0), 2, 2, 100, 200, 300, 400).isEmpty())
        assertTrue(AutoPlan.taps(rows, cols, null, 2, 2, 100, 200, 300, 400).isEmpty())
    }
}
