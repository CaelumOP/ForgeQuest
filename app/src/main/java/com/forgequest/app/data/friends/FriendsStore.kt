package com.forgequest.app.data.friends

import android.content.Context
import java.io.File

/**
 * Persists the Friends session token, last registered push token, and last uploaded snapshot hash
 * in backup-excluded SharedPreferences (`friends_prefs`).
 */
class FriendsSession(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun session(): String? = prefs.getString(KEY_SESSION, null)

    fun setSession(token: String?) {
        prefs.edit().apply {
            if (token == null) remove(KEY_SESSION) else putString(KEY_SESSION, token)
        }.apply()
    }

    fun lastPushToken(): String? = prefs.getString(KEY_PUSH_TOKEN, null)

    fun setLastPushToken(token: String?) {
        prefs.edit().apply {
            if (token == null) remove(KEY_PUSH_TOKEN) else putString(KEY_PUSH_TOKEN, token)
        }.apply()
    }

    fun snapshotHash(): String? = prefs.getString(KEY_SNAPSHOT_HASH, null)

    fun setSnapshotHash(hash: String?) {
        prefs.edit().apply {
            if (hash == null) remove(KEY_SNAPSHOT_HASH) else putString(KEY_SNAPSHOT_HASH, hash)
        }.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "friends_prefs"
        private const val KEY_SESSION = "session"
        private const val KEY_PUSH_TOKEN = "last_push_token"
        private const val KEY_SNAPSHOT_HASH = "snapshot_hash"
    }
}

/**
 * Atomic file cache for the last [FriendsView] response in `noBackupFilesDir/friends_cache.json`.
 */
class FriendsCache(context: Context) {
    private val file = File(context.applicationContext.noBackupFilesDir, CACHE_FILE)

    fun load(): FriendsView? = runCatching {
        if (!file.exists()) return null
        FriendsApi.json.decodeFromString(FriendsView.serializer(), file.readText())
    }.getOrNull()

    fun save(view: FriendsView) {
        val dir = file.parentFile ?: return
        dir.mkdirs()
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeText(FriendsApi.json.encodeToString(FriendsView.serializer(), view))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun clear() {
        file.delete()
    }

    companion object {
        private const val CACHE_FILE = "friends_cache.json"
    }
}
