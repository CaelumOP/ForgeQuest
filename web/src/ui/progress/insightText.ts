import { getLang, intlLocale, plural, t } from '../../i18n'
import type { ExerciseEntity } from '../../domain/entities'
import type { Insight, InsightSlot } from '../../domain/insights'
import { formatWeight, fromKg, type WeightUnit } from '../../domain/units'

const nf = () => new Intl.NumberFormat(intlLocale())

function slotText(slot: InsightSlot, exercises: ReadonlyMap<string, ExerciseEntity>, unit: WeightUnit, ar: boolean): string {
  switch (slot.type) {
    case 'count': return nf().format(slot.n)
    case 'kg': return formatWeight(unit, slot.kg, ar)
    case 'seconds': return `${slot.s} ${t('unit_s')}`
    case 'exercise': return exercises.get(slot.id)?.name ?? slot.id
    case 'cue': return ''
    case 'group': return t(`muscle_${slot.muscle.toLowerCase()}`)
    case 'weekday': {
      // 2024-01-01 was a Monday (dayOfWeek = 1)
      const d = new Date(Date.UTC(2024, 0, slot.day))
      return new Intl.DateTimeFormat(intlLocale(), { weekday: 'long', timeZone: 'UTC' }).format(d)
    }
  }
}

export function formatInsight(
  insight: Insight,
  exercises: ReadonlyMap<string, ExerciseEntity>,
  unit: WeightUnit,
): string {
  const ar = getLang() === 'ar'
  const v = insight.variant + 1
  if (insight.kind === 'CROSS_KG') {
    const kg = insight.slots[0]?.type === 'count' ? insight.slots[0].n : 0
    const key = unit === 'LB' ? `insight_cross_lb_${v}` : `insight_cross_kg_${v}`
    const shown = unit === 'LB' ? Math.round(fromKg(unit, kg)) : kg
    return t(key, nf().format(shown))
  }
  const key = `insight_${insight.kind.toLowerCase()}_${v}`
  const args = insight.slots.map((s) => slotText(s, exercises, unit, ar))
  if (insight.plural) {
    const firstCount = insight.slots.find((s) => s.type === 'count')
    const count = firstCount?.type === 'count' ? firstCount.n : 0
    return plural(key, count, ...args)
  }
  return t(key, ...args)
}
