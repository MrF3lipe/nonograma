package com.felipe.nonograma

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

/** Abre, pide permisos (superposición, notificaciones, captura), lanza la burbuja y se cierra. */
class MainActivity : Activity() {
    private var askedOverlay = false
    private var askedNotif = false
    private var askedProj = false
    private lateinit var btn: Button

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        btn = Button(this).apply { text = "Iniciar"; setOnClickListener { askedOverlay = false; askedProj = false; step() } }
        setContentView(LinearLayout(this).apply { gravity = Gravity.CENTER; addView(btn) })
    }

    override fun onResume() { super.onResume(); step() }

    private fun step() {
        if (!Settings.canDrawOverlays(this)) {
            btn.text = "Permiso"
            if (!askedOverlay) {
                askedOverlay = true
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !askedNotif &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askedNotif = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            return
        }
        if (OverlayService.hasProjection) { finish(); return }
        if (!askedProj) {
            askedProj = true
            @Suppress("DEPRECATION")
            startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 1)
        }
    }

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, r: IntArray) { step() }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        if (rc != 1) return
        val i = Intent(this, OverlayService::class.java)
        if (res == RESULT_OK && data != null) i.setAction(OverlayService.ACTION_PROJECTION).putExtra(OverlayService.EXTRA_CODE, res).putExtra(OverlayService.EXTRA_DATA, data)
        startForegroundService(i)
        finish()
    }
}
