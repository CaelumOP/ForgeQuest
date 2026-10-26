import { describe, expect, it } from 'vitest'
import {
  alternatingTurns, answeredHistory, applyPatch, canonicalId, coachSystemPrompt, decodeDebriefFull, encodeDebrief, extractMemory, extractPatch,
  extractPlan, hasBrokenBlock, isReplyToIntake, languageOf, mergeMemory, parseDebrief, parsePastedPlan, planHash, stripPartial, stripReply,
  validatePlan, wantsPlanText, type CoachPlan,
} from './coach'
import { decodeDebrief } from '../ui/history/helpers'
import type { ExerciseEntity } from '../domain/entities'

const lib = new Map<string, ExerciseEntity>([
  ['push_up', { id: 'push_up', name: 'Push-up', kind: 'REPS', primary: ['CHEST'], secondary: [] }],
  ['dip', { id: 'dip', name: 'Dip', kind: 'REPS', primary: ['TRICEPS'], secondary: [] }],
  ['plank', { id: 'plank', name: 'Plank', kind: 'HOLD', primary: ['ABS'], secondary: [] }],
])

const planBlock = JSON.stringify({
  name: 'Push Pull', description: 'Two days.', days: [
    { dayOfWeek: 1, title: 'Day 1 · Push', isRest: false, items: [{ exerciseId: 'push_ups', sets: 4, repsMin: 8, repsMax: 12 }, { exerciseId: 'plank', sets: 3, seconds: 30 }] },
    { dayOfWeek: 2, title: 'Rest', isRest: true },
    { dayOfWeek: 2, title: 'Duplicate day is dropped' },
    { dayOfWeek: 9, title: 'Out of range is dropped' },
  ],
})
const reply = `Here is your week. Hit the push day hard.\n\n\`\`\`json\n${planBlock}\n\`\`\`\nMEMORY: Has a doorway pull-up bar.`

describe('plan from a coach reply', () => {
  it('reads the last json block, with defaults for missing fields', () => {
    const p = extractPlan(reply)!
    expect(p.name).toBe('Push Pull')
    expect(p.days.map((d) => d.dayOfWeek)).toEqual([1, 2])
    expect(p.days[0].items[0]).toMatchObject({ exerciseId: 'push_ups', block: 'Main', circuit: false, restSeconds: 90, sets: 4, weightKg: null })
  })
  it('shows only the prose and keeps MEMORY out of it', () => {
    expect(stripReply(reply)).toBe('Here is your week. Hit the push day hard.')
    expect(extractMemory(reply)).toEqual(['Has a doorway pull-up bar'])
    expect(stripPartial('Intro\n\n```json\n{"name": "x", "da')).toBe('Intro')
  })
  it('fixes near-miss ids and keeps numbers sane', () => {
    expect(canonicalId('push_ups', null, lib)).toBe('push_up')
    expect(canonicalId('Push-up', null, lib)).toBe('push_up')
    const v = validatePlan({ ...extractPlan(reply)!, days: [{ dayOfWeek: 1, title: 'x', isRest: false, note: '', items: [{ ...extractPlan(reply)!.days[0].items[0], exerciseId: 'push_ups', sets: 40 }, { ...extractPlan(reply)!.days[0].items[0], exerciseId: 'mystery_move' }] }] }, lib)
    expect(v.draft.days[0].items[0]).toMatchObject({ exerciseId: 'push_up', sets: 10 })
    expect(v.unknownIds.has('mystery_move')).toBe(true)
    expect(v.errors).toHaveLength(1)
  })
  it('reads change blocks and applies them', () => {
    const base: CoachPlan = extractPlan(reply)!
    const patchReply = 'Swapping.\n```json\n{"changes":[{"type":"swap","dayOfWeek":1,"exerciseId":"push_ups","newExerciseId":"dip","sets":5}]}\n```'
    const patch = extractPatch(patchReply)!
    const out = applyPatch(validatePlan(base, lib).draft, patch, lib)
    expect(out.errors).toEqual([])
    expect(out.draft!.days[0].items[0]).toMatchObject({ exerciseId: 'dip', sets: 5 })
    expect(out.draft!.patch).toEqual(patch)
    expect(applyPatch(base, { changes: [{ type: 'remove', dayOfWeek: 1, exerciseId: 'nope' }] }, lib).draft).toBeNull()
  })
  it('flags a broken block and reads a pasted plan', () => {
    expect(hasBrokenBlock('```json\n{"days": [oops]}\n```')).toBe(true)
    expect(hasBrokenBlock(reply)).toBe(false)
    expect(parsePastedPlan(planBlock)?.days).toHaveLength(2)
    expect(parsePastedPlan('```json\n' + planBlock + '\n```')?.name).toBe('Push Pull')
    expect(parsePastedPlan('Monday: push-ups 3x10')).toBeNull()
  })
  it('hashes training content only', () => {
    const p = extractPlan(reply)!
    expect(planHash(p)).toMatch(/^[0-9a-f]{16}$/)
    const renamed: CoachPlan = { ...p, name: 'Other' }
    expect(planHash(renamed)).toBe(planHash(p))
    expect(planHash({ ...p, days: [{ ...p.days[0], items: [] }, p.days[1]] })).not.toBe(planHash(p))
  })
})

describe('debrief encode / decode', () => {
  const d = { score: 8, summary: 'Strong push day.', highlights: ['Dips 3x10'], watch: ['Left elbow felt hard'], nextTime: ['Add 1 kg'] }
  it('round-trips through the session row and the journal decoder', () => {
    const text = encodeDebrief(d)
    expect(decodeDebriefFull(text)).toEqual(d)
    expect(decodeDebrief(text)).toEqual({ score: 8, summary: 'Strong push day.', nextTime: ['Add 1 kg'] })
  })
  it('parses chatty model output and clamps it like Android', () => {
    const p = parseDebrief('Sure!\n```json\n{"score": 1, "summary": " Done. ", "highlights": ["a","b","c"], "watch": ["x","y"], "nextTime": []}\n```')!
    expect(p).toEqual({ score: 4, summary: 'Done.', highlights: ['a', 'b'], watch: ['x'], nextTime: [] })
    expect(parseDebrief('{"score": 9, "summary": "  "}')).toBeNull()
    expect(parseDebrief('no json')).toBeNull()
    expect(parseDebrief('{"summary": "No score"}')).toBeNull()
    expect(decodeDebriefFull('')).toBeNull()
  })
  it('forgives drifting shapes', () => {
    // Fast models sometimes send a text score and single strings where lists were asked for.
    const d = parseDebrief('{"score": "8/10", "summary": "Strong session.", "highlights": "12 dips, 2 more than last time", "watch": [], "next_time": "Add a set of rows"}')!
    expect(d.score).toBe(8)
    expect(d.highlights).toEqual(['12 dips, 2 more than last time'])
    expect(d.nextTime).toEqual(['Add a set of rows'])
  })
})

describe('chat helpers', () => {
  const m = (role: string, text: string, kind = '', planJson: string | null = null) => ({ role, text, kind, planJson })
  it('detects plan requests in English and Arabic', () => {
    expect(wantsPlanText('Build me a 4-day plan')).toBe(true)
    expect(wantsPlanText('اعمل لي خطة')).toBe(true)
    expect(wantsPlanText('swap dips on Tuesday')).toBe(true)
    expect(wantsPlanText('add more pull-ups', ['pull ups', 'pull-ups'])).toBe(true)
    expect(wantsPlanText('how do I sleep better')).toBe(false)
  })
  it('knows an answer to the equipment intake', () => {
    const h = [m('user', 'make me a plan'), m('coach', 'What equipment do you have?'), m('user', 'dumbbells only')]
    expect(isReplyToIntake(h, 'dumbbells only')).toBe(true)
    expect(isReplyToIntake(h.slice(1), 'x')).toBe(false)
  })
  it('keeps clean exchanges and alternates turns', () => {
    const h = [m('user', 'a'), m('coach', 'err', 'error'), m('user', 'b'), m('coach', 'B'), m('user', 'c'), m('user', 'd')]
    expect(answeredHistory(h).map((x) => x.text)).toEqual(['b', 'B', 'd'])
    expect(alternatingTurns(answeredHistory(h))).toEqual([{ role: 'user', text: 'b' }, { role: 'model', text: 'B' }, { role: 'user', text: 'd' }])
  })
  it('picks the reply language from the script', () => {
    expect(languageOf('كيف حالك')).toBe('Arabic')
    expect(languageOf('hello')).toBe('English')
    expect(languageOf('123')).toContain('latest message')
  })
  it('keeps notes unique and capped', () => {
    expect(mergeMemory('A\nB', ['b', 'C'])).toBe('A\nB\nC')
    expect(mergeMemory('', Array.from({ length: 20 }, (_, i) => `n${i}`)).split('\n')).toHaveLength(15)
  })
  it('builds the system prompt in the right mode', () => {
    const base = { summary: 'User data (...)', replyLanguage: 'Arabic' }
    expect(coachSystemPrompt(base)).toContain('Never write plan JSON');
    expect(coachSystemPrompt(base)).toContain('formal Modern Standard Arabic')
    expect(coachSystemPrompt({ ...base, needsEquipmentIntake: true })).toContain('EQUIPMENT INTAKE REQUIRED')
    expect(coachSystemPrompt({ ...base, plan: true, schema: 'SCHEMA-HERE' })).toContain('SCHEMA-HERE')
  })
})
