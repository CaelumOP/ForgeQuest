// Days are epoch days: whole days since 1970-01-01 (a Thursday), like Kotlin's LocalDate.toEpochDay().

const MS_PER_DAY = 86_400_000

/** 1 = Monday .. 7 = Sunday */
export function dayOfWeek(epochDay: number): number {
  return ((((epochDay + 3) % 7) + 7) % 7) + 1
}

/** Monday of the week containing [epochDay]. */
export function weekStart(epochDay: number): number {
  return epochDay - (dayOfWeek(epochDay) - 1)
}

/** Epoch day of a calendar date (month 1..12). */
export function epochDayOf(year: number, month: number, day: number): number {
  return Date.UTC(year, month - 1, day) / MS_PER_DAY
}

/** Edge: today's epoch day in the phone's own time zone. The only place that reads the clock. */
export function todayEpochDay(now: Date = new Date()): number {
  return epochDayOf(now.getFullYear(), now.getMonth() + 1, now.getDate())
}
