import { describe, expect, it } from 'vitest'
import { debriefLines } from './debrief-text'
import { HEADLINE_VARIANTS, type DebriefResult } from '../domain/debrief'
import { formatWeight } from '../domain/units'

const names: Record<string, string> = { squat: 'Squat', plank: 'Plank' }
const name = (id: string) => names[id] ?? id
const base: DebriefResult = {
  headline: { type: 'PR_AND_UP', variant: 1 }, wins: [], watch: [], next: [], sameRest: false, footnote: false,
}

describe('debrief text', () => {
  it('has every headline wording in both languages', () => {
    for (const lang of ['en', 'ar'] as const) {
      for (const [type, variants] of Object.entries(HEADLINE_VARIANTS)) {
        for (let variant = 0; variant < variants; variant++) {
          const text = debriefLines({ ...base, headline: { type: type as keyof typeof HEADLINE_VARIANTS, variant } }, lang, name, (kg) => `${kg}`).headline
          expect(text).not.toMatch(/^debrief_h/)
        }
      }
    }
  })

  it('writes weights in the person unit and counts with plurals', () => {
    const d: DebriefResult = {
      ...base,
      wins: [{ type: 'PR', exerciseId: 'squat', kind: 'WEIGHT', weightKg: 62.5, reps: 1, seconds: null }, { type: 'ALL_SETS', sets: 12 }],
      next: [{ type: 'PLUS_SECONDS', exerciseId: 'plank', seconds: 50 }],
      sameRest: true,
    }
    const en = debriefLines(d, 'en', name, (kg) => formatWeight('LB', kg, false))
    expect(en.wins[0]).toContain('137.8 lb')
    expect(en.wins[0]).toContain('1 rep')
    expect(en.wins[1]).toBe('Every planned set done (12 sets)')
    expect(en.next[0]).toBe('Next time: Plank, 50 seconds')
    expect(en.sameRest).toBe('Everything else: same numbers.')
  })

  it('SOME_UP shows its numbers; adjusted sets have their own line', () => {
    const d: DebriefResult = { ...base, headline: { type: 'SOME_UP', variant: 0, up: 1, compared: 4 }, wins: [{ type: 'ALL_ADJUSTED_SETS', sets: 7 }] }
    const en = debriefLines(d, 'en', name, (kg) => `${kg}`)
    expect(en.headline).toBe('Up on 1 of 4, the rest held steady.')
    expect(en.wins[0]).toBe("All 7 adjusted sets done for today's recovery")
    const ar = debriefLines(d, 'ar', name, (kg) => `${kg}`)
    expect(ar.headline).toContain('1 من 4')
    expect(ar.wins[0]).toContain('(7)')
  })

  it('uses Arabic plurals and no arrows', () => {
    const d: DebriefResult = {
      ...base,
      wins: [{ type: 'MORE_WORK', exerciseId: 'squat', kind: 'REPS', from: 20, to: 24 }, { type: 'ALL_SETS', sets: 2 }, { type: 'ALL_SETS', sets: 5 }],
    }
    const ar = debriefLines(d, 'ar', name, (kg) => `${kg}`)
    expect(ar.wins[0]).toContain('من 20 إلى 24 تكراراً'.replace('تكراراً', 'تكرارًا'))
    expect(ar.wins[1]).toContain('مجموعتين')
    expect(ar.wins[2]).toContain('5 مجموعات')
    expect(ar.wins.join(' ')).not.toMatch(/[→←]/)
  })
})
