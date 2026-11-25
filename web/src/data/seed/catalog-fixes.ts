// Built-in exercises whose equipment or movement group was wrong in an earlier catalog (plan audit 2026-11-25).
// Same table as Android data/seed/CatalogFixes.kt. Seeding rewrites a row only while it still holds the old value.
import type { ExerciseEntity } from '../../domain/entities'

export type CatalogFix = { id: string; oldEquipment: string; oldPattern: string; equipment: string; pattern: string }

export const CATALOG_METADATA_FIXES: readonly CatalogFix[] = [
  { id: 'sled_overhead_backward_walk', oldEquipment: 'BODYWEIGHT', oldPattern: 'VERTICAL_PUSH', equipment: 'MACHINE', pattern: 'CARRY_CONDITIONING' },
  { id: 'sled_reverse_flye', oldEquipment: 'BODYWEIGHT', oldPattern: 'UPPER_BACK_PULL', equipment: 'MACHINE', pattern: 'UPPER_BACK_PULL' },
  { id: 'sled_row', oldEquipment: 'BODYWEIGHT', oldPattern: 'HORIZONTAL_PULL', equipment: 'MACHINE', pattern: 'HORIZONTAL_PULL' },
  { id: 'sled_overhead_triceps_extension', oldEquipment: 'BODYWEIGHT', oldPattern: 'ELBOW_EXTENSION', equipment: 'MACHINE', pattern: 'ELBOW_EXTENSION' },
  { id: 'sled_push', oldEquipment: 'BODYWEIGHT', oldPattern: 'CARRY_CONDITIONING', equipment: 'MACHINE', pattern: 'CARRY_CONDITIONING' },
  { id: 'sledgehammer_swings', oldEquipment: 'BODYWEIGHT', oldPattern: 'CARRY_CONDITIONING', equipment: 'MACHINE', pattern: 'CARRY_CONDITIONING' },
  { id: 'kipping_muscle_up', oldEquipment: 'BODYWEIGHT', oldPattern: 'HORIZONTAL_PULL', equipment: 'PULL_UP_BAR', pattern: 'VERTICAL_PULL' },
  { id: 'isometric_neck_exercise_front_and_back', oldEquipment: 'BODYWEIGHT', oldPattern: 'UPPER_BACK_PULL', equipment: 'BODYWEIGHT', pattern: 'MOBILITY' },
  { id: 'isometric_neck_exercise_sides', oldEquipment: 'BODYWEIGHT', oldPattern: 'UPPER_BACK_PULL', equipment: 'BODYWEIGHT', pattern: 'MOBILITY' },
  { id: 'seated_head_harness_neck_resistance', oldEquipment: 'BODYWEIGHT', oldPattern: 'UPPER_BACK_PULL', equipment: 'BODYWEIGHT', pattern: 'MOBILITY' },
  { id: 'lying_face_down_plate_neck_resistance', oldEquipment: 'BARBELL', oldPattern: 'UPPER_BACK_PULL', equipment: 'BARBELL', pattern: 'MOBILITY' },
  { id: 'lying_face_up_plate_neck_resistance', oldEquipment: 'BARBELL', oldPattern: 'UPPER_BACK_PULL', equipment: 'BARBELL', pattern: 'MOBILITY' },
]

export const CATALOG_SECONDARY_FIXES: Readonly<Record<string, { old: readonly string[]; next: readonly string[] }>> = {
  db_bench_press: { old: ['TRICEPS'], next: ['TRICEPS', 'SHOULDERS'] },
}

/** The stored row with the catalog fixes applied, or the same object when nothing applies (custom rows are never touched). */
export function applyCatalogFixes<T extends Pick<ExerciseEntity, 'id' | 'custom' | 'equipment' | 'pattern' | 'secondary'>>(row: T): T {
  if (row.custom) return row
  let out = row
  const fix = CATALOG_METADATA_FIXES.find((f) => f.id === row.id)
  if (fix) {
    const equipment = row.equipment === fix.oldEquipment ? fix.equipment : row.equipment
    const pattern = row.pattern === fix.oldPattern ? fix.pattern : row.pattern
    if (equipment !== row.equipment || pattern !== row.pattern) out = { ...out, equipment, pattern }
  }
  const sec = CATALOG_SECONDARY_FIXES[row.id]
  if (sec && row.secondary.length === sec.old.length && row.secondary.every((m, i) => m === sec.old[i])) {
    out = { ...out, secondary: [...sec.next] as T['secondary'] }
  }
  return out
}
