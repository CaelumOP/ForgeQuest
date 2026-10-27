import { useEffect, useRef, useState } from 'preact/hooks'
import { getLang, plural, t } from '../../i18n'
import type { SetRow } from '../../data/db'
import { getSession, useWorkoutScore } from '../../data/workouts'
import { useSettings, weightUnit } from '../../data/settings'
import { deleteFinishedSet, deleteSession, moveSession, updateFinishedSet, useLibrary, useSession, useSets } from '../../data/workouts'
import { todayEpochDay } from '../../domain/dates'
import { fromKg, loadStep, toKg, unitLabel } from '../../domain/units'
import { Button, Card, Dialog, EmptyState, IconButton, ListRow, Sheet, Stepper, TextField } from '../kit'
import { CloseIcon, MoreIcon } from '../icons'
import { musclesLabel } from '../workouts/helpers'
import { dayOfIso, decodeDebrief, isoOfDay, minutesOf, scoreReasons } from './helpers'
import './history.css'

const goHistory = () => { location.hash = '#/history' }

/** One finished set: steppers write straight to the database; long-press (or tap the note) edits its note. */
function SetLine({ set, index, kind, onNote, onRemove }: { set: SetRow; index: number; kind: string; onNote: () => void; onRemove: () => void }) {
  const unit = weightUnit(useSettings())
  const ar = getLang() === 'ar'
  const timer = useRef<number | undefined>(undefined)
  const clear = () => { if (timer.current) { clearTimeout(timer.current); timer.current = undefined } }
  const save = (patch: Partial<SetRow>) => void updateFinishedSet({ ...set, ...patch })
  const u = unitLabel(unit, ar)
  return (
    <div class="hs-set"
      onPointerDown={() => { clear(); timer.current = window.setTimeout(onNote, 550) }}
      onPointerUp={clear} onPointerLeave={clear} onPointerCancel={clear}
      onContextMenu={(e) => { e.preventDefault(); onNote() }}>
      <span class="hs-num">{index + 1}</span>
      <span class="hs-setinfo">
        {set.isPr && <span><span class="hs-pr">{t('live_pr')}</span></span>}
        {set.note && <button type="button" class="hs-setnote" onClick={onNote}>{set.note}</button>}
      </span>
      <span class="hs-steps" onPointerDown={(e) => e.stopPropagation()}>
        {kind === 'HOLD' && (
          <Stepper label={t('stepper_hold_time')} value={set.seconds ?? 0} min={0} max={3600} step={5}
            format={(n) => `${n} ${t('unit_s')}`} onChange={(v) => save({ seconds: Math.round(v) })} />
        )}
        {kind !== 'HOLD' && (
          <Stepper label={t('stepper_reps')} value={set.reps ?? 0} min={0} max={999}
            format={kind === 'WEIGHTED' ? (n) => `${n} ${t('unit_reps')}` : undefined} onChange={(v) => save({ reps: Math.round(v) })} />
        )}
        {kind === 'WEIGHTED' && (
          <Stepper label={t('stepper_weight')} value={fromKg(unit, set.weightKg ?? 0)} min={0} max={fromKg(unit, 999)} step={loadStep(unit)}
            format={(n) => `${Math.round(n * 10) / 10} ${u}`}
            onChange={(v) => save({ weightKg: Math.min(999, Math.max(0, Math.round(toKg(unit, v) * 10) / 10)) })} />
        )}
      </span>
      <IconButton label={t('cd_remove_set')} onClick={onRemove}><CloseIcon size={18} /></IconButton>
    </div>
  )
}

/** One finished workout: fix a number, remove a set, move it to another day or delete it. Records are re-checked after each change. */
export function SessionScreen({ param }: { param?: string }) {
  const id = Number(param)
  const session = useSession(id)
  const sets = useSets(id)
  const library = useLibrary()
  const score = useWorkoutScore(id)
  const [missing, setMissing] = useState(false)
  const [menu, setMenu] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [picking, setPicking] = useState(false)
  const [iso, setIso] = useState('')
  const [pendingDelete, setPendingDelete] = useState<SetRow | null>(null)
  const [noteFor, setNoteFor] = useState<SetRow | null>(null)
  const [noteText, setNoteText] = useState('')

  useEffect(() => { void getSession(id).then((s) => { if (!s) setMissing(true) }) }, [id])

  if (!session) {
    return (
      <div class="content">
        {missing && <div class="hs-list"><Card><EmptyState title={t('history_web_not_found')} action={<Button onClick={goHistory}>{t('cd_workout_history')}</Button>} /></Card></div>}
      </div>
    )
  }
  const exercises = new Map(library.map((e) => [e.id, e]))
  const date = new Intl.DateTimeFormat(getLang(), { weekday: 'long', month: 'short', day: 'numeric', timeZone: 'UTC' }).format(new Date(session.epochDay * 86_400_000))
  const mins = minutesOf(session)
  const groupMap = new Map<number, SetRow[]>()
  for (const s of sets) groupMap.set(s.itemPosition, [...(groupMap.get(s.itemPosition) ?? []), s])
  const groups = [...groupMap.entries()].sort((a, b) => a[0] - b[0]).map(([, g]) => g.sort((a, b) => a.setIndex - b.setIndex))
  const debrief = decodeDebrief(session.debrief)
  const today = todayEpochDay()

  return (
    <div class="content">
      <div class="hs-toolbar">
        <div class="hs-toolbar-text">
          <h2 class="hs-title">{session.title}</h2>
          <p class="hs-sub">{[date, mins != null ? t('hist_minutes', mins) : null, plural('hist_sets', sets.length)].filter(Boolean).join(' · ')}</p>
        </div>
        <IconButton label={t('cd_more')} onClick={() => setMenu(true)}><MoreIcon /></IconButton>
      </div>
      <div class="hs-list">
        <p class="hs-hint">{t('hist_edit_hint')}</p>
        {session.note && <p class="hs-hint"><i>{session.note}</i></p>}
        {(debrief || score) && (
          <Card>
            <div class="hs-deb-title">{t(debrief ? 'debrief_title' : 'score_title')}</div>
            {score && <div class="hs-deb-score">{t('debrief_score', score.score)}</div>}
            {score && <p class="hs-deb-body">{scoreReasons(score)}</p>}
            {debrief && <p class="hs-deb-body">{debrief.summary}</p>}
            {debrief?.nextTime.map((x) => <p class="hs-deb-body" key={x}>{t('debrief_next_time', x)}</p>)}
          </Card>
        )}
        {groups.map((group) => {
          const first = group[0]
          const ex = exercises.get(first.exerciseId)
          return (
            <Card key={first.itemPosition}>
              <a class="hs-ex" href={`#/exercise/${first.exerciseId}`}>
                <span class="hs-ex-name">{ex?.name ?? first.exerciseId}</span>
                <span class="hs-meta">{(ex ? musclesLabel(ex) : '') || first.block}</span>
              </a>
              {group.map((set, i) => (
                <SetLine key={set.id} set={set} index={i} kind={ex?.kind ?? 'REPS'}
                  onNote={() => { setNoteFor(set); setNoteText(set.note) }} onRemove={() => setPendingDelete(set)} />
              ))}
            </Card>
          )
        })}
        {groups.length === 0 && <Card><EmptyState title={t('hist_no_sets')} body={t('hist_no_sets_body')} /></Card>}
      </div>

      <Sheet open={menu} onClose={() => setMenu(false)} title={t('cd_more')}>
        <ListRow title={t('hist_change_date')} onClick={() => { setMenu(false); setIso(isoOfDay(session.epochDay)); setPicking(true) }} />
        <ListRow title={t('hist_delete_workout')} onClick={() => { setMenu(false); setConfirmDelete(true) }} />
      </Sheet>

      <Sheet open={picking} onClose={() => setPicking(false)} title={t('hist_change_date')}>
        <div class="hs-sheet">
          <label class="field">{t('history_web_date')}
            <input class="hs-datefield" type="date" value={iso} max={isoOfDay(today)} onInput={(e) => setIso((e.target as HTMLInputElement).value)} />
          </label>
          <Button onClick={() => {
            const day = dayOfIso(iso)
            setPicking(false)
            if (day != null && day <= today && day !== session.epochDay) void moveSession(id, day)
          }}>{t('action_save')}</Button>
        </div>
      </Sheet>

      <Sheet open={noteFor != null} onClose={() => setNoteFor(null)} title={t('live_note_title')}>
        <div class="hs-sheet">
          <TextField label={t('live_note_title')} value={noteText} onInput={(v) => setNoteText(v.slice(0, 120))} placeholder={t('live_note_hint')} />
          <Button onClick={() => { if (noteFor) void updateFinishedSet({ ...noteFor, note: noteText.trim().slice(0, 120) }); setNoteFor(null) }}>{t('action_save')}</Button>
        </div>
      </Sheet>

      <Dialog open={pendingDelete != null} destructive title={t('hist_remove_set_title')}
        message={sets.length === 1 ? t('hist_remove_last_set') : t('hist_records_rechecked')}
        confirmLabel={t('action_remove')} onCancel={() => setPendingDelete(null)}
        onConfirm={() => {
          const set = pendingDelete
          setPendingDelete(null)
          if (set) void deleteFinishedSet(set).then(() => { if (sets.length === 1) goHistory() })
        }} />

      <Dialog open={confirmDelete} destructive title={t('hist_delete_title')} message={plural('hist_delete_body', sets.length)}
        confirmLabel={t('action_delete')} cancelLabel={t('action_keep')} onCancel={() => setConfirmDelete(false)}
        onConfirm={() => { setConfirmDelete(false); void deleteSession(id).then(goHistory) }} />
    </div>
  )
}
