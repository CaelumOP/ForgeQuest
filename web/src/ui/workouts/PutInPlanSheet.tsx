import { useState } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import type { ExerciseRow, PlanItemRow } from '../../data/db'
import type { ActivePlan, PlanDayWithItems } from '../../data/workouts'
import { replaceOrder } from '../../domain/muscle-exercises'
import { Button, IconButton, Sheet } from '../kit'
import { BackChevron, ForwardChevron } from '../icons'
import { AddIcon } from './glyphs'
import { dayName, musclesLabel } from './helpers'

/**
 * "Put in my plan" from an exercise page: pick a plan day, then the exercise it replaces (the same movement first,
 * sets and reps carried over by the swap engine) or add it to that day.
 */
export function PutInPlanSheet({ ex, plan, library, onReplace, onAdd, onClose }: {
  ex: ExerciseRow
  plan: ActivePlan
  library: ExerciseRow[]
  onReplace: (day: PlanDayWithItems, item: PlanItemRow, old: ExerciseRow) => void
  onAdd: (day: PlanDayWithItems) => void
  onClose: () => void
}) {
  const lang = getLang()
  const [dow, setDow] = useState<number | null>(null)
  const day = dow == null ? undefined : plan.days.find((d) => d.day.dayOfWeek === dow)
  const byId = new Map(library.map((e) => [e.id, e]))

  if (!day) {
    return (
      <Sheet open onClose={onClose} title={t('plan_put_in_title', ex.name)}>
        <p class="wk-s">{t('plan_put_in_pick_day')}</p>
        {[...plan.days].sort((a, b) => a.day.dayOfWeek - b.day.dayOfWeek).map((d) => {
          const workout = !d.day.isRest && d.items.length > 0
          const subtitle = [workout ? d.day.title : t('today_rest_day'), d.items.some((i) => i.exerciseId === ex.id) ? t('plan_put_in_already') : null].filter(Boolean).join(' · ')
          return (
            <button key={d.day.id} type="button" class="wk-pick" onClick={() => setDow(d.day.dayOfWeek)}>
              <span class="wk-pick-text"><span class="wk-t">{dayName(d.day.dayOfWeek, lang)}</span><span class="wk-s">{subtitle}</span></span>
              <ForwardChevron />
            </button>
          )
        })}
      </Sheet>
    )
  }

  // One row per plan item, in the order the swap engine ranks their exercises against this one.
  const rank = replaceOrder(ex, day.items.map((i) => byId.get(i.exerciseId)).filter((e): e is ExerciseRow => e != null))
  const rows = rank.flatMap(([e, close]) => day.items.filter((i) => i.exerciseId === e.id).map((item) => ({ item, e: byId.get(e.id)!, close })))
  return (
    <Sheet open onClose={onClose} title={t('plan_put_in_title', ex.name)}>
      <div class="wk-pick-head">
        <IconButton label={t('cd_back')} onClick={() => setDow(null)}><span class="back"><BackChevron /></span></IconButton>
        <span class="wk-t">{dayName(day.day.dayOfWeek, lang)}</span>
      </div>
      {rows.length > 0 && <h3 class="wk-pick-sec">{t('plan_put_in_replace')}</h3>}
      {rows.map(({ item, e, close }) => (
        <button key={item.id} type="button" class="wk-pick" onClick={() => onReplace(day, item, e)}>
          <span class="wk-pick-text">
            <span class="wk-t">{e.name}</span>
            <span class="wk-s" style={close ? { color: 'var(--teal)' } : undefined}>{close ? t('plan_put_in_same_movement') : musclesLabel(e)}</span>
          </span>
          <ForwardChevron />
        </button>
      ))}
      <div style={{ marginBlockStart: '12px' }}>
        <Button variant="tonal" block icon={<AddIcon />} onClick={() => onAdd(day)}>{t('plan_put_in_add')}</Button>
      </div>
    </Sheet>
  )
}
