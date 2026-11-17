import { db } from '../db'
import { EXERCISE_ALIASES } from '../seed/exercise-aliases'
import { EXERCISE_NAMES_AR } from '../seed/exercise-names-ar'
import { epochDayOf } from '../../domain/dates'
import type { ExerciseEntity } from '../../domain/entities'
import type { ExerciseKind, Muscle } from '../../domain/muscle'
import type { SetType } from '../../domain/set-types'
import { toKg } from '../../domain/units'

export type CsvSource = 'STRONG' | 'HEVY' | 'FITNOTES'
export type MatchStatus = 'MATCHED' | 'AMBIGUOUS' | 'UNMATCHED'

export type ExerciseMatch = {
  rawName: string
  status: MatchStatus
  matchedId: string | null
  candidates: string[]
}

export type ExerciseMapping =
  | { kind: 'use'; exerciseId: string }
  | { kind: 'create'; name: string; exerciseKind?: ExerciseKind; primary?: Muscle }
  | { kind: 'skip' }

export type ParsedSetRow = {
  epochDay: number
  workoutName: string
  rawExerciseName: string
  setIndex: number
  setType: SetType
  weightKg: number | null
  reps: number | null
  seconds: number | null
  note: string
}

export type CsvPreview = {
  source: CsvSource
  rows: ParsedSetRow[]
  exerciseMatches: Map<string, ExerciseMatch>
  totalWorkouts: number
  newSetsCount: number
  duplicateSetsCount: number
  existingDedupeKeys: Set<string>
}

export type CsvImportResult = {
  importedWorkouts: number
  importedSets: number
  skippedDuplicates: number
}

export class CsvParseError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'CsvParseError'
  }
}

export const dedupeKey = (epochDay: number, exerciseId: string, setIndex: number): string =>
  `${epochDay}|${exerciseId}|${setIndex}`

function detectDelimiter(headerLine: string): ',' | ';' {
  let commas = 0
  let semis = 0
  let quoted = false
  for (const ch of headerLine) {
    if (ch === '"') quoted = !quoted
    else if (!quoted) {
      if (ch === ',') commas++
      else if (ch === ';') semis++
    }
  }
  return semis > commas ? ';' : ','
}

export function parseCsvTable(rawText: string): string[][] {
  const text = rawText.replace(/^\uFEFF/, '')
  if (!text.trim()) return []
  const firstLine = text.split(/\r?\n/).find((l) => l.trim().length > 0) ?? ''
  const delim = detectDelimiter(firstLine)
  const rows: string[][] = []
  let row: string[] = []
  let cell = ''
  let inQuotes = false

  for (let i = 0; i < text.length; i++) {
    const ch = text[i]
    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') {
          cell += '"'
          i++
        } else {
          inQuotes = false
        }
      } else {
        cell += ch
      }
    } else if (ch === '"') {
      inQuotes = true
    } else if (ch === delim) {
      row.push(cell)
      cell = ''
    } else if (ch === '\r' || ch === '\n') {
      if (ch === '\r' && text[i + 1] === '\n') i++
      row.push(cell)
      cell = ''
      if (row.some((c) => c.trim().length > 0)) rows.push(row)
      row = []
    } else {
      cell += ch
    }
  }
  if (cell.length > 0 || row.length > 0) {
    row.push(cell)
    if (row.some((c) => c.trim().length > 0)) rows.push(row)
  }
  return rows
}

export function detectCsvSource(headers: readonly string[]): CsvSource {
  const norm = new Set(headers.map((h) => h.trim().toLowerCase()))
  if (norm.has('exercise_title') && (norm.has('start_time') || norm.has('set_index'))) return 'HEVY'
  if (norm.has('exercise name') && (norm.has('set order') || norm.has('workout name'))) return 'STRONG'
  if (
    norm.has('exercise') &&
    norm.has('date') &&
    (norm.has('weight unit') || norm.has('weight (kgs)') || norm.has('weight (lbs)') || norm.has('category'))
  ) {
    return 'FITNOTES'
  }
  throw new CsvParseError('Unrecognized CSV header format')
}

const MONTHS: Record<string, number> = {
  jan: 1, feb: 2, mar: 3, apr: 4, may: 5, jun: 6,
  jul: 7, aug: 8, sep: 9, oct: 10, nov: 11, dec: 12,
}

/**
 * Timezone-safe calendar date parser:
 * - Local `YYYY-MM-DD...` and `DD MMM YYYY...` parse straight to calendar `epochDay` with zero timezone shift.
 * - Explicit ISO offset (`Z` or `+/-HH:MM`) resolves to local calendar day of that instant.
 */
export function parseEpochDay(raw: string): number | null {
  const s = raw.trim()
  if (!s) return null
  if (/(?:Z|[+-]\d{2}:?\d{2})$/.test(s)) {
    const dt = new Date(s)
    if (!Number.isNaN(dt.getTime())) {
      return epochDayOf(dt.getFullYear(), dt.getMonth() + 1, dt.getDate())
    }
  }
  const iso = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(s)
  if (iso) {
    const y = Number(iso[1]), m = Number(iso[2]), d = Number(iso[3])
    if (m >= 1 && m <= 12 && d >= 1 && d <= 31) return epochDayOf(y, m, d)
  }
  const dmy = /^(\d{1,2})\s+([A-Za-z]{3,9})\s+(\d{4})/.exec(s)
  if (dmy) {
    const d = Number(dmy[1])
    const m = MONTHS[dmy[2].slice(0, 3).toLowerCase()]
    const y = Number(dmy[3])
    if (m && d >= 1 && d <= 31) return epochDayOf(y, m, d)
  }
  return null
}

function parseDurationSeconds(raw: string): number | null {
  const s = raw.trim()
  if (!s) return null
  const num = Number(s)
  if (Number.isFinite(num)) return Math.round(num) > 0 ? Math.round(num) : null
  const parts = s.split(':').map((p) => Number(p.trim()))
  if (parts.some((p) => !Number.isInteger(p) || p < 0)) return null
  if (parts.length === 2) {
    const sec = parts[0] * 60 + parts[1]
    return sec > 0 ? sec : null
  }
  if (parts.length === 3) {
    const sec = parts[0] * 3600 + parts[1] * 60 + parts[2]
    return sec > 0 ? sec : null
  }
  return null
}

const mapStrongSetType = (order: string): SetType => {
  switch (order.trim().toUpperCase()) {
    case 'W': return 'WARMUP'
    case 'D': return 'DROPSET'
    case 'F': return 'FAILURE'
    default: return 'WORKING'
  }
}

const mapHevySetType = (raw: string): SetType => {
  switch (raw.trim().toLowerCase()) {
    case 'warmup':
    case 'warm_up':
    case 'warm-up':
      return 'WARMUP'
    case 'dropset':
    case 'drop_set':
    case 'drop':
      return 'DROPSET'
    case 'failure':
    case 'to_failure':
      return 'FAILURE'
    case 'rest_pause':
    case 'restpause':
      return 'RESTPAUSE'
    default:
      return 'WORKING'
  }
}

function posFloat(raw: string): number | null {
  const n = Number(raw.trim())
  return Number.isFinite(n) && n > 0 ? n : null
}

function posInt(raw: string): number | null {
  const n = Number(raw.trim())
  return Number.isFinite(n) && Math.round(n) > 0 ? Math.round(n) : null
}

export function parseCsvRows(csvText: string): { source: CsvSource; rows: ParsedSetRow[] } {
  const table = parseCsvTable(csvText)
  if (table.length < 2) throw new CsvParseError('CSV has no data rows')
  const headers = table[0].map((h) => h.trim())
  const source = detectCsvSource(headers)
  const col = new Map(headers.map((h, i) => [h.toLowerCase(), i]))
  const cell = (row: string[], ...keys: string[]): string => {
    for (const k of keys) {
      const idx = col.get(k)
      if (idx != null && idx < row.length) return row[idx].trim()
    }
    return ''
  }

  const out: ParsedSetRow[] = []
  const groupCounters = new Map<string, number>()

  for (const r of table.slice(1)) {
    if (source === 'STRONG') {
      const usesLbsHeader = headers.some((h) => /\(lbs?\)/i.test(h))
      const setOrder = cell(r, 'set order')
      if (/^(rest timer|note)$/i.test(setOrder)) continue
      const exName = cell(r, 'exercise name')
      if (!exName) continue
      const epochDay = parseEpochDay(cell(r, 'date'))
      if (epochDay == null) continue
      const workoutName = cell(r, 'workout name') || 'Strong'
      const gk = `${epochDay}|${workoutName}|${exName}`
      const seq = groupCounters.get(gk) ?? 0
      groupCounters.set(gk, seq + 1)
      const w = posFloat(cell(r, 'weight', 'weight (kg)', 'weight (lbs)', 'weight (lb)'))
      out.push({
        epochDay,
        workoutName,
        rawExerciseName: exName,
        setIndex: seq,
        setType: mapStrongSetType(setOrder),
        weightKg: w != null ? (usesLbsHeader ? toKg('LB', w) : w) : null,
        reps: posInt(cell(r, 'reps')),
        seconds: parseDurationSeconds(cell(r, 'seconds')),
        note: cell(r, 'notes'),
      })
    } else if (source === 'HEVY') {
      const exName = cell(r, 'exercise_title')
      if (!exName) continue
      const epochDay = parseEpochDay(cell(r, 'start_time'))
      if (epochDay == null) continue
      const workoutName = cell(r, 'title') || 'Hevy'
      const gk = `${epochDay}|${workoutName}|${exName}`
      const seq = groupCounters.get(gk) ?? 0
      groupCounters.set(gk, seq + 1)
      const kg = posFloat(cell(r, 'weight_kg'))
      const lbs = posFloat(cell(r, 'weight_lbs'))
      out.push({
        epochDay,
        workoutName,
        rawExerciseName: exName,
        setIndex: seq,
        setType: mapHevySetType(cell(r, 'set_type')),
        weightKg: kg ?? (lbs != null ? toKg('LB', lbs) : null),
        reps: posInt(cell(r, 'reps')),
        seconds: parseDurationSeconds(cell(r, 'duration_seconds')),
        note: cell(r, 'exercise_notes'),
      })
    } else {
      const hasLbsHeader = headers.some((h) => /\(lbs?\)/i.test(h))
      const exName = cell(r, 'exercise')
      if (!exName) continue
      const epochDay = parseEpochDay(cell(r, 'date'))
      if (epochDay == null) continue
      const cat = cell(r, 'category')
      const workoutName = cat ? `FitNotes · ${cat}` : 'FitNotes'
      const gk = `${epochDay}|${exName}`
      const seq = groupCounters.get(gk) ?? 0
      groupCounters.set(gk, seq + 1)
      const w = posFloat(cell(r, 'weight', 'weight (kgs)', 'weight (kg)', 'weight (lbs)', 'weight (lb)'))
      const unitRaw = cell(r, 'weight unit').toLowerCase()
      const isLb = hasLbsHeader || unitRaw === 'lb' || unitRaw === 'lbs'
      out.push({
        epochDay,
        workoutName,
        rawExerciseName: exName,
        setIndex: seq,
        setType: 'WORKING',
        weightKg: w != null ? (isLb ? toKg('LB', w) : w) : null,
        reps: posInt(cell(r, 'reps')),
        seconds: parseDurationSeconds(cell(r, 'time')),
        note: '',
      })
    }
  }
  if (!out.length) throw new CsvParseError('No importable set rows found')
  return { source, rows: out }
}

const COMMON_ALIASES: Record<string, string> = {
  'bench press': 'bench_press',
  'barbell bench press': 'bench_press',
  'flat barbell bench press': 'bench_press',
  'incline bench press': 'incline_bench_press',
  'incline barbell bench press': 'incline_bench_press',
  'incline dumbbell press': 'incline_db_press',
  'dumbbell bench press': 'db_bench_press',
  'squat': 'back_squat',
  'squat bodyweight': 'squat',
  'barbell squat': 'back_squat',
  'back squat': 'back_squat',
  'front squat': 'front_squat',
  'deadlift': 'deadlift',
  'conventional deadlift': 'deadlift',
  'romanian deadlift': 'romanian_deadlift',
  'rdl': 'romanian_deadlift',
  'overhead press': 'overhead_press',
  'ohp': 'overhead_press',
  'military press': 'overhead_press',
  'bent over row': 'barbell_row',
  'barbell row': 'barbell_row',
  'lat pulldown': 'lat_pulldown',
  'seated row': 'seated_cable_row',
  'seated cable row': 'seated_cable_row',
  'pull up': 'pull_up',
  'pullup': 'pull_up',
  'chin up': 'chin_up',
  'chinup': 'chin_up',
  'dip': 'dip',
  'dips': 'dip',
  'push up': 'push_up',
  'pushup': 'push_up',
  'bicep curl': 'bicep_curl',
  'dumbbell bicep curl': 'bicep_curl',
  'hammer curl': 'hammer_curl',
  'triceps pushdown': 'tricep_pushdown',
  'tricep pushdown': 'tricep_pushdown',
  'skullcrusher': 'skull_crusher',
  'skull crusher': 'skull_crusher',
  'lateral raise': 'lateral_raise',
  'face pull': 'face_pull',
  'leg press': 'leg_press',
  'leg extension': 'leg_extension',
  'leg curl': 'leg_curl',
  'seated leg curl': 'seated_leg_curl',
  'hip thrust': 'hip_thrust',
  'calf raise': 'calf_raise',
  'standing calf raise': 'standing_calf_raise',
  'plank': 'plank',
  'crunch': 'crunch',
}

export function normalizeExerciseName(raw: string): string {
  return raw
    .toLowerCase()
    .replace(/[أإآ]/g, 'ا')
    .replace(/ى/g, 'ي')
    .replace(/ة/g, 'ه')
    .replace(/[\u064B-\u0652]/g, '')
    .replace(/[^a-z0-9\u0621-\u064A]+/g, ' ')
    .trim()
}

export function matchCsvExercises(
  rawNames: readonly string[],
  library: readonly ExerciseEntity[],
): Map<string, ExerciseMatch> {
  const exactIndex = new Map<string, Set<string>>()
  const idIndex = new Map<string, Set<string>>()
  const addKey = (index: Map<string, Set<string>>, key: string, id: string) => {
    const n = normalizeExerciseName(key)
    if (!n) return
    const set = index.get(n) ?? new Set<string>()
    set.add(id)
    index.set(n, set)
  }
  const byId = new Map(library.map((e) => [e.id, e]))
  for (const ex of library) {
    addKey(idIndex, ex.id.replace(/_/g, ' '), ex.id)
    addKey(exactIndex, ex.name, ex.id)
    const ar = EXERCISE_NAMES_AR[ex.id]
    if (ar) addKey(exactIndex, ar, ex.id)
    const aliasesRaw = ex.aliases || EXERCISE_ALIASES[ex.id] || ''
    if (aliasesRaw) {
      for (const a of aliasesRaw.split(/[,|;]/)) addKey(exactIndex, a, ex.id)
    }
  }

  const out = new Map<string, ExerciseMatch>()
  for (const raw of new Set(rawNames)) {
    const normFull = normalizeExerciseName(raw)
    const normStripped = normalizeExerciseName(raw.replace(/\s*\([^)]*\)\s*$/, ''))
    const direct = [...(exactIndex.get(normFull) ?? [])]
    const aliasRaw = COMMON_ALIASES[normFull] ?? COMMON_ALIASES[normStripped]
    const aliasHit = aliasRaw && byId.has(aliasRaw) ? aliasRaw : null
    const idHits = [...(idIndex.get(normFull) ?? [])]
    const exactStripped = [...(exactIndex.get(normStripped) ?? [])]
    const stripped = exactStripped.length > 0 ? exactStripped : [...(idIndex.get(normStripped) ?? [])]

    if (direct.length === 1) {
      out.set(raw, { rawName: raw, status: 'MATCHED', matchedId: direct[0], candidates: [] })
    } else if (direct.length > 1) {
      out.set(raw, { rawName: raw, status: 'AMBIGUOUS', matchedId: null, candidates: direct })
    } else if (aliasHit) {
      out.set(raw, { rawName: raw, status: 'MATCHED', matchedId: aliasHit, candidates: [] })
    } else if (idHits.length === 1) {
      out.set(raw, { rawName: raw, status: 'MATCHED', matchedId: idHits[0], candidates: [] })
    } else if (idHits.length > 1) {
      out.set(raw, { rawName: raw, status: 'AMBIGUOUS', matchedId: null, candidates: idHits })
    } else if (stripped.length === 1) {
      out.set(raw, { rawName: raw, status: 'MATCHED', matchedId: stripped[0], candidates: [] })
    } else if (stripped.length > 1) {
      out.set(raw, { rawName: raw, status: 'AMBIGUOUS', matchedId: null, candidates: stripped })
    } else {
      out.set(raw, { rawName: raw, status: 'UNMATCHED', matchedId: null, candidates: [] })
    }
  }
  return out
}

async function loadExistingDedupeKeys(): Promise<Set<string>> {
  const [sessions, sets] = await Promise.all([db.sessions.toArray(), db.sets.toArray()])
  const byId = new Map(sessions.filter((s) => s.finishedAt != null && s.id != null).map((s) => [s.id!, s]))
  const keys = new Set<string>()
  for (const set of sets) {
    const s = byId.get(set.sessionId)
    if (s) keys.add(dedupeKey(s.epochDay, set.exerciseId, set.setIndex))
  }
  return keys
}

export async function previewCsvImport(csvText: string): Promise<CsvPreview> {
  const { source, rows } = parseCsvRows(csvText)
  const [library, existingKeys] = await Promise.all([db.exercises.toArray(), loadExistingDedupeKeys()])
  const exerciseMatches = matchCsvExercises(rows.map((r) => r.rawExerciseName), library)

  const seen = new Set<string>()
  let newSets = 0
  let dupes = 0
  const workouts = new Set<string>()

  for (const r of rows) {
    const matchedId = exerciseMatches.get(r.rawExerciseName)?.matchedId
    if (matchedId) {
      const k = dedupeKey(r.epochDay, matchedId, r.setIndex)
      if (existingKeys.has(k) || seen.has(k)) {
        dupes++
        continue
      }
      seen.add(k)
    }
    newSets++
    workouts.add(`${r.epochDay}|${r.workoutName}`)
  }

  return {
    source,
    rows,
    exerciseMatches,
    totalWorkouts: workouts.size,
    newSetsCount: newSets,
    duplicateSetsCount: dupes,
    existingDedupeKeys: existingKeys,
  }
}

function uniqueCustomId(name: string, existingIds: ReadonlySet<string>): string {
  const slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '') || 'custom_import'
  let cand = `custom_${slug}`
  let n = 2
  while (existingIds.has(cand)) {
    cand = `custom_${slug}_${n++}`
  }
  return cand
}

export async function executeCsvImport(
  preview: CsvPreview,
  mappings: ReadonlyMap<string, ExerciseMapping>,
): Promise<CsvImportResult> {
  return db.transaction('rw', [db.exercises, db.sessions, db.sets], async () => {
    const existingKeys = await loadExistingDedupeKeys()
    const existingExIds = new Set((await db.exercises.toArray()).map((e) => e.id))
    const customIds = new Map<string, string>()

    for (const [rawName, mapping] of mappings) {
      if (mapping.kind === 'create') {
        const cleanName = mapping.name.trim() || rawName
        const id = uniqueCustomId(cleanName, existingExIds)
        existingExIds.add(id)
        await db.exercises.put({
          id,
          name: cleanName,
          kind: mapping.exerciseKind ?? 'WEIGHTED',
          primary: [mapping.primary ?? 'CHEST'],
          secondary: [],
          videoUrl: null,
          custom: true,
          equipment: '',
          pattern: '',
          setupNote: '',
          aliases: '',
          unitOverride: '',
          pinned: false,
          mediaKey: '',
        })
        customIds.set(rawName, id)
      }
    }

    const resolveId = (rawName: string): string | null => {
      const m = mappings.get(rawName)
      if (m) {
        if (m.kind === 'use') return m.exerciseId
        if (m.kind === 'create') return customIds.get(rawName) ?? null
        return null
      }
      return preview.exerciseMatches.get(rawName)?.matchedId ?? null
    }

    let skippedDuplicates = 0
    const byWorkout = new Map<string, { epochDay: number; title: string; items: { exId: string; r: ParsedSetRow }[] }>()
    for (const r of preview.rows) {
      const exId = resolveId(r.rawExerciseName)
      if (!exId) continue
      const key = dedupeKey(r.epochDay, exId, r.setIndex)
      if (existingKeys.has(key)) {
        skippedDuplicates++
        continue
      }
      existingKeys.add(key)
      const wk = `${r.epochDay}|${r.workoutName}`
      const bucket = byWorkout.get(wk) ?? { epochDay: r.epochDay, title: r.workoutName, items: [] }
      bucket.items.push({ exId, r })
      byWorkout.set(wk, bucket)
    }

    let importedWorkouts = 0
    let importedSets = 0

    for (const { epochDay, title, items } of byWorkout.values()) {
      if (!items.length) continue
      const noonMs = epochDay * 86_400_000 + 12 * 3_600_000
      const sessionId = await db.sessions.add({
        planDayId: null,
        title,
        epochDay,
        startedAt: noonMs,
        finishedAt: noonMs + items.length * 120_000,
        note: '',
        debrief: '',
        plannedSets: null,
      })
      let nextPos = 0
      const posByEx = new Map<string, number>()
      const rowsToInsert = items.map(({ exId, r }) => {
        let pos = posByEx.get(exId)
        if (pos == null) { pos = nextPos++; posByEx.set(exId, pos) }
        return {
          sessionId,
          exerciseId: exId,
          itemPosition: pos,
          block: r.setType === 'WARMUP' ? 'Warm-up' : 'Main',
          circuit: false,
          setIndex: r.setIndex,
          targetLabel: r.seconds != null ? `${r.seconds} s` : r.reps != null ? `${r.reps}` : '',
          reps: r.reps,
          seconds: r.seconds,
          weightKg: r.weightKg != null ? Math.round(r.weightKg * 100) / 100 : null,
          done: true,
          isPr: false,
          restSeconds: 60,
          note: r.note,
          effort: '',
          doneAt: noonMs,
          setType: r.setType,
        }
      })
      await db.sets.bulkAdd(rowsToInsert)
      importedWorkouts++
      importedSets += rowsToInsert.length
    }

    return { importedWorkouts, importedSets, skippedDuplicates }
  })
}
