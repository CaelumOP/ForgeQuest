package com.forgequest.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.forgequest.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * What `apk/version.json` in the repo says about the newest build. [apk] is the GitHub Release file, which only exists
 * once CI has published that version.
 */
@Serializable
data class LatestVersion(val versionCode: Int, val versionName: String, val apk: String, val notes: String = "", val critical: Boolean = false)

/**
 * Updates for an app installed outside the Play Store: read the version file next to the APK on GitHub, and if it's
 * newer, download the APK and hand it to Android's installer. Same signing key, so data stays.
 */
class UpdateChecker(private val context: Context, private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The newer version, or null when this one is the latest. Throws [IOException] when offline. */
    suspend fun check(): LatestVersion? = fetch().takeIf { it.versionCode > BuildConfig.VERSION_CODE }

    /** Whatever version.json says right now, newer or not. */
    suspend fun fetch(): LatestVersion = withContext(Dispatchers.IO) {
        val body = http.newCall(Request.Builder().url(VERSION_URL).header("Cache-Control", "no-cache").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body!!.string()
        }
        parse(body)
    }

    fun parse(body: String): LatestVersion = json.decodeFromString(LatestVersion.serializer(), body)
    fun encode(latest: LatestVersion): String = json.encodeToString(LatestVersion.serializer(), latest)

    /** Downloads the APK into the shared cache folder and returns it. [onProgress] gets 0..1, or -1 when the size is unknown. */
    suspend fun download(latest: LatestVersion, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val file = File(File(context.cacheDir, "export").apply { mkdirs() }, "ForgeQuest-${latest.versionName}.apk")
        http.newCall(Request.Builder().url(latest.apk).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body!!
            val total = body.contentLength()
            file.outputStream().use { out ->
                val input = body.byteStream()
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var lastPct = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val pct = if (total > 0) (done * 100 / total).toInt() else -1
                    if (pct != lastPct) { lastPct = pct; onProgress(if (pct < 0) -1f else pct / 100f) }
                }
            }
        }
        // version.json can go live minutes before the APK it names (and a CDN may still hand out the old one): never
        // install something older than promised.
        val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
        if (info == null || androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info) < latest.versionCode) {
            file.delete()
            throw NotPublishedYet()
        }
        file
    }

    /** The download was an older build: the new one is still being published. */
    class NotPublishedYet : IOException("APK not published yet")

    /** Android asks once whether ForgeQuest may install apps; until allowed, this opens that setting instead. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private companion object {
        const val VERSION_URL = "https://raw.githubusercontent.com/CaelumOP/ForgeQuest/main/apk/version.json"
    }
}
