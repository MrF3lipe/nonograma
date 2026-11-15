package com.felipe.nonograma

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class PuzzleFile(
    val name: String,
    val title: String,
    val rows: List<List<Block>>,
    val cols: List<List<Block>>,
    val goal: IntArray? = null,
) {
    val height get() = rows.size
    val width get() = cols.size
}

object PuzzleParser {
    fun parse(file: File): PuzzleFile = parse(file.readText(), file.name)

    fun parse(text: String, name: String = "puzzle"): PuzzleFile {
        val t = text.trimStart()
        return if (t.startsWith("<?xml") || t.startsWith("<!DOCTYPE") || t.startsWith("<puzzleset") || t.startsWith("<puzzle"))
            parseXml(t, name) else parseNon(t, name)
    }

    fun parseClueLine(s: String, color: Int = 1): List<Block> {
        val t = s.trim()
        if (t.isEmpty() || t == "0") return emptyList()
        return t.split(',', ' ', '\t').filter { it.isNotBlank() }.map { tok ->
            val n = tok.trim().toInt()
            if (n <= 0) null else Block(n, color)
        }.filterNotNull()
    }

    fun parseNon(text: String, name: String): PuzzleFile {
        var width = 0; var height = 0; var title = name
        val rows = ArrayList<List<Block>>(); val cols = ArrayList<List<Block>>()
        var goal: IntArray? = null
        var section: String? = null
        for (raw in text.lineSequence()) {
            val s = raw.trim()
            if (s.startsWith("goal")) {
                section = null
                val q = s.substringAfter("goal").trim().trim('"')
                if (width > 0 && height > 0) goal = parseGoalBits(q, width, height)
                continue
            }
            if (s.isEmpty()) continue
            when {
                s.startsWith("width") -> width = s.substringAfter("width").trim().toInt()
                s.startsWith("height") -> height = s.substringAfter("height").trim().toInt()
                s.startsWith("title") -> title = unquote(s.substringAfter("title"))
                s == "rows" -> section = "rows"
                s == "columns" || s == "cols" -> section = "cols"
                section == "rows" -> rows.add(parseClueLine(s))
                section == "cols" -> cols.add(parseClueLine(s))
            }
        }
        if (width > 0 && cols.size != width) error("$name: expected $width columns, got ${cols.size}")
        if (height > 0 && rows.size != height) error("$name: expected $height rows, got ${rows.size}")
        return PuzzleFile(name, title, rows, cols, goal)
    }

    private fun unquote(s: String) = s.trim().trim('"').trim('\'')

    private fun parseGoalBits(bits: String, w: Int, h: Int): IntArray? {
        val clean = bits.filter { it == '0' || it == '1' }
        if (clean.length != w * h) return null
        return IntArray(w * h) { clean[it].code - '0'.code }
    }

    fun parseXml(text: String, name: String): PuzzleFile {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isValidating = false
            isNamespaceAware = false
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val doc = factory.newDocumentBuilder().parse(text.byteInputStream())
        val puzzles = doc.getElementsByTagName("puzzle")
        val puzzle = if (puzzles.length > 0) puzzles.item(0) as Element else doc.documentElement
        val title = puzzle.getElementsByTagName("title").item(0)?.textContent?.trim() ?: name
        val defaultColorName = puzzle.getAttribute("defaultcolor").ifBlank { "black" }

        val colorId = HashMap<String, Int>()
        val charId = HashMap<Char, Int>()
        var next = 1
        fun isBlank(name: String, ch: String, hex: String): Boolean {
            val n = name.lowercase(); val h = hex.trim().removePrefix("#").uppercase()
            return n == "white" || n == "blank" || n == "empty" || n == "background" ||
                ch == "." || h == "FFFFFF" || h == "FFFFFFFF"
        }
        val colors = puzzle.getElementsByTagName("color")
        for (i in 0 until colors.length) {
            val el = colors.item(i) as Element
            val cname = el.getAttribute("name")
            val ch = el.getAttribute("char")
            val hex = el.textContent ?: ""
            val id = if (isBlank(cname, ch, hex)) 0 else next++
            if (cname.isNotBlank()) colorId[cname] = id
            if (ch.isNotEmpty()) charId[ch[0]] = id
        }
        colorId.putIfAbsent("black", colorId["black"] ?: 1)
        val defId = colorId[defaultColorName] ?: 1

        var rows = emptyList<List<Block>>(); var cols = emptyList<List<Block>>()
        val clueBlocks = puzzle.getElementsByTagName("clues")
        for (i in 0 until clueBlocks.length) {
            val block = clueBlocks.item(i) as Element
            val lines = block.getElementsByTagName("line")
            val parsed = (0 until lines.length).map { li ->
                val line = lines.item(li) as Element
                val counts = line.getElementsByTagName("count")
                (0 until counts.length).mapNotNull { ci ->
                    val c = counts.item(ci) as Element
                    val n = c.textContent.trim().toIntOrNull() ?: return@mapNotNull null
                    if (n <= 0) return@mapNotNull null
                    val cn = c.getAttribute("color")
                    val col = if (cn.isNullOrBlank()) defId else (colorId[cn] ?: defId)
                    if (col == 0) null else Block(n, col)
                }
            }
            when (block.getAttribute("type")) {
                "rows" -> rows = parsed
                "columns", "cols" -> cols = parsed
            }
        }

        var goal: IntArray? = null
        val solutions = puzzle.getElementsByTagName("image")
        if (solutions.length > 0 && cols.isNotEmpty() && rows.isNotEmpty()) {
            val img = solutions.item(0).textContent
            val gridLines = img.lineSequence().map { it.trim().trim('|') }.filter { it.isNotEmpty() }.toList()
            if (gridLines.size == rows.size && gridLines.all { it.length == cols.size }) {
                val g = IntArray(rows.size * cols.size)
                var ok = true
                for (r in gridLines.indices) for (c in gridLines[r].indices) {
                    val ch = gridLines[r][c]
                    val id = charId[ch]
                    if (id == null && ch != '.' && ch != ' ') { ok = false; break }
                    g[r * cols.size + c] = id ?: 0
                }
                if (ok) goal = g
            }
        }
        if (rows.isEmpty() || cols.isEmpty()) error("$name: XML without row/column clues")
        return PuzzleFile(name, title, rows, cols, goal)
    }
}
