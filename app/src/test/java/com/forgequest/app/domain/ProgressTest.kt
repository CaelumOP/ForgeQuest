package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {
    // Hemo foundation: Mon, Tue, Wed, Fri, Sat
    private val hemoDays = setOf(1, 2, 3, 5, 6)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    // 2026-10-05 is a Monday.
    private val mon1 = day(2026, 10, 5)

    @Test
    fun fullWeeksCountAndCurrentWeekDoesNotBreakTheStreak() {
        val done = (0..4).map { mon1 + listOf(0, 1, 2, 4, 5)[it] } + (0..4).map { mon1 + 7 + listOf(0, 1, 2, 4, 5)[it] }
        val today = mon1 + 14 // Monday of week 3, nothing done yet
        val s = StreakCalculator.compute(done.toSet(), hemoDays, 0, mon1, today)
        assertEquals(2, s.weeks)
        assertEquals(0, s.doneThisWeek)
        assertEquals(5, s.targetThisWeek)
    }

    @Test
    fun aShortWeekResetsTheStreak() {
        val done = setOf(mon1, mon1 + 1, mon1 + 2, mon1 + 4, mon1 + 5, mon1 + 7, mon1 + 8) // week 2 only 2/5
        val s = StreakCalculator.compute(done, hemoDays, 0, mon1, mon1 + 14)
        assertEquals(0, s.weeks)
        assertEquals(1, s.best)
    }

    @Test
    fun restDaysNeverCountAsMissed() {
        val today = mon1 + 3 // Thursday, a rest day in Hemo's plan
        val s = StreakCalculator.compute(setOf(mon1, mon1 + 1, mon1 + 2), hemoDays, 0, mon1, today)
        assertEquals(DayState.REST, s.days[3])
        assertEquals(DayState.DONE, s.days[0])
        assertEquals(DayState.UPCOMING, s.days[4])
    }

    @Test
    fun joiningMidWeekIsProrated() {
        val thursday = mon1 + 3
        // Only Fri + Sat remain, so 2 workouts complete the first week.
        val s = StreakCalculator.compute(setOf(mon1 + 4, mon1 + 5), hemoDays, 0, thursday, mon1 + 6)
        assertEquals(2, s.targetThisWeek)
        assertTrue(s.hitThisWeek)
        assertEquals(1, s.weeks)
    }

    @Test
    fun joiningAfterTheLastPlannedDayGivesAFreeWeek() {
        val sunday = mon1 + 6
        val s = StreakCalculator.compute(emptySet(), hemoDays, 0, sunday, sunday)
        assertTrue(s.freeWeek)
        assertEquals(0, s.weeks)
        // ...and the free week neither breaks nor counts toward the next one.
        val nextWeek = (1..7).map { sunday + it }.filter { Dates.dayOfWeek(it) in hemoDays }.toSet()
        val later = StreakCalculator.compute(nextWeek, hemoDays, 0, sunday, sunday + 7)
        assertEquals(1, later.weeks)
    }

    @Test
    fun aWorkoutInTheFreeWeekStillCounts() {
        val sunday = mon1 + 6
        val s = StreakCalculator.compute(setOf(sunday), hemoDays, 0, sunday, sunday)
        assertFalse(s.freeWeek)
        assertTrue(s.hitThisWeek)
        assertEquals(1, s.weeks)
    }

    @Test
    fun overrideTargetWins() {
        val s = StreakCalculator.compute(setOf(mon1, mon1 + 2, mon1 + 4), hemoDays, 3, mon1, mon1 + 6)
        assertEquals(3, s.targetThisWeek)
        assertTrue(s.hitThisWeek)
    }
}

class XpTest {
    @Test
    fun levelsFollowTheCurve() {
        assertEquals(1, Xp.levelFor(0).level)
        assertEquals(1, Xp.levelFor(99).level)
        assertEquals(2, Xp.levelFor(100).level)
        assertEquals(3, Xp.levelFor(250).level)
        val l = Xp.levelFor(260)
        assertEquals(10, l.xpIntoLevel)
        assertEquals(200, l.xpForNextLevel)
    }

    @Test
    fun ranksClimbWithLevel() {
        assertEquals("E", Xp.rankFor(1).letter)
        assertEquals("D", Xp.rankFor(5).letter)
        assertEquals("C", Xp.rankFor(10).letter)
        assertEquals("B", Xp.rankFor(20).letter)
        assertEquals("A", Xp.rankFor(30).letter)
        assertEquals("S", Xp.rankFor(45).letter)
    }

    @Test
    fun totalAddsAllSources() {
        assertEquals(50 + 20 + 25 + 100, Xp.total(workouts = 1, doneSets = 10, prs = 1, badges = 1))
    }
}

class RecoveryTest {
    private val hour = 3_600_000L

    @Test
    fun fatigueFadesOverFortyEightHours() {
        val now = 100 * hour
        val r = Recovery.compute(listOf(Triple(Muscle.CHEST, now - 12 * hour, 1f)), now)
        assertEquals(0.75f, r.getValue(Muscle.CHEST).fatigue, 0.001f)
        assertEquals(36, r.getValue(Muscle.CHEST).hoursUntilReady)
    }

    @Test
    fun oldSessionsAreFullyRecovered() {
        val now = 100 * hour
        assertTrue(Recovery.compute(listOf(Triple(Muscle.QUADS, now - 49 * hour, 1f)), now).isEmpty())
    }

    @Test
    fun secondaryMusclesAreHalfAsTired() {
        val now = 100 * hour
        val r = Recovery.compute(listOf(Triple(Muscle.TRICEPS, now, 0.5f)), now)
        assertEquals(0.5f, r.getValue(Muscle.TRICEPS).fatigue, 0.001f)
    }

    @Test
    fun freshestHardestHitWins() {
        val now = 100 * hour
        val r = Recovery.compute(
            listOf(Triple(Muscle.BICEPS, now - 2 * hour, 0.5f), Triple(Muscle.BICEPS, now - 10 * hour, 1f)),
            now,
        )
        assertEquals(1f - 10f / 48f, r.getValue(Muscle.BICEPS).fatigue, 0.001f)
    }
}

class RecordsTest {
    @Test
    fun weightedSetsCompareByEstimatedMax() {
        val heavy = Records.score(ExerciseKind.WEIGHTED, reps = 5, seconds = null, weightKg = 100f)
        val light = Records.score(ExerciseKind.WEIGHTED, reps = 8, seconds = null, weightKg = 80f)
        assertTrue(heavy > light)
    }

    @Test
    fun holdsCompareBySeconds() {
        assertEquals(45f, Records.score(ExerciseKind.HOLD, null, 45, null))
    }
}

class BadgesTest {
    @Test
    fun badgeRulesReadStats() {
        val stats = BadgeStats(workouts = 10, bestReps = mapOf("pull_up" to 10), bestSeconds = mapOf("dead_hang" to 60))
        val unlocked = Badges.all.filter { it.unlocked(stats) }.map { it.id }.toSet()
        assertTrue("first_workout" in unlocked)
        assertTrue("workouts_10" in unlocked)
        assertTrue("str_pull_10" in unlocked)
        assertTrue("skill_dead_hang" in unlocked)
        assertFalse("workouts_50" in unlocked)
    }

    @Test
    fun badgeIdsAreUnique() {
        assertEquals(Badges.all.size, Badges.all.map { it.id }.toSet().size)
    }
}

class PersonalRecordsTest {
    private data class S(val reps: Int)
    private fun prs(vararg sessions: List<Int>) =
        Records.personalRecords(ExerciseKind.REPS, sessions.map { it.map(::S) }, { it.reps }, { null }, { null }).map { it.reps }.toSet()

    @Test
    fun firstSessionNeverCounts() {
        assertEquals(emptySet<Int>(), prs(listOf(10, 12)))
    }

    @Test
    fun onlyImprovementsOverEverythingBeforeCount() {
        assertEquals(setOf(13, 15), prs(listOf(10, 12), listOf(13, 11), listOf(12), listOf(15, 15)))
    }

    @Test
    fun equalToBestIsNotARecord() {
        assertEquals(emptySet<Int>(), prs(listOf(12), listOf(12), listOf(11)))
    }

    @Test
    fun emptySessionsAreSkipped() {
        assertEquals(setOf(9), prs(listOf(8), emptyList(), listOf(9)))
    }
}

class ExcusedDaysTest {
    private val hemoDays = setOf(1, 2, 3, 5, 6)
    private val mon1 = LocalDate.of(2026, 10, 5).toEpochDay()

    @Test
    fun excusedPlannedDayLowersTheTargetAndShowsAsExcused() {
        // Wednesday sick, four other planned days done: the week still counts.
        val done = setOf(mon1, mon1 + 1, mon1 + 4, mon1 + 5)
        val s = StreakCalculator.compute(done, hemoDays, 0, mon1, mon1 + 6, excusedDays = setOf(mon1 + 2))
        assertEquals(4, s.targetThisWeek)
        assertTrue(s.hitThisWeek)
        assertEquals(DayState.EXCUSED, s.days[2])
    }

    @Test
    fun fullyExcusedWeekNeitherCountsNorBreaks() {
        val week1 = setOf(mon1, mon1 + 1, mon1 + 2, mon1 + 4, mon1 + 5)
        val sickWeek = (mon1 + 7..mon1 + 13).toSet()
        val week3 = week1.map { it + 14 }.toSet()
        val s = StreakCalculator.compute(week1 + week3, hemoDays, 0, mon1, mon1 + 20, excusedDays = sickWeek)
        assertEquals(2, s.weeks)
    }

    @Test
    fun remindersBackOffAfterThreeMissesAndExcusedDaysStayQuiet() {
        // Last workout Monday; Tue, Wed, Fri missed -> Saturday is the 4th planned day: no nudge.
        val sat = mon1 + 5
        val missed = Nudges.missedInARow(sat, hemoDays, setOf(mon1), emptySet(), mon1)
        assertEquals(3, missed)
        assertFalse(Nudges.shouldRemind(plannedToday = true, doneToday = false, excusedToday = false, missedInARow = missed))
        // An excused Wednesday breaks the run.
        assertEquals(1, Nudges.missedInARow(sat, hemoDays, setOf(mon1), setOf(mon1 + 2), mon1))
        assertFalse(Nudges.shouldRemind(plannedToday = true, doneToday = false, excusedToday = true, missedInARow = 0))
        assertTrue(Nudges.shouldRemind(plannedToday = true, doneToday = false, excusedToday = false, missedInARow = 1))
    }

    @Test
    fun welcomeBackOnlyAfterSicknessInjuryOrBackOff() {
        val fri = mon1 + 4
        assertTrue(Nudges.welcomeBack(fri, setOf(mon1), mapOf(mon1 + 2 to DayTag.SICK), hemoDays, mon1))
        assertFalse(Nudges.welcomeBack(fri, setOf(mon1), mapOf(mon1 + 1 to DayTag.TRAVEL, mon1 + 2 to DayTag.TRAVEL), hemoDays, mon1))
        assertFalse(Nudges.welcomeBack(mon1 + 1, setOf(mon1), emptyMap(), hemoDays, mon1))
    }
}
