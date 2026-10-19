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

/** What `apk/version.json` in the repo says about the newest build. */
@Serializable
data class LatestVersion(val versionCode: Int, val versionName: String, val apk: String, val notes: String = "")

/**
 * Updates for an app installed outside the Play Store: read the version file next to the APK on GitHub, and if it's
 * newer, download the APK and hand it to Android's installer. Same signing key, so data stays.
 */
class UpdateChecker(private val context: Context, private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The newer version, or null when this one is the latest. Throws [IOException] when offline. */
    suspend fun check(): LatestVersion? = withContext(Dispatchers.IO) {
        val body = http.newCall(Request.Builder().url(VERSION_URL).header("Cache-Control", "no-cache").build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            r.body!!.string()
        }
        json.decodeFromString(LatestVersion.serializer(), body).takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    /** Downloads the APK into the shared cache folder and returns it. */
    suspend fun download(latest: LatestVersion): File = withContext(Dispatchers.IO) {
        val file = File(File(context.cacheDir, "export").apply { mkdirs() }, "ForgeQuest-${latest.versionName}.apk")
        http.newCall(Request.Builder().url(latest.apk).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            file.outputStream().use { out -> r.body!!.byteStream().copyTo(out) }
        }
        file
    }

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
