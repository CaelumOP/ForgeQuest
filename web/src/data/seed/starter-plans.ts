import type { PlanDraftDay } from '../../domain/entities'

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
  description: "Hemo Calisthenics' 5-day foundation routine. Stretch, then jumping jacks, then each block as a circuit for 3–4 rounds. Do the reps you can; the number is the target.",
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
      note: 'Full rest, then repeat the week. Move to the Beginner level once this feels easy.',
      items: [],
    },
  ],
}

/** Simple full-body plan: Monday, Wednesday, Friday. */
export const FULL_BODY_BEGINNER: PlanDraft = {
  name: 'Full body · 3 days · bodyweight',
  description: 'Simple bodyweight plan: Monday, Wednesday, Friday. Needs little or no equipment.',
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
export const STARTER_PLANS: PlanDraft[] = [HEMO_FOUNDATION, FULL_BODY_BEGINNER, FULL_BODY_DUMBBELLS, PUSH_PULL_LEGS]

/** Positions in [STARTER_PLANS], for onboarding's equipment ↔ plan matching. */
export const BODYWEIGHT_INDEX = 1
export const DUMBBELLS_INDEX = 2
export const GYM_SPLIT_INDEX = 3

/** English starter texts and their formal Arabic versions (from StarterPlans.kt `arabic`). */
export const STARTER_PLANS_AR: Record<string, string> = {
  'Hemo · Foundation level': 'هيمو · المستوى التأسيسي',
  'Hemo Calisthenics\' 5-day foundation routine. Stretch, then jumping jacks, then each block as a circuit for 3–4 rounds. Do the reps you can; the number is the target.': 'روتين Hemo Calisthenics التأسيسي على خمسة أيام: إطالة، ثم قفز مع فتح الذراعين، ثم كل مجموعة تمارين في دائرة من ⁦3–4⁩ جولات. أدِّ ما تستطيع من تكرارات؛ فالرقم هدف لا شرط.',
  'Full body · 3 days': 'الجسم كاملًا · 3 أيام',
  'Simple full-body plan: Monday, Wednesday, Friday. Good for gym or home.': 'خطة بسيطة للجسم كاملًا: الاثنين والأربعاء والجمعة، تصلح للنادي أو المنزل.',
  'Full body · 3 days · bodyweight': 'الجسم كاملًا · 3 أيام · وزن الجسم',
  'Simple bodyweight plan: Monday, Wednesday, Friday. Needs little or no equipment.': 'خطة بسيطة بوزن الجسم: الاثنين والأربعاء والجمعة، لا تحتاج إلا القليل من المعدات أو لا شيء.',
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
  'Full rest, then repeat the week. Move to the Beginner level once this feels easy.': 'راحة تامة، ثم كرّر الأسبوع. انتقل إلى مستوى المبتدئين متى صار هذا سهلًا.',
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
