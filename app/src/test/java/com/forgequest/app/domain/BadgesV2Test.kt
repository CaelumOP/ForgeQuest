package com.forgequest.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgesV2Test {
    private fun ok(id: String, s: BadgeStats) = Badges.byId.getValue(id).unlocked(s)

    @Test
    fun liftsUseBodyweightWhenKnown() {
        val s = BadgeStats(bodyKg = 80f, bestWeight = mapOf("bench_press" to 80f, "back_squat" to 120f, "deadlift" to 119f))
        assertTrue(ok("str_bench_1", s)); assertFalse(ok("str_bench_2", s))
        assertTrue(ok("str_squat_2", s)); assertFalse(ok("str_deadlift_2", s))
        assertTrue(ok("str_deadlift_1", s))
    }

    @Test
    fun liftsFallBackToFixedKgWithoutBodyweight() {
        val s = BadgeStats(bestWeight = mapOf("bench_press" to 60f, "deadlift" to 100f))
        assertTrue(ok("str_bench_1", s)); assertFalse(ok("str_bench_2", s))
        assertTrue(ok("str_deadlift_2", s)); assertFalse(ok("str_squat_1", s))
    }

    @Test
    fun lifetimeAndLoggingBadges() {
        assertFalse(ok("life_sets_500", BadgeStats(lifetimeSets = 499)))
        assertTrue(ok("life_sets_500", BadgeStats(lifetimeSets = 500)))
        assertTrue(ok("life_hours_50", BadgeStats(lifetimeHours = 50)))
        assertFalse(ok("life_kg_100000", BadgeStats(lifetimeKg = 99_999.0)))
        assertTrue(ok("life_kg_100000", BadgeStats(lifetimeKg = 100_000.0)))
        assertTrue(ok("weigh_streak_7", BadgeStats(weighInRun = 7))); assertFalse(ok("weigh_streak_30", BadgeStats(weighInRun = 29)))
        assertTrue(ok("log_days_30", BadgeStats(mealDays = 30))); assertFalse(ok("log_days_100", BadgeStats(mealDays = 99)))
        assertTrue(ok("kcal_days_10", BadgeStats(kcalGoalDays = 10))); assertFalse(ok("kcal_days_30", BadgeStats(kcalGoalDays = 10)))
    }

    @Test
    fun runsAndGoalDays() {
        assertEquals(3, Badges.longestRun(listOf(5L, 1L, 2L, 3L, 3L, 9L)))
        assertEquals(0, Badges.longestRun(emptyList()))
        assertEquals(2, Badges.kcalGoalDays(mapOf(1L to 2000, 2L to 2400, 3L to 1900, 4L to 1500), 2000 + 0).let { it })
    }

    @Test
    fun progressShowsCounts() {
        assertEquals(12 to 30, Badges.progress("log_days_30", BadgeStats(mealDays = 12)))
        assertEquals(500 to 500, Badges.progress("life_sets_500", BadgeStats(lifetimeSets = 800)))
        assertEquals(null, Badges.progress("str_bench_1", BadgeStats()))
    }

    @Test
    fun backfillIsSilentOnlyForNewIdsAndOnlyOnce() {
        val s = BadgeStats(workouts = 12, mealsLogged = 1, lifetimeSets = 600)
        val fresh = Badges.all.filter { it.unlocked(s) }
        val (silent, loud) = Badges.splitBackfill(fresh, backfill = true)
        assertEquals(setOf("life_sets_500"), silent.map { it.id }.toSet())
        assertTrue(loud.isNotEmpty() && loud.none { it.id in Badges.V2_IDS })
        val (silent2, loud2) = Badges.splitBackfill(fresh, backfill = false)
        assertTrue(silent2.isEmpty()); assertEquals(fresh, loud2)
    }

    @Test
    fun everyNewIdHasADefinition() {
        assertTrue(Badges.V2_IDS.all { it in Badges.byId })
        assertEquals(15, Badges.V2_IDS.size)
    }
}
