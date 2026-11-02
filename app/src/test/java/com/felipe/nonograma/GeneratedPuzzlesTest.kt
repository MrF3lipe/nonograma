package com.felipe.nonograma

import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Capturas reales (screencap del emulador) de nonogramas generados con aspecto de tg-1557, en varias
 * resoluciones/densidades. Dir: env NONO_GEN (puzzles.json + caps/<WxH>_<id>.png). Sin datos, no hace nada.
 */
class GeneratedPuzzlesTest {
    @Test fun all() {
        val dir = File(System.getenv("NONO_GEN") ?: "/workspace/nonotest")
        val pj = File(dir, "puzzles.json"); val caps = File(dir, "caps")
        if (!pj.exists() || !caps.isDirectory) return
        val arr = JSONArray(pj.readText())
        val truth = (0 until arr.length()).associate { i -> val o = arr.getJSONObject(i)
            fun ll(a: JSONArray) = (0 until a.length()).map { k -> val x = a.getJSONArray(k); (0 until x.length()).joinToString(" ") { x.getInt(it).toString() } }
            o.getString("id") to (ll(o.getJSONArray("rc")) to ll(o.getJSONArray("cc"))) }
        val fails = ArrayList<String>(); var n = 0
        for (f in caps.listFiles()!!.filter { it.name.endsWith(".png") && it.length() > 0 }.sortedBy { it.name }) {
            val id = f.name.substringAfter('_').removeSuffix(".png"); val (rt, ct) = truth[id] ?: continue
            val im = ImageIO.read(f) ?: continue; n++
            val w = im.width; val h = im.height; val px = im.getRGB(0, 0, w, h, null, 0, w)
            val density = when { w >= 1400 -> 3.5f; w >= 1000 -> 2.625f; else -> 1.75f }
            val g = GridDetect.detect(px, w, h, (6 * density).toInt())
            if (g == null || g.rows != rt.size || g.cols != ct.size) { fails.add("${f.name}: rejilla ${g?.rows}x${g?.cols}"); continue }
            val o = CluePipeline.run(px, w, h, g.left, g.top, g.right, g.bottom, 0, 0, g.rows, g.cols)
            val rows = o?.rows?.map { l -> l.joinToString(" ") { it.len.toString() } }
            val cols = o?.cols?.map { l -> l.joinToString(" ") { it.len.toString() } }
            val bad = (rt.indices.filter { rows?.getOrNull(it) != rt[it] }.map { "f${it + 1}:${rows?.getOrNull(it)}≠${rt[it]}" } +
                ct.indices.filter { cols?.getOrNull(it) != ct[it] }.map { "c${it + 1}:${cols?.getOrNull(it)}≠${ct[it]}" })
            println("${f.name}: ok=${o?.ok} intentos=${o?.attempts} ${if (bad.isEmpty()) "EXACTA" else bad.take(6).toString()}")
            if (o?.ok != true || bad.isNotEmpty()) fails.add("${f.name} $bad")
        }
        println("generadas: ${n - fails.size}/$n")
        assertTrue("fallan: $fails", fails.isEmpty())
    }
}
