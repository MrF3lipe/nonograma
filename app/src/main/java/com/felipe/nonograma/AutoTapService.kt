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

/** Servicio de accesibilidad que toca, una a una y de forma visible, las casillas de la solución. */
class AutoTapService : AccessibilityService() {
    companion object {
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

    /** Toca los puntos en orden con [intervalMs] entre toques. onStep(i) tras cada toque; onEnd(completo). */
    fun play(taps: List<AutoPlan.Tap>, intervalMs: Long, onStep: (Int) -> Unit, onEnd: (Boolean) -> Unit) {
        stop()
        if (taps.isEmpty()) { onEnd(false); return }
        running = true
        fun next(i: Int) {
            if (!running) { onEnd(false); return }
            if (i >= taps.size) { running = false; onEnd(true); return }
            val t = taps[i]
            val path = Path().apply { moveTo(t.x, t.y); lineTo(t.x + 0.5f, t.y + 0.5f) }
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 40)).build()
            val ok = dispatchGesture(g, object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { onStep(i); main.postDelayed({ next(i + 1) }, intervalMs) }
                override fun onCancelled(d: GestureDescription?) { running = false; onEnd(false) }
            }, main)
            if (!ok) { running = false; onEnd(false) }
        }
        next(0)
    }
}
