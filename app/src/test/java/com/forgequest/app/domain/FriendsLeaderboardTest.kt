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
        username = "tariq",
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
                Friend("f_stale", name = "Noor", snapshot = snap(week = weekStart - 7, score = 95, badge = ForgeScore.SLACKING, prs = listOf(Pr("Squat", 100.0, 5)))),
                Friend("f_missing", name = "Karim", snapshot = null),
                Friend("f_current", name = "Omar", snapshot = snap(week = weekStart, score = 70, badge = ForgeScore.SLACKING)),
            ),
        )

        val board = FriendsLeaderboard.build(view, meSnapshot = snap(week = weekStart, score = 90, badge = ForgeScore.TRAINED))

        assertEquals(listOf("tariq", "Omar", "Karim", "Noor"), board.map { it.name })
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
                Friend("f1", name = "Zaid", snapshot = snap(score = 85, badge = ForgeScore.ON_TRACK)),
                Friend("f2", name = "lina", snapshot = snap(score = 85, badge = ForgeScore.REST)),
                Friend("f3", name = "Adam", snapshot = snap(score = 100, badge = ForgeScore.TRAINED)),
            ),
        )

        val board = FriendsLeaderboard.build(view, meSnapshot = snap(score = 85, badge = ForgeScore.TRAINED))

        assertEquals(listOf("Adam", "lina", "tariq", "Zaid"), board.map { it.name })
        assertEquals(listOf(100, 85, 85, 85), board.map { it.score })
    }

    @Test
    fun includesMeHighlightedAndNeutralisesStaleMeSnapshot() {
        val view = FriendsView(
            me = me,
            today = weekStart + 1,
            weekStart = weekStart,
            friends = listOf(
                Friend("me", name = "Tariq", snapshot = snap(score = 100, badge = ForgeScore.TRAINED)),
                Friend("f1", name = "Omar", snapshot = snap(score = 60, badge = ForgeScore.ON_TRACK)),
            ),
        )

        val board = FriendsLeaderboard.build(view, meSnapshot = snap(week = weekStart - 7, score = 100, badge = ForgeScore.SLACKING))

        assertEquals(2, board.size)
        val meEntry = board.single { it.isMe }
        assertEquals("me", meEntry.id)
        assertEquals(0, meEntry.score)
        assertEquals(FriendBadge.NOT_SYNCED, meEntry.badge)
        assertFalse(meEntry.syncedThisWeek)
        assertEquals(listOf("Omar", "tariq"), board.map { it.name })
    }

    @Test
    fun effectiveWeekStateMarksHitWhenTargetMet() {
        assertEquals("hit", FriendsLeaderboard.effectiveWeekState(weekState = null, done = 3, due = 3))
        assertEquals("hit", FriendsLeaderboard.effectiveWeekState(weekState = "open", done = 4, due = 3))
        assertEquals("open", FriendsLeaderboard.effectiveWeekState(weekState = "open", done = 2, due = 3))
        assertEquals("free", FriendsLeaderboard.effectiveWeekState(weekState = "free", done = 0, due = 3))
        assertEquals(null, FriendsLeaderboard.effectiveWeekState(weekState = null, done = 0, due = 0))
    }

    @Test
    fun englishOrdinalSuffixHandlesTeensHundredsAndStandardCases() {
        val expected = mapOf(
            1 to "st",
            2 to "nd",
            3 to "rd",
            4 to "th",
            5 to "th",
            10 to "th",
            11 to "th",
            12 to "th",
            13 to "th",
            14 to "th",
            20 to "th",
            21 to "st",
            22 to "nd",
            23 to "rd",
            24 to "th",
            100 to "th",
            101 to "st",
            102 to "nd",
            103 to "rd",
            111 to "th",
            112 to "th",
            113 to "th",
            121 to "st",
            122 to "nd",
            123 to "rd",
        )
        for ((rank, suffix) in expected) {
            assertEquals("suffix for $rank", suffix, FriendsLeaderboard.ordinalSuffix(rank))
            assertEquals("ordinal for $rank", "$rank$suffix", FriendsLeaderboard.ordinal(rank))
        }
    }
}
