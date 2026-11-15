package com.forgequest.app.domain

import com.forgequest.app.data.friends.FriendsView
import com.forgequest.app.data.friends.Pr
import com.forgequest.app.data.friends.Snapshot
import java.util.Locale

enum class FriendBadge {
    TRAINED,
    ON_TRACK,
    SLACKING,
    REST,
    NOT_SYNCED;

    companion object {
        fun fromWire(raw: String): FriendBadge = when (raw) {
            ForgeScore.TRAINED -> TRAINED
            ForgeScore.ON_TRACK -> ON_TRACK
            ForgeScore.SLACKING -> SLACKING
            ForgeScore.REST -> REST
            else -> NOT_SYNCED
        }
    }
}

data class LeaderboardEntry(
    val id: String,
    val name: String,
    val photoUrl: String?,
    val isMe: Boolean,
    val score: Int,
    val badge: FriendBadge,
    val done: Int,
    val due: Int,
    val proteinDays: Int,
    val streakWeeks: Int,
    val prs: List<Pr>,
    val syncedThisWeek: Boolean,
)

object FriendsLeaderboard {

    /**
     * Builds the Friends leaderboard for [view] including `ME` (from [meSnapshot], with `isMe = true`).
     * Friends whose snapshot is absent or from an earlier week (`snapshot.weekStart < view.weekStart`) show
     * score 0 and a neutral [FriendBadge.NOT_SYNCED] state (never [FriendBadge.SLACKING]). Sorted by score
     * descending, then name ascending.
     */
    fun build(view: FriendsView, meSnapshot: Snapshot?): List<LeaderboardEntry> {
        val entries = buildList {
            for (f in view.friends) {
                if (f.id == view.me.id) continue
                add(toEntry(f.id, f.name, f.photoUrl, isMe = false, snapshot = f.snapshot, weekStart = view.weekStart))
            }
            add(
                toEntry(
                    id = view.me.id,
                    name = view.me.name,
                    photoUrl = view.me.photoUrl,
                    isMe = true,
                    snapshot = meSnapshot,
                    weekStart = view.weekStart,
                ),
            )
        }
        return entries.sortedWith(
            compareByDescending<LeaderboardEntry> { it.score }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.name }
                .thenByDescending { it.isMe }
                .thenBy { it.id },
        )
    }

    internal fun toEntry(
        id: String,
        name: String,
        photoUrl: String?,
        isMe: Boolean,
        snapshot: Snapshot?,
        weekStart: Long,
    ): LeaderboardEntry {
        val synced = snapshot != null && snapshot.weekStart >= weekStart
        return if (!synced) {
            LeaderboardEntry(
                id = id,
                name = name,
                photoUrl = photoUrl,
                isMe = isMe,
                score = 0,
                badge = FriendBadge.NOT_SYNCED,
                done = 0,
                due = 0,
                proteinDays = 0,
                streakWeeks = 0,
                prs = emptyList(),
                syncedThisWeek = false,
            )
        } else {
            LeaderboardEntry(
                id = id,
                name = name,
                photoUrl = photoUrl,
                isMe = isMe,
                score = snapshot!!.score,
                badge = FriendBadge.fromWire(snapshot.badge),
                done = snapshot.done,
                due = snapshot.due,
                proteinDays = snapshot.proteinDays,
                streakWeeks = snapshot.streakWeeks,
                prs = snapshot.prs.orEmpty(),
                syncedThisWeek = true,
            )
        }
    }
}
