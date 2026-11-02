package com.felipe.nonograma

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Grabación H.264/MP4 a la galería (Movies/Nonograma). No crea otra MediaProjection ni otro VirtualDisplay:
 * el servicio apunta su único VirtualDisplay a [surface] mientras graba (Android 14+ solo permite uno por token).
 */
class ScreenRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private var file: File? = null
    var surface: Surface? = null; private set
    var w = 0; private set
    var h = 0; private set

    /** Prepara y arranca con el primer tamaño que el codificador acepte. */
    fun start(screenW: Int, screenH: Int): Boolean {
        val name = "nonograma_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        for ((cw, ch) in RecSize.candidates(screenW, screenH)) {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            try {
                openOutput(name)
                r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                r.setVideoSize(cw, ch); r.setVideoFrameRate(30); r.setVideoEncodingBitRate(RecSize.bitrate(cw, ch))
                pfd?.let { r.setOutputFile(it.fileDescriptor) } ?: r.setOutputFile(file!!.absolutePath)
                r.prepare(); surface = r.surface; r.start()
                rec = r; w = cw; h = ch
                return true
            } catch (e: Throwable) {
                ErrorLog.write(ctx, "grabar", RuntimeException("${cw}x$ch: ${e.javaClass.simpleName} ${e.message}"))
                runCatching { r.release() }; surface = null; discard()
            }
        }
        return false
    }

    /** Detiene y publica el vídeo. Devuelve true si quedó guardado. */
    fun stop(): Boolean {
        val r = rec ?: return false
        rec = null; surface = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        runCatching { pfd?.close() }; pfd = null
        if (!ok) { discard(); return false }
        uri?.let { u ->
            if (Build.VERSION.SDK_INT >= 29) ctx.contentResolver.update(u, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        }
        file?.let { MediaScannerConnection.scanFile(ctx, arrayOf(it.absolutePath), arrayOf("video/mp4"), null) }
        uri = null; file = null
        return true
    }

    val recording get() = rec != null

    private fun openOutput(name: String) {
        discard()
        if (Build.VERSION.SDK_INT >= 29) {
            val v = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Nonograma"); put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val u = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: error("MediaStore")
            uri = u; pfd = ctx.contentResolver.openFileDescriptor(u, "w")
        } else {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Nonograma").apply { mkdirs() }
            file = File(dir, name)
        }
    }

    private fun discard() {
        runCatching { pfd?.close() }; pfd = null
        uri?.let { u -> runCatching { ctx.contentResolver.delete(u, null, null) } }; uri = null
        file?.delete(); file = null
    }
}
