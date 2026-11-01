package com.forgequest.app.data.crash

import android.content.Context
import android.os.Build
import com.forgequest.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The last crash, kept on the phone: when the app dies, its time, versions, device model and stack trace go to one small
 * file (no user data). Next launch, Home offers to share it; nothing is ever sent on its own.
 */
object CrashReport {
    private const val FILE = "last_crash.txt"
    private const val MAX_TRACE = 16_000

    /** Records an uncaught exception to the file, then lets the previous handler (Android's crash dialog) carry on. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                file(app).writeText(
                    format(
                        time = System.currentTimeMillis(),
                        appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        device = "${Build.MANUFACTURER} ${Build.MODEL}",
                        thread = thread.name,
                        stackTrace = stackTrace(error),
                    ),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved report, or null when there's none waiting. */
    fun pending(context: Context): String? =
        file(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }?.takeIf { it.isNotBlank() }

    fun clear(context: Context) { file(context).delete() }

    fun stackTrace(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    /** Plain text for the share sheet. Very long traces are cut so the report stays small. */
    fun format(time: Long, appVersion: String, android: String, device: String, thread: String, stackTrace: String): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(time))
        val trace = stackTrace.trimEnd().let { if (it.length > MAX_TRACE) it.take(MAX_TRACE) + "\n... (cut)" else it }
        return buildString {
            appendLine("ForgeQuest crash report")
            appendLine("Time: $stamp")
            appendLine("App: $appVersion")
            appendLine("Android: $android")
            appendLine("Device: $device")
            appendLine("Thread: $thread")
            appendLine()
            append(trace)
        }
    }

    private fun file(context: Context) = File(context.filesDir, FILE)
}
