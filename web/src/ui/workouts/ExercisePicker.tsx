import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import type { ExerciseRow } from '../../data/db'
import { useSettings } from '../../data/settings'
import {
  ALL_ACTIVE_EQUIPMENT, EQUIPMENT_LABEL, MOVEMENT_PATTERN_LABEL, compareSwapTier, expandLegacyEquipment,
  profileFits, profileFor, rankSwapSheet, type Equipment, type MovementPattern, type SwapDifficulty,
} from '../../domain/exercise-swap-engine'
import { tierOf } from '../../domain/exercise-tiers'
import { EXERCISE_KINDS, MUSCLES, type ExerciseKind, type Muscle } from '../../domain/muscle'
import { Button, Chip, ChipGroup, Sheet, TextField } from '../kit'
import { ExerciseThumbnail } from '../kit/ExerciseMediaView'
import { MuscleFilterSheet } from '../kit/MuscleMapMini'
import { TierBadge } from './MuscleSheet'
import { AddIcon, SearchIcon } from './glyphs'
import { filterExercises, kindLabel, muscleLabel, primaryMusclesLabel } from './helpers'
import './workouts.css'

const eqName = (e: Equipment) => EQUIPMENT_LABEL[e][getLang()]
const patternName = (p: MovementPattern) => t(`pattern_${p.toLowerCase()}`)
const PAGE_SIZE = 60

const BODY_MAP_ICON = (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
    <path d="M12 2a2 2 0 1 1 0 4 2 2 0 0 1 0-4zm9 7h-6v13h-2v-6h-2v6H9V9H3V7h18v2z" />
  </svg>
)

export type ExercisePickerProps = {
  open: boolean
  title: string
  library: readonly ExerciseRow[]
  onPick: (ex: ExerciseRow) => void
  /** Omit to hide "Create a custom exercise" (e.g. when merging). */
  onCreate?: (name: string, kind: ExerciseKind, muscle: Muscle) => void
  onClose: () => void
  initialMuscle?: Muscle
  /** Swap mode: the plan anchor exercise being replaced; its best biomechanical matches come first. */
  sourceExercise?: ExerciseRow
  userEquipment?: ReadonlySet<Equipment>
  showAllMovesToggle?: boolean
  /** Currently active exercise in this live slot (marked "Current" in Swap mode). */
  currentExerciseId?: string | null
  /** Recently used exercise IDs shown at the top when unfiltered. */
  recentIds?: readonly string[]
}

/** Searchable exercise sheet with equipment/muscle/body-map filters, pinned/recent sections, swap v2 ranking, and "create custom". */
export function ExercisePicker(p: ExercisePickerProps) {
  return (
    <Sheet open={p.open} onClose={p.onClose} title={p.title}>
      <PickerBody {...p} />
    </Sheet>
  )
}

function SwapDifficultyPill({ difficulty }: { difficulty: SwapDifficulty }) {
  const cls = difficulty === 'EASIER' ? 'easier' : difficulty === 'HARDER' ? 'harder' : 'same'
  const key = difficulty === 'EASIER' ? 'swap_easier' : difficulty === 'HARDER' ? 'swap_harder' : 'swap_same_level'
  return <span class={`wk-pill ${cls}`}>{t(key)}</span>
}

function PickerRow({ ex, prof, difficulty, isCurrent, highlighted, onClick }: {
  ex: ExerciseRow
  prof: ReturnType<typeof profileFor>
  difficulty?: SwapDifficulty | null
  isCurrent?: boolean
  highlighted?: boolean
  onClick: () => void
}) {
  const tier = tierOf(ex)
  const holdTag = ex.kind === 'HOLD' ? ` · ${kindLabel(ex.kind)}` : ''
  return (
    <button type="button" class={`wk-prow${highlighted ? ' best' : ''}`} onClick={onClick}>
      <ExerciseThumbnail exercise={ex} />
      <span class="wk-prow-text">
        <span class="wk-prow-top">
          <span class="wk-prow-name">{ex.name}</span>
          {ex.custom && <span class="wk-pill custom">{t('lib_custom')}</span>}
          {!ex.custom && tier && !difficulty && <TierBadge tier={tier} />}
          {difficulty && <SwapDifficultyPill difficulty={difficulty} />}
          {isCurrent && <span class="wk-pill current">{t('swap_current')}</span>}
        </span>
        <span class="wk-prow-sub">{eqName(prof.equipment)} · {primaryMusclesLabel(ex)}{holdTag}</span>
      </span>
    </button>
  )
}

function PickerBody({ library, onPick, onCreate, initialMuscle, sourceExercise, userEquipment, currentExerciseId, recentIds, showAllMovesToggle }: ExercisePickerProps) {
  const settings = useSettings()
  const mine = useMemo(() => expandLegacyEquipment(userEquipment ?? new Set<Equipment>()), [userEquipment])
  const limited = mine.size > 0 && !ALL_ACTIVE_EQUIPMENT.every((e) => mine.has(e))
  const [query, setQuery] = useState('')
  const [muscle, setMuscle] = useState<Muscle | null>(sourceExercise ? null : initialMuscle ?? null)
  const [onlyMine, setOnlyMine] = useState(showAllMovesToggle || limited)
  const [eq, setEq] = useState<Equipment | null>(null)
  const [creating, setCreating] = useState(false)
  const [mapFilterOpen, setMapFilterOpen] = useState(false)
  const [limit, setLimit] = useState(PAGE_SIZE)
  const sentinelRef = useRef<HTMLDivElement>(null)

  const effective = useMemo<ReadonlySet<Equipment>>(() => {
    if (eq) return new Set([eq])
    if (onlyMine && mine.size > 0) return new Set<Equipment>([...mine, 'BODYWEIGHT'])
    return new Set(ALL_ACTIVE_EQUIPMENT)
  }, [eq, onlyMine, mine])

  const top = useMemo(() => {
    if (!sourceExercise || query.trim() || muscle) return []
    const swapSet = eq ? new Set(ALL_ACTIVE_EQUIPMENT) : effective
    return rankSwapSheet(sourceExercise, library, swapSet)
      .filter((c) => {
        const prof = profileFor(c.exercise)
        const ok = eq ? eq === prof.equipment || Boolean(prof.requires?.includes(eq)) : c.matchesUserEquipment
        return ok && c.score >= 45
      })
      .slice(0, 8)
  }, [sourceExercise, library, effective, eq, query, muscle])

  const showQuickSections = !sourceExercise && !query.trim() && muscle == null && eq == null
  const pinned = useMemo(() => {
    if (!showQuickSections) return []
    return library
      .filter((ex) => ex.pinned && profileFits(profileFor(ex), effective))
      .sort((a, b) => a.name.localeCompare(b.name))
  }, [library, effective, showQuickSections])

  const recent = useMemo(() => {
    if (!showQuickSections || !recentIds?.length) return []
    const byId = new Map(library.map((e) => [e.id, e]))
    const pinIds = new Set(pinned.map((e) => e.id))
    const out: ExerciseRow[] = []
    for (const id of recentIds) {
      if (pinIds.has(id)) continue
      const ex = byId.get(id)
      if (ex && !out.some((x) => x.id === id) && profileFits(profileFor(ex), effective)) {
        out.push(ex)
        if (out.length >= 6) break
      }
    }
    return out
  }, [library, recentIds, effective, showQuickSections, pinned])

  const rows = useMemo(() => {
    const exclude = new Set(top.map((c) => c.exercise.id))
    if (sourceExercise) exclude.add(sourceExercise.id)
    return filterExercises(library, { query, muscle, equipment: effective, selectedEquipment: eq, excludeIds: exclude, focusMuscle: sourceExercise?.primary[0] })
  }, [library, query, muscle, effective, eq, top, sourceExercise])

  useEffect(() => { setLimit(PAGE_SIZE) }, [query, muscle, effective, eq, sourceExercise?.id])
  useEffect(() => {
    const el = sentinelRef.current
    if (!el || limit >= rows.length) return
    const obs = new IntersectionObserver((entries) => {
      if (entries.some((e) => e.isIntersecting)) setLimit((n) => Math.min(rows.length, n + PAGE_SIZE))
    })
    obs.observe(el)
    return () => obs.disconnect()
  }, [limit, rows.length])

  if (creating && onCreate) {
    return <CreateForm initialName={query.trim()} initialMuscle={muscle ?? sourceExercise?.primary[0] ?? null} onCreate={onCreate} onCancel={() => setCreating(false)} />
  }
  const src = sourceExercise && profileFor(sourceExercise)
  const visibleRows = rows.slice(0, limit)
  return (
    <div class="wk-pickbody">
      {sourceExercise && src && (
        <div class="wk-s" style={{ color: 'var(--teal)' }}>
          {sourceExercise.name} · {patternName(src.pattern)} · {eqName(src.equipment)}
        </div>
      )}
      <div class="wk-search-row">
        <div class="wk-search">
          <SearchIcon size={20} />
          <input type="search" value={query} placeholder={t('picker_search')} aria-label={t('picker_search')} onInput={(e) => setQuery((e.target as HTMLInputElement).value)} />
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
      <div class="wk-scroll" role="group" aria-label={t('workouts_web_filter_equipment')}>
        {limited && <Chip label={t(showAllMovesToggle ? 'baseline_show_all_moves' : 'picker_my_equipment')} selected={(showAllMovesToggle ? !onlyMine : onlyMine) && !eq} onClick={() => { setOnlyMine(!onlyMine); setEq(null) }} />}
        {ALL_ACTIVE_EQUIPMENT.map((e) => (
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
              <PickerRow
                key={`swap_${c.exercise.id}`}
                ex={c.exercise as ExerciseRow}
                prof={c.profile}
                difficulty={c.difficulty}
                isCurrent={c.exercise.id === currentExerciseId}
                highlighted
                onClick={() => onPick(c.exercise as ExerciseRow)}
              />
            ))}
            <div class="wk-group">{t('picker_all')}</div>
          </>
        )}
        {pinned.length > 0 && (
          <>
            <div class="wk-group">{t('picker_pinned')}</div>
            {pinned.map((ex) => (
              <PickerRow key={`pin_${ex.id}`} ex={ex} prof={profileFor(ex)} onClick={() => onPick(ex)} />
            ))}
          </>
        )}
        {recent.length > 0 && (
          <>
            <div class="wk-group">{t('picker_recent')}</div>
            {recent.map((ex) => (
              <PickerRow key={`rec_${ex.id}`} ex={ex} prof={profileFor(ex)} onClick={() => onPick(ex)} />
            ))}
          </>
        )}
        {(pinned.length > 0 || recent.length > 0) && <div class="wk-group">{t('picker_all')}</div>}
        {visibleRows.map((ex) => (
          <PickerRow
            key={ex.id}
            ex={ex}
            prof={profileFor(ex)}
            difficulty={sourceExercise ? compareSwapTier(sourceExercise, ex) : null}
            isCurrent={!!sourceExercise && ex.id === currentExerciseId}
            onClick={() => onPick(ex)}
          />
        ))}
        {limit < rows.length && <div ref={sentinelRef} style={{ height: '1px' }} />}
        {rows.length === 0 && top.length === 0 && <p class="wk-muted" style={{ padding: '12px 8px' }}>{t('workouts_web_no_matches')}</p>}
        {onCreate && (
          <button type="button" class="wk-prow wk-create" onClick={() => setCreating(true)}>
            <AddIcon />
            <span class="wk-prow-name">{query.trim() ? t('picker_create_named', query.trim()) : t('picker_create_custom')}</span>
          </button>
        )}
      </div>
      <MuscleFilterSheet
        open={mapFilterOpen}
        selected={muscle}
        bodyType={settings.bodyType}
        onSelect={setMuscle}
        onClose={() => setMapFilterOpen(false)}
      />
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

/** Extended metadata fields (equipment, secondary muscles, pattern, unit override) for custom exercises. */
export function CustomExerciseFields(p: {
  nameLabel?: string
  name: string
  kind: ExerciseKind
  muscle: Muscle
  secondary: readonly Muscle[]
  equipment: Equipment
  pattern: MovementPattern | ''
  unitOverride: string
  onName: (v: string) => void
  onKind: (k: ExerciseKind) => void
  onMuscle: (m: Muscle) => void
  onSecondary: (s: Muscle[]) => void
  onEquipment: (e: Equipment) => void
  onPattern: (pat: MovementPattern | '') => void
  onUnitOverride: (u: string) => void
}) {
  return (
    <>
      <ExerciseFields
        nameLabel={p.nameLabel}
        name={p.name}
        kind={p.kind}
        muscle={p.muscle}
        onName={p.onName}
        onKind={p.onKind}
        onMuscle={(m) => {
          p.onMuscle(m)
          p.onSecondary(p.secondary.filter((x) => x !== m))
        }}
      />
      <div class="wk-group" style={{ margin: 0 }}>{t('editor_secondary_muscles')}</div>
      <div class="wk-scroll" role="group" aria-label={t('editor_secondary_muscles')}>
        {MUSCLES.filter((m) => m !== p.muscle).map((m) => {
          const on = p.secondary.includes(m)
          return (
            <Chip
              key={m}
              label={muscleLabel(m)}
              selected={on}
              onClick={() => p.onSecondary(on ? p.secondary.filter((x) => x !== m) : [...p.secondary, m])}
            />
          )
        })}
      </div>
      <div class="wk-group" style={{ margin: 0 }}>{t('editor_equipment')}</div>
      <div class="wk-scroll" role="group" aria-label={t('editor_equipment')}>
        {ALL_ACTIVE_EQUIPMENT.map((e) => (
          <Chip key={e} label={eqName(e)} selected={p.equipment === e} onClick={() => p.onEquipment(e)} />
        ))}
      </div>
      <div class="wk-group" style={{ margin: 0 }}>{t('editor_pattern')}</div>
      <div class="wk-scroll" role="group" aria-label={t('editor_pattern')}>
        {(Object.keys(MOVEMENT_PATTERN_LABEL) as MovementPattern[]).map((pat) => (
          <Chip
            key={pat}
            label={patternName(pat)}
            selected={p.pattern === pat}
            onClick={() => p.onPattern(p.pattern === pat ? '' : pat)}
          />
        ))}
      </div>
      {p.kind === 'WEIGHTED' && (
        <>
          <div class="wk-group" style={{ margin: 0 }}>{t('editor_unit_override')}</div>
          <div class="wk-chip-wrap" role="group" aria-label={t('editor_unit_override')}>
            <Chip label={t('editor_unit_default')} selected={!p.unitOverride} onClick={() => p.onUnitOverride('')} />
            <Chip label={t('editor_unit_kg')} selected={p.unitOverride.toUpperCase() === 'KG'} onClick={() => p.onUnitOverride('KG')} />
            <Chip label={t('editor_unit_lb')} selected={p.unitOverride.toUpperCase() === 'LB'} onClick={() => p.onUnitOverride('LB')} />
          </div>
        </>
      )}
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
