package com.felipe.nonograma

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/** Pide el permiso de captura cuando se perdió (p. ej. al pulsar Leer). */
class ProjectionActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        @Suppress("DEPRECATION")
        startActivityForResult(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent(), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        if (res == RESULT_OK && data != null) {
            startForegroundService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_PROJECTION)
                .putExtra(OverlayService.EXTRA_CODE, res).putExtra(OverlayService.EXTRA_DATA, data)
                .putExtra(OverlayService.EXTRA_READ, intent.getBooleanExtra(OverlayService.EXTRA_READ, false)))
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
