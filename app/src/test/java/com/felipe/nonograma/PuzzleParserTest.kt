package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PuzzleParserTest {
    private fun dir(): File {
        val cands = listOf(
            File("src/test/resources/puzzles"),
            File("app/src/test/resources/puzzles"),
            File("../src/test/resources/puzzles"),
        )
        return cands.first { it.isDirectory }
    }

    @Test fun nonDancer() {
        val p = PuzzleParser.parse(File(dir(), "webpbn-00001.non"))
        assertEquals(5, p.width); assertEquals(10, p.height)
        assertEquals(listOf(Block(2, 1)), p.rows[0])
        assertEquals(listOf(Block(2, 1), Block(1, 1)), p.rows[1])
        assertEquals(50, p.goal!!.size)
        assertEquals(0, p.goal!![0]); assertEquals(1, p.goal!![1])
    }

    @Test fun emptyClueZero() {
        val p = PuzzleParser.parse(File(dir(), "synthetic/empty-clue.non"))
        assertEquals(3, p.width); assertEquals(2, p.height)
        assertEquals(listOf(Block(3, 1)), p.rows[0])
        assertTrue(p.rows[1].isEmpty())
        val r = Solver.solve(p.rows, p.cols, 1000)
        assertEquals(Solver.Status.SOLVED, r.status)
        assertTrue(Solver.verify(p.rows, p.cols, r.grid!!))
        assertArrayEquals(p.goal, r.grid)
    }

    @Test fun multiSolutionAccepted() {
        val p = PuzzleParser.parse(File(dir(), "synthetic/multi-sol.non"))
        val r = Solver.solve(p.rows, p.cols, 1000)
        assertEquals(Solver.Status.SOLVED, r.status)
        assertTrue(Solver.verify(p.rows, p.cols, r.grid!!))
    }

    @Test fun colorXml() {
        val p = PuzzleParser.parse(File(dir(), "webpbn-color-00047.xml"))
        assertEquals(31, p.width); assertEquals(18, p.height)
        assertTrue(p.rows.flatten().any { it.color > 1 })
        val r = Solver.solve(p.rows, p.cols, 5000)
        assertEquals(Solver.Status.SOLVED, r.status)
        assertTrue(Solver.verify(p.rows, p.cols, r.grid!!))
    }

    @Test fun parseClueLine() {
        assertEquals(emptyList<Block>(), PuzzleParser.parseClueLine("0"))
        assertEquals(emptyList<Block>(), PuzzleParser.parseClueLine(""))
        assertEquals(listOf(Block(2, 1), Block(1, 1)), PuzzleParser.parseClueLine("2,1"))
        assertEquals(listOf(Block(3, 1)), PuzzleParser.parseClueLine("3"))
    }
}
