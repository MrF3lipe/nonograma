package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** Evaluación del reconocedor propio de dígitos sobre las capturas reales (+ aumentos). */
class DigitEvalTest {
    class Img(val px: IntArray, val w: Int, val h: Int)
    class Sample(val f: DigitClassifier.Feat?, val truth: Int, val key: String)

    companion object {
        val FIX = mapOf(
            "juego.jpg" to (listOf("132123", "14223", "142141", "231243", "12241", "1125", "4131", "32", "621", "72", "82", "15411", "26422", "2644", "32342", "2143", "36221", "12514", "21624", "10212") to
                listOf("473", "3342", "21331", "754", "51621", "4272", "2182", "5344", "3138", "1237", "12235", "13331", "2342", "137", "441", "4222", "324", "1124", "3132", "1331")),
            "juego2.jpg" to (listOf("11111", "3112", "4111", "3111", "411", "28", "431", "5221", "663", "6134", "6215", "5236", "516", "236", "716", "4116", "3135", "119", "3110", "315") to
                listOf("43", "366", "3742", "1222", "3911", "1181", "223", "331", "423", "511", "52111", "6235", "6215", "626", "638", "910", "673", "1573", "1373", "31153")))

        fun load(name: String): BufferedImage = ImageIO.read(DigitEvalTest::class.java.classLoader!!.getResourceAsStream(name))
        fun toImg(b: BufferedImage) = Img(b.getRGB(0, 0, b.width, b.height, null, 0, b.width), b.width, b.height)
        fun scale(b: BufferedImage, s: Double): BufferedImage {
            val o = BufferedImage((b.width * s).toInt(), (b.height * s).toInt(), BufferedImage.TYPE_INT_RGB)
            o.createGraphics().apply { setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); drawImage(b, 0, 0, o.width, o.height, null) }
            return o
        }
        fun jpeg(b: BufferedImage, q: Float): BufferedImage {
            val bo = ByteArrayOutputStream()
            val wr = ImageIO.getImageWritersByFormatName("jpg").next()
            wr.output = ImageIO.createImageOutputStream(bo)
            wr.write(null, IIOImage(b, null, null), wr.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = q })
            return ImageIO.read(bo.toByteArray().inputStream())
        }

        var WHY = ""
        val DUMP = HashMap<String, Pair<Segment.Ink, Int>>()
        fun glyphFeat(im: Img, g: Segment.Glyph): DigitClassifier.Feat? {
            return Segment.digitFeat(g)
        }

        /** Glifos con su verdad. Devuelve null si la segmentación no cuadra con la verdad. */
        fun samples(im: Img, name: String, minCell: Int): List<Sample>? {
            val (rt, ct) = FIX[name]!!
            val g = GridDetect.detect(im.px, im.w, im.h, minCell) ?: run { WHY = "sin rejilla"; null } ?: return null
            if (g.rows != 20 || g.cols != 20) { WHY = "rejilla ${g.rows}x${g.cols}"; return null }
            val ch = (g.bottom - g.top) / 20f; val cw = (g.right - g.left) / 20f
            val rowGl = (0 until 20).map { r -> Segment.rowGlyphs(im.px, im.w, 0, (g.top + r * ch).toInt(), g.left - 3, (g.top + (r + 1) * ch).toInt()) }
            val dom = Segment.dominant(rowGl.flatten())
            val out = ArrayList<Sample>()
            for (r in 0 until 20) {
                val gs = Segment.stackRow(Segment.groupRow(rowGl[r], dom), g.left, cw).flatMap { it.glyphs }
                if (gs.size != rt[r].length) { WHY = "fila ${r + 1}: ${gs.size} glifos vs ${rt[r].length}"; return null }
                gs.forEachIndexed { i, gl -> out.add(Sample(glyphFeat(im, gl), rt[r][i] - '0', "f${r + 1}.$i")); DUMP["$name f${r + 1}.$i"] = Segment.Ink(gl.aw, gl.ah, 0, 0, gl.alpha!!, IntArray(0)) to (rt[r][i] - '0') }
            }
            for (c in 0 until 20) {
                val gs = Segment.stackCol(Segment.colGroupsRaw(im.px, im.w, (g.left + c * cw).toInt(), 0, (g.left + (c + 1) * cw).toInt(), g.top - 3), g.top, ch).flatMap { it.glyphs }
                if (gs.size != ct[c].length) { WHY = "col ${c + 1}: ${gs.size} glifos vs ${ct[c].length}"; return null }
                gs.forEachIndexed { i, gl -> out.add(Sample(glyphFeat(im, gl), ct[c][i] - '0', "c${c + 1}.$i")) }
            }
            return out
        }

        /** Variantes: original + reducciones + JPEG fuerte. */
        fun variants(name: String): List<Pair<String, Img>> {
            val b = load(name)
            val v = mutableListOf("orig" to b, "jpeg40" to jpeg(b, 0.4f))
            if (b.width >= 1000) { v.add("x0.75" to scale(b, 0.75)); v.add("x0.5" to scale(b, 0.5)); v.add("x0.5+jpeg50" to jpeg(scale(b, 0.5), 0.5f)) }
            else { v.add("x1.5" to scale(b, 1.5)); v.add("x1.875" to scale(b, 1.875)) }
            return v.map { it.first to toImg(it.second) }
        }

        fun minCell(im: Img) = if (im.w >= 1000) 14 else 8

        // ---------- plantillas de fuentes ----------
        val FONTS = listOf("Montserrat", "Roboto", "Open Sans", "Lato", "Poppins", "Nunito", "Inter", "Noto Sans", "Source Sans 3", "DejaVu Sans", "Ubuntu", "Work Sans", "Rubik", "Fira Sans", "PT Sans", "Mulish", "Quicksand", "Raleway")

        private var fontCache: List<DigitClassifier.Template>? = null
        fun fontTemplates(): List<DigitClassifier.Template> = fontCache ?: buildFonts().also { fontCache = it }
        private fun buildFonts(): List<DigitClassifier.Template> {
            val out = ArrayList<DigitClassifier.Template>()
            for (fam in FONTS) for (style in intArrayOf(Font.PLAIN, Font.BOLD)) for (px in intArrayOf(9, 12, 16, 24, 40)) {
                val font = Font(fam, style, 10).deriveFont(px * 1.4f)
                for (d in 0..9) {
                    val im = BufferedImage(px * 3, px * 3, BufferedImage.TYPE_INT_RGB)
                    val gr = im.createGraphics()
                    gr.color = Color(0x26, 0x29, 0x3d); gr.fillRect(0, 0, im.width, im.height)
                    gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    gr.font = font; gr.color = Color(235, 238, 245)
                    gr.drawString(d.toString(), px / 2, (px * 2.2).toInt())
                    val img = toImg(im)
                    val ink = Segment.ink(img.px, img.w, 0, 0, img.w, img.h) ?: continue
                    DigitClassifier.features(ink.a, ink.w, ink.h)?.let { out.add(DigitClassifier.Template(d, it.v)) }
                }
            }
            return out
        }

        fun gameTemplates(name: String): List<DigitClassifier.Template> =
            variants(name).flatMap { (_, im) -> samples(im, name, minCell(im)) ?: emptyList() }
                .mapNotNull { s -> s.f?.let { DigitClassifier.Template(s.truth, it.v) } }
    }

    private fun eval(tpl: List<DigitClassifier.Template>, label: String, only: String? = null): Map<String, Pair<Int, Int>> {
        val res = LinkedHashMap<String, Pair<Int, Int>>()
        for (name in FIX.keys) {
            if (only != null && name != only) continue
            for ((vn, im) in variants(name)) {
                val ss = samples(im, name, minCell(im))
                if (ss == null) { res["$name/$vn"] = 0 to -1; continue }
                val pred = DigitClassifier.classifyAll(ss.map { it.f }, tpl = tpl)
                val ok = ss.indices.count { pred[it] == ss[it].truth }
                val errs = ss.indices.filter { pred[it] != ss[it].truth }.take(8).joinToString(" ") { "${ss[it].key}:${ss[it].truth}→${pred[it]}" }
                res["$name/$vn"] = ok to ss.size
                println("[$label] $name/$vn: $ok/${ss.size} ${if (errs.isNotEmpty()) "errores: $errs" else ""}")
            }
        }
        return res
    }

    @Test fun dump() {
        if (System.getenv("DUMP") != "1") return
        DUMP.clear(); samples(toImg(load("juego.jpg")), "juego.jpg", 14)
        val items = DUMP.entries.sortedBy { it.value.second }
        val cell = 40
        val out = BufferedImage(cell * 20, cell * (items.size / 20 + 1), BufferedImage.TYPE_INT_RGB)
        items.forEachIndexed { k, e -> val ink = e.value.first
            for (y in 0 until minOf(ink.h, cell)) for (x in 0 until minOf(ink.w, cell)) { val v = (ink.a[y * ink.w + x] * 255).toInt(); out.setRGB((k % 20) * cell + x, (k / 20) * cell + y, (v shl 16) or (v shl 8) or v) } }
        ImageIO.write(out, "png", File("/tmp/glyphs.png"))
    }

    @Test fun stats() {
        // distancias intra/inter dígito dentro de cada captura (para el umbral de consistencia)
        for (name in FIX.keys) for ((vn, im) in variants(name)) {
            val ss = samples(im, name, minCell(im)) ?: run { println("$name/$vn: segmentación no cuadra: $WHY"); null } ?: continue
            var intraMax = 0f; var interMin = Float.MAX_VALUE
            for (i in ss.indices) for (j in i + 1 until ss.size) {
                val a = ss[i].f ?: continue; val b = ss[j].f ?: continue
                val d = DigitClassifier.distance(a.v, b.v)
                if (ss[i].truth == ss[j].truth) intraMax = maxOf(intraMax, d) else interMin = minOf(interMin, d)
            }
            val plain = ss.count { it.f != null && DigitClassifier.classify(it.f, fontTemplates()) == it.truth }
            println("$name/$vn: n=${ss.size} intraMax=%.3f interMin=%.3f plain-fuentes=$plain".format(intraMax, interMin))
        }
    }

    @Test fun evaluate() {
        val fonts = fontTemplates()
        println("plantillas de fuentes: ${fonts.size}")
        eval(fonts, "solo fuentes")
        // validación cruzada: fuentes + glifos reales de la OTRA captura
        val g1 = gameTemplates("juego.jpg"); val g2 = gameTemplates("juego2.jpg")
        eval(fonts + g2, "fuentes+juego2", "juego.jpg")
        eval(fonts + g1, "fuentes+juego", "juego2.jpg")
        val all = eval(DigitClassifier.templates(), "incluidas en la app")
        if (DigitClassifier.templates().isNotEmpty()) for ((k, v) in all) assertEquals(k, v.second, v.first)
    }

    /** Genera DigitTemplates.kt (ejecutar con -DgenTemplates=true). */
    @Test fun generate() {
        if (System.getProperty("genTemplates") != "true" && System.getenv("GEN_TEMPLATES") != "1") return
        val tpl = fontTemplates() + gameTemplates("juego.jpg") + gameTemplates("juego2.jpg")
        val bytes = ByteArrayOutputStream()
        for (t in tpl) { bytes.write(t.digit); for (x in t.f) bytes.write((x * 100f).toInt().coerceIn(0, 255)) }
        val b64 = java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())
        val chunks = b64.chunked(30000).joinToString(",\n") { "        \"$it\"" }
        val src = """package com.felipe.nonograma

/** Plantillas de dígitos generadas por DigitEvalTest.generate (fuentes libres + glifos reales). No editar. */
object DigitTemplates {
    private val DATA = arrayOf(
$chunks
    )
    fun load(): List<DigitClassifier.Template> {
        val b = java.util.Base64.getDecoder().decode(DATA.joinToString(""))
        val n = DigitClassifier.FLEN + 1
        return (0 until b.size / n).map { i ->
            val o = i * n
            DigitClassifier.Template(b[o].toInt(), FloatArray(DigitClassifier.FLEN) { (b[o + 1 + it].toInt() and 255) / 100f })
        }
    }
}
"""
        File(System.getProperty("user.dir"), "src/main/java/com/felipe/nonograma/DigitTemplates.kt").writeText(src)
        println("plantillas escritas: ${tpl.size}")
    }
}
