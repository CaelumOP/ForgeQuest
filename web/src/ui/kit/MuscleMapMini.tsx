import { t } from '../../i18n'
import { MUSCLES, muscleFromSlug, type Muscle } from '../../domain/muscle'
import art from '../progress/body_map.json'
import { Button, Chip, Sheet, haptics } from './index'

type View = { viewBox: number[]; outline: string | null; parts: [string, string][] }
type Art = Record<'male' | 'female', Record<'front' | 'back', View>>

function MiniSvg({ view, primary, secondary }: { view: View; primary: Set<Muscle>; secondary: Set<Muscle> }) {
  const [x, y, w, h] = view.viewBox
  return (
    <svg class="kit-mm-svg" viewBox={`${x} ${y} ${w} ${h}`} aria-hidden="true" focusable="false">
      {view.outline && <path d={view.outline} style={{ fill: 'var(--card-high)' }} />}
      {view.parts.map(([slug, d], i) => {
        const m = muscleFromSlug(slug)
        const fill = !m
          ? 'var(--card-high)'
          : primary.has(m)
            ? 'var(--blue)'
            : secondary.has(m)
              ? 'color-mix(in srgb, var(--blue) 36%, transparent)'
              : 'var(--card-high)'
        return <path key={i} d={d} style={{ fill }} />
      })}
      {view.outline && <path d={view.outline} style={{ fill: 'none', stroke: 'var(--divider)', strokeWidth: 1.5 }} vector-effect="non-scaling-stroke" />}
    </svg>
  )
}

/** Compact front/back muscle map for ExerciseDetailScreen (primary in data blue, secondary lighter, no teal). */
export function MuscleMapMini({ primary, secondary, bodyType = 'MALE' }: {
  primary: readonly Muscle[]
  secondary: readonly Muscle[]
  bodyType?: 'MALE' | 'FEMALE'
}) {
  const body = (art as unknown as Art)[bodyType === 'FEMALE' ? 'female' : 'male']
  const primSet = new Set(primary)
  const secSet = new Set(secondary.filter((m) => !primSet.has(m)))
  return (
    <div class="kit-mm-row" dir="ltr" role="img" aria-label={[...primary, ...secondary].map((m) => t(`muscle_${m.toLowerCase()}`)).join(', ')}>
      <MiniSvg view={body.front} primary={primSet} secondary={secSet} />
      <MiniSvg view={body.back} primary={primSet} secondary={secSet} />
    </div>
  )
}

function FilterSvg({ view, selected, onTap, figureLabel }: { view: View; selected: Muscle | null; onTap: (m: Muscle) => void; figureLabel: string }) {
  const [x, y, w, h] = view.viewBox
  return (
    <svg class="kit-mm-filter-svg" viewBox={`${x} ${y} ${w} ${h}`} role="group" aria-label={figureLabel}>
      {view.outline && <path d={view.outline} style={{ fill: 'var(--card-high)' }} />}
      {view.parts.map(([slug, d], i) => {
        const m = muscleFromSlug(slug)
        if (!m) {
          return <path key={i} d={d} style={{ fill: 'var(--card-high)', stroke: 'none', cursor: 'default' }} />
        }
        const isSel = selected != null && m === selected
        const name = t(`muscle_${m.toLowerCase()}`)
        return (
          <path
            key={i}
            d={d}
            role="button"
            tabIndex={0}
            aria-label={name}
            aria-pressed={isSel}
            vector-effect="non-scaling-stroke"
            style={{
              fill: isSel ? 'var(--teal)' : 'color-mix(in srgb, var(--text-2) 28%, transparent)',
              stroke: isSel ? 'var(--text)' : 'var(--divider)',
              strokeWidth: isSel ? 1.5 : 1,
              cursor: 'pointer',
            }}
            onClick={(e) => { e.stopPropagation(); haptics.lightTick(); onTap(m) }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                e.stopPropagation()
                haptics.lightTick()
                onTap(m)
              }
            }}
          />
        )
      })}
      {view.outline && <path d={view.outline} style={{ fill: 'none', stroke: 'var(--divider)', strokeWidth: 1.5 }} vector-effect="non-scaling-stroke" />}
    </svg>
  )
}

export function MuscleFilterSheet({ open, selected, bodyType = 'MALE', onSelect, onClose }: {
  open: boolean
  selected: Muscle | null
  bodyType?: 'MALE' | 'FEMALE'
  onSelect: (m: Muscle | null) => void
  onClose: () => void
}) {
  const body = (art as unknown as Art)[bodyType === 'FEMALE' ? 'female' : 'male']
  const pick = (m: Muscle) => {
    onSelect(selected === m ? null : m)
    onClose()
  }
  const selectedName = selected ? t(`muscle_${selected.toLowerCase()}`) : null
  return (
    <Sheet open={open} onClose={onClose} title={t('picker_map_filter')}>
      <div class="kit-mm-filter-head">
        <p class="kit-mm-filter-hint">{t('v42_muscle_tap_hint')}</p>
        {selectedName && (
          <div class="kit-mm-filter-status">
            <span class="kit-mm-filter-showing">{t('v42_muscle_showing', selectedName)}</span>
            <Button variant="tonal" onClick={() => { onSelect(null); onClose() }}>{t('picker_clear_filter')}</Button>
          </div>
        )}
      </div>
      <div class="kit-mm-filter-row" dir="ltr">
        <div class="kit-mm-filter-col">
          <FilterSvg view={body.front} selected={selected} onTap={pick} figureLabel={t('v42_muscle_front')} />
          <span class="kit-mm-filter-lbl">{t('v42_muscle_front')}</span>
        </div>
        <div class="kit-mm-filter-col">
          <FilterSvg view={body.back} selected={selected} onTap={pick} figureLabel={t('v42_muscle_back')} />
          <span class="kit-mm-filter-lbl">{t('v42_muscle_back')}</span>
        </div>
      </div>
      <div class="kit-chips kit-mm-filter-chips" role="group" aria-label={t('muscles_all')}>
        {MUSCLES.map((m) => (
          <Chip
            key={m}
            label={t(`muscle_${m.toLowerCase()}`)}
            selected={selected === m}
            onClick={() => { haptics.lightTick(); pick(m) }}
          />
        ))}
      </div>
    </Sheet>
  )
}
