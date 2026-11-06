package com.felipe.nonograma

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

/** Servicio de accesibilidad que toca o arrastra, de forma visible, las casillas de la solución. */
class AutoTapService : AccessibilityService() {
    companion object {
        /** Mínimos para que a ×3+ el sistema no cancele ni se salte gestos. */
        const val MIN_INTERVAL_MS = 40L
        const val MIN_TAP_STROKE_MS = 25L

        @Volatile var instance: AutoTapService? = null
        fun enabled(ctx: Context): Boolean {
            val cn = ComponentName(ctx, AutoTapService::class.java).flattenToString()
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return list.split(':').any { it.equals(cn, ignoreCase = true) } && instance != null
        }
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    val isRunning get() = running

    override fun onServiceConnected() { instance = this }
    override fun onDestroy() { stop(); instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stop() }

    fun stop() { running = false; main.removeCallbacksAndMessages(null) }

    /** Toca los puntos en orden. No arranca el siguiente hasta onCompleted + intervalo. */
    fun play(taps: List<AutoPlan.Tap>, intervalMs: Long, strokeMs: Long = 40, onStep: (Int) -> Unit, onEnd: (Boolean) -> Unit) {
        stop()
        if (taps.isEmpty()) { onEnd(false); return }
        running = true
        val wait = intervalMs.coerceAtLeast(MIN_INTERVAL_MS)
        val dur = strokeMs.coerceAtLeast(MIN_TAP_STROKE_MS)
        fun next(i: Int) {
            if (!running) { onEnd(false); return }
            if (i >= taps.size) { running = false; onEnd(true); return }
            val t = taps[i]
            val path = Path().apply { moveTo(t.x, t.y); lineTo(t.x + 0.5f, t.y + 0.5f) }
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, dur)).build()
            val ok = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { onStep(i); main.postDelayed({ next(i + 1) }, wait) }
                override fun onCancelled(d: GestureDescription?) { running = false; onEnd(false) }
            }, main)
            if (!ok) { running = false; onEnd(false) }
        }
        next(0)
    }

    /** Arrastra cada bloque (pista completa). Duración según nº de celdas y velocidad. */
    fun playStrokes(strokes: List<AutoPlan.Stroke>, intervalMs: Long, speed: Float, onStep: (Int) -> Unit, onEnd: (Boolean) -> Unit) {
        stop()
        if (strokes.isEmpty()) { onEnd(false); return }
        running = true
        val wait = intervalMs.coerceAtLeast(MIN_INTERVAL_MS)
        fun next(i: Int) {
            if (!running) { onEnd(false); return }
            if (i >= strokes.size) { running = false; onEnd(true); return }
            val s = strokes[i]
            val cells = abs(s.c1 - s.c0) + 1
            val dur = AutoPlan.strokeFor(cells, speed)
            val path = Path().apply { moveTo(s.x0, s.y0); lineTo(s.x1, s.y1) }
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, dur)).build()
            val ok = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { onStep(i); main.postDelayed({ next(i + 1) }, wait) }
                override fun onCancelled(d: GestureDescription?) { running = false; onEnd(false) }
            }, main)
            if (!ok) { running = false; onEnd(false) }
        }
        next(0)
    }
}
