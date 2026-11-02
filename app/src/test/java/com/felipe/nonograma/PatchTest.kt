package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test

class PatchTest {
    private fun res(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.readBytes()

    @Test fun bzip2MatchesPython() {
        assertArrayEquals(res("mix.bin"), Bzip2.decompress(res("mix.bz2")))
        assertEquals("hola", String(Bzip2.decompress(res("small.bz2"))))
    }

    @Test fun bspatchMatchesBsdiff4() {
        assertArrayEquals(res("new.bin"), BsPatch.apply(res("old.bin"), res("p.bsdiff")))
    }

    @Test fun rejectsGarbage() {
        assertThrows(IllegalStateException::class.java) { BsPatch.apply(ByteArray(10), ByteArray(40)) }
    }

    @Test fun versionCompare() {
        val j = """{"format":1,"app":"com.felipe.nonograma","variants":{"universal":{"version":"1.0.2","versionCode":3,
            "apk":{"url":"https://x/a.apk","sha256":"ab","size":5},"patches":[{"fromVersionCode":2,"fromSha256":"cd","url":"https://x/p","sha256":"ef","size":1}]}}}"""
        val m = Updater.parse(j)!!
        assertEquals(3, m.versionCode); assertEquals("1.0.2", m.version); assertEquals(1, m.patches.size)
        assertEquals(2, m.patches[0].fromVersionCode)
    }
}
