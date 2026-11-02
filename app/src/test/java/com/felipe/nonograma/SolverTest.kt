package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SolverTest {
    private fun mono(v: List<List<Int>>) = v.map { l -> l.map { Block(it, 1) } }

    private fun cluesFrom(grid: Array<IntArray>): Pair<List<List<Block>>, List<List<Block>>> {
        val h = grid.size; val w = grid[0].size
        val rows = (0 until h).map { Solver.runs(grid[it]) }
        val cols = (0 until w).map { c -> Solver.runs(IntArray(h) { grid[it][c] }) }
        return rows to cols
    }

    @Test fun felipe15x15() {
        val filas = listOf(listOf(1,10),listOf(2,6,2),listOf(3,4,4),listOf(4,6),listOf(7,6),listOf(6,2,2,1),listOf(6,6),listOf(4,1,1,2),listOf(1,3,1,1,1),listOf(2,1,1,1),listOf(3,3),listOf(4,7,1),listOf(3,3,1,2),listOf(2,5,1,1),listOf(2,2,3))
        val columnas = listOf(listOf(7,7),listOf(7,6),listOf(7,3),listOf(1,7,1,2),listOf(2,5,3),listOf(3,3,5),listOf(3,1,4),listOf(3,4,2,2),listOf(3,3,1,1),listOf(2,2,4,2,1),listOf(1,4,1,1),listOf(1,6,1),listOf(1,3,3),listOf(4,1),listOf(3,1,3))
        val t0 = System.nanoTime()
        val r = Solver.solve(mono(filas), mono(columnas), 5000)
        val ms = (System.nanoTime() - t0) / 1e6
        assertEquals(Solver.Status.SOLVED, r.status)
        assertTrue(Solver.verify(mono(filas), mono(columnas), r.grid!!))
        val sb = StringBuilder("felipe15x15 resuelto en %.1f ms\n".format(ms))
        for (y in 0 until 15) { for (x in 0 until 15) sb.append(if (r.at(y, x) == 1) '#' else '.'); sb.append('\n') }
        println(sb)
    }

    @Test fun lineSolverBasics() {
        // 10 celdas, pista [8]: las 6 del medio seguras
        val res = Solver.solveLine(IntArray(10) { 3 }, intArrayOf(8), intArrayOf(1))!!
        assertEquals(listOf(3,3,2,2,2,2,2,2,3,3), res.toList())
        // colores distintos adyacentes no necesitan hueco: [2a,3b] en 5 celdas = exacto
        val c = Solver.solveLine(IntArray(5) { 7 }, intArrayOf(2, 3), intArrayOf(1, 2))!!
        assertEquals(listOf(2,2,4,4,4), c.toList())
        // mismo color sí necesita hueco: [2,2] no cabe en 4
        assertNull(Solver.solveLine(IntArray(4) { 3 }, intArrayOf(2, 2), intArrayOf(1, 1)))
    }

    @Test fun colorPuzzle() {
        // 0 vacía, 1 rojo, 2 verde, 3 azul
        val g = arrayOf(
            intArrayOf(0,1,1,0,0,2,2,0),
            intArrayOf(1,1,1,1,2,2,2,2),
            intArrayOf(1,1,3,3,3,3,2,2),
            intArrayOf(0,1,3,0,0,3,2,0),
            intArrayOf(0,0,3,3,3,3,0,0),
            intArrayOf(0,2,2,1,1,2,2,0),
            intArrayOf(2,2,0,1,1,0,2,2),
        )
        val (rows, cols) = cluesFrom(g)
        val r = Solver.solve(rows, cols, 5000)
        assertEquals(Solver.Status.SOLVED, r.status)
        assertTrue(Solver.verify(rows, cols, r.grid!!))
    }

    @Test fun randomColor20x20() {
        val rnd = Random(7)
        val g = Array(20) { IntArray(20) { if (rnd.nextDouble() < 0.4) 0 else 1 + rnd.nextInt(3) } }
        val (rows, cols) = cluesFrom(g)
        val r = Solver.solve(rows, cols, 10_000)
        assertEquals(Solver.Status.SOLVED, r.status)
        assertTrue(Solver.verify(rows, cols, r.grid!!))
    }

    @Test(timeout = 15_000) fun big30x30WithTimeLimit() {
        val rnd = Random(2026)
        // patrón con estructura (círculos + ruido) típico de un nonograma grande
        val g = Array(30) { y -> IntArray(30) { x ->
            val d = (x - 14.5) * (x - 14.5) + (y - 14.5) * (y - 14.5)
            if ((d < 140 && d > 40) || (x + y) % 9 == 0 || rnd.nextDouble() < 0.15) 1 else 0 } }
        val (rows, cols) = cluesFrom(g)
        val t0 = System.nanoTime()
        val r = Solver.solve(rows, cols, 10_000)
        println("30x30 en %.1f ms".format((System.nanoTime() - t0) / 1e6))
        assertEquals(Solver.Status.SOLVED, r.status)
        assertTrue(Solver.verify(rows, cols, r.grid!!))
    }

    @Test(timeout = 20_000) fun random40x40Dense() {
        val rnd = Random(11)
        val g = Array(40) { IntArray(40) { if (rnd.nextDouble() < 0.6) 1 else 0 } }
        val (rows, cols) = cluesFrom(g)
        val r = Solver.solve(rows, cols, 15_000)
        assertEquals(Solver.Status.SOLVED, r.status)
        assertTrue(Solver.verify(rows, cols, r.grid!!))
    }

    @Test fun invalidAndUnsolvable() {
        assertEquals(Solver.Status.INVALID, Solver.solve(mono(listOf(listOf(6))), mono(listOf(listOf(1),listOf(1),listOf(1),listOf(1),listOf(1))), 1000).status)
        // totales coinciden pero no hay solución
        val rows = mono(listOf(listOf(1, 1)))
        val cols = mono(listOf(listOf(1), listOf(1), listOf()))
        assertEquals(Solver.Status.NO_SOLUTION, Solver.solve(rows, cols, 1000).status)
    }

    @Test fun timeoutIsReported() {
        val rnd = Random(3)
        val g = Array(60) { IntArray(60) { if (rnd.nextDouble() < 0.5) 1 else 0 } }
        val (rows, cols) = cluesFrom(g)
        val r = Solver.solve(rows, cols, 1)
        assertTrue(r.status == Solver.Status.TIMEOUT || r.status == Solver.Status.SOLVED)
    }
}
