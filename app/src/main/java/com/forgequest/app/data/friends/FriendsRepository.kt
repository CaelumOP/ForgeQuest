package com.forgequest.app.data.friends

import android.app.Activity
import android.content.Context
import com.forgequest.app.BuildConfig
import com.forgequest.app.data.ProgressRepository
import com.forgequest.app.data.ProgressSnapshot
import com.forgequest.app.domain.Dates
import com.forgequest.app.domain.ForgeScore
import com.forgequest.app.domain.FriendsLeaderboard
import com.forgequest.app.domain.LeaderboardEntry
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToLong

data class FriendsUiData(
    val view: FriendsView,
    val leaderboard: List<LeaderboardEntry>,
    val mySnapshot: Snapshot?,
)

/**
 * Coordinates Google sign-in, Friends API calls, offline cache, weekly snapshot uploads, and debug demo mode.
 */
class FriendsRepository(
    context: Context,
    private val api: FriendsApi,
    private val session: FriendsSession,
    private val cache: FriendsCache,
    private val progress: ProgressRepository,
) {
    private val prefs = context.applicationContext.getSharedPreferences(FriendsSession.PREFS_NAME, Context.MODE_PRIVATE)
    private val syncMutex = Mutex()
    private val appContext = context.applicationContext

    /** True once this process has registered the current FCM token with the Worker. */
    @Volatile
    private var pushSynced = false

    val signedIn: Boolean
        get() = session.session() != null

    val isDemo: Boolean
        get() = BuildConfig.DEBUG && prefs.getBoolean(KEY_DEMO, false)

    private val _signedInState = MutableStateFlow(signedIn)
    val signedInState: StateFlow<Boolean> = _signedInState.asStateFlow()

    private val _me = MutableStateFlow(loadInitialMe())
    val me: StateFlow<Me?> = _me.asStateFlow()

    private val _state = MutableStateFlow(loadInitialState())
    val state: StateFlow<FriendsUiData?> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _nudgedToday = MutableStateFlow(loadNudgedToday(Dates.today()))
    val nudgedToday: StateFlow<Set<String>> = _nudgedToday.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    suspend fun signIn(activity: Activity): Boolean {
        return try {
            val idToken = googleIdToken(activity)
            val auth = api.authGoogle(idToken)
            session.setSession(auth.session)
            saveMe(auth.user)
            _signedInState.value = true
            _error.value = null
            if (auth.user.joined) {
                refresh()
            } else {
                markNotJoined(keepMe = auth.user)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: FriendsException) {
            if (e.code != "cancelled") _error.value = e.code
            false
        }
    }

    /**
     * Joins Friends or updates the profile; [api.putMe] runs immediately so disabling a toggle nulls the column on the server.
     * [username] (raw input) is required on first join; null keeps the current one.
     * The v4 alert flags are sent only when non-null (null keeps the server value).
     */
    suspend fun join(
        sharePhoto: Boolean,
        sharePrs: Boolean,
        username: String? = null,
        alertsOn: Boolean? = null,
        shareWorkouts: Boolean? = null,
        pingsOn: Boolean? = null,
    ): Boolean {
        if (isDemo) {
            val curMe = _state.value?.view?.me ?: _me.value ?: return false
            val cleanUsername = username?.let { Usernames.normalize(it) ?: run { _error.value = "bad_username"; return false } }
            val updatedMe = curMe.copy(
                sharePhoto = sharePhoto,
                sharePrs = sharePrs,
                username = cleanUsername ?: curMe.username,
                alertsOn = alertsOn ?: curMe.alertsOn,
                shareWorkouts = shareWorkouts ?: curMe.shareWorkouts,
                pingsOn = pingsOn ?: curMe.pingsOn,
            )
            applyLocalMe(updatedMe)
            _error.value = null
            return true
        }
        if (!signedIn) return false
        val cleanUsername = username?.let { Usernames.normalize(it) ?: run { _error.value = "bad_username"; return false } }
        return try {
            val updated = syncMutex.withLock {
                val updatedMe = api.putMe(sharePhoto, sharePrs, cleanUsername, alertsOn, shareWorkouts, pingsOn)
                saveMe(updatedMe)
                // If share toggles changed while cached view is displayed, reflect it immediately too.
                _state.value?.let { cur ->
                    val updatedView = cur.view.copy(me = updatedMe)
                    cache.save(updatedView)
                    _state.value = cur.copy(
                        view = updatedView,
                        leaderboard = FriendsLeaderboard.build(updatedView, cur.mySnapshot),
                    )
                }
                uploadSnapshotLocked(force = true, sharePrs = updatedMe.sharePrs)
                refreshViewLocked()
                updatedMe
            }
            saveMe(updated)
            _error.value = null
            syncPushToken()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: FriendsException) {
            _error.value = e.code
            false
        }
    }

    suspend fun uploadSnapshot(force: Boolean = false) {
        if (!signedIn || isDemo) return
        if (_me.value?.joined == false) return
        syncMutex.withLock {
            uploadSnapshotLocked(force = force, sharePrs = currentSharePrs())
        }
    }

    suspend fun refresh(): Boolean {
        if (isDemo) {
            val progressSnap = runCatching { progress.freshSnapshot() }.getOrNull()
            val data = demoUiData(progressSnap)
            _me.value = data.view.me
            _state.value = data
            _error.value = null
            return true
        }
        if (!signedIn) return false
        return try {
            val ok = syncMutex.withLock {
                if (_me.value?.joined == false) {
                    val remoteMe = api.me()
                    saveMe(remoteMe)
                    if (!remoteMe.joined) {
                        markNotJoined(keepMe = remoteMe)
                        return@withLock false
                    }
                }
                uploadSnapshotLocked(force = false, sharePrs = currentSharePrs())
                refreshViewLocked()
                true
            }
            _error.value = null
            if (ok) syncPushToken()
            ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: FriendsException) {
            if (e.code == "not_joined") {
                markNotJoined()
                _error.value = null
                false
            } else {
                _error.value = e.code
                false
            }
        }
    }

    /** Best-effort background sync (for workout completion); ignores errors when offline or not joined. */
    suspend fun syncQuietly() {
        if (!signedIn || isDemo || !isJoined()) return
        try {
            syncMutex.withLock {
                uploadSnapshotLocked(force = false, sharePrs = currentSharePrs())
                refreshViewLocked()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Silent background sync never surfaces transient network errors.
        }
    }

    /** Sends a friend request by username. Returns null on success, else the error code (bad syntax never hits the server). */
    suspend fun invite(username: String): String? {
        val clean = Usernames.normalize(username) ?: return "bad_username".also { _error.value = it }
        if (isDemo) return null
        return try {
            api.invite(clean)
            refresh()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: FriendsException) {
            _error.value = e.code
            e.code
        }
    }

    suspend fun accept(id: String) = runAction { api.accept(id) }

    suspend fun decline(id: String) = runAction { api.decline(id) }

    suspend fun remove(id: String) = runAction { api.remove(id) }

    suspend fun block(id: String) = runAction { api.block(id) }

    /** Updates in-memory Me (and the cached leaderboard view if loaded) for optimistic toggles or demo mode. */
    internal fun applyLocalMe(updatedMe: Me) {
        _me.value = updatedMe
        if (isDemo) saveDemoMe(updatedMe)
        _state.value?.let { cur ->
            val updatedView = cur.view.copy(me = updatedMe)
            _state.value = cur.copy(
                view = updatedView,
                leaderboard = FriendsLeaderboard.build(updatedView, cur.mySnapshot),
            )
        }
    }

    /** Serializes Friends share/alert flag updates against the latest in-memory [Me] so rapid taps never clobber each other. */
    suspend fun syncMeSettings(desired: Me): Boolean {
        if (isDemo) {
            applyLocalMe(desired)
            _error.value = null
            return true
        }
        if (!signedIn) return false
        return try {
            val updated = syncMutex.withLock {
                val target = _me.value ?: desired
                val updatedMe = api.putMe(
                    target.sharePhoto,
                    target.sharePrs,
                    username = null,
                    alertsOn = target.alertsOn,
                    shareWorkouts = target.shareWorkouts,
                    pingsOn = target.pingsOn,
                )
                val newest = _me.value
                val merged = if (newest != null && newest != target) {
                    updatedMe.copy(
                        sharePhoto = newest.sharePhoto,
                        sharePrs = newest.sharePrs,
                        alertsOn = newest.alertsOn,
                        shareWorkouts = newest.shareWorkouts,
                        pingsOn = newest.pingsOn,
                    )
                } else {
                    updatedMe
                }
                saveMe(merged)
                _state.value?.let { cur ->
                    val updatedView = cur.view.copy(me = merged)
                    cache.save(updatedView)
                    _state.value = cur.copy(
                        view = updatedView,
                        leaderboard = FriendsLeaderboard.build(updatedView, cur.mySnapshot),
                    )
                }
                uploadSnapshotLocked(force = true, sharePrs = merged.sharePrs)
                refreshViewLocked()
                merged
            }
            saveMe(updated)
            _error.value = null
            syncPushToken()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: FriendsException) {
            _error.value = e.code
            false
        }
    }

    /** Changes one of the v4 alert switches in Friends settings, keeping everything else. */
    suspend fun setAlertFlags(alertsOn: Boolean? = null, shareWorkouts: Boolean? = null, pingsOn: Boolean? = null): Boolean {
        val me = _state.value?.view?.me ?: _me.value ?: return false
        return join(me.sharePhoto, me.sharePrs, alertsOn = alertsOn, shareWorkouts = shareWorkouts, pingsOn = pingsOn)
    }

    /**
     * Marks the inbox read: local state first (the badge clears at once), then POST /v1/inbox/seen.
     * Only calls the server when there is an unseen "Do your workout" message.
     */
    suspend fun markInboxSeen() {
        val cur = _state.value ?: return
        if (cur.view.inbox.pings.none { !it.seen }) return
        val seenView = cur.view.copy(inbox = cur.view.inbox.copy(pings = cur.view.inbox.pings.map { it.copy(seen = true) }))
        _state.value = cur.copy(view = seenView)
        if (isDemo) {
            prefs.edit().putBoolean(KEY_DEMO_PINGS_SEEN, true).apply()
            return
        }
        if (!signedIn) return
        cache.save(seenView)
        try {
            api.inboxSeen()
        } catch (e: CancellationException) {
            throw e
        } catch (_: FriendsException) {
            // Not critical: the next refresh shows the server's seen state again.
        }
    }

    /** Mutes or unmutes one friend's workout alerts (optimistic; reverted with an error on failure). */
    suspend fun setMuted(id: String, muted: Boolean) {
        val before = _state.value ?: return
        applyMuted(id, muted)
        if (isDemo) return
        try {
            api.setMuted(id, muted)
            _state.value?.let { cache.save(it.view) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: FriendsException) {
            _state.value = before
            _error.value = e.code
        }
    }

    private fun applyMuted(id: String, muted: Boolean) {
        val cur = _state.value ?: return
        val view = cur.view.copy(friends = cur.view.friends.map { if (it.id == id) it.copy(muted = muted) else it })
        _state.value = cur.copy(view = view, leaderboard = FriendsLeaderboard.build(view, cur.mySnapshot))
    }

    /** Changes the username in Settings, keeping the share toggles. */
    suspend fun changeUsername(raw: String): Boolean {
        val me = _state.value?.view?.me ?: _me.value ?: return false
        val clean = Usernames.normalize(raw) ?: run { _error.value = "bad_username"; return false }
        if (clean == me.username) return true
        return join(me.sharePhoto, me.sharePrs, clean)
    }

    /**
     * Nudges [id]. Returns `null` when sent, `"already_nudged"` when already used today (also disabling Nudge for today),
     * or another error code on failure.
     */
    suspend fun nudge(id: String): String? {
        if (isDemo) {
            markNudgedToday(id, Dates.today())
            return null
        }
        val day = _state.value?.view?.today ?: Dates.today()
        return try {
            api.nudge(id)
            markNudgedToday(id, day)
            _error.value = null
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: FriendsException) {
            if (e.code == "already_nudged") {
                markNudgedToday(id, day)
                "already_nudged"
            } else {
                _error.value = e.code
                e.code
            }
        }
    }

    /**
     * Registers this phone's FCM token with the Worker (PUT /v1/push) when signed in, joined and allowed to notify.
     * [newToken] comes from FriendsMessagingService.onNewToken; otherwise the token is fetched. Never throws: phones
     * without Play services, or Firebase failing to start, simply get no push. Runs outside [syncMutex].
     */
    suspend fun syncPushToken(newToken: String? = null) {
        if (newToken != null) pushSynced = false
        if (pushSynced || !signedIn || isDemo || !isJoined()) return
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return
        val token = newToken ?: try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        } ?: return
        try {
            val old = session.lastPushToken()
            api.putPush(token)
            session.setLastPushToken(token)
            pushSynced = true
            if (!old.isNullOrBlank() && old != token) runCatching { api.deletePush(old) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: FriendsException) {
            // Offline or server error: the next refresh tries again.
        }
    }

    /** Call after the person grants notifications, so the token is registered right away. */
    suspend fun onNotificationsAllowed() {
        pushSynced = false
        syncPushToken()
    }

    suspend fun signOut() {
        val pushToken = session.lastPushToken()
        if (!pushToken.isNullOrBlank() && session.session() != null && !isDemo) {
            runCatching { api.deletePush(pushToken) }
        }
        pushSynced = false
        val keepDemo = if (BuildConfig.DEBUG) prefs.getBoolean(KEY_DEMO, false) else false
        session.clear()
        if (keepDemo) prefs.edit().putBoolean(KEY_DEMO, true).apply()
        cache.clear()
        _me.value = null
        _signedInState.value = false
        _error.value = null
        _nudgedToday.value = emptySet()
        _state.value = if (isDemo) demoUiData() else null
    }

    suspend fun deleteAccount(): Boolean {
        if (!signedIn || isDemo) return false
        return try {
            // Unregister the push token while the session is still valid, then delete the account.
            session.lastPushToken()?.takeIf { it.isNotBlank() }?.let { t ->
                runCatching { api.deletePush(t) }
                session.setLastPushToken(null)
            }
            api.deleteMe()
            signOut()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: FriendsException) {
            _error.value = e.code
            false
        }
    }

    private fun isJoined(): Boolean = _state.value != null || _me.value?.joined == true

    private fun currentSharePrs(): Boolean = _state.value?.view?.me?.sharePrs ?: _me.value?.sharePrs ?: true

    private suspend fun runAction(block: suspend () -> Unit) {
        if (isDemo) return
        try {
            block()
            refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FriendsException) {
            _error.value = e.code
        }
    }

    private suspend fun uploadSnapshotLocked(force: Boolean, sharePrs: Boolean) {
        if (_me.value?.joined == false) return
        val progressSnap = progress.freshSnapshot()
        val snapshot = buildSnapshot(progressSnap, sharePrs = sharePrs)
        val json = FriendsApi.json.encodeToString(Snapshot.serializer(), snapshot)
        val hash = sha256(json)
        if (!force && hash == session.snapshotHash()) return
        try {
            api.putSnapshot(snapshot)
            session.setSnapshotHash(hash)
            saveLastSnapshot(snapshot)
        } catch (e: FriendsException) {
            if (e.code == "not_joined") {
                markNotJoined()
                return
            }
            throw e
        }
    }

    private suspend fun refreshViewLocked() {
        val view = api.friends()
        saveMe(view.me)
        if (!view.me.joined) {
            markNotJoined(keepMe = view.me)
            return
        }
        cache.save(view)
        _nudgedToday.value = loadNudgedToday(view.today)
        val mySnap = loadLastSnapshot()
        _state.value = FriendsUiData(
            view = view,
            leaderboard = FriendsLeaderboard.build(view, mySnap),
            mySnapshot = mySnap,
        )
    }

    private fun markNotJoined(keepMe: Me? = null) {
        cache.clear()
        session.setSnapshotHash(null)
        prefs.edit().remove(KEY_LAST_SNAPSHOT).apply()
        val updatedMe = keepMe ?: _me.value?.copy(joined = false)
        if (updatedMe != null) saveMe(updatedMe) else prefs.edit().remove(KEY_LAST_ME).apply()
        _me.value = updatedMe
        _state.value = null
    }

    private fun loadInitialMe(): Me? {
        if (isDemo) return loadDemoMe()
        cache.load()?.me?.let { return it }
        val raw = prefs.getString(KEY_LAST_ME, null) ?: return null
        return runCatching { FriendsApi.json.decodeFromString(Me.serializer(), raw) }.getOrNull()
    }

    private fun defaultDemoMe() = Me(
        id = "demo_me",
        username = "tariq",
        name = "tariq",
        photoUrl = null,
        sharePhoto = true,
        sharePrs = true,
        joined = true,
    )

    private fun loadDemoMe(): Me {
        val raw = prefs.getString(KEY_DEMO_ME, null) ?: return defaultDemoMe()
        return runCatching { FriendsApi.json.decodeFromString(Me.serializer(), raw) }.getOrNull() ?: defaultDemoMe()
    }

    private fun saveDemoMe(me: Me) {
        prefs.edit().putString(KEY_DEMO_ME, FriendsApi.json.encodeToString(Me.serializer(), me)).apply()
    }

    private fun loadInitialState(): FriendsUiData? {
        if (isDemo) return demoUiData()
        val cached = cache.load() ?: return null
        if (!cached.me.joined) return null
        val mySnap = loadLastSnapshot()
        return FriendsUiData(
            view = cached,
            leaderboard = FriendsLeaderboard.build(cached, mySnap),
            mySnapshot = mySnap,
        )
    }

    private fun saveMe(me: Me) {
        _me.value = me
        prefs.edit().putString(KEY_LAST_ME, FriendsApi.json.encodeToString(Me.serializer(), me)).apply()
    }

    private fun saveLastSnapshot(snapshot: Snapshot) {
        prefs.edit()
            .putString(KEY_LAST_SNAPSHOT, FriendsApi.json.encodeToString(Snapshot.serializer(), snapshot))
            .apply()
    }

    private fun loadLastSnapshot(): Snapshot? {
        val raw = prefs.getString(KEY_LAST_SNAPSHOT, null) ?: return null
        return runCatching { FriendsApi.json.decodeFromString(Snapshot.serializer(), raw) }.getOrNull()
    }

    private fun loadNudgedToday(today: Long): Set<String> {
        val savedDay = prefs.getLong(KEY_NUDGED_DAY, -1L)
        if (savedDay != today) return emptySet()
        return prefs.getStringSet(KEY_NUDGED_IDS, emptySet())?.toSet().orEmpty()
    }

    private fun markNudgedToday(id: String, day: Long) {
        val next = loadNudgedToday(day) + id
        prefs.edit()
            .putLong(KEY_NUDGED_DAY, day)
            .putStringSet(KEY_NUDGED_IDS, next)
            .apply()
        _nudgedToday.value = next
    }

    internal fun buildSnapshot(
        snap: ProgressSnapshot,
        sharePrs: Boolean,
        today: Long = Dates.today(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Snapshot {
        val weekStart = Dates.weekStart(today)
        val weekEnd = weekStart + 6L
        val scheduledDays = snap.plan?.trainingDays ?: emptySet()
        val trainedDays = snap.sessions.asSequence()
            .filter { it.finishedAt != null }
            .map { it.epochDay }
            .filter { it in weekStart..today }
            .toSet()
        val proteinGoal = snap.settings.proteinGoal
        val proteinHitDays = if (proteinGoal <= 0) {
            emptySet()
        } else {
            snap.nutritionByDay.asSequence()
                .filter { it.epochDay in weekStart..today && it.protein >= proteinGoal }
                .map { it.epochDay }
                .toSet()
        }
        val week = ForgeScore.compute(
            today = today,
            scheduledDays = scheduledDays,
            trainedDays = trainedDays,
            proteinHitDays = proteinHitDays,
        )
        val prs = if (!sharePrs) {
            null
        } else {
            val sessionsById = snap.sessions.associateBy { it.id }
            snap.doneSets.asSequence()
                .filter { it.isPr }
                .mapNotNull { set ->
                    val s = sessionsById[set.sessionId] ?: return@mapNotNull null
                    val fin = s.finishedAt ?: return@mapNotNull null
                    val day = Instant.ofEpochMilli(fin).atZone(zone).toLocalDate().toEpochDay()
                    if (day !in weekStart..weekEnd) return@mapNotNull null
                    val rawName = snap.exercises[set.exerciseId]?.name ?: set.exerciseId
                    val name = rawName.trim().ifBlank { set.exerciseId }.take(60)
                    val kg = (((set.weightKg ?: 0f) * 100.0).roundToLong() / 100.0).coerceIn(0.0, 1000.0)
                    val reps = (set.reps ?: set.seconds ?: 1).coerceIn(1, 100)
                    Triple(fin, set.id, Pr(exercise = name, kg = kg, reps = reps))
                }
                .sortedWith(compareByDescending<Triple<Long, Long, Pr>> { it.first }.thenByDescending { it.second })
                .map { it.third }
                .take(10)
                .toList()
        }
        return Snapshot(
            weekStart = week.weekStart,
            done = week.done,
            due = week.due,
            score = week.score,
            badge = week.badge,
            streakWeeks = snap.streak.weeks,
            proteinDays = week.proteinDays,
            prs = prs,
            weekState = when {
                snap.streak.hitThisWeek -> "hit"
                snap.streak.freeWeek -> "free"
                else -> "open"
            },
            lastWeekState = snap.streak.lastWeekState,
        )
    }

    private fun demoUiData(progressSnap: ProgressSnapshot? = null): FriendsUiData {
        val today = Dates.today()
        val weekStart = Dates.weekStart(today)
        val now = System.currentTimeMillis()
        val me = loadDemoMe()
        val samplePrs = listOf(Pr("Bench Press", 92.5, 5), Pr("Pull-up", 15.0, 8))
        val mySnap = progressSnap?.let { ps ->
            val built = buildSnapshot(ps, sharePrs = me.sharePrs, today = today)
            built.copy(prs = if (!me.sharePrs) null else built.prs?.ifEmpty { samplePrs })
        } ?: Snapshot(
            weekStart = weekStart,
            done = 0,
            due = 0,
            score = 100,
            badge = ForgeScore.REST,
            streakWeeks = 0,
            proteinDays = 0,
            prs = if (me.sharePrs) samplePrs else null,
            weekState = "open",
        )
        val friends = listOf(
            Friend(
                id = "demo_1",
                name = "omar",
                username = "omar",
                snapshot = Snapshot(
                    weekStart = weekStart,
                    done = 4,
                    due = 4,
                    score = 100,
                    badge = ForgeScore.TRAINED,
                    streakWeeks = 9,
                    proteinDays = 6,
                    prs = listOf(Pr("Barbell Back Squat", 120.0, 5), Pr("Overhead Press", 60.0, 6)),
                    weekState = "hit",
                ),
                sharedStreak = 6,
            ),
            Friend(
                id = "demo_2",
                name = "lina",
                username = "lina",
                snapshot = Snapshot(
                    weekStart = weekStart,
                    done = 3,
                    due = 4,
                    score = 85,
                    badge = ForgeScore.ON_TRACK,
                    streakWeeks = 4,
                    proteinDays = 5,
                    prs = listOf(Pr("Romanian Deadlift", 85.0, 8)),
                    weekState = "open",
                ),
                sharedStreak = 4,
            ),
            Friend(
                id = "demo_3",
                name = "zaid",
                username = "zaid",
                snapshot = Snapshot(
                    weekStart = weekStart,
                    done = 2,
                    due = 3,
                    score = 75,
                    badge = ForgeScore.REST,
                    streakWeeks = 3,
                    proteinDays = 4,
                    prs = emptyList(),
                    weekState = "open",
                ),
                sharedStreak = 2,
            ),
            Friend(
                id = "demo_4",
                name = "sami",
                username = "sami",
                snapshot = Snapshot(
                    weekStart = weekStart,
                    done = 1,
                    due = 4,
                    score = 35,
                    badge = ForgeScore.SLACKING,
                    streakWeeks = 1,
                    proteinDays = 2,
                    prs = null,
                    weekState = "open",
                ),
                sharedStreak = 0,
            ),
            Friend(
                id = "demo_5",
                name = "noor",
                username = "noor",
                snapshot = Snapshot(
                    weekStart = weekStart - 7L,
                    done = 3,
                    due = 3,
                    score = 95,
                    badge = ForgeScore.SLACKING,
                    streakWeeks = 2,
                    proteinDays = 4,
                ),
            ),
        )
        val view = FriendsView(
            me = me,
            today = today,
            weekStart = weekStart,
            friends = friends,
            incoming = listOf(Person(id = "demo_in_1", username = "karim", name = "karim")),
            outgoing = listOf(Person(id = "demo_out_1", username = "maya", name = "maya")),
            nudges = listOf(Nudge(fromId = "demo_1", name = "omar", username = "omar", day = today)),
            inbox = Inbox(
                pings = listOf(
                    InboxPing(
                        fromId = "demo_1",
                        name = "omar",
                        username = "omar",
                        day = today,
                        createdAt = now / 1000 - 2 * HOUR_S,
                        seen = prefs.getBoolean(KEY_DEMO_PINGS_SEEN, false),
                    ),
                ),
                activity = listOf(
                    InboxActivity(userId = "demo_2", name = "lina", username = "lina", at = now / 1000 - 3 * HOUR_S),
                    InboxActivity(userId = "demo_1", name = "omar", username = "omar", at = now / 1000 - 26 * HOUR_S),
                ),
            ),
        )
        return FriendsUiData(
            view = view,
            leaderboard = FriendsLeaderboard.build(view, mySnap),
            mySnapshot = mySnap,
        )
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_DEMO = "friends_demo"
        private const val KEY_DEMO_ME = "demo_me"
        private const val KEY_DEMO_PINGS_SEEN = "demo_pings_seen"
        private const val KEY_LAST_SNAPSHOT = "last_snapshot"
        private const val KEY_LAST_ME = "last_me"
        private const val KEY_NUDGED_DAY = "nudged_day"
        private const val KEY_NUDGED_IDS = "nudged_ids"
        private const val HOUR_S = 3_600L // inbox times are epoch seconds
    }
}
