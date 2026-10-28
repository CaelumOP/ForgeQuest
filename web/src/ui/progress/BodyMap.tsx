// Muscle recovery map. Anatomy art: react-native-body-highlighter 3.2.0, MIT License (c) 2022 ELABBASSI Hicham.
// Exported so the Workouts tab can reuse it: <BodyMap recovery={snapshot.recovery} bodyType={settings.bodyType} />.
import { useState } from 'preact/hooks'
import { t } from '../../i18n'
import { MUSCLES, muscleFromSlug, type Muscle } from '../../domain/muscle'
import type { MuscleRecovery } from '../../domain/progress'
import art from './body_map.json'
import { Button, Chip } from '../kit'
import { recoveryFill } from './helpers'
import './progress.css'

type View = { viewBox: number[]; outline: string | null; parts: [string, string][] }
type Art = Record<'male' | 'female', Record<'front' | 'back', View>>

const label = (m: Muscle) => t(`muscle_${m.toLowerCase()}`)

/** A muscle's state in the legend's words, for screen readers: "Fresh", or "Recovering, 60% recovered". */
function status(r: MuscleRecovery | undefined): string {
  const fatigue = r?.fatigue ?? 0
  if (fatigue <= 0) return t('map_fresh')
  const stage = t(fatigue < 0.33 ? 'map_almost_ready' : fatigue < 0.66 ? 'map_recovering' : 'map_just_trained')
  return t('a11y_muscle_recovering', stage, Math.round((1 - fatigue) * 100))
}

function Figure({ view, recovery, selected, onTap }: {
  view: View; recovery: Map<Muscle, MuscleRecovery>; selected: Muscle | null; onTap: (m: Muscle) => void
}) {
  const [x, y, w, h] = view.viewBox
  return (
    <svg class="bm-svg" viewBox={`${x} ${y} ${w} ${h}`} aria-hidden="true" focusable="false">
      {view.outline && <path d={view.outline} class="bm-neutral" />}
      {view.parts.map(([slug, d], i) => {
        const m = muscleFromSlug(slug)
        if (!m) return <path key={i} d={d} class="bm-neutral" />
        return (
          <path key={i} d={d} class="bm-muscle" style={{ fill: recoveryFill(recovery.get(m)?.fatigue ?? 0) }}
            onClick={() => onTap(m)} />
        )
      })}
      {view.outline && <path d={view.outline} class="bm-outline" vector-effect="non-scaling-stroke" />}
      {selected && view.parts.map(([slug, d], i) => (muscleFromSlug(slug) === selected
        ? <path key={i} d={d} class="bm-selected" vector-effect="non-scaling-stroke" /> : null))}
    </svg>
  )
}

/**
 * Front and back figures (front on the start-left, back on the right in every language), a detail pill and the legend.
 * With `exerciseCount`, every muscle is also a chip and a picked muscle gets a "See exercises" button.
 */
export function BodyMap({ recovery, bodyType, exerciseCount, onSeeExercises }: {
  recovery: Map<Muscle, MuscleRecovery>; bodyType: 'MALE' | 'FEMALE'
  exerciseCount?: (m: Muscle) => number; onSeeExercises?: (m: Muscle) => void
}) {
  const [selected, setSelected] = useState<Muscle | null>(null)
  const body = (art as unknown as Art)[bodyType === 'FEMALE' ? 'female' : 'male']
  const tap = (m: Muscle) => setSelected((s) => (s === m ? null : m))

  const ranked = [...recovery.entries()].sort((a, b) => b[1].fatigue - a[1].fatigue)
  const summary = ranked.length === 0
    ? t('map_cd_all_fresh')
    : t('map_cd_recovering', ranked.map(([m, r]) => t('map_cd_muscle_ready', label(m), r.hoursUntilReady)).join(t('list_separator')))
  const r = selected ? recovery.get(selected) : undefined
  const info = selected
    ? (r ? t('map_recovering_detail', label(selected), Math.round((1 - r.fatigue) * 100), r.hoursUntilReady) : t('map_fully_recovered', label(selected)))
    : (recovery.size === 0 ? t('map_all_fresh_tap') : t('map_tap_hint'))

  return (
    <div class="bm">
      {/* A picture of a body, not a reading order: the figures keep their sides in right-to-left too. */}
      {/* Screen readers get the summary as one image; the drawing is hidden and the chips below pick a muscle. */}
      <div class="bm-row" dir="ltr" role="img" aria-label={summary}>
        {([['map_front', body.front], ['map_back', body.back]] as const).map(([key, view]) => (
          <div class="bm-col" key={key}>
            <Figure view={view} recovery={recovery} selected={selected} onTap={tap} />
            <span class="bm-cap" dir="auto" aria-hidden="true">{t(key)}</span>
          </div>
        ))}
      </div>
      {exerciseCount && (
        // Small muscles (forearms, tibialis) are hard to hit on the picture.
        <div class="bm-chips" role="group" aria-label={t('recovery_title')}>
          {MUSCLES.map((m) => <Chip key={m} label={label(m)} selected={selected === m} onClick={() => tap(m)} srDetail={status(recovery.get(m))} />)}
        </div>
      )}
      <p class="bm-info" aria-live="polite">{info}</p>
      {selected && exerciseCount && onSeeExercises && (
        <Button variant="tonal" block onClick={() => onSeeExercises(selected)}>{t('muscle_see_exercises', label(selected), exerciseCount(selected))}</Button>
      )}
      <ul class="bm-legend">
        {([['--coral', 'map_just_trained'], ['--gold', 'map_recovering'], ['--teal', 'map_almost_ready'], ['--muscle-rest', 'map_fresh']] as const).map(([c, k]) => (
          <li key={k}><span class="pg-dot" style={{ background: `var(${c})` }} aria-hidden="true" />{t(k)}</li>
        ))}
      </ul>
      {!exerciseCount && <div class="kit-sr" aria-hidden="false">
        {MUSCLES.map((m) => (
          <button key={m} type="button" onClick={() => tap(m)}>{label(m)}</button>
        ))}
      </div>}
    </div>
  )
}
