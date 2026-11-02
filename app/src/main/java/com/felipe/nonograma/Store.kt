package com.felipe.nonograma

import android.content.Context
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject

/** Estado persistente: geometría, tamaño de rejilla, pistas, paleta y solución. */
object Store {
    var rows = 15
    var cols = 15
    var colorMode = false
    /** 0 = auto, 1 = color, 2 = blanco y negro */
    var colorPref = 0
    var framesManual = false
    var autoSize = true
    var locked = false
    val palette = mutableListOf(0xFF000000.toInt())
    var rowClues: MutableList<MutableList<Block>> = MutableList(15) { mutableListOf() }
    var colClues: MutableList<MutableList<Block>> = MutableList(15) { mutableListOf() }
    var solution: IntArray? = null
    /** Líneas que la lectura no pudo validar (se marcan en el editor). */
    val doubtRows = HashSet<Int>(); val doubtCols = HashSet<Int>()
    /** Rejilla detectada en coordenadas reales de pantalla (para el autoresolver) y ms entre toques. */
    var gridScreen = Rect()
    var tapMs = 150
    /** Velocidad del autoresolver: 1x, 2x o 3x sobre [tapMs]. */
    var speed = 1
    fun intervalMs(): Long = AutoPlan.interval(tapMs, speed)
    fun strokeMs(): Long = AutoPlan.stroke(speed)
    var solH = 0; var solW = 0
    var grid = Rect(); var rowFrame = Rect(); var colFrame = Rect()
    var bubbleX = 0; var bubbleY = 300
    private var loaded = false

    fun resize(r: Int, c: Int) {
        rows = r.coerceIn(1, 99); cols = c.coerceIn(1, 99)
        while (rowClues.size < rows) rowClues.add(mutableListOf())
        while (rowClues.size > rows) rowClues.removeAt(rowClues.size - 1)
        while (colClues.size < cols) colClues.add(mutableListOf())
        while (colClues.size > cols) colClues.removeAt(colClues.size - 1)
    }

    fun displayColor(idx: Int): Int =
        if (!effectiveColor()) 0xFF101010.toInt() else palette.getOrElse(idx - 1) { 0xFF101010.toInt() }

    private fun enc(l: List<List<Block>>) = JSONArray(l.map { line -> JSONArray(line.map { "${it.len}:${it.color}" }) })
    private fun dec(a: JSONArray?): MutableList<MutableList<Block>> {
        val out = mutableListOf<MutableList<Block>>()
        if (a == null) return out
        for (i in 0 until a.length()) {
            val la = a.getJSONArray(i)
            out.add(MutableList(la.length()) { val p = la.getString(it).split(":"); Block(p[0].toInt(), p[1].toInt()) })
        }
        return out
    }
    private fun r2j(r: Rect) = JSONArray(listOf(r.left, r.top, r.right, r.bottom))
    private fun j2r(a: JSONArray?, d: Rect) = if (a == null) d else Rect(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3))

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val dm = ctx.resources.displayMetrics
        val side = (minOf(dm.widthPixels, dm.heightPixels) * 0.7).toInt()
        val l = dm.widthPixels - side - dm.widthPixels / 40
        val t = dm.heightPixels / 3
        grid = Rect(l, t, l + side, t + side)
        rowFrame = Rect(0, t, l - 2, t + side)
        colFrame = Rect(l, maxOf(0, t - side / 2), l + side, t - 2)
        val s = ctx.getSharedPreferences("n", Context.MODE_PRIVATE).getString("s", null) ?: return
        try {
            val o = JSONObject(s)
            rows = o.optInt("rows", 15); cols = o.optInt("cols", 15)
            colorMode = o.optBoolean("color"); autoSize = o.optBoolean("auto", true); locked = o.optBoolean("locked"); colorPref = o.optInt("cp", 0); framesManual = o.optBoolean("fm")
            o.optJSONArray("pal")?.let { a -> palette.clear(); for (i in 0 until a.length()) palette.add(a.getInt(i)) }
            if (palette.isEmpty()) palette.add(0xFF000000.toInt())
            rowClues = dec(o.optJSONArray("rc")); colClues = dec(o.optJSONArray("cc"))
            grid = j2r(o.optJSONArray("g"), grid); rowFrame = j2r(o.optJSONArray("rf"), rowFrame); colFrame = j2r(o.optJSONArray("cf"), colFrame)
            gridScreen = j2r(o.optJSONArray("gs"), gridScreen); tapMs = o.optInt("tms", 150); speed = o.optInt("spd", 1).coerceIn(1, 3)
            bubbleX = o.optInt("bx", 0); bubbleY = o.optInt("by", 300)
            solH = o.optInt("sh"); solW = o.optInt("sw")
            o.optJSONArray("sol")?.let { a -> solution = IntArray(a.length()) { a.getInt(it) } }
            if (solution != null && solution!!.size != solH * solW) solution = null
        } catch (_: Exception) {}
        resize(rows, cols)
    }

    fun save(ctx: Context) {
        val o = JSONObject()
        o.put("rows", rows); o.put("cols", cols); o.put("color", colorMode); o.put("auto", autoSize); o.put("locked", locked); o.put("cp", colorPref); o.put("fm", framesManual)
        o.put("pal", JSONArray(palette)); o.put("rc", enc(rowClues)); o.put("cc", enc(colClues))
        o.put("g", r2j(grid)); o.put("rf", r2j(rowFrame)); o.put("cf", r2j(colFrame))
        o.put("gs", r2j(gridScreen)); o.put("tms", tapMs); o.put("spd", speed)
        o.put("bx", bubbleX); o.put("by", bubbleY); o.put("sh", solH); o.put("sw", solW)
        solution?.let { o.put("sol", JSONArray(it.toList())) }
        ctx.getSharedPreferences("n", Context.MODE_PRIVATE).edit().putString("s", o.toString()).apply()
    }

    /** Resuelve con las pistas actuales. Devuelve el estado. */
    fun effectiveColor() = when (colorPref) { 1 -> true; 2 -> false; else -> colorMode }

    fun solveNow(timeoutMs: Long = 8000): Solver.Status {
        val rc = rowClues.map { l -> l.map { if (effectiveColor()) it else Block(it.len, 1) } }
        val cc = colClues.map { l -> l.map { if (effectiveColor()) it else Block(it.len, 1) } }
        val r = Solver.solve(rc, cc, timeoutMs)
        if (r.status == Solver.Status.SOLVED) { solution = r.grid; solH = rows; solW = cols } else solution = null
        return r.status
    }
}
