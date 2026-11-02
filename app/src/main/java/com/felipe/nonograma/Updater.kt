package com.felipe.nonograma

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Auto-actualización (mismo formato que zfl-update-demo): version.json en GitHub raw,
 * parche bsdiff desde la versión instalada (si coincide su sha256) o APK completo; todo verificado con sha256.
 */
object Updater {
    const val MANIFEST = "https://raw.githubusercontent.com/MrF3lipe/nonograma/main/updates/version.json"
    class FileRef(val url: String, val sha256: String, val size: Long)
    class Patch(val fromVersionCode: Int, val fromSha256: String, val file: FileRef)
    class Manifest(val version: String, val versionCode: Int, val apk: FileRef, val patches: List<Patch>)

    private var pending: Manifest? = null
    private var waitingPermission = false

    fun parse(json: String): Manifest? {
        val o = JSONObject(json)
        val v = o.getJSONObject("variants").optJSONObject("universal") ?: return null
        fun ref(r: JSONObject) = FileRef(r.getString("url"), r.getString("sha256").lowercase(), r.optLong("size"))
        val ps = v.optJSONArray("patches")
        val patches = (0 until (ps?.length() ?: 0)).map { i ->
            val p = ps!!.getJSONObject(i)
            Patch(p.getInt("fromVersionCode"), p.getString("fromSha256").lowercase(), ref(p))
        }
        return Manifest(v.getString("version"), v.getInt("versionCode"), ref(v.getJSONObject("apk")), patches)
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Descarga con reintentos (red lenta o github.com que no responde a tiempo). */
    private fun get(url: String, to: File? = null): ByteArray? {
        var last: java.io.IOException? = null
        for (i in 0 until 3) {
            try { return getOnce(url, to) } catch (e: java.io.IOException) { last = e; to?.delete(); Thread.sleep(1500L * (i + 1)) }
        }
        throw last!!
    }

    private fun getOnce(url: String, to: File? = null): ByteArray? {
        var u = url
        repeat(5) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000; c.instanceFollowRedirects = true
            c.setRequestProperty("Cache-Control", "no-cache")
            val code = c.responseCode
            if (code in 300..399) { u = c.getHeaderField("Location") ?: return null; c.disconnect(); return@repeat }
            if (code != 200) { c.disconnect(); throw IllegalStateException("HTTP $code") }
            c.inputStream.use { s ->
                if (to == null) return s.readBytes()
                to.outputStream().use { o -> s.copyTo(o) }
                return ByteArray(0)
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    fun installedCode(ctx: Context): Int {
        val i = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) i.longVersionCode.toInt() else i.versionCode
    }

    /** Comprueba en segundo plano; si hay versión nueva muestra el aviso. onDone siempre se llama. */
    fun check(a: Activity, onDone: (shown: Boolean) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            val m = try {
                // cache-buster para no recibir una versión vieja del CDN
                get("$MANIFEST?t=${System.currentTimeMillis() / 60000}")?.let { parse(String(it)) }
            } catch (e: Exception) { null }
            main.post {
                if (a.isFinishing || m == null || m.versionCode <= installedCode(a)) { onDone(false); return@post }
                AlertDialog.Builder(a).setTitle("Nonograma ${m.version}")
                    .setPositiveButton("Actualizar") { _, _ -> start(a, m); onDone(true) }
                    .setNegativeButton("Ahora no") { _, _ -> onDone(false) }
                    .setOnCancelListener { onDone(false) }
                    .show()
            }
        }.start()
    }

    /** Llamar desde onResume: continúa si se volvió del ajuste de «instalar apps desconocidas». */
    fun resume(a: Activity) {
        val m = pending ?: return
        if (waitingPermission && canInstall(a)) { waitingPermission = false; start(a, m) }
    }

    private fun canInstall(c: Context) = Build.VERSION.SDK_INT < 26 || c.packageManager.canRequestPackageInstalls()

    fun start(a: Activity, m: Manifest) {
        pending = m
        if (!canInstall(a)) {
            waitingPermission = true
            a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            return
        }
        val ctx = a.applicationContext
        val main = Handler(Looper.getMainLooper())
        Toast.makeText(ctx, "Descargando…", Toast.LENGTH_SHORT).show()
        Thread {
            val dir = File(ctx.filesDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val apk = File(dir, "nonograma-${m.versionCode}.apk")
            val err = try {
                var ok = false
                val cur = installedCode(ctx)
                val p = m.patches.firstOrNull { it.fromVersionCode == cur }
                if (p != null) try {
                    val src = File(ctx.applicationInfo.sourceDir)
                    if (sha256(src) == p.fromSha256) {
                        val pf = File(dir, "p.bsdiff"); get(p.file.url, pf)
                        if (sha256(pf) == p.file.sha256) {
                            apk.writeBytes(BsPatch.apply(src.readBytes(), pf.readBytes()))
                            ok = sha256(apk) == m.apk.sha256
                        }
                        pf.delete()
                    }
                } catch (e: Throwable) { ErrorLog.write(ctx, "parche", e) }
                if (!ok) {
                    get(m.apk.url, apk)
                    if (sha256(apk) != m.apk.sha256) throw IllegalStateException("sha256")
                }
                null
            } catch (e: Throwable) { ErrorLog.write(ctx, "actualizar", e); e.javaClass.simpleName + ": " + (e.message ?: "") }
            main.post {
                if (err != null) Toast.makeText(ctx, err.take(90), Toast.LENGTH_LONG).show()
                else { pending = null; install(a, apk) }
            }
        }.start()
    }

    private fun install(a: Activity, apk: File) {
        try {
            val pi = a.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(a.packageName)
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                s.commit(PendingIntent.getBroadcast(a, id, Intent(a, UpdateReceiver::class.java), flags).intentSender)
            }
        } catch (e: Exception) {
            ErrorLog.write(a, "instalador", e)
            val uri = Uri.parse("content://${a.packageName}.updates/${apk.name}")
            a.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val st = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        } else if (st != PackageInstaller.STATUS_SUCCESS) {
            val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: st.toString()
            Toast.makeText(context, "Instalación: $msg".take(90), Toast.LENGTH_LONG).show()
        }
    }
}

/** Proveedor mínimo (sin androidx) que sirve los APK de filesDir/updates al instalador de respaldo. */
class UpdateFileProvider : ContentProvider() {
    override fun onCreate() = true
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val name = uri.lastPathSegment ?: return null
        if (name.contains('/') || !name.endsWith(".apk")) return null
        val f = File(File(context!!.filesDir, "updates"), name)
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun getType(uri: Uri) = "application/vnd.android.package-archive"
    override fun query(u: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

/** Registro de errores en almacenamiento de la app (files/errores.log y Android/data/.../files/errores.log). */
object ErrorLog {
    fun write(ctx: Context, stage: String, e: Throwable) {
        val text = "${java.util.Date()} [$stage] ${android.util.Log.getStackTraceString(e)}\n"
        for (d in listOfNotNull(ctx.filesDir, ctx.getExternalFilesDir(null))) runCatching {
            val f = File(d, "errores.log")
            if (f.length() > 200_000) f.delete()
            f.appendText(text)
        }
    }
    fun short(stage: String, e: Throwable): String {
        var c: Throwable = e
        while (c.cause != null && (c is java.util.concurrent.ExecutionException || c.message == null)) c = c.cause!!
        return "$stage: ${c.javaClass.simpleName}${c.message?.let { " – $it" } ?: ""}".take(120)
    }
}
