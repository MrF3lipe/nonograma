package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test

class RecSizeTest {
    @Test fun candidatesAreEncoderFriendly() {
        for ((w, h) in listOf(1080 to 2400, 720 to 1600, 1440 to 3200, 1080 to 2340)) {
            val c = RecSize.candidates(w, h)
            assertTrue(c.isNotEmpty())
            for ((cw, ch) in c) { assertEquals(0, cw % 16); assertEquals(0, ch % 16); assertTrue(cw <= w && ch <= h) }
            // la última opción cabe en 1280 de lado largo (cualquier codificador H.264)
            assertTrue(maxOf(c.last().first, c.last().second) <= 1280)
            // proporción de pantalla conservada (±3%)
            for ((cw, ch) in c) assertEquals(w.toFloat() / h, cw.toFloat() / ch, 0.03f)
        }
        assertEquals(1072 to 2400, RecSize.candidates(1080, 2400).first())
        assertTrue(RecSize.bitrate(1080, 2400) in 2_000_000..12_000_000)
    }
}
