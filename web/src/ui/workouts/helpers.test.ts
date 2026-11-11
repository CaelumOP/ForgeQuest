import { describe, expect, it } from 'vitest'
import type { ExerciseRow } from '../../data/db'
import { dayName, dayShort, filterExercises, fitExerciseRows, formVideoUrl } from './helpers'

const ex = (id: string, name: string, primary: ExerciseRow['primary'], secondary: ExerciseRow['secondary'] = []): ExerciseRow =>
  ({ id, name, kind: 'REPS', primary, secondary, videoUrl: null, custom: false })

describe('workouts helpers', () => {
  it('names weekdays from Monday', () => {
    expect(dayName(1, 'en')).toBe('Monday')
    expect(dayName(7, 'en', 'short')).toBe('Sun')
    expect(dayShort(2, 'ar')).toBe('ثلاثاء')
    expect(dayShort(3, 'ar')).toBe('أربعاء')
    expect(dayShort(1, 'en')).toBe('Mon')
  })
  it('falls back to a YouTube search for a missing or bad link', () => {
    expect(formVideoUrl({ name: 'Push up', videoUrl: 'https://youtu.be/x' })).toBe('https://youtu.be/x')
    expect(formVideoUrl({ name: 'Push up', videoUrl: 'nope' })).toBe('https://www.youtube.com/results?search_query=Push%20up%20proper%20form')
  })
  it('filters by name and muscle, primary matches first', () => {
    const lib = [ex('b', 'Bridge', ['GLUTES'], ['ABS']), ex('a', 'Crunch', ['ABS']), ex('c', 'Row', ['UPPER_BACK'])]
    const all = new Set(['BODYWEIGHT', 'PULL_UP_BAR', 'DIP_BARS_OR_BENCH', 'DUMBBELLS', 'KETTLEBELL', 'BARBELL_RACK', 'CABLES_MACHINES'] as const)
    expect(filterExercises(lib, { query: '', muscle: 'ABS', equipment: all }).map((e) => e.id)).toEqual(['a', 'b'])
    expect(filterExercises(lib, { query: 'ro', muscle: null, equipment: all }).map((e) => e.id)).toEqual(['c'])
  })
  it('fits exercise cells into 2 rows by default, growing to 3 or 4 rows on tall screens, with +N more', () => {
    expect(fitExerciseRows(300, 0)).toEqual({ rows: 2, visibleCount: 0, hiddenCount: 0 })
    // Default 2-row height (92px = 2 x 46px = 4 cells): up to 4 fit, >4 reserves 4th cell for "+N more"
    expect(fitExerciseRows(92, 4)).toEqual({ rows: 2, visibleCount: 4, hiddenCount: 0 })
    expect(fitExerciseRows(92, 6)).toEqual({ rows: 2, visibleCount: 3, hiddenCount: 3 })
    expect(fitExerciseRows(137, 8)).toEqual({ rows: 2, visibleCount: 3, hiddenCount: 5 })
    // Tall screen with 3 rows available (138px..183px = 6 cells)
    expect(fitExerciseRows(138, 6)).toEqual({ rows: 3, visibleCount: 6, hiddenCount: 0 })
    expect(fitExerciseRows(160, 8)).toEqual({ rows: 3, visibleCount: 5, hiddenCount: 3 })
    // Tall screen with 4 rows available (>=184px = 8 cells, capped at 4 rows)
    expect(fitExerciseRows(190, 8)).toEqual({ rows: 4, visibleCount: 8, hiddenCount: 0 })
    expect(fitExerciseRows(190, 12)).toEqual({ rows: 4, visibleCount: 7, hiddenCount: 5 })
    expect(fitExerciseRows(310, 12)).toEqual({ rows: 4, visibleCount: 7, hiddenCount: 5 })
    // When itemCount <= 4 on a tall screen, card stays at 2 rows
    expect(fitExerciseRows(310, 4)).toEqual({ rows: 2, visibleCount: 4, hiddenCount: 0 })
  })
})
