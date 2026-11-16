import { useState } from 'preact/hooks'
import { t } from '../../i18n'
import type { ExerciseRow } from '../../data/db'
import { addCustomExercise, useLibrary } from '../../data/workouts'
import { Button, Chip, IconButton, ListRow, Sheet, toast } from '../kit'
import { MUSCLES, type ExerciseKind, type Muscle } from '../../domain/muscle'
import { ExerciseEditor } from './ExerciseEditor'
import { ExerciseFields } from './ExercisePicker'
import { AddIcon, EditIcon, SearchIcon } from './glyphs'
import { kindLabel, musclesLabel } from './helpers'
import './workouts.css'

/** Every exercise the app knows. Custom ones (yours or the coach's) can be renamed, merged or deleted. */
export function LibraryScreen(_: { param?: string }) {
  const library = useLibrary()
  const [query, setQuery] = useState('')
  const [muscle, setMuscle] = useState<Muscle | null>(null)
  const [editing, setEditing] = useState<ExerciseRow | null>(null)
  const [creating, setCreating] = useState(false)
  const q = query.trim().toLowerCase()
  const match = library.filter((e) => !q || e.name.toLowerCase().includes(q))
    .filter((e) => !muscle || e.primary.includes(muscle) || e.secondary.includes(muscle))
  const byName = (a: ExerciseRow, b: ExerciseRow) => a.name.localeCompare(b.name)
  const custom = match.filter((e) => e.custom).sort(byName)
  const builtIn = match.filter((e) => !e.custom).sort(byName)
  const row = (ex: ExerciseRow) => (
    <div key={ex.id} class="wk-opt-row">
      <div class="wk-grow">
        <ListRow
          title={ex.name} subtitle={`${kindLabel(ex.kind)} · ${musclesLabel(ex)}`}
          trailing={ex.custom ? <span class="wk-pill custom">{t('lib_custom')}</span> : undefined}
          href={`#/exercise/${encodeURIComponent(ex.id)}`}
        />
      </div>
      {ex.custom && (
        <IconButton label={t('lib_edit')} onClick={() => setEditing(ex)}><EditIcon size={20} /></IconButton>
      )}
    </div>
  )
  return (
    <div class="content wk">
      <div class="wk-head">
        <div class="wk-head-text">
          <h1 class="wk-title">{t('profile_exercise_library')}</h1>
          <div class="wk-sub">{t('lib_counts', library.filter((e) => e.custom).length, library.filter((e) => !e.custom).length)}</div>
        </div>
        <IconButton label={t('picker_create_custom')} onClick={() => setCreating(true)}><AddIcon /></IconButton>
      </div>
      <div class="wk-search">
        <SearchIcon size={20} />
        <input type="search" value={query} placeholder={t('lib_search')} aria-label={t('lib_search')} onInput={(e) => setQuery((e.target as HTMLInputElement).value)} />
      </div>
      <div class="wk-chips" role="group" aria-label={t('muscles_all')}>
        <Chip label={t('muscles_all')} selected={muscle === null} onClick={() => setMuscle(null)} />
        {MUSCLES.map((m) => <Chip key={m} label={t(`muscle_${m.toLowerCase()}`)} selected={muscle === m} onClick={() => setMuscle(muscle === m ? null : m)} />)}
      </div>
      {custom.length > 0 ? (
        <>
          <h2 class="wk-group">{t('lib_yours')}</h2>
          {custom.map((e) => row(e))}
        </>
      ) : !q && <p class="wk-muted">{t('lib_none_custom')}</p>}
      <h2 class="wk-group">{t('lib_built_in')}</h2>
      {builtIn.map((e) => row(e))}
      {match.length === 0 && <p class="wk-muted">{t('workouts_web_no_matches')}</p>}
      <ExerciseEditor ex={editing} library={library} onClose={() => setEditing(null)} />
      {creating && (
        <CreateExerciseSheet
          initialName={query.trim()} initialMuscle={muscle}
          onClose={() => setCreating(false)}
          onCreate={async (name, kind, m) => {
            await addCustomExercise(name, kind, [m], null)
            setCreating(false)
            toast(t('lib_saved', name))
          }}
        />
      )}
    </div>
  )
}

function CreateExerciseSheet({ initialName, initialMuscle, onClose, onCreate }: {
  initialName: string; initialMuscle: Muscle | null; onClose: () => void
  onCreate: (name: string, kind: ExerciseKind, muscle: Muscle) => void
}) {
  const [name, setName] = useState(initialName)
  const [kind, setKind] = useState<ExerciseKind>('REPS')
  const [muscle, setMuscle] = useState<Muscle>(initialMuscle ?? 'CHEST')
  return (
    <Sheet open onClose={onClose} title={t('picker_create_custom')}>
      <ExerciseFields name={name} kind={kind} muscle={muscle} onName={setName} onKind={setKind} onMuscle={setMuscle} />
      <div class="wk-pair">
        <Button variant="tonal" onClick={onClose}>{t('action_cancel')}</Button>
        <Button disabled={!name.trim()} onClick={() => onCreate(name.trim(), kind, muscle)}>{t('action_save')}</Button>
      </div>
    </Sheet>
  )
}
