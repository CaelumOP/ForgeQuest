import { useMemo, useState } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import type { ExerciseRow } from '../../data/db'
import { EQUIPMENT, EQUIPMENT_LABEL, MOVEMENT_PATTERN_LABEL, profileFor, rankSubstitutes, type Equipment } from '../../domain/exercise-swap-engine'
import { EXERCISE_KINDS, MUSCLES, type ExerciseKind, type Muscle } from '../../domain/muscle'
import { Button, Chip, ChipGroup, Sheet, TextField } from '../kit'
import { AddIcon, SearchIcon } from './glyphs'
import { filterExercises, kindLabel, muscleLabel, musclesLabel } from './helpers'
import './workouts.css'

const eqName = (e: Equipment) => EQUIPMENT_LABEL[e][getLang()]

export type ExercisePickerProps = {
  open: boolean
  title: string
  library: readonly ExerciseRow[]
  onPick: (ex: ExerciseRow) => void
  /** Omit to hide "Create a custom exercise" (e.g. when merging). */
  onCreate?: (name: string, kind: ExerciseKind, muscle: Muscle) => void
  onClose: () => void
  initialMuscle?: Muscle
  /** Swap mode: the exercise being replaced; its best biomechanical matches come first. */
  sourceExercise?: ExerciseRow
  userEquipment?: ReadonlySet<Equipment>
}

/** Searchable exercise sheet with equipment and muscle filters, swap ranking and "create custom" (Android ExercisePickerSheet). */
export function ExercisePicker(p: ExercisePickerProps) {
  return (
    <Sheet open={p.open} onClose={p.onClose} title={p.title}>
      <PickerBody {...p} />
    </Sheet>
  )
}

function PickerBody({ library, onPick, onCreate, initialMuscle, sourceExercise, userEquipment }: ExercisePickerProps) {
  const lang = getLang()
  const mine = userEquipment ?? new Set<Equipment>()
  const limited = mine.size > 0 && mine.size < EQUIPMENT.length
  const [query, setQuery] = useState('')
  const [muscle, setMuscle] = useState<Muscle | null>(sourceExercise ? null : initialMuscle ?? null)
  const [onlyMine, setOnlyMine] = useState(limited)
  const [eq, setEq] = useState<Equipment | null>(null)
  const [creating, setCreating] = useState(false)

  const effective = useMemo<ReadonlySet<Equipment>>(() => {
    if (eq) return new Set([eq])
    if (onlyMine && mine.size > 0) return new Set<Equipment>([...mine, 'BODYWEIGHT'])
    return new Set(EQUIPMENT)
  }, [eq, onlyMine, userEquipment])

  const top = useMemo(() => {
    if (!sourceExercise || query.trim() || muscle) return []
    return rankSubstitutes(sourceExercise, library, effective).filter((c) => c.matchesUserEquipment && c.score >= 40).slice(0, 5)
  }, [sourceExercise, library, effective, query, muscle])

  const rows = useMemo(() => {
    const exclude = new Set(top.map((c) => c.exercise.id))
    if (sourceExercise) exclude.add(sourceExercise.id)
    return filterExercises(library, { query, muscle, equipment: effective, excludeIds: exclude, focusMuscle: sourceExercise?.primary[0] })
  }, [library, query, muscle, effective, top, sourceExercise])

  if (creating && onCreate) {
    return <CreateForm initialName={query.trim()} initialMuscle={muscle ?? sourceExercise?.primary[0] ?? null} onCreate={onCreate} onCancel={() => setCreating(false)} />
  }
  const src = sourceExercise && profileFor(sourceExercise)
  return (
    <div class="wk-pickbody">
      {sourceExercise && src && (
        <div class="wk-s" style={{ color: 'var(--teal)' }}>
          {sourceExercise.name} · {MOVEMENT_PATTERN_LABEL[src.pattern][lang]} · {eqName(src.equipment)}
        </div>
      )}
      <div class="wk-search">
        <SearchIcon size={20} />
        <input type="search" value={query} placeholder={t('picker_search')} aria-label={t('picker_search')} onInput={(e) => setQuery((e.target as HTMLInputElement).value)} />
      </div>
      <div class="wk-scroll" role="group" aria-label={t('workouts_web_filter_equipment')}>
        {limited && <Chip label={t('picker_my_equipment')} selected={onlyMine && !eq} onClick={() => { setOnlyMine(!onlyMine); setEq(null) }} />}
        {EQUIPMENT.map((e) => (
          <Chip key={e} label={eqName(e)} selected={eq === e} onClick={() => { const next = eq === e ? null : e; setEq(next); if (next) setOnlyMine(false) }} />
        ))}
      </div>
      <div class="wk-scroll" role="group" aria-label={t('workouts_web_filter_muscle')}>
        <Chip label={t('picker_all')} selected={muscle == null} onClick={() => setMuscle(null)} />
        {MUSCLES.map((m) => <Chip key={m} label={muscleLabel(m)} selected={muscle === m} onClick={() => setMuscle(muscle === m ? null : m)} />)}
      </div>
      <div class="wk-list">
        {top.length > 0 && (
          <>
            <div class="wk-group teal">{t('picker_best_swaps')}</div>
            {top.map((c) => (
              <button key={c.exercise.id} type="button" class="wk-prow best" onClick={() => onPick(c.exercise as ExerciseRow)}>
                <span class="wk-prow-text">
                  <span class="wk-prow-name">{c.exercise.name}</span>
                  <span class="wk-prow-sub">{MOVEMENT_PATTERN_LABEL[c.profile.pattern][lang]} · {eqName(c.profile.equipment)} · {musclesLabel(c.exercise)}</span>
                </span>
                <span class="wk-pill">{kindLabel(c.exercise.kind)}</span>
              </button>
            ))}
            <div class="wk-group">{t('picker_all')}</div>
          </>
        )}
        {rows.map((ex) => (
          <button key={ex.id} type="button" class="wk-prow" onClick={() => onPick(ex)}>
            <span class="wk-prow-text">
              <span class="wk-prow-name">{ex.name}</span>
              <span class="wk-prow-sub">{kindLabel(ex.kind)} · {eqName(profileFor(ex).equipment)} · {musclesLabel(ex)}</span>
            </span>
          </button>
        ))}
        {rows.length === 0 && top.length === 0 && <p class="wk-muted" style={{ padding: '12px 8px' }}>{t('workouts_web_no_matches')}</p>}
        {onCreate && (
          <button type="button" class="wk-prow wk-create" onClick={() => setCreating(true)}>
            <AddIcon />
            <span class="wk-prow-name">{query.trim() ? t('picker_create_named', query.trim()) : t('picker_create_custom')}</span>
          </button>
        )}
      </div>
    </div>
  )
}

/** Name, how it is measured and the main muscle: shared by the picker and the library editor. */
export function ExerciseFields(p: { nameLabel?: string; name: string; kind: ExerciseKind; muscle: Muscle; onName: (v: string) => void; onKind: (k: ExerciseKind) => void; onMuscle: (m: Muscle) => void }) {
  return (
    <>
      <TextField label={p.nameLabel ?? t('picker_exercise_name')} value={p.name} onInput={(v) => p.onName(v.slice(0, 40))} />
      <div class="wk-group" style={{ margin: 0 }}>{t('picker_measured_in')}</div>
      <ChipGroup label={t('picker_measured_in')} options={EXERCISE_KINDS.map((k) => ({ value: k, label: kindLabel(k) }))} value={p.kind} onChange={p.onKind} />
      <div class="wk-group" style={{ margin: 0 }}>{t('picker_main_muscle')}</div>
      <ChipGroup label={t('picker_main_muscle')} options={MUSCLES.map((m) => ({ value: m, label: muscleLabel(m) }))} value={p.muscle} onChange={p.onMuscle} />
    </>
  )
}

function CreateForm({ initialName, initialMuscle, onCreate, onCancel }: { initialName: string; initialMuscle: Muscle | null; onCreate: ExercisePickerProps['onCreate'] & {}; onCancel: () => void }) {
  const [name, setName] = useState(initialName)
  const [kind, setKind] = useState<ExerciseKind>('REPS')
  const [muscle, setMuscle] = useState<Muscle>(initialMuscle ?? 'CHEST')
  return (
    <div class="wk-pickbody">
      <ExerciseFields name={name} kind={kind} muscle={muscle} onName={setName} onKind={setKind} onMuscle={setMuscle} />
      <div class="wk-pair">
        <Button variant="tonal" onClick={onCancel}>{t('action_cancel')}</Button>
        <Button disabled={!name.trim()} onClick={() => onCreate(name.trim(), kind, muscle)}>{t('action_create')}</Button>
      </div>
    </div>
  )
}
