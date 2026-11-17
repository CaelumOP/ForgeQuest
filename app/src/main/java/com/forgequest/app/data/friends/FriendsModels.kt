package com.forgequest.app.data.friends

import kotlinx.serialization.Serializable

/** Current user's Friends profile and settings. */
@Serializable
data class Me(
    val id: String,
    /** Unique lowercase handle (contract v2); null until the user has joined. */
    val username: String? = null,
    /** v4: always equals [username] ("" before join); 4.0 caches may still hold an old display name. */
    val name: String = "",
    val photoUrl: String? = null,
    val sharePhoto: Boolean,
    val sharePrs: Boolean,
    val joined: Boolean,
    /** v4: push me when a friend finishes a workout. */
    val alertsOn: Boolean = true,
    /** v4: friends may see (and get a push) when I finish a workout. */
    val shareWorkouts: Boolean = true,
    /** v4: push me "Do your workout!" messages. */
    val pingsOn: Boolean = true,
) {
    /** The one name shown in the UI (v4: the username; old caches fall back to the display name). */
    val shownName: String get() = username?.takeIf { it.isNotBlank() } ?: name
}

/** Minimal public profile for a friend or invite counterpart. */
@Serializable
data class Person(
    val id: String,
    val username: String? = null,
    val name: String = "",
    val photoUrl: String? = null,
) {
    val shownName: String get() = username?.takeIf { it.isNotBlank() } ?: name
}

/** Top personal record shared in a weekly snapshot. */
@Serializable
data class Pr(
    val exercise: String,
    val kg: Double,
    val reps: Int,
)

/** Weekly Forge Score snapshot uploaded by a user. */
@Serializable
data class Snapshot(
    val weekStart: Long,
    val done: Int,
    val due: Int,
    val score: Int,
    val badge: String,
    val streakWeeks: Int,
    val proteinDays: Int,
    val prs: List<Pr>? = null,
    /** v3: this week's state ("hit", "free", "open"). On reads: the friend's current-week state, or null. */
    val weekState: String? = null,
    /** v3, upload only: last week's final state ("hit", "free", "miss"); null for a brand-new user. */
    val lastWeekState: String? = null,
    val target: Int? = null,
    val prCount: Int? = null,
    val reachedAt: Long? = null,
    val scoreVersion: Int? = null,
    val breakdown: com.forgequest.app.domain.ForgeScoreBreakdown? = null,
    val updatedAt: Long = 0,
)

/** A friend together with their latest synced snapshot, if any. */
@Serializable
data class Friend(
    val id: String,
    val username: String? = null,
    val name: String = "",
    val photoUrl: String? = null,
    val snapshot: Snapshot? = null,
    /** v3: consecutive weeks you and this friend both hit, computed by the server. */
    val sharedStreak: Int = 0,
    /** v4: I muted this friend's workout alerts. */
    val muted: Boolean = false,
) {
    val shownName: String get() = username?.takeIf { it.isNotBlank() } ?: name
}

/** Unseen nudge delivered via GET /v1/friends. */
@Serializable
data class Nudge(
    val fromId: String,
    val name: String = "",
    val username: String? = null,
    val day: Long,
)

/** v4 inbox: a "Do your workout!" message (last 7 Riyadh days). */
@Serializable
data class InboxPing(
    val fromId: String,
    val name: String = "",
    val username: String? = null,
    val day: Long,
    val createdAt: Long = 0,
    val seen: Boolean = false,
) {
    val shownName: String get() = username?.takeIf { it.isNotBlank() } ?: name
}

/** v4 inbox: a friend finished a workout (last 48 h). */
@Serializable
data class InboxActivity(
    val userId: String,
    val name: String = "",
    val username: String? = null,
    val at: Long = 0,
) {
    val shownName: String get() = username?.takeIf { it.isNotBlank() } ?: name
}

/** v4 inbox returned by GET /v1/friends?v=4. */
@Serializable
data class Inbox(
    val pings: List<InboxPing> = emptyList(),
    val activity: List<InboxActivity> = emptyList(),
)

/** Full response from GET /v1/friends and cached locally. */
@Serializable
data class FriendsView(
    val me: Me,
    val today: Long,
    val weekStart: Long,
    val friends: List<Friend> = emptyList(),
    val incoming: List<Person> = emptyList(),
    val outgoing: List<Person> = emptyList(),
    val nudges: List<Nudge> = emptyList(),
    /** v4: absent in 4.0 caches, so it defaults to empty. */
    val inbox: Inbox = Inbox(),
    val mySnapshot: Snapshot? = null,
) {
    /** Bell badge: incoming requests + unseen "Do your workout" messages (activity never counts). */
    val badgeCount: Int get() = incoming.size + inbox.pings.count { !it.seen }
}

/** Response from POST /v1/auth/google. */
@Serializable
data class AuthResponse(
    val session: String,
    val user: Me,
)

/** Response from POST /v1/invites. */
@Serializable
data class InviteResult(
    val status: String,
)

/** Response from POST /v1/nudges/{id}. */
@Serializable
data class NudgeResult(
    val pushed: Boolean,
)
