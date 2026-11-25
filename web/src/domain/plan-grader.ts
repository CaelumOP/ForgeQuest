/**
 * DEV ONLY: heuristic scorecard, never import from application code. No clinical claims.
 * All ratios clamp to [0,1]; each category clamps to its stated maximum.
 * Efficiency /25 = 15 * main-working-sets / capacity + 5 * rest quality + 5 * useful share.
 * capacity = max(1,(requested minutes - actual extra-block minutes)*60/(40+goal rest)),
 * summed over days; main sets count once, even for multi-muscle lifts. Multiply capacity
 * ratio by min(1, requested/estimated minutes). Goal rest: strength 150, muscle 120,
 * general 90, endurance/fat loss 60 seconds. Rest quality = 1 - excess rest share above
 * goalRest/(40+goalRest). Useful share = 1 - duplicate abs/calf sets / all work sets
 * - max(0, extra minutes/estimated minutes - .20). Extras use auditor's fixed 5 min/block.
 * Coverage /25 = mean over 10 major muscles of 25*(.65*present + .35*min(1,credit/target)).
 * Targets = auditor MEV (ABS uses 4 because MEV=0) * goal factor, capped at MRV:
 * MUSCLE 1.25, STRENGTH/GENERAL 1, FAT_LOSS/ENDURANCE .75. These are explicit grading
 * assumptions, not builder targets (builder targets depend on experience, not goal).
 * Credits use RoutineAuditor 1 primary / .5 secondary; missing muscles cost most.
 * Balance /20 = 20 - auditor penalties: ABOVE_MRV 3, RECOVERY_OVERLAP 2,
 * JUNK_VOLUME 2, PUSH_PULL 3, ISOLATION_FIRST 1 (per finding); SHORT_PLAN/BELOW_MEV 0.
 * Also subtract 2 per spinal-loading exercise beyond two/day and 1 per consecutive
 * weekday muscle overlap below the auditor's six-set threshold (including Sun->Mon).
 * Quality /15 = 5*compound-first day fraction + 5*staple set fraction + 5*(1-odd set
 * fraction) - min(3, repeated non-staple isolation occurrences beyond two days).
 * Odd = non-staple bodyweight work, or jump/hop/burpee for STRENGTH. Repeated staples
 * and compounds are exempt: practice/progression is a plausible reason to repeat them.
 * Pairing /15 = 15 - 5 per unsafe unordered pair in each explicit superset/circuit.
 * No pairing means 15. Circuit members share a block; any shared primary muscle,
 * primary/secondary overlap, unknown exercise, or spinal lift makes a pair ineligible.
 * Secondary/secondary overlap alone is allowed (e.g. push/pull shoulder stabilizers).
 * Opportunity (not score): deterministic greedy, disjoint exercise pairs, 40s work/set,
 * zero transition rest, retain each lift's rest before repeating it. Per paired round
 * save min(restA,restB,40) seconds. Exclude existing paired items and spinal lifts.
 * Extra sets = 2*floor(min(saved seconds, remaining 20-min budget after pairing) /
 * shortest eligible pair-cycle seconds), per day; also bounded by day-primary 10 and
 * weekly MRV. Assumes zero equipment transition, fixed 40s sets, no fatigue slowdown;
 * this is a theoretical capacity estimate, not a prescription or proven safe pairing.
 */
import type { ExerciseEntity, PlanDraft, PlanDraftItem } from './entities'
import type { Muscle } from './muscle'
import { isCompoundExercise, STAPLE_IDS, type TrainGoal } from './plan-builder'
import { isExtraBlock } from './pr-picker'
import { credits, estimateMinutes, fromDraft, landmarkFor, RoutineAuditor } from './routine-auditor'

const MAJOR: Muscle[] = ['CHEST', 'UPPER_BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'QUADS', 'HAMSTRINGS', 'GLUTES', 'CALVES', 'ABS']
const MAIN = new Set<Muscle>(['CHEST', 'UPPER_BACK', 'SHOULDERS', 'QUADS', 'HAMSTRINGS', 'GLUTES'])
const clamp = (n: number) => Math.max(0, Math.min(1, n))
const round = (n: number) => Math.round(n * 100) / 100
const spinal = (e: ExerciseEntity) => /^(back_squat|front_squat|deadlift|sumo_deadlift|romanian_deadlift|barbell_row|good_morning|overhead_press)$/.test(e.id)
export function safePair(a: ExerciseEntity, b: ExerciseEntity): boolean {
  return a.id !== b.id && !spinal(a) && !spinal(b)
    && !a.primary.some(m => b.primary.includes(m) || b.secondary.includes(m))
    && !b.primary.some(m => a.secondary.includes(m))
}
type Item = PlanDraftItem & { supersetGroup?: number | null }
export type Grade = {
  total: number
  scores: { efficiency: number; coverage: number; balance: number; quality: number; pairing: number }
  estimatedMinutes: number; workingSets: number; savedMinutes: number; extraSets: number
  reasons: string[]
}

export function gradePlan(draft: PlanDraft, catalog: ReadonlyMap<string, ExerciseEntity>, goal: TrainGoal, minutes: number): Grade {
  const days = draft.days.filter(d => !d.isRest)
  const auditDays = fromDraft(draft)
  const estimated = auditDays.reduce((s, d) => s + estimateMinutes(d), 0)
  const weekly = new Map<Muscle, number>()
  const repeated = new Map<string, number>()
  const reasons: string[] = []
  const restTarget = goal === 'STRENGTH' ? 150 : goal === 'MUSCLE' ? 120 : goal === 'GENERAL' ? 90 : 60
  let work = 0; let main = 0; let rests = 0; let extras = 0; let duplicates = 0
  let staple = 0; let odd = 0; let first = 0; let spinalExcess = 0; let unsafe = 0; let saved = 0
  const dayPrimary: Map<Muscle, number>[] = []
  const opportunities: { day: number; a: ExerciseEntity; b: ExerciseEntity; cycle: number }[] = []
  const daySavings: number[] = []
  days.forEach((day, index) => {
    const items: Item[] = day.items.filter(i => !isExtraBlock(i.block) && i.sets > 0)
    const primary = new Map<Muscle, number>(); dayPrimary.push(primary)
    const seenSmall = new Set<Muscle>(); const seenIds = new Set<string>()
    let spinalCount = 0
    const groups = new Map<string, Item[]>()
    for (const i of items) {
      const key = i.supersetGroup != null ? `s:${i.supersetGroup}` : i.circuit ? `c:${i.block}` : null
      if (key != null) groups.set(key, [...(groups.get(key) ?? []), i])
      work += i.sets; rests += i.sets * i.restSeconds
      const e = catalog.get(i.exerciseId)
      if (!e) { reasons.push(`Unknown catalog exercise: ${i.exerciseId}`); continue }
      if (e.primary.some(m => MAIN.has(m))) main += i.sets
      for (const [m, c] of credits(e)) weekly.set(m, (weekly.get(m) ?? 0) + i.sets * c)
      for (const m of new Set(e.primary)) primary.set(m, (primary.get(m) ?? 0) + i.sets)
      const small = e.primary.filter(m => m === 'ABS' || m === 'CALVES')
      if (small.some(m => seenSmall.has(m))) duplicates += i.sets
      small.forEach(m => seenSmall.add(m))
      if (STAPLE_IDS.has(e.id)) staple += i.sets
      if ((!STAPLE_IDS.has(e.id) && e.kind !== 'WEIGHTED' && (!e.equipment || e.equipment === 'BODYWEIGHT'))
        || (goal === 'STRENGTH' && /jump|hop|burpee/.test(e.id))) odd += i.sets
      if (spinal(e)) spinalCount++
      if (!seenIds.has(e.id)) repeated.set(e.id, (repeated.get(e.id) ?? 0) + 1)
      seenIds.add(e.id)
    }
    const firstExercise = items.length ? catalog.get(items[0].exerciseId) : undefined
    if (firstExercise && isCompoundExercise(firstExercise)) first++
    spinalExcess += Math.max(0, spinalCount - 2)
    extras += estimateMinutes(auditDays[index]) - Math.ceil(items.reduce((s, i) => s + i.sets * (40 + i.restSeconds), 0) / 60)
    for (const group of groups.values()) for (let a = 0; a < group.length; a++) for (let b = a + 1; b < group.length; b++) {
      const ea = catalog.get(group[a].exerciseId); const eb = catalog.get(group[b].exerciseId)
      if (!ea || !eb || !safePair(ea, eb)) unsafe++
    }
    const used = new Set<number>(); let saving = 0
    for (let a = 0; a < items.length; a++) {
      if (used.has(a) || items[a].circuit || items[a].supersetGroup != null) continue
      for (let b = a + 1; b < items.length; b++) {
        if (used.has(b) || items[b].circuit || items[b].supersetGroup != null) continue
        const ea = catalog.get(items[a].exerciseId); const eb = catalog.get(items[b].exerciseId)
        if (!ea || !eb || !safePair(ea, eb)) continue
        const savingPerRound = Math.min(40, items[a].restSeconds, items[b].restSeconds)
        saving += Math.min(items[a].sets, items[b].sets) * savingPerRound
        opportunities.push({ day: index, a: ea, b: eb, cycle: 80 + items[a].restSeconds + items[b].restSeconds - savingPerRound })
        used.add(a); used.add(b); break
      }
    }
    saved += saving; daySavings.push(saving)
  })
  const findings = RoutineAuditor.audit(auditDays, catalog)
  const penalties = { ABOVE_MRV: 3, RECOVERY_OVERLAP: 2, JUNK_VOLUME: 2, PUSH_PULL: 3, ISOLATION_FIRST: 1, SHORT_PLAN: 0, BELOW_MEV: 0 }
  let overlap = 0
  days.forEach((day, i) => {
    const next = days.findIndex(d => d.dayOfWeek === day.dayOfWeek % 7 + 1)
    if (next < 0) return
    for (const [m, n] of dayPrimary[i]) {
      const other = dayPrimary[next].get(m) ?? 0
      if (other > 0 && (n < 6 || other < 6)) overlap++
    }
  })
  const factor = goal === 'MUSCLE' ? 1.25 : goal === 'ENDURANCE' || goal === 'FAT_LOSS' ? .75 : 1
  let coverage = 0
  for (const m of MAJOR) {
    const value = weekly.get(m) ?? 0
    const target = Math.min(landmarkFor(m).mrv, (landmarkFor(m).mev || 4) * factor)
    coverage += 25 / MAJOR.length * (.65 * Number(value > 0) + .35 * clamp(value / target))
    if (value < target) reasons.push(`${m}: ${value}/${target} credited sets${value === 0 ? ' (missing)' : ''}`)
  }
  let repeats = 0
  for (const [id, count] of repeated) {
    const e = catalog.get(id)!
    if (!STAPLE_IDS.has(id) && !isCompoundExercise(e)) repeats += Math.max(0, count - 2)
  }
  const capacity = Math.max(days.length, (minutes * days.length - extras) * 60 / (40 + restTarget))
  const density = clamp(main / Math.max(1, capacity)) * clamp(minutes * days.length / Math.max(1, estimated))
  const restQuality = clamp(1 - Math.max(0, rests / Math.max(1, work * 40 + rests) - restTarget / (40 + restTarget)))
  const useful = clamp(1 - duplicates / Math.max(1, work) - Math.max(0, extras / Math.max(1, estimated) - .2))
  const scores = {
    efficiency: round(work ? 15 * density + 5 * restQuality + 5 * useful : 0),
    coverage: round(coverage),
    balance: round(Math.max(0, 20 - findings.reduce((s, f) => s + penalties[f.kind], 0) - 2 * spinalExcess - overlap)),
    quality: round(Math.max(0, 5 * first / Math.max(1, days.length) + 5 * staple / Math.max(1, work) + (work ? 5 * (1 - odd / work) : 0) - Math.min(3, repeats))),
    pairing: Math.max(0, 15 - 5 * unsafe),
  }
  reasons.push(`Efficiency: ${main} main sets / ${round(capacity)} capacity; ${estimated} min including ${extras} extra-block min; ${round(100 * rests / Math.max(1, work * 40 + rests))}% work/rest time resting; ${duplicates} duplicate abs/calf sets`)
  reasons.push(`Quality: ${first}/${days.length} compound-first days; ${staple}/${work} staple sets; ${odd} odd sets; ${repeats} unexplained repeats`)
  findings.forEach(f => reasons.push(`Plan check ${f.kind}${f.muscle ? ` ${f.muscle}` : ''}: ${f.value}/${f.limit}`))
  if (overlap || spinalExcess) reasons.push(`Recovery: ${overlap} sub-threshold consecutive muscle overlaps; ${spinalExcess} spinal lifts beyond two/day`)
  if (unsafe) reasons.push(`Pairing: ${unsafe} unsafe circuit/superset pairs`)
  let extraSets = 0
  const projected = new Map(weekly)
  days.forEach((_, index) => {
    let budget = Math.max(0, Math.min(daySavings[index], minutes * 60 - estimateMinutes(auditDays[index]) * 60 + daySavings[index]))
    const options = opportunities.filter(o => o.day === index).sort((a, b) => a.cycle - b.cycle)
    for (const o of options) {
      const added = new Map<Muscle, number>()
      for (const e of [o.a, o.b]) for (const [m, c] of credits(e)) added.set(m, (added.get(m) ?? 0) + c)
      while (budget >= o.cycle && [...added].every(([m, c]) => (projected.get(m) ?? 0) + c <= landmarkFor(m).mrv)
        && [...o.a.primary, ...o.b.primary].every(m => (dayPrimary[index].get(m) ?? 0) + 1 <= 10)) {
        budget -= o.cycle; extraSets += 2
        for (const [m, c] of added) projected.set(m, (projected.get(m) ?? 0) + c)
        for (const m of [...new Set(o.a.primary), ...new Set(o.b.primary)]) dayPrimary[index].set(m, (dayPrimary[index].get(m) ?? 0) + 1)
      }
    }
  })
  return { total: round(Object.values(scores).reduce((a, b) => a + b, 0)), scores, estimatedMinutes: estimated, workingSets: work, savedMinutes: round(saved / 60), extraSets, reasons }
}
