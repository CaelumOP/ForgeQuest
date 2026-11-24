// Raw-rep strength estimate shared by charts and records; mirrors Android Float arithmetic.
const f = Math.fround

export function e1rm(weightKg: number, reps: number): number {
  const weight = f(weightKg)
  if (!Number.isFinite(weight) || weight <= 0 || !Number.isFinite(reps) || reps <= 0) return 0
  if (reps === 1) return weight
  const effectiveReps = reps <= 12 ? f(reps) : f(12 + f(3 * f(Math.log(f(1 + f(reps - 12))))))
  return f(weight * f(1 + f(effectiveReps / 30)))
}
