package com.laminebaidari.telelibre.update

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.laminebaidari.telelibre.BuildConfig
import com.laminebaidari.telelibre.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Self-update from the public GitHub releases of LamineBaidari/telelibre.
 * Android only accepts an update signed with the same key, so a tampered APK can't replace the app.
 */
class Updater(private val context: Context, private val client: OkHttpClient) {
    private val prefs = context.getSharedPreferences("telelibre", Context.MODE_PRIVATE)
    private val apk = File(context.cacheDir, "update.apk")

    private val _available = MutableStateFlow(readyVersion())
    /** Version name of a downloaded update waiting to be installed, if any. */
    val available: StateFlow<String?> = _available.asStateFlow()

    val lastCheck: Long get() = prefs.getLong(KEY_LAST_CHECK, 0L)

    suspend fun check() = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(LATEST).header("Accept", "application/vnd.github+json").build()
            val release = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                JSONObject(response.body!!.string())
            }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            val version = release.getString("tag_name").removePrefix("v")
            if (!isNewer(version, BuildConfig.VERSION_NAME)) {
                clear()
                return@withContext
            }
            if (version == readyVersion()) return@withContext
            val url = assetUrl(release) ?: return@withContext
            download(url)
            prefs.edit().putString(KEY_READY, version).apply()
            _available.value = version
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
        }
    }

    /** Hands the downloaded APK to the system installer, which asks the user to confirm. */
    fun install(activity: Activity) {
        if (!activity.packageManager.canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.update_allow_sources, Toast.LENGTH_LONG).show()
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            return
        }
        if (!apk.exists()) {
            clear()
            return
        }
        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("telelibre.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                activity, sessionId, Intent(activity, InstallResultReceiver::class.java), flags,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun download(url: String) {
        val part = File(context.cacheDir, "update.apk.part")
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            part.outputStream().use { response.body!!.byteStream().copyTo(it) }
        }
        if (!part.renameTo(apk)) error("rename failed")
    }

    private fun readyVersion(): String? =
        prefs.getString(KEY_READY, null)?.takeIf { apk.exists() && isNewer(it, BuildConfig.VERSION_NAME) }

    private fun clear() {
        apk.delete()
        prefs.edit().remove(KEY_READY).apply()
        _available.value = null
    }

    companion object {
        private const val TAG = "Updater"
        private const val LATEST = "https://api.github.com/repos/LamineBaidari/telelibre/releases/latest"
        private const val KEY_LAST_CHECK = "updateLastCheck"
        private const val KEY_READY = "updateReady"

        /** Compares dotted versions numerically: 1.10 > 1.9, 1.1.1 > 1.1. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
                if (diff != 0) return diff > 0
            }
            return false
        }

        fun assetUrl(release: JSONObject): String? {
            val assets = release.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk")) return asset.getString("browser_download_url")
            }
            return null
        }
    }
}

/** Receives the installer's answer; the first answer is usually "show the confirmation screen". */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w("Updater", "Install failed: $message")
                Toast.makeText(context, context.getString(R.string.update_failed), Toast.LENGTH_LONG).show()
            }
        }
    }
}
