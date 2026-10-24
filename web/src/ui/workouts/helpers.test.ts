import { describe, expect, it } from 'vitest'
import type { ExerciseRow } from '../../data/db'
import { dayName, filterExercises, formVideoUrl } from './helpers'

const ex = (id: string, name: string, primary: ExerciseRow['primary'], secondary: ExerciseRow['secondary'] = []): ExerciseRow =>
  ({ id, name, kind: 'REPS', primary, secondary, videoUrl: null, custom: false })

describe('workouts helpers', () => {
  it('names weekdays from Monday', () => {
    expect(dayName(1, 'en')).toBe('Monday')
    expect(dayName(7, 'en', 'short')).toBe('Sun')
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
})
