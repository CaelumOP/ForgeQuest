import { useState } from 'preact/hooks'
import { t } from '../../i18n'
import type { ExerciseRow } from '../../data/db'
import { useLibrary } from '../../data/workouts'
import { Chip, ListRow } from '../kit'
import { MUSCLES, type Muscle } from '../../domain/muscle'
import { ExerciseEditor } from './ExerciseEditor'
import { SearchIcon } from './glyphs'
import { kindLabel, musclesLabel } from './helpers'
import './workouts.css'

/** Every exercise the app knows. Custom ones (yours or the coach's) can be renamed, merged or deleted. */
export function LibraryScreen(_: { param?: string }) {
  const library = useLibrary()
  const [query, setQuery] = useState('')
  const [muscle, setMuscle] = useState<Muscle | null>(null)
  const [editing, setEditing] = useState<ExerciseRow | null>(null)
  const q = query.trim().toLowerCase()
  const match = library.filter((e) => !q || e.name.toLowerCase().includes(q))
    .filter((e) => !muscle || e.primary.includes(muscle) || e.secondary.includes(muscle))
  const byName = (a: ExerciseRow, b: ExerciseRow) => a.name.localeCompare(b.name)
  const custom = match.filter((e) => e.custom).sort(byName)
  const builtIn = match.filter((e) => !e.custom).sort(byName)
  const row = (ex: ExerciseRow, open: boolean) => (
    <ListRow
      key={ex.id} title={ex.name} subtitle={`${kindLabel(ex.kind)} · ${musclesLabel(ex)}`}
      trailing={ex.custom ? <span class="wk-pill custom">{t('lib_custom')}</span> : undefined}
      href={open ? `#/exercise/${encodeURIComponent(ex.id)}` : undefined} onClick={open ? undefined : () => setEditing(ex)}
    />
  )
  return (
    <div class="content wk">
      <div>
        <h1 class="wk-title">{t('profile_exercise_library')}</h1>
        <div class="wk-sub">{t('lib_counts', library.filter((e) => e.custom).length, library.filter((e) => !e.custom).length)}</div>
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
          {custom.map((e) => row(e, false))}
        </>
      ) : !q && <p class="wk-muted">{t('lib_none_custom')}</p>}
      <h2 class="wk-group">{t('lib_built_in')}</h2>
      {builtIn.map((e) => row(e, true))}
      {match.length === 0 && <p class="wk-muted">{t('workouts_web_no_matches')}</p>}
      <ExerciseEditor ex={editing} library={library} onClose={() => setEditing(null)} />
    </div>
  )
}
