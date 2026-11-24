import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { getLang, plural, t } from '../../i18n'
import type { ExerciseRow, SetRow } from '../../data/db'
import type { ExerciseKind } from '../../domain/muscle'
import { EFFORT_CHIPS, chipOf, parseEffort, pickEffort, type SetEffort } from '../../domain/effort'
import { SET_TYPES, type SetType } from '../../domain/set-types'
import {
  calculatePlates, DEFAULT_KG_PLATES, formatPlateNumber, formatPlatesSummary,
  isBarbellExercise, type PlateBreakdown,
} from '../../domain/plate-math'
import { fromKg, loadStep, toKg, unitLabel, weightNumber, type WeightUnit } from '../../domain/units'
import { Button, Chip, CoachMark, Sheet, TextField, haptics } from '../kit'
import { NumberStepper } from '../kit/NumberStepper'
import { CheckIcon } from '../icons'
import { clampField } from './groups'
import { PlayIcon } from './glyphs'

const EFFORT_KEY: Record<SetEffort, string> = { EASY: 'effort_easy', OK: '', HARD: 'effort_hard', FAILURE: 'effort41_failure', PAIN: 'effort_pain' }
const EFFORT_CAPTION_KEY: Partial<Record<SetEffort, string>> = { EASY: 'effort41_easy_cap', HARD: 'effort41_hard_cap', FAILURE: 'effort41_failure_cap' }
const accentOf = (e: SetEffort) => (e === 'PAIN' ? 'coral' : e === 'FAILURE' ? 'gold' : e === 'HARD' ? 'purple' : 'teal')

const SET_TYPE_LABEL_KEY: Record<SetType, string> = {
  WORKING: 'live41_set_type_working',
  WARMUP: 'live41_set_type_warmup',
  DROPSET: 'live41_set_type_dropset',
  FAILURE: 'live41_set_type_failure',
  RESTPAUSE: 'live41_set_type_restpause',
}
const SET_TYPE_DESC_KEY: Record<SetType, string> = {
  WORKING: 'live41_set_type_working_desc',
  WARMUP: 'live41_set_type_warmup_desc',
  DROPSET: 'live41_set_type_dropset_desc',
  FAILURE: 'live41_set_type_failure_desc',
  RESTPAUSE: 'live41_set_type_restpause_desc',
}
const SET_TYPE_TAG_KEY: Record<Exclude<SetType, 'WORKING'>, string> = {
  WARMUP: 'live41_tag_warmup',
  DROPSET: 'live41_tag_dropset',
  FAILURE: 'live41_tag_failure',
  RESTPAUSE: 'live41_tag_restpause',
}

export function parseSetType(raw: string | undefined | null): SetType {
  return (SET_TYPES as readonly string[]).includes(raw ?? '') ? (raw as SetType) : 'WORKING'
}

export type EditedField = 'reps' | 'weightKg' | 'seconds'

export type SetRowProps = {
  label: string | null
  sublabel: string | null
  set: SetRow
  kind: ExerciseKind
  exercise?: ExerciseRow
  unit: WeightUnit
  barWeightKg?: number
  plateInventory?: readonly number[]
  previous?: SetRow
  /** The set's numbers beat the person's best: a done tick turns gold with a star. */
  pr?: boolean
  /** Briefly flash stepper numbers gold when a new PR is entered. */
  flash?: boolean
  showEffort: boolean
  onChange: (next: SetRow, editedField?: EditedField) => void
  onToggle: (set: SetRow) => void
  onSetEffort: (set: SetRow, picked: SetEffort) => void
  onStartHold: (set: SetRow) => void
  onDismissEffort?: () => void
  /** Tapping a done set's effort label shows the chips again so it can be re-tagged. */
  onReopenEffort?: (set: SetRow) => void
  onLabelClick?: () => void
}

/** "Last time" text for one previous set. */
export function previousText(prev: SetRow, kind: ExerciseKind, unit: WeightUnit): string {
  if (kind === 'HOLD') return t('rx_seconds', prev.seconds ?? 0)
  if (kind === 'WEIGHTED') return `${prev.reps ?? 0} × ${weightNumber(unit, prev.weightKg ?? 0)} ${unitLabel(unit, getLang() === 'ar')}`
  return String(prev.reps ?? 0)
}

/**
 * The set tick (Android SetTick): a 40px box in a 48px target. Empty: rounded square with an outline; pressed: tighter
 * corners and a little smaller; ticked: a teal circle with the check drawn in; a PR: gold with a star and a pop.
 */
export function SetTick({ checked, pr = false, onToggle }: { checked: boolean; pr?: boolean; onToggle: () => void }) {
  const gold = checked && pr
  const was = useRef(gold)
  const [pop, setPop] = useState(false)
  useEffect(() => {
    if (gold && !was.current) setPop(true)
    if (!gold) setPop(false)
    was.current = gold
  }, [gold])
  return (
    <button type="button" class={`lw-tick${checked ? ' lw-tick-on' : ''}${gold ? ' lw-tick-pr' : ''}${pop ? ' lw-tick-pop' : ''}`}
      role="checkbox" aria-checked={checked} aria-label={t('cd_set_done')} onClick={onToggle}>
      <span class="lw-tick-box" onAnimationEnd={() => setPop(false)}>
        <svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true" focusable="false">
          {gold
            ? <path fill="currentColor" d="M12 17.27 18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z" />
            : <path class="lw-tick-path" pathLength={1} fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" d="M5.5 12.5 10 17l8.5-9.5" />}
        </svg>
      </span>
    </button>
  )
}

/** One set: tappable type/number badge, previous-performance hint, thumb-keypad steppers, plate hint and tick. */
export function SetRowView(p: SetRowProps) {
  const { set, kind, unit, exercise } = p
  const ar = getLang() === 'ar'
  const u = unitLabel(unit, ar)
  const [noteMode, setNoteMode] = useState<'normal' | 'pain' | null>(null)
  const [typeOpen, setTypeOpen] = useState(false)
  const [plateBreakdown, setPlateBreakdown] = useState<PlateBreakdown | null>(null)
  const timer = useRef<number | undefined>(undefined)
  const clear = () => { if (timer.current) { clearTimeout(timer.current); timer.current = undefined } }
  const save = (patch: Partial<SetRow>, editedField?: EditedField) => p.onChange({ ...set, ...patch }, editedField)
  const effort = parseEffort(set.effort || null)
  const pickFromChips = (e: SetEffort) => {
    const next = pickEffort(set, e)
    p.onSetEffort(set, e)
    if (next.effort === 'PAIN') setNoteMode('pain')
    else p.onDismissEffort?.()
  }
  const setType = parseSetType(set.setType)
  const tag = setType === 'WORKING' ? null : t(SET_TYPE_TAG_KEY[setType])
  const badgeLabel = tag ?? p.label ?? String(set.setIndex + 1)
  const badgeClass = `lw-num-pill lw-type-${setType.toLowerCase()}`

  const isBarbell = useMemo(() => isBarbellExercise(exercise), [exercise?.id, exercise?.equipment])
  const weightQuickDeltas = useMemo(() => (unit === 'LB' ? [5, 10, 25] : [2.5, 5, 10]), [unit])
  const lastWeight = p.previous?.weightKg != null && p.previous.weightKg > 0 ? p.previous.weightKg : null
  const lastWeightDisplay = lastWeight != null ? Math.round(fromKg(unit, lastWeight) * 10) / 10 : null

  const weightKg = set.weightKg ?? 0
  const breakdown = useMemo(() => {
    if (!isBarbell || weightKg <= 0) return null
    return calculatePlates(weightKg, unit, p.barWeightKg ?? 20, p.plateInventory ?? DEFAULT_KG_PLATES)
  }, [isBarbell, weightKg, unit, p.barWeightKg, p.plateInventory])

  const weightStepper = (
    <NumberStepper
      label={t('stepper_weight')}
      value={Math.round(fromKg(unit, weightKg) * 10) / 10}
      min={0}
      max={Math.round(fromKg(unit, 999))}
      step={loadStep(unit)}
      decimals={1}
      unit={u}
      quickDeltas={weightQuickDeltas}
      lastTimeValue={lastWeightDisplay}
      lastTimeLabel={lastWeight != null ? `${weightNumber(unit, lastWeight)} ${u}` : null}
      format={(n) => `${Math.round(n * 10) / 10} ${u}`}
      onChange={(v) => save({ weightKg: clampField('weightKg', toKg(unit, v)) }, 'weightKg')}
    />
  )

  return (
    <div class="lw-setwrap">
      <div class={`lw-set${set.done ? ' lw-set-done' : ''}${set.done && (set.isPr || p.pr) ? ' lw-set-pr' : ''}`}
        onPointerDown={() => { clear(); timer.current = window.setTimeout(() => setNoteMode('normal'), 550) }}
        onPointerUp={clear} onPointerLeave={clear} onPointerCancel={clear}
        onContextMenu={(e) => { e.preventDefault(); setNoteMode('normal') }}>
        <button
          type="button"
          class="lw-num-btn"
          aria-label={t('live41_set_badge_cd', p.label ?? String(set.setIndex + 1), t(SET_TYPE_LABEL_KEY[setType]))}
          onPointerDown={(e) => e.stopPropagation()}
          onClick={() => setTypeOpen(true)}
        >
          <span class={badgeClass}>{badgeLabel}</span>
        </button>
        <div class="lw-setinfo">
          {p.sublabel != null && (
            p.onLabelClick
              ? <button type="button" class="lw-sublabel lw-sublabel-btn" aria-label={t('cd_options')} onClick={p.onLabelClick}>{p.sublabel} ▾</button>
              : <span class="lw-sublabel">{p.sublabel}</span>
          )}
          {setType !== 'WORKING' && (
            <span class={`lw-type-label lw-type-ink-${setType.toLowerCase()}`}>{t(SET_TYPE_LABEL_KEY[setType])}</span>
          )}
          {p.previous && <span class="lw-small">{t('live_last_time', previousText(p.previous, kind, unit))}</span>}
          <span class="lw-small">
            {set.targetLabel && p.sublabel != null && <span>{set.targetLabel} </span>}
            {set.done && (set.isPr || p.pr) && <span class="lw-pr">{t('live_pr')}</span>}
            {set.effort !== '' && effort !== 'OK' && (set.done && p.onReopenEffort
              ? <button type="button" class={`lw-effort lw-effort-btn lw-effort-${accentOf(effort)}`} aria-label={t('cd_edit_effort')}
                  onPointerDown={(e) => e.stopPropagation()} onClick={() => p.onReopenEffort?.(set)}>{t(EFFORT_KEY[effort])}</button>
              : <span class={`lw-effort lw-effort-${accentOf(effort)}`}> {t(EFFORT_KEY[effort])}</span>)}
          </span>
          {set.note && <span class="lw-note">{set.note}</span>}
        </div>
        <div class={`lw-steps${p.flash ? ' lw-steps-flash' : ''}`} onPointerDown={(e) => e.stopPropagation()}>
          {kind === 'HOLD' && (
            <div class="lw-holdrow">
              {!set.done && <button type="button" class="lw-play" aria-label={t('cd_start_hold')} onClick={() => p.onStartHold(set)}><PlayIcon /></button>}
              <NumberStepper
                label={t('stepper_hold_time')}
                value={set.seconds ?? 0}
                min={0}
                max={3600}
                step={1}
                unit={t('unit_s')}
                quickDeltas={[5, 10, 30]}
                lastTimeValue={p.previous?.seconds ?? null}
                lastTimeLabel={p.previous?.seconds != null ? t('rx_seconds', p.previous.seconds) : null}
                format={(n) => `${n} ${t('unit_s')}`}
                onChange={(v) => save({ seconds: clampField('seconds', v) }, 'seconds')}
              />
            </div>
          )}
          {kind !== 'HOLD' && (
            <NumberStepper
              label={t('stepper_reps')}
              value={set.reps ?? 0}
              min={0}
              max={999}
              unit={t('unit_reps')}
              quickDeltas={[1, 2, 5]}
              lastTimeValue={p.previous?.reps ?? null}
              format={kind === 'WEIGHTED' ? (n) => plural('rx_reps_n', n, n) : undefined}
              onChange={(v) => save({ reps: clampField('reps', v) }, 'reps')}
            />
          )}
          {(kind === 'WEIGHTED' || set.weightKg != null) && weightStepper}
          {breakdown && !breakdown.belowBar && (
            <button
              type="button"
              class="lw-plates-btn"
              aria-label={t('live41_plates_breakdown_cd', `${weightNumber(unit, weightKg)} ${u}`)}
              onClick={() => setPlateBreakdown(breakdown)}
            >
              {breakdown.barOnly
                ? t('live41_plates_bar_only', `${formatPlateNumber(breakdown.barWeight)} ${u}`)
                : t('live41_plates_side', formatPlatesSummary(breakdown.perSide))}
            </button>
          )}
        </div>
        <CoachMark id="coach_tick" title={t('coach_tick_t')} body={t('coach_tick_b')} below>
          <SetTick checked={set.done} pr={set.isPr || p.pr} onToggle={() => p.onToggle(set)} />
        </CoachMark>
      </div>
      {set.done && p.showEffort && (
        <div class="lw-efforts">
          <div class="lw-effort-head">
            <span class="lw-effort-cap">{t('live_effort_caption')}</span>
            <button type="button" class="lw-effort-skip" onClick={() => p.onDismissEffort?.()}>{t('live_effort_skip')}</button>
          </div>
          <div class="lw-effort-row">
            <div class="lw-effort-group" role="radiogroup" aria-label={t('a11y_set_effort')}>
              {EFFORT_CHIPS.map((e) => (
                <button key={e} type="button" role="radio" aria-checked={chipOf(set.effort) === e}
                  class={`lw-effort-seg kit-accent-${accentOf(e)}${chipOf(set.effort) === e ? ' lw-effort-on' : ''}`}
                  onClick={() => pickFromChips(e)}>
                  <span class="lw-effort-name">{t(EFFORT_KEY[e])}</span>
                  <span class="lw-effort-capt">{t(EFFORT_CAPTION_KEY[e]!)}</span>
                </button>
              ))}
            </div>
            <button type="button" aria-pressed={set.effort === 'PAIN'} aria-label={t('effort41_pain_cd')}
              class={`lw-effort-hurt${set.effort === 'PAIN' ? ' lw-effort-hurt-on' : ''}`}
              onClick={() => pickFromChips('PAIN')}>
              <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" fill="currentColor">
                <path d="M12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16zm-1 4h2v3h3v2h-3v3h-2v-3H8v-2h3z"/>
              </svg>
            </button>
          </div>
          {set.effort === 'PAIN' && <Button variant="text" onClick={() => setNoteMode('pain')}>{t('live_add_note')}</Button>}
        </div>
      )}

      <NoteSheet open={noteMode != null} pain={noteMode === 'pain'} initial={set.note} onClose={() => setNoteMode(null)}
        onSave={(v) => { save({ note: v.trim().slice(0, 120) }); setNoteMode(null); if (p.showEffort) p.onDismissEffort?.() }} />

      <SetTypeSheet
        open={typeOpen}
        selected={setType}
        onClose={() => setTypeOpen(false)}
        onSelect={(nextType) => {
          haptics.tick()
          save({ setType: nextType })
          setTypeOpen(false)
        }}
      />

      <PlateBreakdownSheet
        breakdown={plateBreakdown}
        arabic={ar}
        onClose={() => setPlateBreakdown(null)}
      />
    </div>
  )
}

function SetTypeSheet({
  open,
  selected,
  onClose,
  onSelect,
}: {
  open: boolean
  selected: SetType
  onClose: () => void
  onSelect: (t: SetType) => void
}) {
  return (
    <Sheet open={open} onClose={onClose} title={t('live41_set_type_title')}>
      <div class="lw-type-list">
        {SET_TYPES.map((type) => {
          const isSel = type === selected
          const tag = type === 'WORKING' ? '1' : t(SET_TYPE_TAG_KEY[type])
          return (
            <button
              key={type}
              type="button"
              class={`lw-type-opt${isSel ? ' lw-type-opt-on' : ''}`}
              onClick={() => onSelect(type)}
            >
              <span class={`lw-type-badge${isSel ? ' lw-type-badge-on' : ''}`}>{tag}</span>
              <span class="lw-type-opt-text">
                <span class="lw-type-opt-title">{t(SET_TYPE_LABEL_KEY[type])}</span>
                <span class="lw-small">{t(SET_TYPE_DESC_KEY[type])}</span>
              </span>
              {isSel && <span class="lw-type-check"><CheckIcon size={20} /></span>}
            </button>
          )
        })}
      </div>
    </Sheet>
  )
}

function PlateBreakdownSheet({
  breakdown,
  arabic,
  onClose,
}: {
  breakdown: PlateBreakdown | null
  arabic: boolean
  onClose: () => void
}) {
  if (!breakdown) return null
  const u = unitLabel(breakdown.unit, arabic)
  return (
    <Sheet open onClose={onClose} title={t('live41_plates_title')}>
      <div class="lw-plates-sheet">
        <div class="lw-small">
          {[
            t('live41_plates_target', `${formatPlateNumber(breakdown.targetWeight)} ${u}`),
            t('live41_plates_bar', `${formatPlateNumber(breakdown.barWeight)} ${u}`),
          ].join(' · ')}
        </div>
        {breakdown.perSide.length === 0 ? (
          <div class="lw-peek-box">{t('live41_plates_none_needed')}</div>
        ) : (
          <div class="lw-peek-box">
            {breakdown.perSide.map((p) => (
              <div key={p.weight} class="lw-plate-row">
                <span class="lw-plate-w">{`${formatPlateNumber(p.weight)} ${u}`}</span>
                <span class="lw-plate-c">{`× ${p.count}`}</span>
              </div>
            ))}
          </div>
        )}
        <div class="lw-plate-loaded">
          {t('live41_plates_loaded', `${formatPlateNumber(breakdown.loadedWeight)} ${u}`)}
        </div>
        {breakdown.remainder >= 0.05 && (
          <div class="lw-plate-rem">
            {t('live41_plates_remainder', `${formatPlateNumber(breakdown.remainder)} ${u}`)}
          </div>
        )}
      </div>
    </Sheet>
  )
}

function NoteSheet({ open, pain, initial, onClose, onSave }: { open: boolean; pain: boolean; initial: string; onClose: () => void; onSave: (v: string) => void }) {
  const title = pain ? t('v42_pain_note_title') : t('live_note_title')
  return <Sheet open={open} onClose={onClose} title={title}><NoteBody pain={pain} title={title} initial={initial} onClose={onClose} onSave={onSave} /></Sheet>
}

function NoteBody({ pain, title, initial, onClose, onSave }: { pain: boolean; title: string; initial: string; onClose: () => void; onSave: (v: string) => void }) {
  const [text, setText] = useState(initial)
  const tags: [string, string][] = pain
    ? [
        [t('v42_pain_tag_sharp'), t('v42_pain_tag_sharp')],
        [t('v42_pain_tag_ache'), t('v42_pain_tag_ache')],
        [t('v42_pain_tag_joint'), t('v42_pain_tag_joint')],
      ]
    : []
  const toggle = (value: string) => {
    const active = text.toLowerCase().includes(value.toLowerCase())
    const escaped = value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
    setText(active ? text.replace(new RegExp(escaped, 'gi'), '').trim().replace(/^[·, ]+|[·, ]+$/g, '')
      : !text.trim() ? value : `${value} · ${text}`.slice(0, 120))
  }
  return (
    <div class="lw-notebody">
      {tags.length > 0 && (
        <div class="lw-tags">{tags.map(([label, value]) => <Chip key={label} label={label} selected={text.toLowerCase().includes(value.toLowerCase())} onClick={() => toggle(value)} />)}</div>
      )}
      <TextField label={title} value={text} placeholder={pain ? t('v42_pain_note_hint') : t('v42_note_hint')} onInput={(v) => setText(v.slice(0, 120))} />
      <div class="lw-actions">
        {initial && <Button variant="text" class="kit-danger" onClick={() => onSave('')}>{t('action_clear')}</Button>}
        <Button variant="text" onClick={onClose}>{t('action_cancel')}</Button>
        <Button onClick={() => onSave(text)}>{t('action_save')}</Button>
      </div>
    </div>
  )
}
