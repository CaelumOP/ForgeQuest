import { writeFileSync } from 'node:fs'
import { expect, it } from 'vitest'
import { EXERCISE_CATALOG } from '../data/seed/exercise-catalog'
import type { PlanDraft, PlanDraftItem } from './entities'
import { EQUIPMENT_PRESETS, presetEquipment } from './exercise-swap-engine'
import type { Muscle } from './muscle'
import { EXPERIENCES, PlanBuilder, spreadDays, TRAIN_GOALS } from './plan-builder'
import { gradePlan, safePair, type Grade } from './plan-grader'

const catalog = new Map(EXERCISE_CATALOG.map(e => [e.id, e]))
const focuses: (Muscle | null)[] = [null, 'CHEST', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'ABS', 'UPPER_BACK', 'GLUTES', 'QUADS', 'HAMSTRINGS', 'CALVES']
const item = (exerciseId: string, overrides: Partial<PlanDraftItem> = {}): PlanDraftItem => ({
  exerciseId, name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3,
  repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null, ...overrides,
})
const draft = (items: PlanDraftItem[]): PlanDraft => ({ name: 'Fixture', description: '', days: [{ dayOfWeek: 1, title: 'Day', isRest: false, note: '', items }] })

it('checks purity, coverage, extra blocks, unsafe circuits and pairing capacity', () => {
  const base = draft([item('push_up'), item('db_row'), item('goblet_squat'), item('leg_curl')])
  const before = JSON.stringify(base)
  const grade = gradePlan(base, catalog, 'GENERAL', 30)
  expect(JSON.stringify(base)).toBe(before)
  expect(gradePlan(base, catalog, 'GENERAL', 30)).toEqual(grade)
  expect(grade.scores.pairing).toBe(15)
  expect(grade.scores.coverage).toBeGreaterThan(gradePlan(draft([item('push_up')]), catalog, 'GENERAL', 30).scores.coverage)
  const extras = gradePlan(draft([...base.days[0].items, item('push_up', { block: 'Warm-up', sets: 20 })]), catalog, 'GENERAL', 30)
  expect(extras.scores.coverage).toBe(grade.scores.coverage)
  expect(extras.estimatedMinutes).toBe(grade.estimatedMinutes + 5)
  expect(gradePlan(draft([item('push_up', { circuit: true }), item('push_up', { circuit: true })]), catalog, 'GENERAL', 20).scores.pairing).toBe(10)
  expect(safePair(catalog.get('push_up')!, catalog.get('db_row')!)).toBe(true)
  expect(safePair(catalog.get('deadlift')!, catalog.get('push_up')!)).toBe(false)
  const pairs = gradePlan(draft([item('push_up', { sets: 4, restSeconds: 60 }), item('db_row', { sets: 4, restSeconds: 60 })]), catalog, 'GENERAL', 20)
  expect(pairs.savedMinutes).toBe(2.67)
  expect(pairs.extraSets).toBe(2)
  expect(gradePlan(draft([item('push_up')]), catalog, 'GENERAL', 20).savedMinutes).toBe(0)
})

type Row = { goal: string; days: number; preset: string; minutes: number; key: string; grade: Grade; draft: PlanDraft }
const mean = (rows: Row[], pick: (r: Row) => number) => rows.reduce((s, r) => s + pick(r), 0) / rows.length
const fmt = (n: number) => n.toFixed(2)
const columns = (rows: Row[]) => [mean(rows, r => r.grade.total), ...(['efficiency', 'coverage', 'balance', 'quality', 'pairing'] as const).map(k => mean(rows, r => r.grade.scores[k]))].map(fmt).join(' | ')
const header = '| Group | N | Total /100 | Efficiency /25 | Coverage /25 | Balance /20 | Quality /15 | Pairing /15 |\n|---|---:|---:|---:|---:|---:|---:|---:|'

it('writes the deterministic matrix scorecard within two minutes', () => {
  const start = performance.now()
  const rows: Row[] = []
  let groupIndex = 0
  for (const goal of TRAIN_GOALS) for (let days = 2; days <= 6; days++) for (const preset of EQUIPMENT_PRESETS) for (const minutes of [20, 30, 45, 60]) {
    for (let f = 0; f < focuses.length; f++) {
      // All focuses and all experiences in every coarse group; 11 of its 33 cases.
      const experience = EXPERIENCES[(groupIndex + f) % EXPERIENCES.length]
      const focus = focuses[f]
      const equipment = presetEquipment(preset); equipment.add('BODYWEIGHT')
      const plan = PlanBuilder.build({ goal, days: spreadDays(days), minutes, equipment, experience, focus: focus ? [focus] : [] }, EXERCISE_CATALOG)
      const grade = gradePlan(plan.draft, catalog, goal, minutes)
      const key = `${goal}|${days}|${preset}|${experience}|${focus ?? '-'}|${minutes}`
      expect(Number.isFinite(grade.total), key).toBe(true)
      expect(grade.total, key).toBeGreaterThanOrEqual(0)
      expect(grade.total, key).toBeLessThanOrEqual(100)
      expect(grade.extraSets % 2, key).toBe(0)
      rows.push({ goal, days, preset, minutes, key, grade, draft: plan.draft })
    }
    groupIndex++
  }
  expect(rows).toHaveLength(7700)
  const groups = new Map<string, Row[]>()
  for (const row of rows) {
    const key = `${row.goal}|${row.days}|${row.preset}|${row.minutes}`
    groups.set(key, [...(groups.get(key) ?? []), row])
  }
  expect(groups.size).toBe(700)
  const elapsed = (performance.now() - start) / 1000
  const report = ['# Dev-only plan quality scorecard', '',
    `Deterministic sample: ${rows.length} / 23,100 cases; all 700 goal x days (2-6) x preset x minute groups, 11 cases/group. All 11 focuses in each group; experience = (group index + focus index) mod 3. Experience counts rotate 4/4/3. These are sample means, not exhaustive population results.`, '',
    `Overall mean **${fmt(mean(rows, r => r.grade.total))}/100**; minimum **${fmt(Math.min(...rows.map(r => r.grade.total)))}/100**. Matrix generation/grading: ${fmt(elapsed)} seconds.`, '',
    'Formula source: web/src/domain/plan-grader.ts header. Scores are heuristic comparisons, not measured outcomes. Goal targets are explicit rubric assumptions; builder targets are experience-based. Missing muscles dominate coverage. Duration uses auditor ceil(work seconds/60) plus 5 minutes per warm-up/cool-down block; overhead above 20% is penalized.', '',
    'Pairing checks catalog muscle overlap and named spinal-loading lifts; it cannot establish individual safety. FAT_LOSS items can be circuits despite absent explicit supersets. Same-block circuits are checked pairwise and excluded from additional pairing savings. Duration remains the auditor estimate for comparability.', '',
    '## Mean by minutes', '', header,
    ...[20, 30, 45, 60].map(n => { const s = rows.filter(r => r.minutes === n); return `| ${n} minutes | ${s.length} | ${columns(s)} |` }), '',
    '## Mean by days', '', header,
    ...[2, 3, 4, 5, 6].map(n => { const s = rows.filter(r => r.days === n); return `| ${n} days | ${s.length} | ${columns(s)} |` }), '',
    '## 15 worst groups (goal | days | preset | minutes)', '', header,
    ...[...groups].sort((a, b) => mean(a[1], r => r.grade.total) - mean(b[1], r => r.grade.total) || a[0].localeCompare(b[0])).slice(0, 15)
      .map(([key, s]) => `| ${key.replaceAll('|', ' / ')} | ${s.length} | ${columns(s)} |`), '',
    '## Three days x 20 minutes: every equipment preset', '', header,
    ...EQUIPMENT_PRESETS.map(p => { const s = rows.filter(r => r.days === 3 && r.minutes === 20 && r.preset === p); return `| ${p} | ${s.length} | ${columns(s)} |` }), '',
    'BODYWEIGHT_ONLY = no gear; HOME_GYM and both DUMBBELLS presets cover home/dumbbell setups; FULL_GYM = full gym. All remaining presets are shown separately.', '',
    '### Safe-pair opportunity for three 20-minute sessions', '',
    'Means across 55 sampled cases per preset. Savings are weekly (divide by 3 for a session). Zero transition rest; preserve recovery before repeating a lift. Disjoint greedy pairing saves at most 40 seconds per matched round. Extra sets are whole pairs fitting both the saved time and 20-minute limit, capped at daily-primary 10 and weekly MRV. Existing idle time earns no extra-set credit. Fixed 40-second sets and no setup/fatigue cost: theoretical capacity, not a prescription.', '',
    '| Preset | Mean existing weekly sets | Mean saved min/week | Mean extra sets/week | Extra sets range |', '|---|---:|---:|---:|---:|',
    ...EQUIPMENT_PRESETS.map(p => {
      const s = rows.filter(r => r.days === 3 && r.minutes === 20 && r.preset === p)
      return `| ${p} | ${fmt(mean(s, r => r.grade.workingSets))} | ${fmt(mean(s, r => r.grade.savedMinutes))} | ${fmt(mean(s, r => r.grade.extraSets))} | ${Math.min(...s.map(r => r.grade.extraSets))}-${Math.max(...s.map(r => r.grade.extraSets))} |`
    }), '', '## Three lowest-scoring concrete plans', '']
  const worst = [...rows].sort((a, b) => a.grade.total - b.grade.total || a.key.localeCompare(b.key)).slice(0, 3)
  for (const row of worst) {
    report.push(`### ${row.key}`, '', `Score ${row.grade.total}/100; sub-scores ${JSON.stringify(row.grade.scores)}.`, '')
    for (const day of row.draft.days.filter(d => !d.isRest)) {
      report.push(`- Day ${day.dayOfWeek} -> ${day.items.map(i => `${catalog.get(i.exerciseId)?.name ?? i.exerciseId} (${i.exerciseId}) ${i.sets} x ${i.seconds != null ? `${i.seconds}s` : `${i.repsMin ?? '?'}-${i.repsMax ?? '?'} reps`} [${i.block}; rest ${i.restSeconds}s${i.circuit ? '; circuit' : ''}]`).join('; ')}`)
    }
    report.push('', 'Reasons / diagnostics:', '', ...row.grade.reasons.map(r => `- ${r}`), '')
  }
  report.push('## Verification and limits', '',
    'Command: `cd web && npx vitest run src/domain/plan-grader.dev.test.ts`. Unit checks cover purity/determinism, missing coverage, extra-block exclusion, unsafe circuits, spinal exclusion, and exact pairing capacity. Matrix asserts bounded finite totals and all 700 coarse groups.', '',
    'Gemini Gate 1 and Gate 3 did not run: agy-read writes scratch/log files outside the three authorized paths. Caller must run these reviews. Sampling may miss worse unsampled plans. Spinal/odd-pick classification is a catalog heuristic; equipment transitions, loads and effort cannot be inferred.', '')
  expect(elapsed).toBeLessThan(115)
  writeFileSync(new URL('../../../disc-tmp/grader-scorecard.md', import.meta.url), report.join('\n'), 'utf8')
  console.info(`grader: ${rows.length} plans; mean ${fmt(mean(rows, r => r.grade.total))}; min ${fmt(worst[0].grade.total)}; ${fmt(elapsed)}s`)
}, 115_000)
