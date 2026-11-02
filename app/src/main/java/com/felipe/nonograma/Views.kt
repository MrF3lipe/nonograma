package com.felipe.nonograma

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/** Vista movible y redimensionable (arrastrar = mover, esquina inferior derecha = tamaño). */
abstract class MovableView(ctx: Context) : View(ctx) {
    var editable = true
    var onMove: ((dx: Int, dy: Int, resize: Boolean, done: Boolean) -> Unit)? = null
    protected val dp = ctx.resources.displayMetrics.density
    private var sx = 0f; private var sy = 0f; private var resize = false

    protected fun handleRect(): RectF {
        val s = 30 * dp
        return RectF(width - s, height - s, width.toFloat(), height.toFloat())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!editable) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                sx = e.rawX; sy = e.rawY
                val h = handleRect(); h.inset(-12 * dp, -12 * dp)
                resize = h.contains(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                if (dx != 0 || dy != 0) { onMove?.invoke(dx, dy, resize, false); sx += dx; sy += dy }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onMove?.invoke(0, 0, resize, true)
        }
        return true
    }

    protected fun drawHandle(c: Canvas, p: Paint) {
        val h = handleRect()
        val path = Path().apply { moveTo(h.right, h.top); lineTo(h.right, h.bottom); lineTo(h.left, h.bottom); close() }
        c.drawPath(path, p)
    }
}

/** Cuadro de solución: rejilla + celdas rellenas semitransparentes. */
class GridView(ctx: Context) : MovableView(ctx) {
    private val line = Paint().apply { color = 0xCC1565C0.toInt(); strokeWidth = 1f; style = Paint.Style.STROKE }
    private val border = Paint().apply { color = 0xFF1565C0.toInt(); strokeWidth = 3f; style = Paint.Style.STROKE }
    private val fill = Paint().apply { style = Paint.Style.FILL }
    private val handle = Paint().apply { color = 0xCC1565C0.toInt(); style = Paint.Style.FILL }
    private val tint = Paint().apply { color = 0x221565C0; style = Paint.Style.FILL }

    override fun onDraw(c: Canvas) {
        val rows = Store.rows; val cols = Store.cols
        val w = width.toFloat(); val h = height.toFloat()
        val cw = w / cols; val ch = h / rows
        if (editable) c.drawRect(0f, 0f, w, h, tint)
        val sol = Store.solution
        if (sol != null && Store.solH == rows && Store.solW == cols) {
            val inset = maxOf(1f, minOf(cw, ch) * 0.12f)
            for (r in 0 until rows) for (k in 0 until cols) {
                val v = sol[r * cols + k]
                if (v == 0) continue
                fill.color = (Store.displayColor(v) and 0x00FFFFFF) or (0xA0 shl 24)
                c.drawRect(k * cw + inset, r * ch + inset, (k + 1) * cw - inset, (r + 1) * ch - inset, fill)
            }
        }
        for (k in 1 until cols) { line.strokeWidth = if (k % 5 == 0) 2.5f else 1f; c.drawLine(k * cw, 0f, k * cw, h, line) }
        for (r in 1 until rows) { line.strokeWidth = if (r % 5 == 0) 2.5f else 1f; c.drawLine(0f, r * ch, w, r * ch, line) }
        c.drawRect(1.5f, 1.5f, w - 1.5f, h - 1.5f, border)
        if (editable) drawHandle(c, handle)
    }
}

/** Marco de región de pistas (filas o columnas). */
class FrameView(ctx: Context, private val label: String, private val col: Int) : MovableView(ctx) {
    private val p = Paint().apply { color = col; strokeWidth = 3f; style = Paint.Style.STROKE; pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f) }
    private val bg = Paint().apply { color = (col and 0x00FFFFFF) or 0x22000000 }
    private val hp = Paint().apply { color = col; style = Paint.Style.FILL }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; textSize = 16 * resources.displayMetrics.density; isFakeBoldText = true }
    override fun onDraw(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        c.drawRect(1.5f, 1.5f, width - 1.5f, height - 1.5f, p)
        c.drawText(label, 6 * dp, 20 * dp, tp)
        drawHandle(c, hp)
    }
}

/** Burbuja flotante. */
class BubbleView(ctx: Context) : View(ctx) {
    var glyph = "▦"; set(v) { field = v; invalidate() }
    var onTap: (() -> Unit)? = null
    var onDrag: ((dx: Int, dy: Int, done: Boolean) -> Unit)? = null
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE03949AB.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f }
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = 20 * resources.displayMetrics.density }
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var sx = 0f; private var sy = 0f; private var lx = 0f; private var ly = 0f; private var dragging = false

    override fun onDraw(c: Canvas) {
        val r = width / 2f
        c.drawCircle(r, r, r - 2, bg); c.drawCircle(r, r, r - 3, ring)
        val fm = tp.fontMetrics
        c.drawText(glyph, r, r - (fm.ascent + fm.descent) / 2, tp)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; lx = sx; ly = sy; dragging = false }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && (abs(e.rawX - sx) > slop || abs(e.rawY - sy) > slop)) dragging = true
                if (dragging) { onDrag?.invoke((e.rawX - lx).toInt(), (e.rawY - ly).toInt(), false); lx = e.rawX; ly = e.rawY }
            }
            MotionEvent.ACTION_UP -> if (dragging) onDrag?.invoke(0, 0, true) else onTap?.invoke()
        }
        return true
    }
}
