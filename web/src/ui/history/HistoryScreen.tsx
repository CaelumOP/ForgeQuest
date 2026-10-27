import { useEffect, useRef, useState } from 'preact/hooks'
import { getLang, plural, t } from '../../i18n'
import type { SessionRow, SetRow } from '../../data/db'
import { putSession } from '../../data/db'
import { useLive } from '../../data/live'
import { useSettings, weightUnit } from '../../data/settings'
import { allDoneSets, deleteSession, getSets, restoreSession, useFinishedSessions, useLibrary, useWorkoutScores } from '../../data/workouts'
import { dayOfWeek, todayEpochDay } from '../../domain/dates'
import { formatDay } from '../day-format'
import { fromKg, unitLabel } from '../../domain/units'
import type { WeightUnit } from '../../domain/units'
import { Button, Card, EmptyState, Sheet, SwipeRow, TextField } from '../kit'
import { ExpandMoreIcon } from '../icons'
import { decodeDebrief, groupWeeks, minutesOf, scoreLine } from './helpers'
import { dayShort } from '../workouts/helpers'
import { DebriefSheet } from './DebriefSheet'
import './history.css'

/** "4,210 kg": whole numbers with grouping, number and unit kept in order in right-to-left text. */
const volumeText = (unit: WeightUnit, kg: number, arabic: boolean): string => {
  const text = `${Math.round(fromKg(unit, kg)).toLocaleString('en-US')} ${unitLabel(unit, arabic)}`
  return arabic ? `⁧${text}⁩` : `⁦${text}⁩`
}

function PrTag({ count = 1 }: { count?: number }) {
  return <><span class="hs-pr">{t('live_pr')}</span>{count > 1 && <span class="hs-prx"> ×{count}</span>}</>
}

/** The journal: weeks, newest first, each opening into its days and workouts. */
export function HistoryScreen(_: { param?: string }) {
  const sessions = useFinishedSessions()
  const doneSets = useLive(allDoneSets, [], [] as SetRow[])
  const library = useLibrary()
  const settings = useSettings()
  const unit = weightUnit(settings)
  const lang = getLang()
  const [open, setOpen] = useState<Record<number, boolean>>({})
  const [noteFor, setNoteFor] = useState<SessionRow | null>(null)
  const [noteText, setNoteText] = useState('')
  const scores = useWorkoutScores()
  const [debriefId, setDebriefId] = useState<number | null>(null)
  const [undo, setUndo] = useState<{ text: string; session: SessionRow; sets: SetRow[] } | null>(null)
  const undoTimer = useRef<number>(0)
  useEffect(() => () => clearTimeout(undoTimer.current), [])

  // A swiped-away workout is deleted at once; Undo puts it back (same ids) while the bar is up.
  const removeWorkout = async (s: SessionRow) => {
    const sets = await getSets(s.id!)
    await deleteSession(s.id!)
    setUndo({ text: t('food_removed', s.title), session: s, sets })
    clearTimeout(undoTimer.current)
    undoTimer.current = window.setTimeout(() => setUndo(null), 10000)
  }
  const doUndo = async () => {
    const u = undo
    setUndo(null)
    clearTimeout(undoTimer.current)
    if (u) await restoreSession(u.session, u.sets)
  }

  const debriefSession = sessions.find((x) => x.id === debriefId)
  const names = new Map(library.map((e) => [e.id, e.name]))
  const bySession = new Map<number, SetRow[]>()
  for (const s of doneSets) bySession.set(s.sessionId, [...(bySession.get(s.sessionId) ?? []), s])
  // Weeks start on Monday, like the streak, Home and Trends, so a journal week's count matches the weekly target.
  const weeks = groupWeeks(sessions, 0)
  const thisWeek = weeks.length ? groupWeeks([{ epochDay: todayEpochDay(), startedAt: 0 } as SessionRow], 0)[0].start : 0

  const saveNote = async () => {
    if (noteFor) await putSession({ ...noteFor, note: noteText.trim().slice(0, 300) })
    setNoteFor(null)
  }

  const header = (
    <div class="hs-head">
      <h2 class="hs-title">{t('cd_workout_history')}</h2>
      <p class="hs-sub">{plural('backup_workouts', sessions.length)}</p>
    </div>
  )

  if (sessions.length === 0) {
    return (
      <div class="content">
        {header}
        <div class="hs-list">
          <Card>
            <EmptyState title={t('hist_empty_title')} body={t('hist_empty_body')}
              action={<Button onClick={() => { location.hash = '#/workouts' }}>{t('hist_go_workouts')}</Button>} />
          </Card>
        </div>
      </div>
    )
  }

  return (
    <div class="content">
      {header}
      <div class="hs-list">
        {weeks.map((w, index) => {
          const expanded = open[w.start] ?? index === 0
          const sets = w.sessions.flatMap((s) => bySession.get(s.id!) ?? [])
          const minutes = w.sessions.reduce((a, s) => a + (minutesOf(s) ?? 0), 0)
          const volume = sets.reduce((a, s) => a + (s.weightKg ?? 0) * (s.reps ?? 0), 0)
          const prs = sets.filter((s) => s.isPr).length
          const title = w.start === thisWeek ? t('journal_this_week')
            : w.start === thisWeek - 7 ? t('journal_last_week')
            : t('journal_range', formatDay(w.start, { day: 'numeric', month: 'short' }), formatDay(w.start + 6, { day: 'numeric', month: 'short' }))
          const days = new Map<number, SessionRow[]>()
          for (const s of w.sessions) days.set(s.epochDay, [...(days.get(s.epochDay) ?? []), s])
          return (
            <>
              <Card key={`w${w.start}`} padded onClick={() => setOpen({ ...open, [w.start]: !expanded })}>
                <div class="hs-week" aria-expanded={expanded}>
                  <span class="hs-week-text">
                    <span class="hs-week-title">{title}</span>
                    <span class="hs-meta">
                      {[plural('backup_workouts', w.sessions.length), t('hist_minutes', minutes),
                        volume > 0 ? t('journal_volume', volumeText(unit, volume, lang === 'ar')) : null].filter(Boolean).join(' · ')}
                    </span>
                  </span>
                  {prs > 0 && <span><PrTag count={prs} /></span>}
                  <span class={`hs-chev${expanded ? ' open' : ''}`}><ExpandMoreIcon /></span>
                </div>
              </Card>
              {expanded && [...days.entries()].map(([day, list]) => (
                <>
                  <h3 class="hs-day" key={`d${day}`}>{formatDay(day, { weekday: 'long', day: 'numeric', month: 'long' })}</h3>
                  {list.map((s) => {
                    const ss = bySession.get(s.id!) ?? []
                    const mins = minutesOf(s)
                    const ex = [...new Set(ss.map((x) => x.exerciseId))].map((id) => names.get(id)).filter((n): n is string => !!n)
                    const prc = ss.filter((x) => x.isPr).length
                    const d = decodeDebrief(s.debrief)
                    return (
                      <div class="hs-sess" key={s.id}>
                        <SwipeRow label={t('action_delete')} radius={24} srDelete onDelete={() => void removeWorkout(s)}>
                        <Card href={`#/session/${s.id}`}>
                          <div class="hs-srow">
                            <span class="hs-date"><b>{formatDay(day, { day: 'numeric' })}</b><span>{dayShort(dayOfWeek(day), getLang())}</span></span>
                            <span class="hs-stext">
                              <span class="hs-sname"><span>{s.title}</span>{prc > 0 && <span><PrTag count={prc} /></span>}</span>
                              <span class="hs-meta">{[plural('hist_sets', ss.length), mins != null ? t('hist_minutes', mins) : null].filter(Boolean).join(' · ')}</span>
                              {ex.length > 0 && <span class="hs-meta hs-ellip">{ex.slice(0, 4).join(t('list_separator')) + (ex.length > 4 ? ` +${ex.length - 4}` : '')}</span>}
                            </span>
                          </div>
                        </Card>
                        </SwipeRow>
                        {scores.get(s.id!) && <p class="hs-debrief hs-score">{scoreLine(scores.get(s.id!)!)}</p>}
                        {d && <p class="hs-debrief">{d.summary}</p>}
                        <div class="hs-actions">
                          <button type="button" class={`hs-notebtn${s.note ? ' has' : ''}`} onClick={() => { setNoteFor(s); setNoteText(s.note) }}>
                            {s.note || t('journal_add_note')}
                          </button>
                          {ss.length > 0 && <button type="button" class="hs-notebtn" onClick={() => setDebriefId(s.id!)}>{t('debrief_open')}</button>}
                        </div>
                      </div>
                    )
                  })}
                </>
              ))}
            </>
          )
        })}
      </div>
      {undo && <div class="hs-undo" role="status"><span>{undo.text}</span><button type="button" onClick={() => void doUndo()}>{t('action_undo')}</button></div>}
      {debriefSession && <DebriefSheet key={debriefSession.id} session={debriefSession} score={scores.get(debriefSession.id!)} onClose={() => setDebriefId(null)} />}
      <Sheet open={noteFor != null} onClose={() => setNoteFor(null)} title={t('history_web_note_title')}>
        <div class="hs-sheet">
          <TextField label={t('history_web_note_title')} value={noteText} onInput={(v) => setNoteText(v.slice(0, 300))} multiline placeholder={t('history_web_note_hint')} />
          <Button onClick={() => void saveNote()}>{t('action_save')}</Button>
        </div>
      </Sheet>
    </div>
  )
}
