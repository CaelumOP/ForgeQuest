package com.forgequest.app.domain

import com.forgequest.app.data.friends.Friend
import com.forgequest.app.data.friends.FriendsView
import com.forgequest.app.data.friends.Me
import com.forgequest.app.data.friends.Pr
import com.forgequest.app.data.friends.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsLeaderboardTest {

    private val weekStart = 20_000L
    private val me = Me(
        id = "me",
        code = "FQ2345",
        name = "Tariq",
        photoUrl = null,
        sharePhoto = true,
        sharePrs = true,
        joined = true,
    )

    private fun snap(
        week: Long = weekStart,
        score: Int = 80,
        badge: String = ForgeScore.ON_TRACK,
        prs: List<Pr>? = emptyList(),
    ) = Snapshot(
        weekStart = week,
        done = 2,
        due = 3,
        score = score,
        badge = badge,
        streakWeeks = 3,
        proteinDays = 4,
        prs = prs,
    )

    @Test
    fun staleAndMissingSnapshotsNeutralisedNeverSlacking() {
        val view = FriendsView(
            me = me,
            today = weekStart + 3,
            weekStart = weekStart,
            friends = listOf(
                Friend("f_stale", "Noor", snapshot = snap(week = weekStart - 7, score = 95, badge = ForgeScore.SLACKING, prs = listOf(Pr("Squat", 100.0, 5)))),
                Friend("f_missing", "Karim", snapshot = null),
                Friend("f_current", "Omar", snapshot = snap(week = weekStart, score = 70, badge = ForgeScore.SLACKING)),
            ),
        )

        val board = FriendsLeaderboard.build(view, meSnapshot = snap(week = weekStart, score = 90, badge = ForgeScore.TRAINED))

        assertEquals(listOf("Tariq", "Omar", "Karim", "Noor"), board.map { it.name })
        val noor = board.first { it.id == "f_stale" }
        assertEquals(0, noor.score)
        assertEquals(FriendBadge.NOT_SYNCED, noor.badge)
        assertFalse(noor.syncedThisWeek)
        assertTrue(noor.prs.isEmpty())

        val karim = board.first { it.id == "f_missing" }
        assertEquals(0, karim.score)
        assertEquals(FriendBadge.NOT_SYNCED, karim.badge)
        assertFalse(karim.syncedThisWeek)

        val omar = board.first { it.id == "f_current" }
        assertEquals(70, omar.score)
        assertEquals(FriendBadge.SLACKING, omar.badge)
        assertTrue(omar.syncedThisWeek)
    }

    @Test
    fun sortsByScoreDescendingThenNameCaseInsensitive() {
        val view = FriendsView(
            me = me,
            today = weekStart + 2,
            weekStart = weekStart,
            friends = listOf(
                Friend("f1", "Zaid", snapshot = snap(score = 85, badge = ForgeScore.ON_TRACK)),
                Friend("f2", "lina", snapshot = snap(score = 85, badge = ForgeScore.REST)),
                Friend("f3", "Adam", snapshot = snap(score = 100, badge = ForgeScore.TRAINED)),
            ),
        )

        val board = FriendsLeaderboard.build(view, meSnapshot = snap(score = 85, badge = ForgeScore.TRAINED))

        assertEquals(listOf("Adam", "lina", "Tariq", "Zaid"), board.map { it.name })
        assertEquals(listOf(100, 85, 85, 85), board.map { it.score })
    }

    @Test
    fun includesMeHighlightedAndNeutralisesStaleMeSnapshot() {
        val view = FriendsView(
            me = me,
            today = weekStart + 1,
            weekStart = weekStart,
            friends = listOf(
                Friend("me", "Tariq", snapshot = snap(score = 100, badge = ForgeScore.TRAINED)),
                Friend("f1", "Omar", snapshot = snap(score = 60, badge = ForgeScore.ON_TRACK)),
            ),
        )

        val board = FriendsLeaderboard.build(view, meSnapshot = snap(week = weekStart - 7, score = 100, badge = ForgeScore.SLACKING))

        assertEquals(2, board.size)
        val meEntry = board.single { it.isMe }
        assertEquals("me", meEntry.id)
        assertEquals(0, meEntry.score)
        assertEquals(FriendBadge.NOT_SYNCED, meEntry.badge)
        assertFalse(meEntry.syncedThisWeek)
        assertEquals(listOf("Omar", "Tariq"), board.map { it.name })
    }
}
