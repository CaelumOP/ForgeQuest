import type { PlanDraftDay, PlanDraftItem } from '../../domain/entities'

/** A plan before it's saved (mirrors app/.../data/plan/PlanDraft.kt; the TS PlanDraft has only these fields). */
export type PlanDraft = {
  name: string
  description: string
  days: PlanDraftDay[]
}

/**
 * Ready-made plans offered during onboarding, mechanically converted from app/.../data/seed/StarterPlans.kt.
 * The Kotlin helpers (warmUp, core, main, legs, reps, hold) are expanded into plain objects with the same values.
 */

/** Hemo Calisthenics' 5-day foundation routine (see docs/hemo-foundation.md in the Android repo). */
export const HEMO_FOUNDATION: PlanDraft = {
  name: 'Hemo · Foundation level',
  description: "Hemo Calisthenics' 5-day foundation routine. Jumping jacks to warm up, then each block as a circuit for 3–4 rounds. Do the reps you can; the number is the target.",
  days: [
    {
      dayOfWeek: 1,
      title: 'Day 1 · Core + upper body',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'jumping_jacks', name: null, kind: null, muscles: [], block: 'Warm-up', circuit: false, sets: 5, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 20, note: '', videoUrl: null },
        { exerciseId: 'incline_hollow_plank', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: null, repsMax: null, seconds: 30, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'hip_bridge', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'crunch', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'superman_hold', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: null, repsMax: null, seconds: 20, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'knee_scapular_push_up', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'scapular_australian_pull_up', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'bent_leg_bench_dip', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'scapular_australian_chin_up', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
      ],
    },
    {
      dayOfWeek: 2,
      title: 'Day 2 · Upper body',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'jumping_jacks', name: null, kind: null, muscles: [], block: 'Warm-up', circuit: false, sets: 5, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 20, note: '', videoUrl: null },
        { exerciseId: 'knee_push_up', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'australian_pull_up', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'reverse_grip_bench_dip', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'australian_chin_up', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
      ],
    },
    {
      dayOfWeek: 3,
      title: 'Day 3 · Core + legs',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'jumping_jacks', name: null, kind: null, muscles: [], block: 'Warm-up', circuit: false, sets: 5, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 20, note: '', videoUrl: null },
        { exerciseId: 'incline_hollow_plank', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: null, repsMax: null, seconds: 30, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'incline_reverse_plank', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: null, repsMax: null, seconds: 30, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'crunch', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'superman_hold', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: null, repsMax: null, seconds: 20, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'half_squat', name: null, kind: null, muscles: [], block: 'Legs', circuit: true, sets: 3, repsMin: 10, repsMax: 15, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'half_wide_squat', name: null, kind: null, muscles: [], block: 'Legs', circuit: true, sets: 3, repsMin: 10, repsMax: 15, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'half_narrow_squat', name: null, kind: null, muscles: [], block: 'Legs', circuit: true, sets: 3, repsMin: 10, repsMax: 15, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'hip_bridge', name: null, kind: null, muscles: [], block: 'Legs', circuit: true, sets: 3, repsMin: 10, repsMax: 15, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'calf_raise_turned_out', name: null, kind: null, muscles: [], block: 'Legs', circuit: true, sets: 3, repsMin: 20, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'calf_raise', name: null, kind: null, muscles: [], block: 'Legs', circuit: true, sets: 3, repsMin: 20, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
      ],
    },
    {
      dayOfWeek: 4,
      title: 'Rest day',
      isRest: true,
      note: 'Full rest. Hemo recommends a complete break after day 3.',
      items: [],
    },
    {
      dayOfWeek: 5,
      title: 'Day 4 · Core + upper body',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'jumping_jacks', name: null, kind: null, muscles: [], block: 'Warm-up', circuit: false, sets: 5, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 20, note: '', videoUrl: null },
        { exerciseId: 'incline_hollow_plank', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: null, repsMax: null, seconds: 30, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'hip_bridge', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'crunch', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'superman_hold', name: null, kind: null, muscles: [], block: 'Core', circuit: true, sets: 3, repsMin: null, repsMax: null, seconds: 20, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'wide_knee_push_up', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'dead_hang', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: null, repsMax: null, seconds: 20, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'bent_leg_bench_dip', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: 15, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'australian_chin_up', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
      ],
    },
    {
      dayOfWeek: 6,
      title: 'Day 5 · Upper body',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'jumping_jacks', name: null, kind: null, muscles: [], block: 'Warm-up', circuit: false, sets: 5, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 20, note: '', videoUrl: null },
        { exerciseId: 'incline_diamond_knee_push_up', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 8, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'australian_pull_up', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'reverse_grip_bench_dip', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: 15, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
        { exerciseId: 'australian_chin_up', name: null, kind: null, muscles: [], block: 'Main', circuit: true, sets: 3, repsMin: 10, repsMax: null, seconds: null, weightKg: null, restSeconds: 30, note: '', videoUrl: null },
      ],
    },
    {
      dayOfWeek: 7,
      title: 'Rest day',
      isRest: true,
      note: 'Full rest, then repeat the week. Once this feels easy, try a harder variation of each move.',
      items: [],
    },
  ],
}

/** Simple full-body plan: Monday, Wednesday, Friday. */
export const FULL_BODY_BEGINNER: PlanDraft = {
  name: 'Full body · 3 days · bodyweight',
  description: 'Simple bodyweight plan: Monday, Wednesday, Friday. Bodyweight only; the rows need a low bar or a sturdy table.',
  days: [
    {
      dayOfWeek: 1,
      title: 'Full body',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'push_up', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'australian_pull_up', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'squat', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 12, repsMax: 15, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'hip_bridge', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 12, repsMax: null, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
        { exerciseId: 'plank', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: null, repsMax: null, seconds: 30, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
      ],
    },
    { dayOfWeek: 2, title: 'Rest day', isRest: true, note: '', items: [] },
    {
      dayOfWeek: 3,
      title: 'Full body',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'push_up', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'australian_pull_up', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'squat', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 12, repsMax: 15, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'hip_bridge', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 12, repsMax: null, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
        { exerciseId: 'plank', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: null, repsMax: null, seconds: 30, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
      ],
    },
    { dayOfWeek: 4, title: 'Rest day', isRest: true, note: '', items: [] },
    {
      dayOfWeek: 5,
      title: 'Full body',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'push_up', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'australian_pull_up', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'squat', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 12, repsMax: 15, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'hip_bridge', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 12, repsMax: null, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
        { exerciseId: 'plank', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: null, repsMax: null, seconds: 30, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
      ],
    },
    { dayOfWeek: 6, title: 'Rest day', isRest: true, note: '', items: [] },
    { dayOfWeek: 7, title: 'Rest day', isRest: true, note: '', items: [] },
  ],
}

/** The same three days with dumbbells, for a gym or a home set: what "Full gym" people expect to see. */
const dumbbellDay = (dayOfWeek: number): PlanDraftDay => ({
  dayOfWeek,
  title: 'Full body',
  isRest: false,
  note: '',
  items: [
    { exerciseId: 'goblet_squat', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
    { exerciseId: 'db_bench_press', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
    { exerciseId: 'db_row', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
    { exerciseId: 'db_shoulder_press', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
    { exerciseId: 'plank', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: null, repsMax: null, seconds: 30, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
  ],
})
export const FULL_BODY_DUMBBELLS: PlanDraft = {
  name: 'Full body · 3 days · dumbbells',
  description: 'Simple full-body plan with dumbbells: Monday, Wednesday, Friday. For a gym or a home set.',
  days: [1, 2, 3, 4, 5, 6, 7].map((d) => (d % 2 === 1 && d < 7 ? dumbbellDay(d) : { dayOfWeek: d, title: 'Rest day', isRest: true, note: '', items: [] })),
}

/** 6-day gym split. Each muscle twice a week. */
export const PUSH_PULL_LEGS: PlanDraft = {
  name: 'Push · Pull · Legs (gym)',
  description: '6-day gym split. Each muscle twice a week.',
  days: [
    {
      dayOfWeek: 1,
      title: 'Push',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'bench_press', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 4, repsMin: 6, repsMax: 10, seconds: null, weightKg: null, restSeconds: 150, note: '', videoUrl: null },
        { exerciseId: 'overhead_press', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: null, restSeconds: 120, note: '', videoUrl: null },
        { exerciseId: 'incline_db_press', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'lateral_raise', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 12, repsMax: 15, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
        { exerciseId: 'tricep_pushdown', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 15, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
      ],
    },
    {
      dayOfWeek: 2,
      title: 'Pull',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'pull_up', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 4, repsMin: 6, repsMax: 10, seconds: null, weightKg: null, restSeconds: 150, note: '', videoUrl: null },
        { exerciseId: 'barbell_row', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: null, restSeconds: 120, note: '', videoUrl: null },
        { exerciseId: 'lat_pulldown', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'face_pull', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 12, repsMax: 15, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
        { exerciseId: 'bicep_curl', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
      ],
    },
    {
      dayOfWeek: 3,
      title: 'Legs',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'back_squat', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 4, repsMin: 6, repsMax: 10, seconds: null, weightKg: null, restSeconds: 180, note: '', videoUrl: null },
        { exerciseId: 'romanian_deadlift', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: null, restSeconds: 120, note: '', videoUrl: null },
        { exerciseId: 'leg_press', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'leg_curl', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
        { exerciseId: 'standing_calf_raise', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 4, repsMin: 12, repsMax: 15, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
      ],
    },
    {
      dayOfWeek: 4,
      title: 'Push',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'bench_press', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 4, repsMin: 6, repsMax: 10, seconds: null, weightKg: null, restSeconds: 150, note: '', videoUrl: null },
        { exerciseId: 'overhead_press', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: null, restSeconds: 120, note: '', videoUrl: null },
        { exerciseId: 'incline_db_press', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'lateral_raise', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 12, repsMax: 15, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
        { exerciseId: 'tricep_pushdown', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 15, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
      ],
    },
    {
      dayOfWeek: 5,
      title: 'Pull',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'pull_up', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 4, repsMin: 6, repsMax: 10, seconds: null, weightKg: null, restSeconds: 150, note: '', videoUrl: null },
        { exerciseId: 'barbell_row', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: null, restSeconds: 120, note: '', videoUrl: null },
        { exerciseId: 'lat_pulldown', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'face_pull', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 12, repsMax: 15, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
        { exerciseId: 'bicep_curl', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
      ],
    },
    {
      dayOfWeek: 6,
      title: 'Legs',
      isRest: false,
      note: '',
      items: [
        { exerciseId: 'back_squat', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 4, repsMin: 6, repsMax: 10, seconds: null, weightKg: null, restSeconds: 180, note: '', videoUrl: null },
        { exerciseId: 'romanian_deadlift', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 8, repsMax: 10, seconds: null, weightKg: null, restSeconds: 120, note: '', videoUrl: null },
        { exerciseId: 'leg_press', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 90, note: '', videoUrl: null },
        { exerciseId: 'leg_curl', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 3, repsMin: 10, repsMax: 12, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
        { exerciseId: 'standing_calf_raise', name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets: 4, repsMin: 12, repsMax: 15, seconds: null, weightKg: null, restSeconds: 60, note: '', videoUrl: null },
      ],
    },
    { dayOfWeek: 7, title: 'Rest day', isRest: true, note: '', items: [] },
  ],
}

/** All starter plans in the Kotlin order (StarterPlans.all, before localization). */

/** Positions in [STARTER_PLANS], for onboarding's equipment ↔ plan matching. */
export const BODYWEIGHT_INDEX = 1
export const DUMBBELLS_INDEX = 2
export const GYM_SPLIT_INDEX = 3

// ---- More plans, offered next to the onboarding four in the plan library ----

const lift = (exerciseId: string, sets: number, repsMin: number, repsMax: number, restSeconds = 90): PlanDraftItem => ({
  exerciseId, name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets, repsMin, repsMax, seconds: null, weightKg: null, restSeconds, note: '', videoUrl: null,
})

const holdSet = (exerciseId: string, sets: number, seconds: number, restSeconds = 60): PlanDraftItem => ({
  exerciseId, name: null, kind: null, muscles: [], block: 'Main', circuit: false, sets, repsMin: null, repsMax: null, seconds, weightKg: null, restSeconds, note: '', videoUrl: null,
})

/** A week from `trained` (1 = Monday): every other day is a rest day. */
const week = (trained: Record<number, [string, PlanDraftItem[]]>): PlanDraftDay[] =>
  [1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => {
    const t = trained[dayOfWeek]
    return t
      ? { dayOfWeek, title: t[0], isRest: false, note: '', items: t[1] }
      : { dayOfWeek, title: 'Rest day', isRest: true, note: '', items: [] }
  })

export const FULL_BODY_GYM: PlanDraft = {
  name: 'Full body · 3 days · gym',
  description: 'Barbell and machine plan: Monday, Wednesday, Friday. A squat or hinge, a press and a pull every session.',
  days: week({
    1: ['Full body', [
      lift('back_squat', 3, 6, 8, 150), lift('bench_press', 3, 6, 8, 150), lift('barbell_row', 3, 8, 10, 120),
      lift('lateral_raise', 2, 12, 15, 60), holdSet('plank', 3, 30),
    ]],
    3: ['Full body', [
      lift('romanian_deadlift', 3, 8, 10, 120), lift('overhead_press', 3, 6, 8, 120), lift('lat_pulldown', 3, 8, 12),
      lift('leg_press', 3, 10, 12), lift('bicep_curl', 2, 10, 12, 60),
    ]],
    5: ['Full body', [
      lift('back_squat', 3, 8, 10, 120), lift('incline_db_press', 3, 8, 12), lift('pull_up', 3, 6, 10, 120),
      lift('leg_curl', 3, 10, 12, 60), lift('tricep_pushdown', 2, 10, 15, 60),
    ]],
  }),
}

export const UPPER_LOWER_GYM: PlanDraft = {
  name: 'Upper / lower · 4 days · gym',
  description: '4-day gym split: upper body on Monday and Thursday, lower body on Tuesday and Friday.',
  days: week({
    1: ['Upper body', [
      lift('bench_press', 4, 6, 8, 150), lift('barbell_row', 4, 6, 8, 150), lift('overhead_press', 3, 8, 10, 120),
      lift('lat_pulldown', 3, 10, 12), lift('bicep_curl', 2, 10, 12, 60), lift('tricep_pushdown', 2, 10, 12, 60),
    ]],
    2: ['Lower body', [
      lift('back_squat', 4, 6, 8, 180), lift('romanian_deadlift', 3, 8, 10, 120), lift('leg_press', 3, 10, 12),
      lift('leg_curl', 3, 10, 12, 60), lift('standing_calf_raise', 3, 12, 15, 60),
    ]],
    4: ['Upper body', [
      lift('incline_db_press', 3, 8, 12), lift('pull_up', 3, 6, 10, 120), lift('lateral_raise', 3, 12, 15, 60),
      lift('face_pull', 3, 12, 15, 60), lift('hammer_curl', 2, 10, 12, 60), lift('skull_crusher', 2, 10, 12, 60),
    ]],
    5: ['Lower body', [
      lift('hip_thrust', 3, 8, 10, 120), lift('leg_press', 3, 10, 15), lift('bulgarian_split_squat', 3, 8, 10, 90),
      lift('leg_curl', 3, 10, 12, 60), lift('standing_calf_raise', 3, 12, 15, 60),
    ]],
  }),
}

export const UPPER_LOWER_DUMBBELLS: PlanDraft = {
  name: 'Upper / lower · 4 days · dumbbells',
  description: '4-day plan with dumbbells and your body weight: upper body on Monday and Thursday, lower body on Tuesday and Friday.',
  days: week({
    1: ['Upper body', [
      lift('db_bench_press', 3, 8, 12), lift('db_row', 3, 8, 12), lift('db_shoulder_press', 3, 8, 12),
      lift('hammer_curl', 2, 10, 12, 60), lift('push_up', 2, 10, 15, 60),
    ]],
    2: ['Lower body', [
      lift('goblet_squat', 3, 8, 12), lift('db_romanian_deadlift', 3, 8, 12), lift('db_lunge', 3, 8, 10),
      lift('db_calf_raise', 3, 12, 15, 60), holdSet('plank', 3, 30),
    ]],
    4: ['Upper body', [
      lift('incline_db_press', 3, 8, 12), lift('db_row', 3, 10, 12), lift('lateral_raise', 3, 12, 15, 60),
      lift('bicep_curl', 2, 10, 12, 60), lift('diamond_push_up', 2, 8, 12, 60),
    ]],
    5: ['Lower body', [
      lift('bulgarian_split_squat', 3, 8, 10), lift('single_leg_rdl', 3, 8, 10), lift('goblet_squat', 3, 10, 15),
      lift('hip_bridge', 3, 12, 15, 60), holdSet('side_plank', 2, 30),
    ]],
  }),
}

export const CALISTHENICS_LEVEL_2: PlanDraft = {
  name: 'Calisthenics · level 2',
  description: 'Needs a pull-up bar and dip bars. Harder push-ups, pull-ups and one-leg moves, plus L-sit holds, four days a week.',
  days: week({
    1: ['Push + core', [
      lift('diamond_push_up', 4, 8, 12), lift('pseudo_planche_push_up', 3, 5, 8), lift('dip', 3, 6, 10, 120),
      lift('pike_push_up', 3, 6, 10), holdSet('l_sit', 4, 10),
    ]],
    2: ['Pull + legs', [
      lift('pull_up', 4, 5, 8, 120), lift('chin_up', 3, 5, 8, 120), lift('cossack_squat', 3, 6, 8),
      lift('bulgarian_split_squat', 3, 8, 10), lift('pistol_squat', 3, 3, 5, 120),
    ]],
    4: ['Push + core', [
      lift('dip', 4, 6, 10, 120), lift('diamond_push_up', 3, 10, 15), lift('pike_push_up', 3, 8, 10),
      lift('hanging_knee_raise', 3, 8, 12), holdSet('hollow_hold', 3, 30),
    ]],
    5: ['Pull + legs', [
      lift('chin_up', 4, 5, 8, 120), lift('pull_up', 3, 5, 8, 120), lift('pistol_squat', 4, 3, 5, 120),
      lift('cossack_squat', 3, 8, 10), holdSet('l_sit', 4, 12),
    ]],
  }),
}

export const MINIMAL_TWO_DAY: PlanDraft = {
  name: 'Minimal · 2 days',
  description: 'Two short full-body sessions a week with one pair of dumbbells: Monday and Thursday. Enough to keep strength going.',
  days: week({
    1: ['Full body', [
      lift('goblet_squat', 3, 8, 12), lift('db_bench_press', 3, 8, 12), lift('db_row', 3, 8, 12), holdSet('plank', 2, 30),
    ]],
    4: ['Full body', [
      lift('db_romanian_deadlift', 3, 8, 12), lift('db_shoulder_press', 3, 8, 12), lift('db_row', 3, 10, 12), holdSet('side_plank', 2, 30),
    ]],
  }),
}

export const ZERO_EQUIPMENT: PlanDraft = {
  name: 'Zero equipment · 3 days',
  description: 'No equipment at all, no bar and no table: Monday, Wednesday, Friday. Floor, wall and your body weight.',
  days: week({
    1: ['Full body', [
      lift('push_up', 3, 8, 12, 60), lift('squat', 3, 12, 15, 60), lift('hip_bridge', 3, 12, 15, 45),
      holdSet('superman_hold', 3, 20, 45), holdSet('plank', 3, 30, 45),
    ]],
    3: ['Full body', [
      lift('wide_knee_push_up', 3, 10, 15, 60), lift('lunge', 3, 8, 12, 60), lift('calf_raise', 3, 15, 20, 45),
      lift('bird_dog', 3, 8, 12, 45), lift('mountain_climber', 3, 20, 30, 45),
    ]],
    5: ['Full body', [
      lift('push_up', 3, 8, 12, 60), lift('cossack_squat', 3, 6, 10, 60), lift('hip_bridge', 3, 12, 15, 45),
      holdSet('side_plank', 2, 30, 45), holdSet('hollow_hold', 3, 20, 45),
    ]],
  }),
}

/** The extra plans, shown after STARTER_PLANS in the plan library; onboarding does not use them. */
export const MORE_PLANS: PlanDraft[] = [FULL_BODY_GYM, UPPER_LOWER_GYM, UPPER_LOWER_DUMBBELLS, CALISTHENICS_LEVEL_2, MINIMAL_TWO_DAY, ZERO_EQUIPMENT]

/** Every starter plan: the onboarding four (StarterPlans.all) first, then the library extras (StarterPlans.more). */
export const STARTER_PLANS: PlanDraft[] = [HEMO_FOUNDATION, FULL_BODY_BEGINNER, FULL_BODY_DUMBBELLS, PUSH_PULL_LEGS, ...MORE_PLANS]

/** How many of [STARTER_PLANS] onboarding offers (the rest are library-only). */
export const ONBOARDING_PLAN_COUNT = 4

/** English starter texts and their formal Arabic versions (from StarterPlans.kt `arabic`). */
export const STARTER_PLANS_AR: Record<string, string> = {
  'Hemo · Foundation level': 'هيمو · المستوى التأسيسي',
  'Hemo Calisthenics\' 5-day foundation routine. Jumping jacks to warm up, then each block as a circuit for 3–4 rounds. Do the reps you can; the number is the target.': 'روتين Hemo Calisthenics التأسيسي على خمسة أيام: قفز مع فتح الذراعين للإحماء، ثم كل مجموعة تمارين في دائرة من ⁦3–4⁩ جولات. أدِّ ما تستطيع من تكرارات؛ فالرقم هدف لا شرط.',
  'Full body · 3 days': 'الجسم كاملًا · 3 أيام',
  'Simple full-body plan: Monday, Wednesday, Friday. Good for gym or home.': 'خطة بسيطة للجسم كاملًا: الاثنين والأربعاء والجمعة، تصلح للنادي أو المنزل.',
  'Full body · 3 days · bodyweight': 'الجسم كاملًا · 3 أيام · وزن الجسم',
  'Simple bodyweight plan: Monday, Wednesday, Friday. Bodyweight only; the rows need a low bar or a sturdy table.': 'خطة بسيطة بوزن الجسم: الاثنين والأربعاء والجمعة، بوزن الجسم فقط، وتحتاج تمارين السحب إلى عارضة منخفضة أو طاولة متينة.',
  'Full body · 3 days · dumbbells': 'الجسم كاملًا · 3 أيام · دمبل',
  'Simple full-body plan with dumbbells: Monday, Wednesday, Friday. For a gym or a home set.': 'خطة بسيطة للجسم كاملًا بالدمبل: الاثنين والأربعاء والجمعة، للنادي أو للمعدات المنزلية.',
  'Push · Pull · Legs (gym)': 'دفع · سحب · أرجل (النادي)',
  '6-day gym split. Each muscle twice a week.': 'تقسيم على ستة أيام في النادي، تُمرَّن فيه كل عضلة مرتين أسبوعيًا.',
  'Day 1 · Core + upper body': 'اليوم 1 · الجذع والجزء العلوي',
  'Day 2 · Upper body': 'اليوم 2 · الجزء العلوي',
  'Day 3 · Core + legs': 'اليوم 3 · الجذع والأرجل',
  'Day 4 · Core + upper body': 'اليوم 4 · الجذع والجزء العلوي',
  'Day 5 · Upper body': 'اليوم 5 · الجزء العلوي',
  'Rest day': 'يوم راحة',
  'Full body': 'الجسم كاملًا',
  'Push': 'دفع',
  'Pull': 'سحب',
  'Legs': 'أرجل',
  'Full rest. Hemo recommends a complete break after day 3.': 'راحة تامة؛ يوصي Hemo براحة كاملة بعد اليوم الثالث.',
  'Full body · 3 days · gym': 'الجسم كاملًا · 3 أيام · النادي',
  'Barbell and machine plan: Monday, Wednesday, Friday. A squat or hinge, a press and a pull every session.': 'خطة بالبار والأجهزة: الاثنين والأربعاء والجمعة، وفي كل حصة قرفصاء أو انثناء للورك، ثم تمرين دفع وآخر سحب.',
  'Upper / lower · 4 days · gym': 'علوي / سفلي · 4 أيام · النادي',
  '4-day gym split: upper body on Monday and Thursday, lower body on Tuesday and Friday.': 'تقسيم على أربعة أيام في النادي: الجزء العلوي الاثنين والخميس، والجزء السفلي الثلاثاء والجمعة.',
  'Upper / lower · 4 days · dumbbells': 'علوي / سفلي · 4 أيام · دمبل',
  '4-day plan with dumbbells and your body weight: upper body on Monday and Thursday, lower body on Tuesday and Friday.': 'خطة على أربعة أيام بالدمبل ووزن الجسم: الجزء العلوي الاثنين والخميس، والجزء السفلي الثلاثاء والجمعة.',
  'Calisthenics · level 2': 'كاليستنكس · المستوى الثاني',
  'Needs a pull-up bar and dip bars. Harder push-ups, pull-ups and one-leg moves, plus L-sit holds, four days a week.': 'تحتاج إلى عقلة ومتوازي. ضغط وعقلة وتمارين الساق الواحدة بصعوبة أعلى، مع ثبات الجلوس على شكل L، أربعة أيام أسبوعيًا.',
  'Minimal · 2 days': 'الحد الأدنى · يومان',
  'Two short full-body sessions a week with one pair of dumbbells: Monday and Thursday. Enough to keep strength going.': 'حصتان قصيرتان أسبوعيًا للجسم كاملًا بزوج دمبل واحد: الاثنين والخميس، وهما كافيتان للحفاظ على القوة.',
  'Zero equipment · 3 days': 'بلا معدات · 3 أيام',
  'No equipment at all, no bar and no table: Monday, Wednesday, Friday. Floor, wall and your body weight.': 'بلا أي معدات، لا عقلة ولا طاولة: الاثنين والأربعاء والجمعة. الأرض والحائط ووزن جسمك فقط.',
  'Upper body': 'الجزء العلوي',
  'Lower body': 'الجزء السفلي',
  'Push + core': 'دفع والجذع',
  'Pull + legs': 'سحب وأرجل',
  'Full rest, then repeat the week. Once this feels easy, try a harder variation of each move.': 'راحة تامة، ثم كرّر الأسبوع. متى صار هذا سهلًا فجرّب نسخة أصعب من كل تمرين.',
}

/** Mirrors Kotlin StarterPlans.localize(): in Arabic, translates names, descriptions and day titles/notes; anything not in the map stays as it is. */
export function localizePlan(p: PlanDraft, lang: 'en' | 'ar'): PlanDraft {
  if (lang !== 'ar') return p
  const t = (s: string) => STARTER_PLANS_AR[s] ?? s
  return {
    ...p,
    name: t(p.name),
    description: t(p.description),
    days: p.days.map((d) => ({ ...d, title: t(d.title), note: t(d.note) })),
  }
}
