import { useEffect, useRef, useState } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import type { SetRow } from '../../data/db'
import type { ExerciseKind } from '../../domain/muscle'
import { SET_EFFORTS, parseEffort, type SetEffort } from '../../domain/effort'
import { fromKg, loadStep, toKg, unitLabel, weightNumber, type WeightUnit } from '../../domain/units'
import { Chip, Sheet, Stepper, TextField, Button } from '../kit'
import { clampField } from './groups'
import { PlayIcon } from './glyphs'

const EFFORT_KEY: Record<SetEffort, string> = { EASY: 'effort_easy', OK: 'effort_ok', HARD: 'effort_hard', PAIN: 'effort_pain' }
const accentOf = (e: SetEffort) => (e === 'PAIN' ? 'coral' : e === 'HARD' ? 'gold' : 'teal')

export type SetRowProps = {
  label: string | null
  sublabel: string | null
  set: SetRow
  kind: ExerciseKind
  unit: WeightUnit
  previous?: SetRow
  /** The set's numbers beat the person's best: a done tick turns gold with a star. */
  pr?: boolean
  showEffort: boolean
  onChange: (next: SetRow) => void
  onToggle: (set: SetRow) => void
  onStartHold: (set: SetRow) => void
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
  // Pop only when a set turns gold on screen, not when a gold row is drawn again.
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

/** One set: number, previous-performance hint, steppers and the big tick (Android SetRow). Long-press opens the note. */
export function SetRowView(p: SetRowProps) {
  const { set, kind, unit } = p
  const ar = getLang() === 'ar'
  const u = unitLabel(unit, ar)
  const [noteOpen, setNoteOpen] = useState(false)
  const timer = useRef<number | undefined>(undefined)
  const clear = () => { if (timer.current) { clearTimeout(timer.current); timer.current = undefined } }
  const save = (patch: Partial<SetRow>) => p.onChange({ ...set, ...patch })
  const effort = parseEffort(set.effort || null)
  const weightStepper = (
    <Stepper label={t('stepper_weight')} value={Math.round(fromKg(unit, set.weightKg ?? 0) * 10) / 10} min={0} max={fromKg(unit, 999)} step={loadStep(unit)}
      format={(n) => `${Math.round(n * 10) / 10} ${u}`} onChange={(v) => save({ weightKg: clampField('weightKg', toKg(unit, v)) })} />
  )
  return (
    <div class="lw-setwrap">
      <div class={`lw-set${set.done ? ' lw-set-done' : ''}${set.done && (set.isPr || p.pr) ? ' lw-set-pr' : ''}`}
        onPointerDown={() => { clear(); timer.current = window.setTimeout(() => setNoteOpen(true), 550) }}
        onPointerUp={clear} onPointerLeave={clear} onPointerCancel={clear}
        onContextMenu={(e) => { e.preventDefault(); setNoteOpen(true) }}>
        {p.label != null && <span class="lw-num">{p.label}</span>}
        <div class="lw-setinfo">
          {p.sublabel != null && (
            p.onLabelClick
              ? <button type="button" class="lw-sublabel lw-sublabel-btn" aria-label={t('cd_options')} onClick={p.onLabelClick}>{p.sublabel} ▾</button>
              : <span class="lw-sublabel">{p.sublabel}</span>
          )}
          {p.previous && <span class="lw-small">{t('live_last_time', previousText(p.previous, kind, unit))}</span>}
          <span class="lw-small">
            {set.targetLabel && p.sublabel != null && <span>{set.targetLabel} </span>}
            {set.isPr && <span class="lw-pr">{t('live_pr')}</span>}
            {set.effort !== '' && effort !== 'OK' && <span class={`lw-effort lw-effort-${accentOf(effort)}`}> {t(EFFORT_KEY[effort])}</span>}
          </span>
          {set.note && <span class="lw-note">{set.note}</span>}
        </div>
        <div class="lw-steps" onPointerDown={(e) => e.stopPropagation()}>
          {kind === 'HOLD' && (
            <div class="lw-holdrow">
              {!set.done && <button type="button" class="lw-play" aria-label={t('cd_start_hold')} onClick={() => p.onStartHold(set)}><PlayIcon /></button>}
              <Stepper label={t('stepper_hold_time')} value={set.seconds ?? 0} min={0} max={3600} step={5}
                format={(n) => `${n} ${t('unit_s')}`} onChange={(v) => save({ seconds: clampField('seconds', v) })} />
            </div>
          )}
          {kind !== 'HOLD' && (
            <Stepper label={t('stepper_reps')} value={set.reps ?? 0} min={0} max={999}
              format={kind === 'WEIGHTED' ? (n) => `${n} ${t('unit_reps')}` : undefined} onChange={(v) => save({ reps: clampField('reps', v) })} />
          )}
          {(kind === 'WEIGHTED' || set.weightKg != null) && weightStepper}
        </div>
        <SetTick checked={set.done} pr={set.isPr || p.pr} onToggle={() => p.onToggle(set)} />
      </div>
      {set.done && p.showEffort && (
        <div class="lw-efforts">
          {SET_EFFORTS.map((e) => (
            <Chip key={e} label={t(EFFORT_KEY[e])} selected={set.effort === e} accent={accentOf(e)} onClick={() => save({ effort: set.effort === e ? '' : e })} />
          ))}
          {set.effort === 'PAIN' && <Button variant="text" onClick={() => setNoteOpen(true)}>{t('live_add_note')}</Button>}
        </div>
      )}
      <NoteSheet open={noteOpen} initial={set.note} onClose={() => setNoteOpen(false)} onSave={(v) => { save({ note: v.trim().slice(0, 120) }); setNoteOpen(false) }} />
    </div>
  )
}

function NoteSheet({ open, initial, onClose, onSave }: { open: boolean; initial: string; onClose: () => void; onSave: (v: string) => void }) {
  return <Sheet open={open} onClose={onClose} title={t('live_note_title')}><NoteBody initial={initial} onClose={onClose} onSave={onSave} /></Sheet>
}

function NoteBody({ initial, onClose, onSave }: { initial: string; onClose: () => void; onSave: (v: string) => void }) {
  const [text, setText] = useState(initial)
  const tags: [string, string][] = [
    [t('live_tag_easy'), t('live_tag_easy')], [t('live_tag_solid'), t('live_tag_solid')],
    [t('live_tag_max'), t('live_tag_max_note')], [t('live_tag_joint'), t('live_tag_joint_note')],
  ]
  const toggle = (value: string) => {
    const active = text.toLowerCase().includes(value.toLowerCase())
    setText(active ? text.replace(value, '').trim().replace(/^[·, ]+|[·, ]+$/g, '')
      : !text.trim() ? value : `${value} · ${text}`.slice(0, 120))
  }
  return (
    <div class="lw-notebody">
      <div class="lw-tags">{tags.map(([label, value]) => <Chip key={label} label={label} selected={text.toLowerCase().includes(value.toLowerCase())} onClick={() => toggle(value)} />)}</div>
      <TextField label={t('live_note_title')} value={text} placeholder={t('live_note_hint')} onInput={(v) => setText(v.slice(0, 120))} />
      <div class="lw-actions">
        {initial && <Button variant="text" class="kit-danger" onClick={() => onSave('')}>{t('action_clear')}</Button>}
        <Button variant="text" onClick={onClose}>{t('action_cancel')}</Button>
        <Button onClick={() => onSave(text)}>{t('action_save')}</Button>
      </div>
    </div>
  )
}
