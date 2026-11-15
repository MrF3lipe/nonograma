package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Resuelve todos los fixtures libres del repo. Los puzzles con copyright
 * restringido se corren con scripts/run-puzzles.sh tras download-puzzles.sh.
 */
class WebpbnFixturesTest {
    private fun dir(): File {
        val cands = listOf(
            File("src/test/resources/puzzles"),
            File("app/src/test/resources/puzzles"),
            File("../src/test/resources/puzzles"),
        )
        return cands.first { it.isDirectory }
    }

    @Test(timeout = 60_000) fun allFixturesSolve() {
        val files = dir().walkTopDown().filter { it.isFile && it.extension in setOf("non", "xml") }.sortedBy { it.path }.toList()
        assertTrue("no hay fixtures en ${dir().absolutePath}", files.isNotEmpty())
        val fails = ArrayList<String>()
        for (f in files) {
            val p = PuzzleParser.parse(f)
            val r = Solver.solve(p.rows, p.cols, Solver.timeoutFor(p.height, p.width).coerceAtMost(15_000))
            val ok = r.status == Solver.Status.SOLVED && r.grid != null && Solver.verify(p.rows, p.cols, r.grid)
            val goalOk = if (ok && p.goal != null && !p.goal.contentEquals(r.grid) && f.name.contains("multi")) true
                else ok
            println("${f.name} ${p.width}x${p.height} ${r.status}")
            if (!goalOk) fails.add("${f.name}: ${r.status}")
        }
        assertTrue("fallan: $fails", fails.isEmpty())
    }
}
