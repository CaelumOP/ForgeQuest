// Display unit for weights. Everything is stored in kilograms; only the screen converts.
// Kotlin does this math in 32-bit Float, so results go through Math.fround to give the same numbers.

const f = Math.fround

export const WEIGHT_UNITS = ['KG', 'LB'] as const
export type WeightUnit = (typeof WEIGHT_UNITS)[number]

const SPEC: Record<WeightUnit, { code: string; arabic: string; perKg: number; loadStep: number; bodyStep: number }> = {
  KG: { code: 'kg', arabic: 'كغ', perKg: 1, loadStep: 2.5, bodyStep: f(0.1) },
  LB: { code: 'lb', arabic: 'رطل', perKg: f(2.2046225), loadStep: 5, bodyStep: f(0.2) },
}

export const unitCode = (unit: WeightUnit): string => SPEC[unit].code
export const loadStep = (unit: WeightUnit): number => SPEC[unit].loadStep
export const bodyStep = (unit: WeightUnit): number => SPEC[unit].bodyStep

/** The unit as the reader writes it: "kg" / "كغ". */
export function unitLabel(unit: WeightUnit, arabic: boolean): string {
  return arabic ? SPEC[unit].arabic : SPEC[unit].code
}

export function fromKg(unit: WeightUnit, kg: number): number {
  return f(f(kg) * SPEC[unit].perKg)
}

export function toKg(unit: WeightUnit, value: number): number {
  return f(f(value) / SPEC[unit].perKg)
}

/** "72.5 kg" / "160.0 lb", isolated so the number and unit keep their order in right-to-left text. */
export function formatWeight(unit: WeightUnit, kg: number, arabic: boolean, decimals = 1): string {
  const text = `${fromKg(unit, kg).toFixed(decimals)} ${unitLabel(unit, arabic)}`
  return arabic ? `⁧${text}⁩` : `⁦${text}⁩`
}

/** Just the number, e.g. for compact "8×60" labels. */
export function weightNumber(unit: WeightUnit, kg: number, decimals = 1): string {
  const s = fromKg(unit, kg).toFixed(decimals)
  return s.endsWith('.0') ? s.slice(0, -2) : s
}

/** Resolves both canonical codes ("kg", "lb") and localized labels ("كغ", "رطل"). */
export function unitFromCodeOrLabel(raw: string | null | undefined): WeightUnit {
  const cleaned = (raw ?? '').trim()
  const lower = cleaned.toLowerCase()
  return lower === 'lb' || lower === 'lbs' || cleaned === 'رطل' ? 'LB' : 'KG'
}
