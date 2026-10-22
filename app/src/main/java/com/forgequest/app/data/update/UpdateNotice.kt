package com.forgequest.app.data.update

import android.content.Context
import com.forgequest.app.BuildConfig
import com.forgequest.app.data.suspendRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The Home banner's state. [progress] is null until a download starts, then 0..1, or -1 while the size is unknown. */
data class UpdateBanner(val latest: LatestVersion, val progress: Float? = null, val needsPermission: Boolean = false, val failed: Boolean = false)

/**
 * "There's an update!" without anyone having to tell people: whenever Home opens, look at version.json (at most every
 * 6 hours), show a banner when there's a newer build, and after an update show what changed once.
 */
class UpdateNotice(context: Context, private val checker: UpdateChecker, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
    private val _banner = MutableStateFlow<UpdateBanner?>(null)
    val banner: StateFlow<UpdateBanner?> = _banner
    private val _whatsNew = MutableStateFlow<LatestVersion?>(null)
    val whatsNew: StateFlow<LatestVersion?> = _whatsNew

    fun refresh() = scope.launch {
        if (_banner.value?.progress != null) return@launch
        val seen = prefs.getInt(SEEN, 0)
        // 0 means a fresh install: nothing to announce.
        val justUpdated = seen in 1 until BuildConfig.VERSION_CODE
        if (seen != BuildConfig.VERSION_CODE) prefs.edit().putInt(SEEN, BuildConfig.VERSION_CODE).apply()
        val stale = System.currentTimeMillis() - prefs.getLong(LAST_CHECK, 0) > CHECK_EVERY_MS
        val latest = if (stale || justUpdated) {
            suspendRunCatching { checker.fetch() }.onSuccess {
                prefs.edit().putLong(LAST_CHECK, System.currentTimeMillis()).putString(CACHED, checker.encode(it)).apply()
            }.getOrNull() ?: cached()
        } else cached()
        if (justUpdated) _whatsNew.value = latest?.takeIf { it.versionCode == BuildConfig.VERSION_CODE }
            ?: LatestVersion(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, apk = "")
        val current = _banner.value
        _banner.value = latest?.takeIf { it.versionCode > BuildConfig.VERSION_CODE && !snoozed(it) }
            ?.let { if (current?.latest == it) current else UpdateBanner(it) }
        // Back from the "Install unknown apps" setting with it switched on: carry on without a second tap.
        if (current?.needsPermission == true && checker.canInstall()) update()
    }

    /** Download with progress on the banner, then hand the APK to Android's installer. */
    fun update() = scope.launch {
        val b = _banner.value ?: return@launch
        if (b.progress != null) return@launch
        if (!checker.canInstall()) {
            _banner.value = b.copy(needsPermission = true, failed = false)
            checker.openInstallPermission()
            return@launch
        }
        _banner.value = b.copy(progress = 0f, needsPermission = false, failed = false)
        suspendRunCatching { checker.download(b.latest) { p -> _banner.value = _banner.value?.copy(progress = p) } }
            // The banner stays: if the installer is cancelled, Update works again.
            .onSuccess { _banner.value = UpdateBanner(b.latest); checker.install(it) }
            .onFailure { _banner.value = UpdateBanner(b.latest, failed = true) }
    }

    /** Hide for 3 days, or until a newer version than this one comes out. */
    fun later() {
        val b = _banner.value ?: return
        prefs.edit().putInt(SNOOZE_CODE, b.latest.versionCode).putLong(SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MS).apply()
        _banner.value = null
    }

    fun dismissWhatsNew() { _whatsNew.value = null }

    private fun snoozed(latest: LatestVersion) = !latest.critical &&
        prefs.getInt(SNOOZE_CODE, 0) == latest.versionCode && System.currentTimeMillis() < prefs.getLong(SNOOZE_UNTIL, 0)

    private fun cached(): LatestVersion? = prefs.getString(CACHED, null)?.let { runCatching { checker.parse(it) }.getOrNull() }

    private companion object {
        const val SEEN = "seen_code"
        const val LAST_CHECK = "last_check"
        const val CACHED = "cached"
        const val SNOOZE_CODE = "snooze_code"
        const val SNOOZE_UNTIL = "snooze_until"
        const val CHECK_EVERY_MS = 6 * 60 * 60 * 1000L
        const val SNOOZE_MS = 3 * 24 * 60 * 60 * 1000L
    }
}
