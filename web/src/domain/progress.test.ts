import { describe, expect, it } from 'vitest'
import { dayOfWeek, epochDayOf, weekStart } from './dates'
import type { ExerciseEntity, SetEntity } from './entities'
import type { Muscle } from './muscle'
import {
  cappedSets, computeRecovery, recoveryWindowHours, sessionHits, computeStreak, effectiveSetEffort, fullTarget, levelFor, missedInARow, personalRecords, rankFor,
  recordScore, setsForXp, shouldRemind, totalXp, XP_PER_SET, XP_SET_CAP_FROM_DAY, weeksToLock, welcomeBack, type DayTag, type MuscleHit,
} from './progress'

// Hemo foundation: Mon, Tue, Wed, Fri, Sat
const hemoDays = new Set([1, 2, 3, 5, 6])
// 2026-10-04 is a Sunday; 2026-10-05 is a Monday.
const sun1 = epochDayOf(2026, 10, 4)
const mon1 = epochDayOf(2026, 10, 5)
const days = (...d: number[]) => new Set(d)

describe('StreakCalculatorTest', () => {
  it('lockedTargetsKeepPastWeeksAfterAPlanChange', () => {
    // Two full weeks on a Mon/Wed/Fri plan, then the plan grows to five days.
    const threeDays = days(1, 3, 5)
    const done = new Set([0, 1].flatMap((w) => [0, 2, 4].map((d) => mon1 + 7 * w + d)))
    const today = mon1 + 14
    const locked = weeksToLock(new Map(), fullTarget(threeDays, 0), mon1, today)
    expect(locked).toEqual(new Map([[sun1, 3], [sun1 + 7, 3]]))
    // Judged by the new plan, both past weeks would count as missed.
    expect(computeStreak(done, hemoDays, 0, mon1, today).weeks).toBe(0)
    const s = computeStreak(done, hemoDays, 0, mon1, today, undefined, locked)
    expect(s.weeks).toBe(2)
    expect(s.targetThisWeek).toBe(5)
    expect(weeksToLock(locked, 5, mon1, today).size).toBe(0)
  })

  it('fullWeeksCountAndCurrentWeekDoesNotBreakTheStreak', () => {
    const pattern = [0, 1, 2, 4, 5]
    const done = new Set([...pattern.map((d) => mon1 + d), ...pattern.map((d) => mon1 + 7 + d)])
    const today = mon1 + 14 // Monday of week 3, nothing done yet
    const s = computeStreak(done, hemoDays, 0, mon1, today)
    expect(s.weeks).toBe(2)
    expect(s.doneThisWeek).toBe(0)
    expect(s.targetThisWeek).toBe(5)
  })

  it('aShortWeekResetsTheStreak', () => {
    const done = days(mon1, mon1 + 1, mon1 + 2, mon1 + 4, mon1 + 5, mon1 + 7, mon1 + 8) // week 2 only 2/5
    const s = computeStreak(done, hemoDays, 0, mon1, mon1 + 14)
    expect(s.weeks).toBe(0)
    expect(s.best).toBe(1)
  })

  it('restDaysNeverCountAsMissed', () => {
    const today = mon1 + 3 // Thursday, a rest day in Hemo's plan
    const s = computeStreak(days(mon1, mon1 + 1, mon1 + 2), hemoDays, 0, mon1, today)
    // Strip is Sun..Sat: [0]=Sun (before startEpochDay), [1]=Mon, [4]=Thu, [5]=Fri.
    expect(s.days[0]).toBe('OFF')
    expect(s.days[4]).toBe('REST')
    expect(s.days[1]).toBe('DONE')
    expect(s.days[5]).toBe('UPCOMING')
  })

  it('joiningMidWeekIsProrated', () => {
    const thursday = mon1 + 3
    // Only Fri + Sat remain, so 2 workouts complete the first week.
    const s = computeStreak(days(mon1 + 4, mon1 + 5), hemoDays, 0, thursday, mon1 + 5)
    expect(s.targetThisWeek).toBe(2)
    expect(s.hitThisWeek).toBe(true)
    expect(s.weeks).toBe(1)
  })

  it('joiningAfterTheLastPlannedDayGivesAFreeWeek', () => {
    const mwf = days(1, 3, 5)
    const saturday = mon1 + 5
    const s = computeStreak(new Set(), mwf, 0, saturday, saturday)
    expect(s.freeWeek).toBe(true)
    expect(s.weeks).toBe(0)
    // ...and the free week neither breaks nor counts toward the next one.
    const nextWeek = new Set([1, 2, 3, 4, 5, 6, 7].map((i) => saturday + i).filter((d) => mwf.has(dayOfWeek(d))))
    const later = computeStreak(nextWeek, mwf, 0, saturday, saturday + 7)
    expect(later.weeks).toBe(1)
  })

  it('aWorkoutInTheFreeWeekStillCounts', () => {
    const mwf = days(1, 3, 5)
    const saturday = mon1 + 5
    const s = computeStreak(days(saturday), mwf, 0, saturday, saturday)
    expect(s.freeWeek).toBe(false)
    expect(s.hitThisWeek).toBe(true)
    expect(s.weeks).toBe(1)
  })

  it('sundayToSaturdayWeekCountsAsOneStreakWeek', () => {
    const planDays = days(7, 2, 4) // Sun, Tue, Thu
    const done = days(sun1, sun1 + 2, sun1 + 4)
    const inWeek = computeStreak(done, planDays, 0, sun1, sun1 + 6)
    expect(inWeek.doneThisWeek).toBe(3)
    expect(inWeek.hitThisWeek).toBe(true)
    expect(inWeek.days[0]).toBe('DONE')
    // Next Sunday starts a new week and banks week 1 in the streak.
    const nextWeek = computeStreak(done, planDays, 0, sun1, sun1 + 7)
    expect(nextWeek.weeks).toBe(1)
    expect(nextWeek.doneThisWeek).toBe(0)
  })

  it('overrideTargetWins', () => {
    const s = computeStreak(days(mon1, mon1 + 2, mon1 + 4), hemoDays, 3, mon1, mon1 + 5)
    expect(s.targetThisWeek).toBe(3)
    expect(s.hitThisWeek).toBe(true)
  })

  it('streakTargetWorksWithoutAPlan', () => {
    // Fresh quickStart: no active plan (empty plannedTrainingDays), weeklyTargetOverride = 3.
    const oneDone = computeStreak(days(mon1 + 2), new Set(), 3, mon1 + 2, mon1 + 2)
    expect(oneDone.doneThisWeek).toBe(1)
    expect(oneDone.targetThisWeek).toBe(3)
    expect(oneDone.hitThisWeek).toBe(false)

    const threeDone = computeStreak(days(mon1 + 2, mon1 + 4, mon1 + 5), new Set(), 3, mon1 + 2, mon1 + 5)
    expect(threeDone.doneThisWeek).toBe(3)
    expect(threeDone.targetThisWeek).toBe(3)
    expect(threeDone.hitThisWeek).toBe(true)
    expect(threeDone.weeks).toBe(1)
  })

  it('lastWeekStateReportsHitFreeMissOrNullForTheSharedStreak', () => {
    // Target 3, no plan. Joined Monday of week 1 (mon1).
    const firstWeek = computeStreak(days(mon1), new Set(), 3, mon1, mon1 + 2)
    expect(firstWeek.lastWeekState).toBeNull()
    const hitLast = computeStreak(days(mon1, mon1 + 1, mon1 + 2), new Set(), 3, mon1, mon1 + 9)
    expect(hitLast.lastWeekState).toBe('hit')
    const missLast = computeStreak(days(mon1), new Set(), 3, mon1, mon1 + 9)
    expect(missLast.lastWeekState).toBe('miss')
    // Every planned day of last week excused (sick) and no workout -> free.
    const excused = new Set([mon1, mon1 + 1, mon1 + 2, mon1 + 3, mon1 + 4, mon1 + 5])
    const freeLast = computeStreak(new Set(), new Set([1, 2, 3, 4, 5, 6]), 0, mon1 - 1, mon1 + 9, excused)
    expect(freeLast.lastWeekState).toBe('free')
  })
})

describe('XpTest', () => {
  it('levelsFollowTheCurve', () => {
    expect(levelFor(0).level).toBe(1)
    expect(levelFor(99).level).toBe(1)
    expect(levelFor(100).level).toBe(2)
    expect(levelFor(250).level).toBe(3)
    const l = levelFor(260)
    expect(l.xpIntoLevel).toBe(10)
    expect(l.xpForNextLevel).toBe(200)
  })

  it('ranksClimbWithLevel', () => {
    expect(rankFor(1).letter).toBe('E')
    expect(rankFor(5).letter).toBe('D')
    expect(rankFor(10).letter).toBe('C')
    expect(rankFor(20).letter).toBe('B')
    expect(rankFor(30).letter).toBe('A')
    expect(rankFor(45).letter).toBe('S')
  })

  it('totalAddsAllSources', () => {
    expect(totalXp(1, 10, 1, 1)).toBe(50 + 20 + 25 + 100)
  })

  it('setXpIsCappedAt25SetsFromTheCutOff', () => {
    const after = XP_SET_CAP_FROM_DAY
    const before = XP_SET_CAP_FROM_DAY - 1
    expect(XP_SET_CAP_FROM_DAY).toBe(epochDayOf(2026, 11, 1))
    expect(setsForXp(30, after) * XP_PER_SET).toBe(25 * XP_PER_SET)
    expect(setsForXp(30, before) * XP_PER_SET).toBe(30 * XP_PER_SET)
    expect(setsForXp(10, after)).toBe(10)
    expect(setsForXp(10, before)).toBe(10)
    expect(cappedSets(new Map([[1, 30], [2, 30], [3, 10]]), new Map([[1, after], [2, before], [3, after]]))).toBe(25 + 30 + 10)
  })
})

describe('RecoveryTest', () => {
  const hour = 3_600_000
  const now = 100 * hour

  it('fatigueFadesOverFortyEightHours', () => {
    const r = computeRecovery([['CHEST', now - 12 * hour, 1]], now)
    expect(r.get('CHEST')!.fatigue).toBeCloseTo(0.75, 3)
    expect(r.get('CHEST')!.hoursUntilReady).toBe(36)
  })

  it('oldSessionsAreFullyRecovered', () => {
    expect(computeRecovery([['QUADS', now - 61 * hour, 1]], now).size).toBe(0)
  })

  it('secondaryMusclesAreHalfAsTired', () => {
    expect(computeRecovery([['TRICEPS', now, 0.5]], now).get('TRICEPS')!.fatigue).toBeCloseTo(0.5, 3)
  })

  it('sessionsAddUpInsteadOfTheHardestWinning', () => {
    // 1.0 ten hours ago has 0.833 left when 0.5 more lands (capped at 1), then 2 h of clearing.
    const hits: MuscleHit[] = [['BICEPS', now - 2 * hour, 0.5], ['BICEPS', now - 10 * hour, 1]]
    expect(computeRecovery(hits, now).get('BICEPS')!.fatigue).toBeCloseTo(1 - 2 / 48, 3)
  })

  it('recoveryWindowDependsOnTheMuscle', () => {
    // Four hard sets on an exercise that only works the muscle, finished at hour 0.
    const fourHardSets = (muscle: Muscle, afterHours: number) => {
      const ex: ExerciseEntity = { id: 'ex', name: 'Ex', kind: 'REPS', primary: [muscle], secondary: [] }
      const sets: SetEntity[] = [0, 1, 2, 3].map((i) => ({
        id: i, sessionId: 1, exerciseId: 'ex', itemPosition: 0, block: 'Main', circuit: false, setIndex: i,
        targetLabel: '', reps: 10, seconds: null, weightKg: null, done: true, isPr: false, restSeconds: 60, note: '', effort: 'HARD',
      }))
      return computeRecovery(sessionHits(sets, new Map([['ex', ex]]), new Map([[1, 0]])), afterHours * hour).get(muscle)?.fatigue
    }
    for (const [muscle, window] of [['QUADS', 60], ['BICEPS', 48], ['ABS', 36]] as const) {
      expect(recoveryWindowHours(muscle)).toBe(window)
      expect(fourHardSets(muscle, window - 1) ?? 0).toBeGreaterThan(0)
      expect(fourHardSets(muscle, window)).toBeUndefined()
    }
    expect(recoveryWindowHours('ADDUCTORS')).toBe(48)
  })

  it('twoSessionsTwelveHoursApartNeedThirtySixHours', () => {
    const r = computeRecovery([['CHEST', now - 12 * hour, 0.5], ['CHEST', now, 0.5]], now).get('CHEST')!
    expect(r.fatigue).toBeCloseTo(0.75, 3)
    expect(r.hoursUntilReady).toBe(36)
  })
})

describe('RecordsTest', () => {
  it('weightedSetsCompareByEstimatedMax', () => {
    expect(recordScore('WEIGHTED', 5, null, 100)).toBeGreaterThan(recordScore('WEIGHTED', 8, null, 80))
  })

  it('holdsCompareBySeconds', () => {
    expect(recordScore('HOLD', null, 45, null)).toBe(45)
  })
})

describe('PersonalRecordsTest', () => {
  const prs = (...sessions: number[][]) =>
    new Set([...personalRecords('REPS', sessions.map((s) => s.map((reps) => ({ reps }))), (s) => s.reps, () => null, () => null)].map((s) => s.reps))

  it('firstSessionNeverCounts', () => {
    expect(prs([10, 12])).toEqual(new Set())
  })

  it('onlyImprovementsOverEverythingBeforeCount', () => {
    expect(prs([10, 12], [13, 11], [12], [15, 15])).toEqual(new Set([13, 15]))
  })

  it('equalToBestIsNotARecord', () => {
    expect(prs([12], [12], [11])).toEqual(new Set())
  })

  it('emptySessionsAreSkipped', () => {
    expect(prs([8], [], [9])).toEqual(new Set([9]))
  })
})

describe('ExcusedDaysTest', () => {
  it('excusedPlannedDayLowersTheTargetAndShowsAsExcused', () => {
    // Wednesday sick, four other planned days done: the week still counts.
    const done = days(mon1, mon1 + 1, mon1 + 4, mon1 + 5)
    const s = computeStreak(done, hemoDays, 0, mon1, mon1 + 5, days(mon1 + 2))
    expect(s.targetThisWeek).toBe(4)
    expect(s.hitThisWeek).toBe(true)
    expect(s.days[3]).toBe('EXCUSED')
  })

  it('fullyExcusedWeekNeitherCountsNorBreaks', () => {
    const week1 = [mon1, mon1 + 1, mon1 + 2, mon1 + 4, mon1 + 5]
    const sickWeek = new Set([7, 8, 9, 10, 11, 12, 13].map((d) => mon1 + d))
    const week3 = week1.map((d) => d + 14)
    const s = computeStreak(new Set([...week1, ...week3]), hemoDays, 0, mon1, mon1 + 20, sickWeek)
    expect(s.weeks).toBe(2)
  })

  it('remindersBackOffAfterThreeMissesAndExcusedDaysStayQuiet', () => {
    // Last workout Monday; Tue, Wed, Fri missed -> Saturday is the 4th planned day: no nudge.
    const sat = mon1 + 5
    const missed = missedInARow(sat, hemoDays, days(mon1), new Set(), mon1)
    expect(missed).toBe(3)
    expect(shouldRemind(true, false, false, missed)).toBe(false)
    // An excused Wednesday breaks the run.
    expect(missedInARow(sat, hemoDays, days(mon1), days(mon1 + 2), mon1)).toBe(1)
    expect(shouldRemind(true, false, true, 0)).toBe(false)
    expect(shouldRemind(true, false, false, 1)).toBe(true)
  })

  it('welcomeBackOnlyAfterSicknessInjuryOrBackOff', () => {
    const fri = mon1 + 4
    expect(welcomeBack(fri, days(mon1), new Map<number, DayTag>([[mon1 + 2, 'SICK']]), hemoDays, mon1)).toBe(true)
    expect(welcomeBack(fri, days(mon1), new Map<number, DayTag>([[mon1 + 1, 'TRAVEL'], [mon1 + 2, 'TRAVEL']]), hemoDays, mon1)).toBe(false)
    expect(welcomeBack(mon1 + 1, days(mon1), new Map(), hemoDays, mon1)).toBe(false)
  })
})

describe('web additions', () => {
  // Audit #8: recovery reads old notes with the same words as progression.
  it('recovery weighs a «صعب» note as hard and an «سهل» note as easy', () => {
    const pushUp: ExerciseEntity = { id: 'push_up', name: 'Push-up', kind: 'REPS', primary: ['CHEST'], secondary: [] }
    const set = (note: string, effort = ''): SetEntity => ({
      id: 0, sessionId: 1, exerciseId: 'push_up', itemPosition: 0, block: 'Main', circuit: false, setIndex: 0,
      targetLabel: '', reps: 10, seconds: null, weightKg: null, done: true, isPr: false, restSeconds: 60, note, effort,
    })
    expect(effectiveSetEffort(set(''), pushUp)).toBe(1)
    expect(effectiveSetEffort(set('صعب'), pushUp)).toBeCloseTo(1.15, 5)
    expect(effectiveSetEffort(set('سهل'), pushUp)).toBeCloseTo(0.8, 5)
    expect(effectiveSetEffort(set('صعب', 'OK'), pushUp)).toBe(1)
  })

  it('weeks start on Sunday', () => {
    expect(dayOfWeek(mon1)).toBe(1)
    expect(dayOfWeek(sun1)).toBe(7)
    expect(dayOfWeek(0)).toBe(4) // 1970-01-01 was a Thursday
    expect(weekStart(sun1)).toBe(sun1)
    expect(weekStart(mon1)).toBe(sun1)
  })
})

describe('audit 2026-10-24', () => {
  it('weighted pull-ups count the body, so +10 kg x 10 beats +15 kg x 1', () => {
    const tenReps = recordScore('WEIGHTED', 10, null, 10, 'weighted_pull_up')
    const single = recordScore('WEIGHTED', 1, null, 15, 'weighted_pull_up')
    expect(tenReps).toBeCloseTo(38.33, 1) // (75 + 10) x (1 + 10/30) - 75
    expect(tenReps).toBeGreaterThan(single)
    // a set with no added weight scores like a bodyweight set, not reps / 100
    expect(recordScore('WEIGHTED', 8, null, 0, 'weighted_pull_up')).toBeCloseTo(20, 1)
    // other weighted lifts are unchanged
    expect(recordScore('WEIGHTED', 5, null, 100)).toBeCloseTo(116.67, 1)
  })

  it('a one-day-a-week plan can still reach three misses in a row', () => {
    expect(missedInARow(30, days(6), days(), days(), 0)).toBeGreaterThanOrEqual(3)
  })
})
