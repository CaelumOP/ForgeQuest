import { describe, expect, it } from 'vitest'
import { dayOfWeek, epochDayOf } from './dates'
import type { ExerciseEntity, SetEntity } from './entities'
import {
  computeRecovery, computeStreak, effectiveSetEffort, fullTarget, levelFor, missedInARow, personalRecords, rankFor,
  recordScore, shouldRemind, totalXp, weeksToLock, welcomeBack, type DayTag, type MuscleHit,
} from './progress'

// Hemo foundation: Mon, Tue, Wed, Fri, Sat
const hemoDays = new Set([1, 2, 3, 5, 6])
// 2026-10-05 is a Monday.
const mon1 = epochDayOf(2026, 10, 5)
const days = (...d: number[]) => new Set(d)

describe('StreakCalculatorTest', () => {
  it('lockedTargetsKeepPastWeeksAfterAPlanChange', () => {
    // Two full weeks on a Mon/Wed/Fri plan, then the plan grows to five days.
    const threeDays = days(1, 3, 5)
    const done = new Set([0, 1].flatMap((w) => [0, 2, 4].map((d) => mon1 + 7 * w + d)))
    const today = mon1 + 14
    const locked = weeksToLock(new Map(), fullTarget(threeDays, 0), mon1, today)
    expect(locked).toEqual(new Map([[mon1, 3], [mon1 + 7, 3]]))
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
    expect(s.days[3]).toBe('REST')
    expect(s.days[0]).toBe('DONE')
    expect(s.days[4]).toBe('UPCOMING')
  })

  it('joiningMidWeekIsProrated', () => {
    const thursday = mon1 + 3
    // Only Fri + Sat remain, so 2 workouts complete the first week.
    const s = computeStreak(days(mon1 + 4, mon1 + 5), hemoDays, 0, thursday, mon1 + 6)
    expect(s.targetThisWeek).toBe(2)
    expect(s.hitThisWeek).toBe(true)
    expect(s.weeks).toBe(1)
  })

  it('joiningAfterTheLastPlannedDayGivesAFreeWeek', () => {
    const sunday = mon1 + 6
    const s = computeStreak(new Set(), hemoDays, 0, sunday, sunday)
    expect(s.freeWeek).toBe(true)
    expect(s.weeks).toBe(0)
    // ...and the free week neither breaks nor counts toward the next one.
    const nextWeek = new Set([1, 2, 3, 4, 5, 6, 7].map((i) => sunday + i).filter((d) => hemoDays.has(dayOfWeek(d))))
    const later = computeStreak(nextWeek, hemoDays, 0, sunday, sunday + 7)
    expect(later.weeks).toBe(1)
  })

  it('aWorkoutInTheFreeWeekStillCounts', () => {
    const sunday = mon1 + 6
    const s = computeStreak(days(sunday), hemoDays, 0, sunday, sunday)
    expect(s.freeWeek).toBe(false)
    expect(s.hitThisWeek).toBe(true)
    expect(s.weeks).toBe(1)
  })

  it('overrideTargetWins', () => {
    const s = computeStreak(days(mon1, mon1 + 2, mon1 + 4), hemoDays, 3, mon1, mon1 + 6)
    expect(s.targetThisWeek).toBe(3)
    expect(s.hitThisWeek).toBe(true)
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
    expect(computeRecovery([['QUADS', now - 49 * hour, 1]], now).size).toBe(0)
  })

  it('secondaryMusclesAreHalfAsTired', () => {
    expect(computeRecovery([['TRICEPS', now, 0.5]], now).get('TRICEPS')!.fatigue).toBeCloseTo(0.5, 3)
  })

  it('sessionsAddUpInsteadOfTheHardestWinning', () => {
    // 1.0 ten hours ago has 0.833 left when 0.5 more lands (capped at 1), then 2 h of clearing.
    const hits: MuscleHit[] = [['BICEPS', now - 2 * hour, 0.5], ['BICEPS', now - 10 * hour, 1]]
    expect(computeRecovery(hits, now).get('BICEPS')!.fatigue).toBeCloseTo(1 - 2 / 48, 3)
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
    const s = computeStreak(done, hemoDays, 0, mon1, mon1 + 6, days(mon1 + 2))
    expect(s.targetThisWeek).toBe(4)
    expect(s.hitThisWeek).toBe(true)
    expect(s.days[2]).toBe('EXCUSED')
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

  it('weeks start on Monday', () => {
    expect(dayOfWeek(mon1)).toBe(1)
    expect(dayOfWeek(0)).toBe(4) // 1970-01-01 was a Thursday
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
