import { describe, expect, it } from 'vitest'
import { chipOf, effortOf, effortRir, isStrain, parseEffort, pickEffort } from './effort'

// Audit #8: one word list decides effort from an old note, for progression and recovery alike.
describe('effortOf', () => {
  it('reads hard, easy and pain words in English and Arabic', () => {
    expect(effortOf('', 'to failure')).toBe('HARD')
    expect(effortOf('', 'صعب')).toBe('HARD')
    expect(effortOf('', 'فشل في آخر عدة')).toBe('HARD')
    expect(effortOf('', 'felt easy')).toBe('EASY')
    expect(effortOf('', 'RIR 3')).toBe('EASY')
    expect(effortOf('', 'سهل')).toBe('EASY')
    expect(effortOf('', 'ألم في الركبة')).toBe('PAIN')
  })

  it('pain beats hard beats easy, and a tapped chip beats the note', () => {
    expect(effortOf('', 'easy until a twinge')).toBe('PAIN')
    expect(effortOf('', 'easy start, rpe 10 finish')).toBe('HARD')
    expect(effortOf('OK', 'failure')).toBe('OK')
  })

  it('parses stored names, unknown is OK', () => {
    expect(parseEffort('HARD')).toBe('HARD')
    expect(parseEffort('hard')).toBe('OK')
    expect(parseEffort(null)).toBe('OK')
  })
})

describe('audit 2026-10-24: negated words', () => {
  it('no pain, pain-free and without pain are not pain', () => {
    expect(effortOf('', 'felt strong, no pain')).toBe('OK')
    expect(effortOf('', 'pain-free today')).toBe('OK')
    expect(effortOf('', 'without pain this time')).toBe('OK')
    expect(effortOf('', 'بدون ألم')).toBe('OK')
    expect(effortOf('', "didn't fail")).toBe('OK')
  })
  it('real pain still counts, also in a later clause and with an Arabic prefix', () => {
    expect(effortOf('', 'sharp pain in left shoulder')).toBe('PAIN')
    expect(effortOf('', 'no rest, knee pain')).toBe('PAIN')
    expect(effortOf('', 'no pain at first but pain on the last rep')).toBe('PAIN')
    expect(effortOf('', 'بألم في الركبة')).toBe('PAIN')
  })
})

describe('effort scale', () => {
  it('reads old stored values and the new Failure', () => {
    for (const e of ['EASY', 'HARD', 'PAIN', 'FAILURE'] as const) expect(parseEffort(e)).toBe(e)
    expect(chipOf('OK')).toBe('')
    expect(chipOf('')).toBe('')
    expect(chipOf('EASY')).toBe('EASY')
    expect(chipOf('HARD')).toBe('HARD')
    expect(chipOf('FAILURE')).toBe('FAILURE')
    expect(parseEffort('nonsense')).toBe('OK')
    expect(parseEffort('')).toBe('OK')
  })
  it('maps effort to RIR', () => {
    expect(effortRir('EASY')).toBe(3)
    expect(effortRir('OK')).toBe(2)
    expect(effortRir('HARD')).toBe(1)
    expect(effortRir('FAILURE')).toBe(0)
    expect(effortRir('PAIN')).toBe(2)
  })
  it('Failure also sets the set type and leaving it puts it back', () => {
    expect(pickEffort({ effort: '', setType: 'WORKING' }, 'FAILURE')).toEqual({ effort: 'FAILURE', setType: 'FAILURE' })
    expect(pickEffort({ effort: 'FAILURE', setType: 'FAILURE' }, 'FAILURE')).toEqual({ effort: '', setType: 'WORKING' })
    expect(pickEffort({ effort: 'FAILURE', setType: 'FAILURE' }, 'EASY')).toEqual({ effort: 'EASY', setType: 'WORKING' })
    expect(pickEffort({ effort: '', setType: 'DROPSET' }, 'FAILURE')).toEqual({ effort: 'FAILURE', setType: 'DROPSET' })
    expect(pickEffort({ effort: 'HARD' }, 'PAIN')).toEqual({ effort: 'PAIN', setType: 'WORKING' })
  })
  it('a stored OK picked as Hard selects Hard like blank', () => {
    expect(pickEffort({ effort: 'OK', setType: 'WORKING' }, 'HARD')).toEqual({ effort: 'HARD', setType: 'WORKING' })
  })
  it('only Hard and Failure count as strain', () => {
    expect(['EASY', 'OK', 'HARD', 'FAILURE', 'PAIN'].filter((e) => isStrain(e as never))).toEqual(['HARD', 'FAILURE'])
  })
})
