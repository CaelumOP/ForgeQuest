// The exercises for one muscle (docs/plans/muscle-exercises-plan.md): a note while it is still recovering, the plan's
// exercises first, then the rest for the person's equipment. A row opens the exercise page. Android: MuscleExercisesSheet.kt.
import { useMemo, useState } from 'preact/hooks'
import { t } from '../../i18n'
import type { ExerciseEntity } from '../../domain/entities'
import type { Equipment } from '../../domain/exercise-swap-engine'
import type { Tier } from '../../domain/exercise-tiers'
import type { Muscle } from '../../domain/muscle'
import { COMPANIONS, muscleExercises, type MuscleExercise } from '../../domain/muscle-exercises'
import type { MuscleRecovery } from '../../domain/progress'
import { ListRow, Segmented, Sheet } from '../kit'

const label = (m: Muscle) => t(`muscle_${m.toLowerCase()}`)

/** Foundation / Intermediate / Advanced as a small coloured pill. */
export function TierBadge({ tier }: { tier: Tier }) {
  return <span class={`wk-tier wk-tier-${tier.toLowerCase()}`}>{t(`tier_${tier.toLowerCase()}`)}</span>
}

export function MuscleSheet({ muscle, library, planIds, equipment, recovery, onClose }: {
  muscle: Muscle | null; library: ExerciseEntity[]; planIds: Set<string>; equipment: Set<Equipment>
  recovery: MuscleRecovery | undefined; onClose: () => void
}) {
  const [all, setAll] = useState<'MINE' | 'ALL'>('MINE')
  const list = useMemo(() => (muscle ? muscleExercises(muscle, library, planIds, equipment, all === 'ALL') : null), [muscle, library, planIds, equipment, all])
  if (!muscle || !list) return null
  const open = (id: string) => { onClose(); location.hash = `#/exercise/${encodeURIComponent(id)}` }
  const row = (r: MuscleExercise) => (
    <ListRow key={r.exercise.id} title={r.exercise.name} subtitle={t(r.main ? 'muscle_main' : 'muscle_also')}
      trailing={r.tier ? <TierBadge tier={r.tier} /> : undefined} onClick={() => open(r.exercise.id)} />
  )
  const more = COMPANIONS[muscle]
  return (
    <Sheet open onClose={onClose} title={label(muscle)}>
      {more && <p class="wk-msub">{t('muscle_shown_with', more.map(label).join(t('list_separator')))}</p>}
      {recovery && recovery.hoursUntilReady > 0 && <p class="wk-note-gold">{t('muscle_recovering_note', recovery.hoursUntilReady)}</p>}
      {(list.hidden > 0 || all === 'ALL') && (
        <Segmented label={t('muscle_all_equipment')} value={all} onChange={setAll}
          options={[{ value: 'MINE', label: t('muscle_my_equipment') }, { value: 'ALL', label: t('muscle_all_equipment') }]} />
      )}
      {list.inPlan.length > 0 && <h3 class="wk-sheet-h">{t('muscle_in_plan')}</h3>}
      {list.inPlan.map(row)}
      <h3 class="wk-sheet-h">{t('muscle_more')}</h3>
      {list.others.length === 0 && <p class="wk-msub">{t('muscle_none')}</p>}
      {list.others.map(row)}
    </Sheet>
  )
}
