// Muscle recovery map. Anatomy art: react-native-body-highlighter 3.2.0, MIT License (c) 2022 ELABBASSI Hicham.
// Exported so the Workouts tab can reuse it: <BodyMap recovery={snapshot.recovery} bodyType={settings.bodyType} />.
import type { Ref } from 'preact'
import { t } from '../../i18n'
import { MUSCLES, muscleFromSlug, type Muscle } from '../../domain/muscle'
import type { MuscleRecovery } from '../../domain/progress'
import { haptics } from '../kit'
import art from './body_map.json'
import { recoveryFill } from './helpers'
import './progress.css'

type View = { viewBox: number[]; outline: string | null; parts: [string, string][] }
type Art = Record<'male' | 'female', Record<'front' | 'back', View>>

const label = (m: Muscle) => t(`muscle_${m.toLowerCase()}`)
const DUMBBELL_PATH = 'M20.57 14.86L22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 9.14 3.43 7.71 2 5.57 4.14 4.14 2.71 2.71 4.14l1.43 1.43L2 7.71l1.43 1.43L2 10.57 3.43 12 7 8.43 15.57 17 12 20.57 13.43 22l1.43-1.43L16.29 22l2.14-2.14 1.43 1.43 1.43-1.43-1.43-1.43L22 16.29z'

/** A muscle's state in the legend's words, for screen readers: "Fresh", or "Recovering, 60% recovered". */
function status(r: MuscleRecovery | undefined): string {
  const fatigue = r?.fatigue ?? 0
  if (fatigue <= 0) return t('map_fresh')
  const stage = t(fatigue < 0.33 ? 'map_almost_ready' : fatigue < 0.66 ? 'map_recovering' : 'map_just_trained')
  return t('a11y_muscle_recovering', stage, Math.round((1 - fatigue) * 100))
}

function Figure({ view, recovery, selected, onTap, onClear }: {
  view: View; recovery: Map<Muscle, MuscleRecovery>; selected?: Muscle | null; onTap?: (m: Muscle) => void; onClear?: () => void
}) {
  const [x, y, w, h] = view.viewBox
  return (
    <svg class="bm-svg" viewBox={`${x} ${y} ${w} ${h}`} aria-hidden="true" focusable="false" onClick={onClear}>
      {view.outline && <path d={view.outline} class="bm-neutral" />}
      {view.parts.map(([slug, d], i) => {
        const m = muscleFromSlug(slug)
        if (!m) return <path key={i} d={d} class="bm-neutral" />
        const isSel = selected === m
        return (
          <path
            key={i}
            d={d}
            class={`bm-muscle${isSel ? ' selected' : ''}`}
            style={{ fill: recoveryFill(recovery.get(m)?.fatigue ?? 0) }}
            onClick={onTap ? (e) => { e.stopPropagation(); haptics.lightTick(); onTap(m) } : undefined}
          />
        )
      })}
      {view.outline && <path d={view.outline} class="bm-outline" vector-effect="non-scaling-stroke" />}
    </svg>
  )
}

export function recoverySummary(recovery: Map<Muscle, MuscleRecovery>): string {
  const ranked = [...recovery.entries()].filter(([, r]) => r.fatigue > 0).sort((a, b) => b[1].fatigue - a[1].fatigue)
  const base = ranked.length === 0
    ? t('map_cd_all_fresh')
    : t('map_cd_recovering', ranked.map(([m, r]) => t('map_cd_muscle_ready', label(m), r.hoursUntilReady)).join(t('list_separator')))
  return `${base} ${t('map_tap_hint')}`
}

/**
 * Front and back figures side by side, centred (front on the left, back on the right in every language),
 * with the four-dot recovery legend on one line underneath. Tapping a muscle opens its details sheet.
 */
export function BodyMap({ recovery, bodyType, selected, onSelect, exerciseCount, onSeeExercises, figuresRef }: {
  recovery: Map<Muscle, MuscleRecovery>
  bodyType: 'MALE' | 'FEMALE'
  selected?: Muscle | null
  onSelect?: (m: Muscle | null) => void
  exerciseCount?: (m: Muscle) => number
  onSeeExercises?: (m: Muscle) => void
  figuresRef?: Ref<HTMLDivElement>
}) {
  const body = (art as unknown as Art)[bodyType === 'FEMALE' ? 'female' : 'male']
  const summary = recoverySummary(recovery)
  const tap = onSelect
    ? (m: Muscle) => onSelect(selected === m ? null : m)
    : onSeeExercises
  const clear = onSelect && selected ? () => onSelect(null) : undefined

  return (
    <div class="bm">
      {/* A picture of a body, not a reading order: the figures keep their sides in right-to-left too. */}
      <div class="bm-row" dir="ltr" role="img" aria-label={summary} ref={figuresRef}>
        <Figure view={body.front} recovery={recovery} selected={selected} onTap={tap} onClear={clear} />
        <Figure view={body.back} recovery={recovery} selected={selected} onTap={tap} onClear={clear} />
      </div>
      <div class="bm-slot">
        {selected && onSeeExercises ? (
          <button type="button" class="bm-pill" onClick={() => onSeeExercises(selected)}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false"><path d={DUMBBELL_PATH} /></svg>
            <span>{t('muscle_see_exercises', label(selected), exerciseCount ? exerciseCount(selected) : 0)}</span>
          </button>
        ) : (
          <ul class="bm-legend">
            {([['--coral', 'map_just_trained'], ['--gold', 'map_recovering'], ['--teal', 'map_almost_ready'], ['--muscle-rest', 'map_fresh']] as const).map(([c, k]) => (
              <li key={k}><span class="pg-dot" style={{ background: `var(${c})` }} aria-hidden="true" />{t(k)}</li>
            ))}
          </ul>
        )}
      </div>
      {onSeeExercises && (
        <div class="kit-sr" aria-hidden="false">
          {MUSCLES.map((m) => (
            <button key={m} type="button" onClick={() => onSeeExercises(m)}>{`${label(m)}, ${status(recovery.get(m))}`}</button>
          ))}
        </div>
      )}
    </div>
  )
}
