package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * tg-1549 es el puzzle de tg-1557/tg-1536 con la superposición (marcos y asas) aún visible en la captura: es lo
 * que leyó mal la app en tg-1554 (filas 5-7 y 9 con "4"→"2", 18 corrida, 19-20 vacías). Debe detectarse como
 * captura sucia; las capturas limpias del mismo juego no.
 */
class CaptureCheckTest {
    private val media = "/home/box/telegram-relay/media"
    // asas en tg-1549 (576x1280): marco de filas (naranja), cuadro de solución (azul), marco de columnas (verde)
    private val handles = listOf(
        CaptureCheck.Handle(52, 754, 94, 796, 0xE65100),
        CaptureCheck.Handle(534, 754, 576, 796, 0x1565C0),
        CaptureCheck.Handle(534, 268, 576, 310, 0x2E7D32))

    private fun px(name: String): Triple<IntArray, Int, Int>? {
        val f = File(media, name); if (!f.exists()) return null
        val im = ImageIO.read(f); return Triple(im.getRGB(0, 0, im.width, im.height, null, 0, im.width), im.width, im.height)
    }

    @Test fun dirtyCaptureDetected() {
        val (p, w, h) = px("tg-1549.jpg") ?: return
        println("1549: " + handles.map { CaptureCheck.coverage(p, w, h, it) })
        assertTrue(CaptureCheck.overlayVisible(p, w, h, handles))
        assertTrue("asa del marco de filas", CaptureCheck.coverage(p, w, h, handles[0]) >= 0.5f)
    }

    @Test fun cleanCapturesNotFlagged() {
        for (n in listOf("tg-1557.jpg", "tg-1534.jpg", "tg-1535.jpg", "tg-1537.jpg", "tg-1538.jpg", "tg-1539.jpg")) {
            val (p, w, h) = px(n) ?: continue
            println("$n: " + handles.map { CaptureCheck.coverage(p, w, h, it) })
            assertFalse(n, CaptureCheck.overlayVisible(p, w, h, handles))
        }
    }

    /** tg-1536: sin marcos pero con el cuadro de solución (asa azul) aún encima: también es sucia. */
    @Test fun gridOverlayOnly() {
        val (p, w, h) = px("tg-1536.jpg") ?: return
        assertTrue(CaptureCheck.coverage(p, w, h, handles[1]) >= 0.5f)
        assertFalse(CaptureCheck.overlayVisible(p, w, h, listOf(handles[0], handles[2])))
    }

    @Test fun syntheticHandle() {
        val w = 100; val h = 100
        val p = IntArray(w * h) { 0xFF202040.toInt() }
        val m = CaptureCheck.Handle(50, 50, 90, 90, 0xE65100)
        assertFalse(CaptureCheck.overlayVisible(p, w, h, listOf(m)))
        for (y in 50 until 90) for (x in 50 until 90) if ((x - 50) + (y - 50) >= 40) p[y * w + x] = 0xFFE65100.toInt()
        assertTrue(CaptureCheck.overlayVisible(p, w, h, listOf(m)))
        // asa fuera de la pantalla: no cuenta
        assertFalse(CaptureCheck.overlayVisible(p, w, h, listOf(CaptureCheck.Handle(200, 200, 240, 240, 0xE65100))))
    }
}
