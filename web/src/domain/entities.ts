// Inputs the domain functions read. Field names match the Room entities (app/.../data/db/Entities.kt) and
// PlanDraft.kt, so Dexie rows and backups fit without mapping. Room nullables are `number | null`.
import type { ExerciseKind, Muscle } from './muscle'

export type ExerciseEntity = {
  id: string
  name: string
  kind: ExerciseKind
  primary: Muscle[]
  secondary: Muscle[]
  videoUrl?: string | null
  custom?: boolean
}

export type PlanItemEntity = {
  id: number
  dayId: number
  position: number
  exerciseId: string
  block: string
  circuit: boolean
  sets: number
  repsMin: number | null
  repsMax: number | null
  seconds: number | null
  weightKg: number | null
  restSeconds: number
  note: string
}

export type SessionEntity = {
  id: number
  planDayId: number | null
  title: string
  epochDay: number
  startedAt: number
  finishedAt: number | null
  note: string
  debrief: string
}

export type SetEntity = {
  id: number
  sessionId: number
  exerciseId: string
  itemPosition: number
  block: string
  circuit: boolean
  setIndex: number
  targetLabel: string
  reps: number | null
  seconds: number | null
  weightKg: number | null
  done: boolean
  isPr: boolean
  restSeconds: number
  note: string
  /** A SetEffort name, or empty when not tapped. */
  effort: string
}

export type ReadinessDayEntity = {
  epochDay: number
  recovery: number
  sleepPerf: number
  sleepMin: number
  strain: number
  hrv: number
  rhr: number
  feltGreat: boolean
  updatedAt: number
}

export type PlanDraftItem = {
  exerciseId: string
  name: string | null
  /** "REPS", "HOLD" or "WEIGHTED" */
  kind: string | null
  /** Muscle names, e.g. "CHEST" */
  muscles: string[]
  block: string
  circuit: boolean
  sets: number
  repsMin: number | null
  repsMax: number | null
  seconds: number | null
  weightKg: number | null
  restSeconds: number
  note: string
  videoUrl: string | null
}

export type PlanDraftDay = {
  /** 1 = Monday .. 7 = Sunday */
  dayOfWeek: number
  title: string
  isRest: boolean
  note: string
  items: PlanDraftItem[]
}
