import { t } from '../../i18n'
import { muscleFromSlug, type Muscle } from '../../domain/muscle'
import art from '../progress/body_map.json'
import { Button, Sheet, haptics } from './index'

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

function FilterSvg({ view, selected, onTap }: { view: View; selected: Muscle | null; onTap: (m: Muscle) => void }) {
  const [x, y, w, h] = view.viewBox
  return (
    <svg class="kit-mm-filter-svg" viewBox={`${x} ${y} ${w} ${h}`} aria-hidden="true" focusable="false">
      {view.outline && <path d={view.outline} style={{ fill: 'var(--card-high)' }} />}
      {view.parts.map(([slug, d], i) => {
        const m = muscleFromSlug(slug)
        const isSel = m != null && selected === m
        return (
          <path
            key={i}
            d={d}
            style={{
              fill: isSel ? 'var(--blue)' : 'var(--card-high)',
              stroke: isSel ? 'var(--text)' : 'none',
              strokeWidth: isSel ? 1.5 : 0,
              cursor: m ? 'pointer' : 'default',
            }}
            onClick={m ? (e) => { e.stopPropagation(); haptics.lightTick(); onTap(m) } : undefined}
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
  return (
    <Sheet open={open} onClose={onClose} title={t('picker_map_filter')}>
      {selected && (
        <div style={{ marginBlockEnd: '12px' }}>
          <Button variant="tonal" onClick={() => { onSelect(null); onClose() }}>{t('picker_clear_filter')}</Button>
        </div>
      )}
      <div class="kit-mm-filter-row" dir="ltr">
        <FilterSvg view={body.front} selected={selected} onTap={pick} />
        <FilterSvg view={body.back} selected={selected} onTap={pick} />
      </div>
    </Sheet>
  )
}
