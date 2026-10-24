import { useState } from 'preact/hooks'
import { getLang, plural, t } from '../../i18n'
import type { SessionRow, SetRow } from '../../data/db'
import { putSession } from '../../data/db'
import { useLive } from '../../data/live'
import { useSettings, weightUnit } from '../../data/settings'
import { allDoneSets, useFinishedSessions, useLibrary } from '../../data/workouts'
import { todayEpochDay } from '../../domain/dates'
import { formatWeight } from '../../domain/units'
import { Button, Card, EmptyState, Sheet, TextField } from '../kit'
import { ExpandMoreIcon } from '../icons'
import { decodeDebrief, groupWeeks, minutesOf } from './helpers'
import './history.css'

const fmt = (day: number, o: Intl.DateTimeFormatOptions) =>
  new Intl.DateTimeFormat(getLang(), { ...o, timeZone: 'UTC' }).format(new Date(day * 86_400_000))

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
            : t('journal_range', fmt(w.start, { day: 'numeric', month: 'short' }), fmt(w.start + 6, { day: 'numeric', month: 'short' }))
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
                        volume > 0 ? t('journal_volume', formatWeight(unit, volume, lang === 'ar', 0)) : null].filter(Boolean).join(' · ')}
                    </span>
                  </span>
                  {prs > 0 && <span><PrTag count={prs} /></span>}
                  <span class={`hs-chev${expanded ? ' open' : ''}`}><ExpandMoreIcon /></span>
                </div>
              </Card>
              {expanded && [...days.entries()].map(([day, list]) => (
                <>
                  <h3 class="hs-day" key={`d${day}`}>{fmt(day, { weekday: 'long', day: 'numeric', month: 'long' })}</h3>
                  {list.map((s) => {
                    const ss = bySession.get(s.id!) ?? []
                    const mins = minutesOf(s)
                    const ex = [...new Set(ss.map((x) => x.exerciseId))].map((id) => names.get(id)).filter((n): n is string => !!n)
                    const prc = ss.filter((x) => x.isPr).length
                    const d = decodeDebrief(s.debrief)
                    return (
                      <div class="hs-sess" key={s.id}>
                        <Card href={`#/session/${s.id}`}>
                          <div class="hs-srow">
                            <span class="hs-date"><b>{fmt(day, { day: 'numeric' })}</b><span>{fmt(day, { weekday: 'short' })}</span></span>
                            <span class="hs-stext">
                              <span class="hs-sname"><span>{s.title}</span>{prc > 0 && <span><PrTag count={prc} /></span>}</span>
                              <span class="hs-meta">{[plural('hist_sets', ss.length), mins != null ? t('hist_minutes', mins) : null].filter(Boolean).join(' · ')}</span>
                              {ex.length > 0 && <span class="hs-meta hs-ellip">{ex.slice(0, 4).join(t('list_separator')) + (ex.length > 4 ? ` +${ex.length - 4}` : '')}</span>}
                            </span>
                          </div>
                        </Card>
                        {d && <p class="hs-debrief">{t('debrief_journal', d.score, d.summary)}</p>}
                        <button type="button" class={`hs-notebtn${s.note ? ' has' : ''}`} onClick={() => { setNoteFor(s); setNoteText(s.note) }}>
                          {s.note || t('journal_add_note')}
                        </button>
                      </div>
                    )
                  })}
                </>
              ))}
            </>
          )
        })}
      </div>
      <Sheet open={noteFor != null} onClose={() => setNoteFor(null)} title={t('history_web_note_title')}>
        <div class="hs-sheet">
          <TextField label={t('history_web_note_title')} value={noteText} onInput={(v) => setNoteText(v.slice(0, 300))} multiline placeholder={t('history_web_note_hint')} />
          <Button onClick={() => void saveNote()}>{t('action_save')}</Button>
        </div>
      </Sheet>
    </div>
  )
}
