package com.felipe.nonograma

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class SnapTest {
    private val B = Snap::Box

    @Test fun framesSnapToGridEdges() {
        val g = B(95, 311, 573, 791)
        // marcos colocados a mano, unos píxeles desviados
        val (rf, cf) = Snap.frames(g, B(4, 305, 101, 786), B(90, 40, 570, 318))
        assertEquals(B(4, 311, 94, 791), rf); assertEquals(B(95, 40, 573, 310), cf)
        // F metido dentro de la rejilla / C por debajo del borde: se abren hasta el borde de la pantalla
        val (rf2, cf2) = Snap.frames(g, B(120, 300, 200, 700), B(100, 400, 500, 500))
        assertEquals(0, rf2.l); assertEquals(0, cf2.t)
    }

    @Test fun nearOnlyForTheIndicatedGrid() {
        val det = B(95, 311, 573, 791)
        assertTrue(Snap.near(det, B(100, 320, 560, 780), B(0, 0, 1, 1), B(0, 0, 1, 1)))
        assertTrue(Snap.near(det, B(0, 0, 10, 10), B(0, 315, 90, 785), B(0, 0, 1, 1)))
        assertFalse(Snap.near(det, B(0, 900, 200, 1200), B(0, 900, 50, 1200), B(0, 850, 200, 890)))
    }

    /** tg-1557: rejilla detectada + marcos desviados → acople exacto y lectura correcta con los marcos acoplados. */
    @Test fun snappedFramesRead1557() {
        val f = File("/home/box/telegram-relay/media/tg-1557.jpg"); if (!f.exists()) return
        val im = ImageIO.read(f); val w = im.width; val h = im.height; val px = im.getRGB(0, 0, w, h, null, 0, w)
        val d = GridDetect.detect(px, w, h, 8)!!
        val g = B(d.left, d.top, d.right, d.bottom)
        val (rf, cf) = Snap.frames(g, B(10, d.top - 9, d.left + 12, d.bottom - 7), B(d.left + 8, 45, d.right - 6, d.top + 10))
        val o = CluePipeline.run(px, w, h, g.l, g.t, g.r, g.b, rf.l, cf.t, d.rows, d.cols)!!
        assertTrue(o.ok)
        assertEquals("6 9", o.rows[19].joinToString(" ") { it.len.toString() })
    }
}

class SpeedTest {
    @Test fun speedsAreFaster() {
        val t1 = AutoPlan.interval(150, 1f) + AutoPlan.stroke(1f)
        val t2 = AutoPlan.interval(150, 2f) + AutoPlan.stroke(2f)
        val t3 = AutoPlan.interval(150, 3f) + AutoPlan.stroke(3f)
        assertTrue(t1 / t2.toFloat() >= 1.9f); assertTrue(t1 / t3.toFloat() >= 2.8f)
        // cualquier velocidad, no solo 1x/2x/3x
        assertEquals(30L, AutoPlan.interval(150, 5f)); assertEquals(15L, AutoPlan.interval(150, 10f))
        assertEquals(300L, AutoPlan.interval(150, 0.5f)); assertEquals(10L, AutoPlan.stroke(50f))
    }
}
