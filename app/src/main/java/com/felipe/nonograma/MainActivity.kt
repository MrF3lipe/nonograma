package com.felipe.nonograma

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Pantalla principal = tutorial corto ilustrado + permisos + botón para iniciar la burbuja. */
class MainActivity : Activity() {
    private lateinit var permBtn: Button
    private lateinit var startBtn: Button
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val steps = listOf(
        1 to "Toca «Iniciar burbuja» y acepta la captura de pantalla.",
        2 to "Abre tu app de nonogramas.",
        3 to "Toca la burbuja: aparece el cuadro.",
        4 to "Arrastra el cuadro cerca de la rejilla y suéltalo: se acopla solo a sus bordes. La esquina cambia el tamaño.",
        5 to "«Pistas» muestra los marcos F (filas) y C (columnas). Al soltarlos se acoplan solos a la rejilla. Luego toca «Leer».",
        6 to "El botón de modo cambia entre Auto, Color y B/N.",
        7 to "Si algo sale mal, «Editar» permite corregir tamaño, números y colores; luego «Resolver».",
        8 to "Toca la burbuja para bloquear: el cuadro deja pasar los toques y puedes jugar debajo. Tócala otra vez para desbloquear.",
        9 to "▶ rellena la solución en el juego (requiere el permiso de accesibilidad). «1x / 2x / 3x» cambia la velocidad.",
        10 to "⏺ graba la pantalla (sin otra grabadora); ⏹ la detiene y guarda el vídeo en Galería › Movies/Nonograma."
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(28), dp(16), dp(16)) }
        list.addView(TextView(this).apply { text = "Nonograma"; textSize = 24f; setTypeface(typeface, Typeface.BOLD) })
        for ((n, t) in steps) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
            row.addView(StepArt(this, n), LinearLayout.LayoutParams(dp(96), dp(96)))
            row.addView(TextView(this).apply { text = "$n. $t"; textSize = 15f; setPadding(dp(12), 0, 0, 0) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            list.addView(row)
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this).apply { setPadding(dp(12), dp(6), dp(12), dp(12)) }
        permBtn = Button(this).apply { text = "Permisos"; setOnClickListener { askPerms() } }
        startBtn = Button(this).apply { text = "Iniciar burbuja"; setOnClickListener { startBubble() } }
        bar.addView(permBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(startBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(bar)
        setContentView(root)
        Updater.check(this) {}
    }

    override fun onResume() {
        super.onResume()
        Updater.resume(this)
        val ok = Settings.canDrawOverlays(this)
        permBtn.visibility = if (ok) View.GONE else View.VISIBLE
        startBtn.isEnabled = ok
        startBtn.text = if (OverlayService.instance != null) "Reiniciar burbuja" else "Iniciar burbuja"
    }

    private fun askPerms() {
        if (!Settings.canDrawOverlays(this))
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun startBubble() {
        if (!Settings.canDrawOverlays(this)) { askPerms(); return }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2); return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 1)
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) { if (rc == 2) {
        @Suppress("DEPRECATION")
        startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 1)
    } }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        if (rc != 1) return
        val i = Intent(this, OverlayService::class.java)
        if (res == RESULT_OK && data != null) i.setAction(OverlayService.ACTION_PROJECTION).putExtra(OverlayService.EXTRA_CODE, res).putExtra(OverlayService.EXTRA_DATA, data)
        startForegroundService(i)
        moveTaskToBack(true)
    }
}

/** Ilustraciones esquemáticas de cada paso (dibujadas, sin imágenes). */
class StepArt(ctx: Context, private val step: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blue = 0xFF3949AB.toInt()
    override fun onDraw(c: Canvas) {
        val s = width.toFloat(); val u = s / 12
        // teléfono
        p.style = Paint.Style.STROKE; p.strokeWidth = u / 3; p.color = 0xFF616161.toInt()
        c.drawRoundRect(u * 2, u * 0.5f, u * 10, u * 11.5f, u, u, p)
        fun grid(x: Float, y: Float, w: Float, n: Int, fill: Boolean, color: Int = blue) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = color
            val cw = w / n
            for (i in 0..n) { c.drawLine(x + i * cw, y, x + i * cw, y + w, p); c.drawLine(x, y + i * cw, x + w, y + i * cw, p) }
            if (fill) { p.style = Paint.Style.FILL; p.color = (color and 0x00FFFFFF) or 0x90000000.toInt()
                for (i in 0 until n) for (j in 0 until n) if ((i * 7 + j * 3) % 5 < 2) c.drawRect(x + j * cw + 1, y + i * cw + 1, x + (j + 1) * cw - 1, y + (i + 1) * cw - 1, p) }
        }
        fun bubble(x: Float, y: Float, label: String) {
            p.style = Paint.Style.FILL; p.color = blue; c.drawCircle(x, y, u * 1.1f, p)
            p.color = Color.WHITE; p.textSize = u * 1.1f; p.textAlign = Paint.Align.CENTER; c.drawText(label, x, y + u * 0.4f, p)
        }
        fun finger(x: Float, y: Float) { p.style = Paint.Style.FILL; p.color = 0x88FF7043.toInt(); c.drawCircle(x, y, u * 0.9f, p) }
        fun chip(x: Float, y: Float, t: String, col: Int = 0xFF202020.toInt()) {
            p.style = Paint.Style.FILL; p.color = col; c.drawRoundRect(x, y, x + u * 4.4f, y + u * 1.4f, u / 2, u / 2, p)
            p.color = Color.WHITE; p.textSize = u * 0.9f; p.textAlign = Paint.Align.CENTER; c.drawText(t, x + u * 2.2f, y + u * 1.05f, p)
        }
        when (step) {
            1 -> { chip(u * 3.8f, u * 8.5f, "Iniciar", blue); bubble(u * 6, u * 4, "▦") }
            2 -> grid(u * 4, u * 4, u * 5, 5, false, 0xFF757575.toInt())
            3 -> { grid(u * 4, u * 4, u * 5, 5, false, 0xFF757575.toInt()); bubble(u * 3.6f, u * 2.2f, "▦"); finger(u * 4.2f, u * 2.8f) }
            4 -> { grid(u * 4, u * 4.5f, u * 5, 5, false, 0xFF757575.toInt()); grid(u * 4.6f, u * 3.8f, u * 4.6f, 5, false)
                p.style = Paint.Style.FILL; p.color = blue; c.drawCircle(u * 9.2f, u * 8.4f, u * 0.5f, p); finger(u * 9.4f, u * 8.6f) }
            5 -> { grid(u * 5, u * 5, u * 4.5f, 5, false, 0xFF757575.toInt())
                p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.color = 0xFFE65100.toInt(); c.drawRect(u * 2.6f, u * 5, u * 4.8f, u * 9.5f, p)
                p.color = 0xFF2E7D32.toInt(); c.drawRect(u * 5, u * 2.4f, u * 9.5f, u * 4.8f, p) }
            6 -> { chip(u * 3.8f, u * 3, "Color", 0xFFC62828.toInt()); chip(u * 3.8f, u * 5.2f, "B/N"); chip(u * 3.8f, u * 7.4f, "Auto", blue) }
            7 -> { p.style = Paint.Style.FILL; for (i in 0..3) { p.color = 0xFFE0E0E0.toInt(); c.drawRect(u * 3, u * (2.5f + i * 1.8f), u * 9, u * (3.7f + i * 1.8f), p)
                    p.color = listOf(0xFFC62828, 0xFF2E7D32, 0xFF1565C0, 0xFF000000)[i].toInt(); c.drawRect(u * 7.6f, u * (2.7f + i * 1.8f), u * 8.6f, u * (3.5f + i * 1.8f), p) }
                chip(u * 3.8f, u * 9.6f, "Resolver", blue) }
            8 -> { grid(u * 4, u * 4, u * 5, 5, true); bubble(u * 3.6f, u * 2.2f, "🔒"); finger(u * 6.5f, u * 6.5f) }
            10 -> { chip(u * 3.8f, u * 4, "⏺", 0xFFC62828.toInt()); chip(u * 3.8f, u * 6.6f, "⏹") }
            9 -> { chip(u * 3.8f, u * 3, "▶", blue); chip(u * 3.8f, u * 5.2f, "1x"); chip(u * 3.8f, u * 7.4f, "3x", 0xFFC62828.toInt()) }
        }
    }
}
