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
        const val PLAY_START_DELAY_MS = 350L
        var instance: OverlayService? = null
        val hasProjection get() = instance?.projection != null
    }

    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var capW = 0; private var capH = 0
    private var capDpi = 0
    private var recorder: ScreenRecorder? = null
    private lateinit var recBtn: TextView

    private lateinit var bubble: BubbleView
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private lateinit var bar: LinearLayout
    private lateinit var barLp: WindowManager.LayoutParams
    private lateinit var lockBtn: TextView
    private lateinit var modeBtn: TextView
    private lateinit var playBtn: TextView
    private var playing = false
    private lateinit var grid: GridView
    private lateinit var gridLp: WindowManager.LayoutParams
    private lateinit var speedBtn: TextView
    private val barButtons = ArrayList<TextView>()
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
        AutoTapService.instance?.stop()
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
        if (withProj) try { startProjection(code, data!!) } catch (e: Throwable) { ErrorLog.write(this, "proyección", e); Toast.makeText(this, ErrorLog.short("proyección", e), Toast.LENGTH_LONG).show() }
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
        capW = m.widthPixels; capH = m.heightPixels; capDpi = m.densityDpi
        val r = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2)
        vd = p.createVirtualDisplay("nonograma", capW, capH, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, main)
        reader = r; projection = p
    }

    // ---------- Grabación (mismo VirtualDisplay: se alterna su superficie) ----------
    private fun toggleRec() {
        val r = recorder
        if (r != null) { stopRec(); return }
        if (projection == null || vd == null) {
            startActivity(Intent(this, ProjectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        }
        val nr = ScreenRecorder(this)
        if (!nr.start(capW, capH)) { Toast.makeText(this, "⏺ ✕", Toast.LENGTH_SHORT).show(); return }
        recorder = nr
        toEncoder()
        recBtn.text = "⏹"; recBtn.setTextColor(0xFFFF5252.toInt())
    }

    private fun stopRec() {
        val r = recorder ?: return
        recorder = null
        toReader()
        val ok = r.stop()
        if (::recBtn.isInitialized) { recBtn.text = "⏺"; recBtn.setTextColor(0xFFFFFFFF.toInt()) }
        Toast.makeText(this, if (ok) "🎬 ✓" else "🎬 ✕", Toast.LENGTH_SHORT).show()
    }

    /** VirtualDisplay → codificador (tamaño del vídeo). */
    private fun toEncoder() {
        val r = recorder ?: return; val v = vd ?: return
        runCatching { v.resize(r.w, r.h, capDpi); v.surface = r.surface }
    }

    /** VirtualDisplay → ImageReader (tamaño real de pantalla) para leer pistas. */
    private fun toReader() {
        val v = vd ?: return
        runCatching { v.resize(capW, capH, capDpi); v.surface = reader?.surface }
    }

    private fun stopProjection() {
        if (recorder != null) stopRec()
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

    /** Copia la imagen fila a fila (el rowStride puede tener relleno; la última fila puede ser más corta). */
    /** Último fotograma disponible con su marca de tiempo (null si no llegó ninguno nuevo). */
    private fun grab(): Pair<Bitmap, Long>? {
        val img = reader?.acquireLatestImage() ?: return null
        try {
            // fotograma anterior a ocultar la superposición: no sirve (saldrían las asas y marcos encima de las pistas)
            val ts = img.timestamp
            val pl = img.planes[0]
            val ps = pl.pixelStride; val rs = pl.rowStride
            val w = img.width; val h = img.height
            val buf = pl.buffer
            val row = ByteArray(w * ps)
            val px = IntArray(w * h)
            for (y in 0 until h) {
                buf.position(y * rs)
                buf.get(row, 0, minOf(row.size, buf.remaining()))
                var o = y * w
                var k = 0
                for (x in 0 until w) {
                    val r = row[k].toInt() and 255; val g = row[k + 1].toInt() and 255; val bl = row[k + 2].toInt() and 255
                    px[o++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
                    k += ps
                }
            }
            return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888) to ts
        } finally { img.close() }
    }

    private fun screenRect(v: View): Rect {
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        return Rect(loc[0], loc[1], loc[0] + v.width, loc[1] + v.height)
    }

    private var captureTry = 0
    private var captureKind = ""

    private fun box(r: Rect) = Snap.Box(r.left, r.top, r.right, r.bottom)
    private fun rect(b: Snap.Box) = Rect(b.l, b.t, b.r, b.b)

    fun read(keepTry: Boolean = false) {
        if (busy || playing) return
        if (!keepTry) captureTry = 0
        if (projection == null) {
            startActivity(Intent(this, ProjectionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_READ, true))
            return
        }
        busy = true; bubble.glyph = "…"
        if (!shown) show()
        capture { bmp, g0, rf0, cf0, ox, oy -> Thread { analyze(bmp, g0, rf0, cf0, ox, oy) }.start() }
    }

    /** Captura sin la superposición encima y llama a [then] en el hilo principal (o termina con aviso). */
    private fun capture(then: (Bitmap, Rect, Rect, Rect, Int, Int) -> Unit) {
        val bl = screenRect(bubble); val ox = bl.left - bubbleLp.x; val oy = bl.top - bubbleLp.y
        fun abs(l: WindowManager.LayoutParams) = Rect(l.x + ox, l.y + oy, l.x + ox + l.width, l.y + oy + l.height)
        val g0 = abs(gridLp); val rf0 = abs(rowLp); val cf0 = abs(colLp)
        val views = listOf(grid, rowF, colF, bar, bubble)
        val vis = views.map { it.visibility }
        // asas visibles antes de ocultar: si siguen en la captura, el fotograma es anterior a ocultar (sucio)
        val hs = (30 * resources.displayMetrics.density).toInt()
        val handles = listOf(Triple(rowF, rowLp, 0xE65100), Triple(colF, colLp, 0x2E7D32), Triple(grid, gridLp, 0x1565C0))
            .filter { (v, _, _) -> v.visibility == View.VISIBLE && (v as MovableView).editable }
            .map { (_, l, c) -> val r = abs(l); CaptureCheck.Handle(r.right - hs, r.bottom - hs, r.right, r.bottom, c) }
        fun dirty(b: Bitmap): Boolean {
            if (handles.isEmpty()) return false
            val w = b.width; val h = b.height; val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
            return CaptureCheck.overlayVisible(px, w, h, handles)
        }
        var dirtyFrames = 0
        // grabando: el VirtualDisplay apunta al codificador; durante la lectura vuelve un momento al ImageReader
        val recPaused = recorder != null
        if (recPaused) toReader()
        fun resume() { if (recPaused && recorder != null) toEncoder() }
        runCatching { reader?.acquireLatestImage()?.close() }
        views.forEach { it.visibility = View.INVISIBLE; it.alpha = 0f }
        // solo fotogramas compuestos después de ocultar (2 vsync de margen); si la pantalla está quieta
        // y no llega ninguno, al final se acepta cualquiera posterior al momento de ocultar
        val hiddenAt = System.nanoTime()
        var tries = 0
        var early: Bitmap? = null // posterior a ocultar pero quizá aún a medio componer
        var any: Bitmap? = null   // por si el reloj de las marcas de tiempo no es el esperado
        fun attempt() {
            val got = try { grab() } catch (e: Throwable) { views.forEachIndexed { i, v -> v.visibility = vis[i]; v.alpha = 1f }; resume(); fail("captura", e); return }
            var bmp: Bitmap? = null
            if (got != null) {
                if (dirty(got.first)) dirtyFrames++ // con la superposición encima: nunca sirve
                else {
                    any = got.first
                    if (got.second >= hiddenAt + 34_000_000L) bmp = got.first else if (got.second >= hiddenAt) early = got.first
                }
            }
            if (bmp == null && tries++ < 25) { main.postDelayed({ attempt() }, 100); return }
            captureKind = (if (bmp != null) "limpia" else if (early != null) "temprana" else if (any != null) "sin-marca" else "sucia") +
                (if (dirtyFrames > 0) " descartadas=$dirtyFrames" else "")
            if (bmp == null) bmp = early ?: any
            views.forEachIndexed { i, v -> v.visibility = vis[i]; v.alpha = 1f }
            resume()
            if (bmp == null) { done(if (dirtyFrames > 0) "La captura salió con los marcos encima; vuelve a leer" else "Sin captura"); return }
            then(bmp, g0, rf0, cf0, ox, oy)
        }
        main.postDelayed({ attempt() }, 300)
    }

    private fun fail(stage: String, e: Throwable) {
        ErrorLog.write(this, stage, e)
        main.post { busy = false; updateGlyph(); Toast.makeText(this, ErrorLog.short(stage, e), Toast.LENGTH_LONG).show() }
    }

    /** Hilo de fondo: detectar rejilla, ajustar marcos, OCR, resolver. */
    private fun analyze(bmp: Bitmap, g0: Rect, rf0: Rect, cf0: Rect, ox: Int, oy: Int) {
        var stage = "captura"
        try {
            if (isBlank(bmp)) { main.post { done("Captura vacía") }; return }
            var g = g0; var rf = rf0; var cf = cf0
            var rows = Store.rows; var cols = Store.cols
            var sized = !Store.autoSize
            if (Store.autoSize) {
                stage = "rejilla"
                val w = bmp.width; val h = bmp.height
                val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
                val det = GridDetect.detect(px, w, h, minCell = (6 * resources.displayMetrics.density).toInt().coerceAtLeast(6))
                    ?: detectInside(bmp, g0)
                if (det != null) {
                    val ng = Rect(det.left, det.top, det.right, det.bottom)
                    rows = det.rows; cols = det.cols; sized = true
                    // auto-acople: los marcos se pegan a los bordes exactos de la rejilla detectada
                    val (a, c) = if (Store.framesManual) Snap.frames(box(ng), box(rf), box(cf)) else Snap.frames(box(ng), Snap.Box(0, 0, 0, 0), Snap.Box(0, 0, 0, 0))
                    rf = rect(a); cf = rect(c)
                    g = ng
                }
            }
            stage = "ocr"
            val seg = if (sized) ClueReader.readSegmented(bmp, g, rf, cf, rows, cols) else null
            val res = if (seg != null && seg.tokens > 0) seg else checked(ClueReader.read(bmp, g, rf, cf, rows, cols, infer = !sized))
            main.post { applyRead(res, g, rf, cf, ox, oy) }
        } catch (e: Throwable) { fail(stage, e) }
    }

    /** Lectura de respaldo (ML Kit directo): validar también, para no mostrar pistas inválidas como buenas. */
    private fun checked(r: ClueReader.Read): ClueReader.Read {
        if (r.tokens == 0) return r
        val k = CluePipeline.validate(r.rowClues, r.colClues, null, null, 1500)
        return if (k.ok) r else ClueReader.Read(r.rows, r.cols, r.rowClues, r.colClues, r.palette, r.colorDetected, r.tokens, false,
            k.badRows.ifEmpty { r.rowClues.indices.toSet() }, k.badCols.ifEmpty { r.colClues.indices.toSet() })
    }

    /** Segundo intento: buscar la rejilla solo dentro (y alrededor) del cuadro actual. */
    private fun detectInside(bmp: Bitmap, g: Rect): GridDetect.Grid? {
        val m = maxOf(g.width(), g.height()) / 10
        val r = Rect(g.left - m, g.top - m, g.right + m, g.bottom + m)
        if (!r.intersect(0, 0, bmp.width, bmp.height) || r.width() < 40 || r.height() < 40) return null
        val px = IntArray(r.width() * r.height()); bmp.getPixels(px, 0, r.width(), r.left, r.top, r.width(), r.height())
        val d = GridDetect.detect(px, r.width(), r.height()) ?: return null
        return d.copy(left = d.left + r.left, top = d.top + r.top, right = d.right + r.left, bottom = d.bottom + r.top)
    }

    private fun isBlank(b: Bitmap): Boolean {
        val f = b.getPixel(b.width / 2, b.height / 2)
        for (i in 1..200) {
            val x = (i * 7919) % b.width; val y = (i * 104729) % b.height
            if (b.getPixel(x, y) != f) return false
        }
        return true
    }

    /** Coloca cuadro y marcos (coordenadas de pantalla) en sus ventanas. */
    private fun applyGeom(g: Rect, rf: Rect, cf: Rect, ox: Int, oy: Int) {
        fun set(l: WindowManager.LayoutParams, v: View, s: Rect, dst: Rect) {
            l.x = s.left - ox; l.y = s.top - oy; l.width = maxOf(minSide(), s.width()); l.height = maxOf(minSide(), s.height())
            dst.set(l.x, l.y, l.x + l.width, l.y + l.height); wm.updateViewLayout(v, l)
        }
        set(gridLp, grid, g, Store.grid); set(rowLp, rowF, rf, Store.rowFrame); set(colLp, colF, cf, Store.colFrame)
        grid.invalidate()
    }

    private fun applyRead(r: ClueReader.Read, g: Rect, rf: Rect, cf: Rect, ox: Int, oy: Int) {
        applyGeom(g, rf, cf, ox, oy)
        Store.resize(r.rows, r.cols)
        Store.rowClues = r.rowClues.toMutableList(); Store.colClues = r.colClues.toMutableList()
        Store.colorMode = r.colorDetected
        Store.palette.clear(); Store.palette.addAll(r.palette)
        Store.solution = null
        Store.gridScreen = Rect(g)
        Store.doubtRows.clear(); Store.doubtCols.clear()
        Store.doubtRows.addAll(r.doubtRows); Store.doubtCols.addAll(r.doubtCols)
        updateModeChip()
        grid.invalidate()
        Toast.makeText(this, "${r.rows}×${r.cols} · ${r.tokens}", Toast.LENGTH_SHORT).show()
        ErrorLog.write(this, "lectura", RuntimeException("${r.rows}x${r.cols} tokens=${r.tokens} captura=$captureKind ${ClueReader.lastStats}"))
        if (r.tokens == 0) { done(null); openEditor(); return }
        if (!r.valid && captureTry < 2) {
            // captura nueva (por si la anterior salió con algo encima o a medio dibujar)
            ErrorLog.write(this, "pistas", RuntimeException("captura ${captureTry + 1} no válida, se repite; leídas: ${CluePipeline.dump(r.rowClues, r.colClues)}"))
            captureTry++; busy = false; main.postDelayed({ read(keepTry = true) }, 250); return
        }
        captureTry = 0
        if (!r.valid) {
            // tras los reintentos las pistas siguen sin ser resolubles: no resolver, marcar líneas dudosas
            ErrorLog.write(this, "pistas", RuntimeException("no válidas tras reintentos (captura ${captureTry + 1}); ${r.rows}x${r.cols} dudosas filas=${r.doubtRows.map { it + 1 }.sorted()} columnas=${r.doubtCols.map { it + 1 }.sorted()} leídas: ${CluePipeline.dump(r.rowClues, r.colClues)} | ${ClueReader.lastStats}"))
            Store.save(this); done(null); openEditor(); return
        }
        Thread {
            val st = try { Store.solveNow() } catch (e: Throwable) { fail("resolver", e); return@Thread }
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

    fun refresh() { updateModeChip(); grid.invalidate(); show() }

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
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0xE0202020.toInt()); cornerRadius = dp(10).toFloat() }
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        fun btn(t: String, f: () -> Unit) = TextView(this).apply {
            text = t; setTextColor(0xFFFFFFFF.toInt()); textSize = 14f
            setPadding(dp(9), dp(9), dp(9), dp(9)); setOnClickListener { f() }
        }.also { barButtons.add(it) }
        btn("Pistas") { setFrames(!framesShown) }
        modeBtn = btn("Auto") { cycleMode() }
        btn("Leer") { read() }
        btn("Editar") { openEditor() }
        recBtn = btn("⏺") { toggleRec() }
        playBtn = btn("▶") { togglePlay() }
        speedBtn = btn("${Store.speed}x") { Store.speed = Store.speed % 3 + 1; speedBtn.text = "${Store.speed}x"; Store.save(this) }
        lockBtn = btn("🔓") { setLocked(!Store.locked) }
        btn("Ocultar") { hide() }
        btn("⟲") { resetAll() }
        btn("✕") { stopSelf() }
        flowBar()
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
        updateModeChip()
    }

    // ---------- Autoresolver ----------
    private fun togglePlay() { if (playing) stopPlay() else startPlay() }

    private fun startPlay() {
        if (busy) return
        if (!AutoTapService.enabled(this)) {
            // pedir el permiso de accesibilidad antes de empezar
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        busy = true
        Thread {
            // solo con pistas que se resuelven; si no, no se toca nada
            val st = try { if (Store.solution == null || Store.solH != Store.rows || Store.solW != Store.cols) Store.solveNow() else Solver.Status.SOLVED } catch (e: Throwable) { Solver.Status.INVALID }
            main.post {
                busy = false
                // geometría de la cuadrícula detectada (o la ajustada a mano si la rejilla está visible)
                val g = if (grid.visibility == View.VISIBLE && grid.width > 0) screenRect(grid) else Store.gridScreen
                val color = Store.effectiveColor()
                val rc = Store.rowClues.map { l -> l.map { if (color) it else Block(it.len, 1) } }
                val cc = Store.colClues.map { l -> l.map { if (color) it else Block(it.len, 1) } }
                val taps = if (st == Solver.Status.SOLVED) AutoPlan.taps(rc, cc, Store.solution, Store.rows, Store.cols, g.left, g.top, g.right, g.bottom) else emptyList()
                val svc = AutoTapService.instance
                if (taps.isEmpty() || svc == null) { updateGlyph(); if (st != Solver.Status.SOLVED) openEditor(); return@post }
                playing = true; playBtn.text = "■"
                // la superposición no debe recibir los toques: rejilla no táctil y barra oculta
                setLocked(true); grid.visibility = View.GONE
                updateGlyph()
                // el primer toque se perdía: se inyectaba en el mismo instante en que la rejilla pasaba a no táctil y
                // el WindowManager aún tenía la ventana anterior (táctil) encima, que se tragaba el toque. Esperar a
                // que se apliquen los cambios de ventana antes del primer gesto.
                main.postDelayed({
                    if (!playing) return@postDelayed
                    svc.play(taps, Store.intervalMs(), Store.strokeMs(), onStep = {}, onEnd = { main.post { endPlay() } })
                }, PLAY_START_DELAY_MS)
            }
        }.start()
    }

    private fun stopPlay() { AutoTapService.instance?.stop(); endPlay() }

    private fun endPlay() {
        if (!playing) return
        playing = false; playBtn.text = "▶"
        if (shown) grid.visibility = View.VISIBLE
        updateGlyph()
    }

    private fun cycleMode() {
        Store.colorPref = (Store.colorPref + 1) % 3
        updateModeChip()
        if ((Store.rowClues + Store.colClues).any { it.isNotEmpty() }) Thread {
            val st = Store.solveNow()
            main.post { Store.save(this); grid.invalidate(); if (st != Solver.Status.SOLVED) Toast.makeText(this, st.name, Toast.LENGTH_SHORT).show() }
        }.start() else Store.save(this)
    }

    fun updateModeChip() {
        if (!::modeBtn.isInitialized) return
        val c = Store.effectiveColor()
        modeBtn.text = when (Store.colorPref) { 1 -> "Color"; 2 -> "B/N"; else -> if (c) "Auto·Color" else "Auto·B/N" }
        modeBtn.setTextColor(if (c) 0xFFFF8A80.toInt() else 0xFFFFFFFF.toInt())
    }

    /**
     * Botones en filas que caben en el ancho de pantalla. Antes era una sola fila WRAP_CONTENT: al añadir ⏺ y 1x
     * la barra pasó del ancho de la pantalla y los últimos botones («Ocultar», «✕») quedaban fuera, recortados.
     */
    private fun flowBar() {
        // 5 % de margen por si el sistema dibuja el texto algo más ancho de lo medido
        val maxW = ((realMetrics().widthPixels - dp(8) - bar.paddingLeft - bar.paddingRight) * 0.95f).toInt()
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        // ancho de cada botón con su texto más largo posible: el de modo se creaba como «Auto» y luego pasaba a
        // «Auto·B/N»/«Auto·Color», la fila medida con «Auto» se salía de la pantalla y el último botón de la
        // primera fila (el selector de velocidad / el candado) quedaba recortado a ancho 0.
        val variants = mapOf<TextView, List<String>>(modeBtn to listOf("Auto·Color", "Auto·B/N", "Color", "B/N"),
            playBtn to listOf("▶", "■"), recBtn to listOf("⏺", "⏹"), speedBtn to listOf("1x", "2x", "3x"), lockBtn to listOf("🔒", "🔓"))
        val widths = barButtons.map { b ->
            val cur = b.text
            val w = (variants[b] ?: listOf(cur.toString())).maxOf { t -> b.text = t; b.measure(spec, spec); b.measuredWidth }
            b.text = cur; w
        }
        bar.removeAllViews()
        for (row in BarFlow.rows(widths, maxW)) {
            val ll = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (i in row) { (barButtons[i].parent as? android.view.ViewGroup)?.removeView(barButtons[i]); ll.addView(barButtons[i]) }
            // y si aun así no cabe, la fila se desplaza en vez de recortar botones
            bar.addView(android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(ll) })
        }
    }

    private fun barSize(): Pair<Int, Int> {
        val spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        bar.measure(spec, spec); return bar.measuredWidth to bar.measuredHeight
    }

    /** ⟲: cuadro y marcos a la posición/tamaño inicial y sin pistas ni solución. */
    private fun resetAll() {
        if (busy || playing) return
        Store.reset(this)
        fun put(l: WindowManager.LayoutParams, v: View, r: Rect) { l.x = r.left; l.y = r.top; l.width = r.width(); l.height = r.height(); if (v.parent != null) wm.updateViewLayout(v, l) }
        put(gridLp, grid, Store.grid); put(rowLp, rowF, Store.rowFrame); put(colLp, colF, Store.colFrame)
        grid.invalidate(); updateModeChip(); updateGlyph()
    }

    private fun placeBar() {
        val m = realMetrics()
        val (bw, bh) = barSize()
        barLp.x = (bubbleLp.x).coerceIn(0, maxOf(0, m.widthPixels - bw))
        barLp.y = if (bubbleLp.y + bubbleLp.height + bh + dp(4) < m.heightPixels) bubbleLp.y + bubbleLp.height + dp(4) else maxOf(0, bubbleLp.y - bh - dp(4))
        if (bar.parent != null) wm.updateViewLayout(bar, barLp)
    }

    private fun onBubble() {
        when {
            playing -> stopPlay()
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
        if (playing) { bubble.glyph = "■"; return }
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
        Store.framesManual = true
        wm.updateViewLayout(v, l)
        if (fin) Store.save(this)
    }
}
