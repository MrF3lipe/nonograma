package com.felipe.nonograma

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {
    companion object {
        const val ACTION_PROJECTION = "proj"
        const val ACTION_REFRESH = "refresh"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        const val EXTRA_READ = "read"
        var instance: OverlayService? = null
        val hasProjection get() = instance?.projection != null
    }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var capW = 0; private var capH = 0

    private lateinit var bubble: BubbleView
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private lateinit var bar: LinearLayout
    private lateinit var barLp: WindowManager.LayoutParams
    private lateinit var lockBtn: TextView
    private lateinit var grid: GridView
    private lateinit var gridLp: WindowManager.LayoutParams
    private lateinit var rowF: FrameView
    private lateinit var rowLp: WindowManager.LayoutParams
    private lateinit var colF: FrameView
    private lateinit var colLp: WindowManager.LayoutParams
    private var built = false
    private var shown = false
    private var framesShown = false
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Store.load(this)
    }

    override fun onDestroy() {
        instance = null
        stopProjection()
        if (built) listOf(bubble, bar, grid, rowF, colF).forEach { runCatching { wm.removeView(it) } }
        Store.save(this)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val data: Intent? = intent?.let {
            if (Build.VERSION.SDK_INT >= 33) it.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") it.getParcelableExtra(EXTRA_DATA)
        }
        if (intent?.action == "stop") { stopSelf(); return START_NOT_STICKY }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val withProj = intent?.action == ACTION_PROJECTION && data != null && code == Activity.RESULT_OK
        goForeground(withProj || projection != null)
        if (withProj) startProjection(code, data!!)
        if (!built) build()
        when {
            intent?.action == ACTION_REFRESH -> { show(); grid.invalidate() }
            intent?.action == ACTION_PROJECTION && intent.getBooleanExtra(EXTRA_READ, false) && projection != null -> main.postDelayed({ read() }, 400)
        }
        return START_STICKY
    }

    private fun goForeground(proj: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("n", "Nonograma", NotificationManager.IMPORTANCE_MIN))
        val stop = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, "n").setSmallIcon(R.drawable.ic_stat).setContentTitle("Nonograma")
            .setOngoing(true).addAction(Notification.Action.Builder(null, "Cerrar", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 34) {
            val t = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or (if (proj) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0)
            startForeground(1, n, t)
        } else if (Build.VERSION.SDK_INT >= 29 && proj) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(1, n)
    }

    // ---------- Captura ----------
    @SuppressLint("WrongConstant")
    private fun startProjection(code: Int, data: Intent) {
        stopProjection()
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(code, data) ?: return
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { main.post { stopProjection() } }
        }, main)
        val m = realMetrics()
        capW = m.widthPixels; capH = m.heightPixels
        val r = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2)
        vd = p.createVirtualDisplay("nonograma", capW, capH, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, main)
        reader = r; projection = p
    }

    private fun stopProjection() {
        vd?.release(); vd = null
        reader?.close(); reader = null
        val p = projection; projection = null
        runCatching { p?.stop() }
    }

    private fun realMetrics(): DisplayMetrics {
        val m = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            m.setTo(resources.displayMetrics); m.widthPixels = b.width(); m.heightPixels = b.height()
        } else @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(m)
        return m
    }

    private fun grab(): Bitmap? {
        val img = reader?.acquireLatestImage() ?: return null
        try {
            val pl = img.planes[0]
            val ps = pl.pixelStride; val rs = pl.rowStride
            val bw = rs / ps
            val b = Bitmap.createBitmap(bw, img.height, Bitmap.Config.ARGB_8888)
            b.copyPixelsFromBuffer(pl.buffer)
            return if (bw == img.width) b else Bitmap.createBitmap(b, 0, 0, img.width, img.height)
        } finally { img.close() }
    }

    private fun screenRect(v: View): Rect {
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
    }

    fun read() {
        if (busy) return
        if (projection == null) {
            startActivity(Intent(this, ProjectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_READ, true))
            return
        }
        busy = true; bubble.glyph = "…"
        if (!shown) show()
        if (!framesShown) setFrames(true)
        val bl = screenRect(bubble); val ox = bl.left - bubbleLp.x; val oy = bl.top - bubbleLp.y
        fun abs(l: WindowManager.LayoutParams) = Rect(l.x + ox, l.y + oy, l.x + ox + l.width, l.y + oy + l.height)
        val g = abs(gridLp); val rf = abs(rowLp); val cf = abs(colLp)
        val views = listOf(grid, rowF, colF, bar, bubble)
        reader?.acquireLatestImage()?.close()
        views.forEach { it.visibility = View.INVISIBLE }
        var tries = 0
        fun attempt() {
            val bmp = grab()
            if (bmp == null && tries++ < 8) { main.postDelayed({ attempt() }, 120); return }
            views.forEach { it.visibility = View.VISIBLE }
            if (bmp == null) { done("Sin captura"); return }
            Thread {
                val msg = try {
                    val res = ClueReader.read(bmp, g, rf, cf, Store.rows, Store.cols, Store.autoSize)
                    main.post { applyRead(res) }
                    null
                } catch (e: Exception) { "OCR: error" }
                if (msg != null) main.post { done(msg) }
            }.start()
        }
        main.postDelayed({ attempt() }, 300)
    }

    private fun applyRead(r: ClueReader.Read) {
        Store.resize(r.rows, r.cols)
        Store.rowClues = r.rowClues.toMutableList(); Store.colClues = r.colClues.toMutableList()
        Store.colorMode = r.colorDetected
        Store.palette.clear(); Store.palette.addAll(r.palette)
        Store.solution = null
        grid.invalidate()
        Thread {
            val st = Store.solveNow()
            main.post {
                Store.save(this)
                if (st == Solver.Status.SOLVED) { setFrames(false); grid.invalidate(); done(null) }
                else { done(null); openEditor() }
            }
        }.start()
    }

    private fun done(msg: String?) {
        busy = false; updateGlyph()
        msg?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
    }

    fun openEditor() {
        startActivity(Intent(this, EditorActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    fun refresh() { grid.invalidate(); show() }

    // ---------- Ventanas ----------
    private fun lp(x: Int, y: Int, w: Int, h: Int, touchable: Boolean = true) = WindowManager.LayoutParams(
        w, h, x, y, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun build() {
        built = true
        val size = dp(52)
        bubble = BubbleView(this)
        bubbleLp = lp(Store.bubbleX, Store.bubbleY, size, size)
        bubble.onTap = { onBubble() }
        bubble.onDrag = { dx, dy, fin ->
            bubbleLp.x += dx; bubbleLp.y += dy; wm.updateViewLayout(bubble, bubbleLp); placeBar()
            if (fin) { Store.bubbleX = bubbleLp.x; Store.bubbleY = bubbleLp.y; Store.save(this) }
        }

        bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setColor(0xE0202020.toInt()); cornerRadius = dp(10).toFloat() }
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        fun btn(t: String, f: () -> Unit) = TextView(this).apply {
            text = t; setTextColor(0xFFFFFFFF.toInt()); textSize = 14f
            setPadding(dp(9), dp(9), dp(9), dp(9)); setOnClickListener { f() }
        }.also { bar.addView(it) }
        btn("Pistas") { setFrames(!framesShown) }
        btn("Leer") { read() }
        btn("Editar") { openEditor() }
        lockBtn = btn("🔓") { setLocked(!Store.locked) }
        btn("Ocultar") { hide() }
        btn("✕") { stopSelf() }
        barLp = lp(0, 0, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)

        grid = GridView(this)
        val g = Store.grid
        gridLp = lp(g.left, g.top, g.width(), g.height())
        grid.onMove = { dx, dy, resize, fin -> moveGrid(dx, dy, resize, fin) }

        rowF = FrameView(this, "F", 0xFFE65100.toInt())
        rowLp = lp(Store.rowFrame.left, Store.rowFrame.top, Store.rowFrame.width(), Store.rowFrame.height())
        rowF.onMove = { dx, dy, rs, fin -> moveFrame(rowF, rowLp, Store.rowFrame, dx, dy, rs, fin) }
        colF = FrameView(this, "C", 0xFF2E7D32.toInt())
        colLp = lp(Store.colFrame.left, Store.colFrame.top, Store.colFrame.width(), Store.colFrame.height())
        colF.onMove = { dx, dy, rs, fin -> moveFrame(colF, colLp, Store.colFrame, dx, dy, rs, fin) }

        for ((v, l) in listOf(rowF to rowLp, colF to colLp, grid to gridLp, bar to barLp)) { v.visibility = View.GONE; wm.addView(v, l) }
        wm.addView(bubble, bubbleLp)
        placeBar()
        updateGlyph()
    }

    private fun placeBar() {
        val m = realMetrics()
        barLp.x = (bubbleLp.x).coerceIn(0, maxOf(0, m.widthPixels - dp(330)))
        barLp.y = if (bubbleLp.y + bubbleLp.height + dp(56) < m.heightPixels) bubbleLp.y + bubbleLp.height + dp(4) else bubbleLp.y - dp(52)
        if (bar.parent != null) wm.updateViewLayout(bar, barLp)
    }

    private fun onBubble() {
        when {
            !shown -> { show(); if (Store.locked) setLocked(false) }
            Store.locked -> setLocked(false)
            else -> setLocked(true)
        }
    }

    private fun show() {
        shown = true
        grid.visibility = View.VISIBLE
        setLocked(Store.locked)
    }

    private fun hide() {
        shown = false
        grid.visibility = View.GONE; bar.visibility = View.GONE; setFrames(false)
        updateGlyph()
    }

    private fun setLocked(l: Boolean) {
        Store.locked = l
        val f = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        gridLp.flags = if (l) gridLp.flags or f else gridLp.flags and f.inv()
        // Android 12+: los toques solo atraviesan ventanas ajenas con opacidad <= 0.8
        gridLp.alpha = if (l) 0.8f else 1f
        grid.editable = !l
        if (grid.parent != null) wm.updateViewLayout(grid, gridLp)
        grid.invalidate()
        bar.visibility = if (l || !shown) View.GONE else View.VISIBLE
        if (l) setFrames(false)
        lockBtn.text = if (l) "🔒" else "🔓"
        updateGlyph()
        Store.save(this)
    }

    private fun updateGlyph() {
        if (busy) return
        bubble.glyph = when { !shown -> "▦"; Store.locked -> "🔒"; else -> "🔓" }
    }

    private fun setFrames(on: Boolean) {
        framesShown = on
        val v = if (on) View.VISIBLE else View.GONE
        rowF.visibility = v; colF.visibility = v
    }

    private fun minSide() = dp(40)

    private fun moveGrid(dx: Int, dy: Int, resize: Boolean, fin: Boolean) {
        if (resize) {
            gridLp.width = maxOf(minSide(), gridLp.width + dx); gridLp.height = maxOf(minSide(), gridLp.height + dy)
        } else {
            gridLp.x += dx; gridLp.y += dy
            rowLp.x += dx; rowLp.y += dy; colLp.x += dx; colLp.y += dy
        }
        Store.grid.set(gridLp.x, gridLp.y, gridLp.x + gridLp.width, gridLp.y + gridLp.height)
        if (resize) {
            // las regiones de pistas siguen el alto/ancho de la rejilla
            rowLp.y = gridLp.y; rowLp.height = gridLp.height
            colLp.x = gridLp.x; colLp.width = gridLp.width
        }
        Store.rowFrame.set(rowLp.x, rowLp.y, rowLp.x + rowLp.width, rowLp.y + rowLp.height)
        Store.colFrame.set(colLp.x, colLp.y, colLp.x + colLp.width, colLp.y + colLp.height)
        wm.updateViewLayout(grid, gridLp); wm.updateViewLayout(rowF, rowLp); wm.updateViewLayout(colF, colLp)
        if (fin) Store.save(this)
    }

    private fun moveFrame(v: View, l: WindowManager.LayoutParams, r: Rect, dx: Int, dy: Int, resize: Boolean, fin: Boolean) {
        if (resize) { l.width = maxOf(minSide(), l.width + dx); l.height = maxOf(minSide(), l.height + dy) }
        else { l.x += dx; l.y += dy }
        r.set(l.x, l.y, l.x + l.width, l.y + l.height)
        wm.updateViewLayout(v, l)
        if (fin) Store.save(this)
    }
}
