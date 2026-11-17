import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import type { ExerciseRow } from '../../data/db'
import { useSettings } from '../../data/settings'
import { addCustomExercise, updateExercise, useLibrary } from '../../data/workouts'
import { filterExercisesBySearch } from '../../data/seed/search'
import { ALL_ACTIVE_EQUIPMENT, EQUIPMENT_LABEL, profileFor, type Equipment, type MovementPattern } from '../../domain/exercise-swap-engine'
import { tierOf } from '../../domain/exercise-tiers'
import { MUSCLES, type ExerciseKind, type Muscle } from '../../domain/muscle'
import { Button, Chip, IconButton, Sheet, toast } from '../kit'
import { ExerciseThumbnail } from '../kit/ExerciseMediaView'
import { MuscleFilterSheet } from '../kit/MuscleMapMini'
import { ExerciseEditor } from './ExerciseEditor'
import { CustomExerciseFields } from './ExercisePicker'
import { TierBadge } from './MuscleSheet'
import { AddIcon, EditIcon, SearchIcon } from './glyphs'
import { kindLabel, primaryMusclesLabel } from './helpers'
import './workouts.css'

const eqName = (e: Equipment) => EQUIPMENT_LABEL[e][getLang()]
const PAGE_SIZE = 60

const BODY_MAP_ICON = (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
    <path d="M12 2a2 2 0 1 1 0 4 2 2 0 0 1 0-4zm9 7h-6v13h-2v-6h-2v6H9V9H3V7h18v2z" />
  </svg>
)

/** Every exercise the app knows. Custom ones (yours or the coach's) can be renamed, edited, merged or deleted. */
export function LibraryScreen(_: { param?: string }) {
  const library = useLibrary()
  const settings = useSettings()
  const [query, setQuery] = useState('')
  const [muscle, setMuscle] = useState<Muscle | null>(null)
  const [eq, setEq] = useState<Equipment | null>(null)
  const [mapFilterOpen, setMapFilterOpen] = useState(false)
  const [editing, setEditing] = useState<ExerciseRow | null>(null)
  const [creating, setCreating] = useState(false)
  const [limit, setLimit] = useState(PAGE_SIZE)
  const sentinelRef = useRef<HTMLDivElement>(null)

  const q = query.trim()
  const match = useMemo(() => {
    const searched = q ? filterExercisesBySearch(library, q) : library
    return searched.filter((e) => {
      const mOk = !muscle || e.primary.includes(muscle) || e.secondary.includes(muscle)
      if (!mOk) return false
      if (!eq) return true
      const prof = profileFor(e)
      return eq === prof.equipment || Boolean(prof.requires?.includes(eq))
    })
  }, [library, q, muscle, eq])

  const custom = useMemo(() => {
    const list = match.filter((e) => e.custom)
    return q ? list : list.sort((a, b) => a.name.localeCompare(b.name))
  }, [match, q])

  const builtIn = useMemo(() => {
    const list = match.filter((e) => !e.custom)
    return q ? list : list.sort((a, b) => a.name.localeCompare(b.name))
  }, [match, q])

  useEffect(() => { setLimit(PAGE_SIZE) }, [q, muscle, eq])
  useEffect(() => {
    const el = sentinelRef.current
    if (!el || limit >= builtIn.length) return
    const obs = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) setLimit((n) => Math.min(builtIn.length, n + PAGE_SIZE))
    })
    obs.observe(el)
    return () => obs.disconnect()
  }, [limit, builtIn.length])

  const row = (ex: ExerciseRow) => {
    const prof = profileFor(ex)
    const tier = tierOf(ex)
    const holdTag = ex.kind === 'HOLD' ? ` · ${kindLabel(ex.kind)}` : ''
    return (
      <div key={ex.id} class="wk-opt-row">
        <a class="wk-prow wk-grow" href={`#/exercise/${encodeURIComponent(ex.id)}`}>
          <ExerciseThumbnail exercise={ex} />
          <span class="wk-prow-text">
            <span class="wk-prow-top">
              <span class="wk-prow-name">{ex.name}</span>
              {ex.custom && <span class="wk-pill custom">{t('lib_custom')}</span>}
              {!ex.custom && tier && <TierBadge tier={tier} />}
            </span>
            <span class="wk-prow-sub">{eqName(prof.equipment)} · {primaryMusclesLabel(ex)}{holdTag}</span>
          </span>
        </a>
        {ex.custom && (
          <IconButton label={t('lib_edit')} onClick={() => setEditing(ex)}><EditIcon size={20} /></IconButton>
        )}
      </div>
    )
  }

  return (
    <div class="content wk">
      <div class="wk-head">
        <div class="wk-head-text">
          <h1 class="wk-title">{t('profile_exercise_library')}</h1>
          <div class="wk-sub">{t('lib_counts', library.filter((e) => e.custom).length, library.filter((e) => !e.custom).length)}</div>
        </div>
        <IconButton label={t('picker_create_custom')} onClick={() => setCreating(true)}><AddIcon /></IconButton>
      </div>
      <div class="wk-search-row">
        <div class="wk-search">
          <SearchIcon size={20} />
          <input type="search" value={query} placeholder={t('lib_search')} aria-label={t('lib_search')} onInput={(e) => setQuery((e.target as HTMLInputElement).value)} />
        </div>
        <button
          type="button"
          class={`wk-mapbtn${muscle ? ' on' : ''}`}
          aria-label={t('picker_map_filter')}
          onClick={() => setMapFilterOpen(true)}
        >
          {BODY_MAP_ICON}
        </button>
      </div>
      <div class="wk-chips" role="group" aria-label={t('workouts_web_filter_equipment')}>
        <Chip label={t('picker_all')} selected={eq === null} onClick={() => setEq(null)} />
        {ALL_ACTIVE_EQUIPMENT.map((e) => <Chip key={e} label={eqName(e)} selected={eq === e} onClick={() => setEq(eq === e ? null : e)} />)}
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
      ) : !q && !muscle && !eq && <p class="wk-muted">{t('lib_none_custom')}</p>}
      {builtIn.length > 0 && <h2 class="wk-group">{t('lib_built_in')}</h2>}
      {builtIn.slice(0, limit).map((e) => row(e))}
      {limit < builtIn.length && <div ref={sentinelRef} style={{ height: '1px' }} />}
      {match.length === 0 && <p class="wk-muted">{t('workouts_web_no_matches')}</p>}
      <MuscleFilterSheet
        open={mapFilterOpen}
        selected={muscle}
        bodyType={settings.bodyType}
        onSelect={setMuscle}
        onClose={() => setMapFilterOpen(false)}
      />
      <ExerciseEditor ex={editing} library={library} onClose={() => setEditing(null)} />
      {creating && (
        <CreateExerciseSheet
          initialName={q} initialMuscle={muscle} initialEquipment={eq}
          onClose={() => setCreating(false)}
          onCreate={async (name, kind, m, sec, equip, pat, uo) => {
            const id = await addCustomExercise(name, kind, [m], null)
            const cleanSec = sec.filter((x) => x !== m)
            if (cleanSec.length || equip || pat || uo) {
              await updateExercise({
                id,
                name,
                kind,
                primary: [m],
                secondary: cleanSec,
                videoUrl: null,
                custom: true,
                equipment: equip,
                pattern: pat,
                unitOverride: kind === 'WEIGHTED' ? uo : '',
              })
            }
            setCreating(false)
            toast(t('lib_saved', name))
          }}
        />
      )}
    </div>
  )
}

function CreateExerciseSheet({ initialName, initialMuscle, initialEquipment, onClose, onCreate }: {
  initialName: string
  initialMuscle: Muscle | null
  initialEquipment: Equipment | null
  onClose: () => void
  onCreate: (name: string, kind: ExerciseKind, muscle: Muscle, secondary: Muscle[], equipment: Equipment, pattern: MovementPattern | '', unitOverride: string) => void
}) {
  const [name, setName] = useState(initialName)
  const [kind, setKind] = useState<ExerciseKind>('REPS')
  const [muscle, setMuscle] = useState<Muscle>(initialMuscle ?? 'CHEST')
  const [secondary, setSecondary] = useState<Muscle[]>([])
  const [equipment, setEquipment] = useState<Equipment>(initialEquipment ?? 'BODYWEIGHT')
  const [pattern, setPattern] = useState<MovementPattern | ''>('')
  const [unitOverride, setUnitOverride] = useState('')
  return (
    <Sheet open onClose={onClose} title={t('picker_create_custom')}>
      <div class="wk-pickbody">
        <CustomExerciseFields
          name={name}
          kind={kind}
          muscle={muscle}
          secondary={secondary}
          equipment={equipment}
          pattern={pattern}
          unitOverride={unitOverride}
          onName={setName}
          onKind={setKind}
          onMuscle={setMuscle}
          onSecondary={setSecondary}
          onEquipment={setEquipment}
          onPattern={setPattern}
          onUnitOverride={setUnitOverride}
        />
        <div class="wk-pair" style={{ marginBlockStart: '8px' }}>
          <Button variant="tonal" onClick={onClose}>{t('action_cancel')}</Button>
          <Button disabled={!name.trim()} onClick={() => onCreate(name.trim(), kind, muscle, secondary, equipment, pattern, unitOverride)}>{t('action_save')}</Button>
        </div>
      </div>
    </Sheet>
  )
}
