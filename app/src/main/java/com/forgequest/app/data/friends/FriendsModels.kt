package com.forgequest.app.data.friends

import kotlinx.serialization.Serializable

/** Current user's Friends profile and settings. */
@Serializable
data class Me(
    val id: String,
    /** Unique lowercase handle (contract v2); null until the user has joined. */
    val username: String? = null,
    val name: String,
    val photoUrl: String? = null,
    val sharePhoto: Boolean,
    val sharePrs: Boolean,
    val joined: Boolean,
)

/** Minimal public profile for a friend or invite counterpart. */
@Serializable
data class Person(
    val id: String,
    val username: String? = null,
    val name: String,
    val photoUrl: String? = null,
)

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
    val updatedAt: Long = 0,
)

/** A friend together with their latest synced snapshot, if any. */
@Serializable
data class Friend(
    val id: String,
    val username: String? = null,
    val name: String,
    val photoUrl: String? = null,
    val snapshot: Snapshot? = null,
)

/** Unseen nudge delivered via GET /v1/friends. */
@Serializable
data class Nudge(
    val fromId: String,
    val name: String,
    val username: String? = null,
    val day: Long,
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
)

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
