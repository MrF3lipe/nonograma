package com.felipe.nonograma

import java.io.ByteArrayOutputStream

/** Descompresor bzip2 mínimo (para parches BSDIFF40). Sin dependencias. */
object Bzip2 {
    private class Bits(val b: ByteArray, var pos: Int) {
        var buf = 0L; var n = 0
        fun bits(k: Int): Int {
            while (n < k) {
                if (pos >= b.size) throw IllegalStateException("bzip2: datos cortos")
                buf = (buf shl 8) or (b[pos++].toLong() and 0xFF); n += 8
            }
            n -= k
            return ((buf ushr n) and ((1L shl k) - 1)).toInt()
        }
        fun bit() = bits(1)
    }

    fun decompress(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(data.size * 4)
        var off = 0
        while (off < data.size) {
            if (data.size - off < 4 || data[off] != 'B'.code.toByte() || data[off + 1] != 'Z'.code.toByte() || data[off + 2] != 'h'.code.toByte())
                throw IllegalStateException("bzip2: cabecera")
            val level = data[off + 3] - '0'.code.toByte()
            if (level !in 1..9) throw IllegalStateException("bzip2: nivel")
            val br = Bits(data, off + 4)
            while (true) {
                val m1 = br.bits(24); val m2 = br.bits(24)
                if (m1 == 0x177245 && m2 == 0x385090) { br.bits(32); break }
                if (m1 != 0x314159 || m2 != 0x265359) throw IllegalStateException("bzip2: bloque")
                br.bits(32) // crc del bloque
                decodeBlock(br, level * 100000, out)
            }
            off = br.pos // el siguiente stream empieza en el siguiente byte
        }
        return out.toByteArray()
    }

    private fun decodeBlock(br: Bits, maxLen: Int, out: ByteArrayOutputStream) {
        if (br.bit() != 0) throw IllegalStateException("bzip2: aleatorizado")
        val origPtr = br.bits(24)
        val used = br.bits(16)
        val seqToUnseq = IntArray(256); var nInUse = 0
        for (i in 0 until 16) if (used and (0x8000 ushr i) != 0) {
            val m = br.bits(16)
            for (j in 0 until 16) if (m and (0x8000 ushr j) != 0) seqToUnseq[nInUse++] = i * 16 + j
        }
        if (nInUse == 0) throw IllegalStateException("bzip2: sin símbolos")
        val alpha = nInUse + 2
        val nGroups = br.bits(3)
        if (nGroups !in 2..6) throw IllegalStateException("bzip2: grupos")
        val nSel = br.bits(15)
        if (nSel < 1) throw IllegalStateException("bzip2: selectores")
        val mtfG = IntArray(nGroups) { it }
        val selectors = IntArray(nSel)
        for (i in 0 until nSel) {
            var j = 0
            while (br.bit() == 1) { j++; if (j >= nGroups) throw IllegalStateException("bzip2: selector") }
            val v = mtfG[j]; for (k in j downTo 1) mtfG[k] = mtfG[k - 1]; mtfG[0] = v
            selectors[i] = v
        }
        // tablas Huffman canónicas
        val lens = Array(nGroups) { IntArray(alpha) }
        for (g in 0 until nGroups) {
            var l = br.bits(5)
            for (s in 0 until alpha) {
                while (true) {
                    if (l < 1 || l > 20) throw IllegalStateException("bzip2: longitud")
                    if (br.bit() == 0) break
                    l += if (br.bit() == 0) 1 else -1
                }
                lens[g][s] = l
            }
        }
        val first = Array(nGroups) { IntArray(22) }; val cnt = Array(nGroups) { IntArray(22) }
        val idx0 = Array(nGroups) { IntArray(22) }; val perm = Array(nGroups) { IntArray(alpha) }
        for (g in 0 until nGroups) {
            var code = 0; var pp = 0
            for (len in 1..20) {
                first[g][len] = code; idx0[g][len] = pp
                for (s in 0 until alpha) if (lens[g][s] == len) { perm[g][pp++] = s; code++; cnt[g][len]++ }
                code = code shl 1
            }
        }
        val mtf = IntArray(256) { it }
        val ll = ByteArray(maxLen); var n = 0
        val eob = nInUse + 1
        var sel = 0; var left = 0; var g = 0
        var runAcc = 0; var runPos = 1; var inRun = false
        fun flushRun() {
            if (!inRun) return
            val b = seqToUnseq[mtf[0]].toByte()
            if (n + runAcc > maxLen) throw IllegalStateException("bzip2: bloque largo")
            for (k in 0 until runAcc) ll[n++] = b
            runAcc = 0; runPos = 1; inRun = false
        }
        while (true) {
            if (left == 0) { if (sel >= nSel) throw IllegalStateException("bzip2: selectores agotados"); g = selectors[sel++]; left = 50 }
            left--
            var code = 0; var sym = -1
            for (len in 1..20) {
                code = (code shl 1) or br.bit()
                val d = code - first[g][len]
                if (d >= 0 && d < cnt[g][len]) { sym = perm[g][idx0[g][len] + d]; break }
            }
            if (sym < 0) throw IllegalStateException("bzip2: código")
            if (sym <= 1) {
                runAcc += if (sym == 0) runPos else 2 * runPos
                runPos = runPos shl 1; inRun = true
                continue
            }
            flushRun()
            if (sym == eob) break
            val j = sym - 1
            val v = mtf[j]; for (k in j downTo 1) mtf[k] = mtf[k - 1]; mtf[0] = v
            if (n >= maxLen) throw IllegalStateException("bzip2: bloque largo")
            ll[n++] = seqToUnseq[v].toByte()
        }
        if (origPtr >= n) throw IllegalStateException("bzip2: origPtr")
        // BWT inversa
        val c = IntArray(256)
        for (i in 0 until n) c[ll[i].toInt() and 0xFF]++
        var sum = 0
        for (i in 0 until 256) { val t = c[i]; c[i] = sum; sum += t }
        val tt = IntArray(n)
        for (i in 0 until n) tt[c[ll[i].toInt() and 0xFF]++] = i
        // deshacer RLE inicial (4 iguales + contador)
        var p = tt[origPtr]
        var last = -1; var same = 0
        for (k in 0 until n) {
            val b = ll[p].toInt() and 0xFF
            p = tt[p]
            if (same == 4) {
                for (r in 0 until b) out.write(last)
                same = 0; last = -1
                continue
            }
            if (b == last) same++ else { last = b; same = 1 }
            out.write(b)
        }
    }
}

/** bspatch para BSDIFF40 (bsdiff4). */
object BsPatch {
    private fun offt(b: ByteArray, o: Int): Long {
        var y = (b[o + 7].toLong() and 0x7F)
        for (i in 6 downTo 0) y = y * 256 + (b[o + i].toLong() and 0xFF)
        return if (b[o + 7].toInt() and 0x80 != 0) -y else y
    }

    fun apply(old: ByteArray, patch: ByteArray): ByteArray {
        if (patch.size < 32 || String(patch, 0, 8, Charsets.US_ASCII) != "BSDIFF40") throw IllegalStateException("bspatch: cabecera")
        val cLen = offt(patch, 8).toInt(); val dLen = offt(patch, 16).toInt(); val newSize = offt(patch, 24).toInt()
        if (cLen < 0 || dLen < 0 || newSize < 0 || 32L + cLen + dLen > patch.size) throw IllegalStateException("bspatch: tamaños")
        val ctrl = Bzip2.decompress(patch.copyOfRange(32, 32 + cLen))
        val diff = Bzip2.decompress(patch.copyOfRange(32 + cLen, 32 + cLen + dLen))
        val extra = Bzip2.decompress(patch.copyOfRange(32 + cLen + dLen, patch.size))
        val out = ByteArray(newSize)
        var np = 0; var op = 0L; var c = 0; var d = 0; var e = 0
        while (np < newSize) {
            if (c + 24 > ctrl.size) throw IllegalStateException("bspatch: control")
            val add = offt(ctrl, c).toInt(); val copy = offt(ctrl, c + 8).toInt(); val seek = offt(ctrl, c + 16)
            c += 24
            if (add < 0 || copy < 0 || np + add > newSize || d + add > diff.size) throw IllegalStateException("bspatch: diff")
            for (i in 0 until add) {
                val o = op + i
                val ob = if (o >= 0 && o < old.size) old[o.toInt()].toInt() else 0
                out[np + i] = (diff[d + i] + ob).toByte()
            }
            np += add; d += add; op += add
            if (np + copy > newSize || e + copy > extra.size) throw IllegalStateException("bspatch: extra")
            System.arraycopy(extra, e, out, np, copy)
            np += copy; e += copy
            op += seek
        }
        return out
    }
}
