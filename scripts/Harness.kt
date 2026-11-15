package com.felipe.nonograma

import java.io.File
import kotlin.system.exitProcess

object Harness {
    data class Row(
        val name: String,
        val size: String,
        val status: Solver.Status,
        val ms: Long,
        val verified: Boolean,
        val matchesGoal: Boolean?,
        val error: String? = null,
    )

    fun runFile(f: File, timeoutMs: Long): Row {
        val p = try { PuzzleParser.parse(f) } catch (e: Exception) {
            return Row(f.name, "?", Solver.Status.INVALID, 0, false, null, "parse: ${e.message}")
        }
        val t0 = System.nanoTime()
        val r = try { Solver.solve(p.rows, p.cols, timeoutMs) } catch (e: Exception) {
            return Row(f.name, "${p.width}x${p.height}", Solver.Status.INVALID, 0, false, null, "crash: ${e.message}")
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        val verified = r.status == Solver.Status.SOLVED && r.grid != null && Solver.verify(p.rows, p.cols, r.grid)
        val matches = if (verified && p.goal != null) p.goal.contentEquals(r.grid) else null
        return Row(f.name, "${p.width}x${p.height}", r.status, ms, verified, matches)
    }

    fun main(args: Array<String>) {
        var timeout = 30_000L
        val dirs = ArrayList<File>()
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--timeout" -> { timeout = args[++i].toLong() }
                else -> dirs.add(File(args[i]))
            }
            i++
        }
        val files = dirs.flatMap { d ->
            if (d.isFile) listOf(d)
            else d.walkTopDown().filter { it.isFile && it.extension.lowercase() in setOf("non", "xml") }.toList()
        }.sortedBy { it.path }
        if (files.isEmpty()) {
            System.err.println("uso: Harness [--timeout ms] <dirs|files>")
            exitProcess(2)
        }
        val rows = ArrayList<Row>()
        for (f in files) {
            val row = runFile(f, timeout)
            rows.add(row)
            val extra = when {
                row.error != null -> "  ${row.error}"
                row.status == Solver.Status.SOLVED && row.verified && row.matchesGoal == false -> "  ok (otra solución, no es la goal)"
                row.status == Solver.Status.SOLVED && !row.verified -> "  VERIFY FAIL"
                else -> ""
            }
            println("${row.name}\t${row.size}\t${row.status}\t${row.ms}ms$extra")
        }
        val solved = rows.count { it.status == Solver.Status.SOLVED && it.verified }
        val timeoutN = rows.count { it.status == Solver.Status.TIMEOUT }
        val nosol = rows.count { it.status == Solver.Status.NO_SOLUTION }
        val invalid = rows.count { it.status == Solver.Status.INVALID || it.error != null || (it.status == Solver.Status.SOLVED && !it.verified) }
        val maxMs = rows.maxOfOrNull { it.ms } ?: 0
        println("---")
        println("total=${rows.size} ok=$solved timeout=$timeoutN no_sol=$nosol fail=$invalid max=${maxMs}ms timeoutMs=$timeout")
        exitProcess(if (solved == rows.size) 0 else 1)
    }
}

fun main(args: Array<String>) = Harness.main(args)
