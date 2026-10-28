import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { getLang, plural, t, useLang } from '../../i18n'
import type { ExerciseRow, SetRow } from '../../data/db'
import { configuredEquipment, useSettings, weightUnit } from '../../data/settings'
import {
  addCustomExercise, addExercise, addSet, allWorkoutScores, discardSession, finishSession, harderHint, recentDoneSets, recentDoneSetsAll, removeSet, stepUp, swapExercise, updateSet,
  useLibrary, useOpenSession, useSets, type FinishResult,
} from '../../data/workouts'
import { weightNumber, unitLabel, type WeightUnit } from '../../domain/units'
import { isWarmUp } from '../../domain/workout-score'
import { Button, Card, Dialog, EmptyState, IconButton, Sheet, ListRow, haptics, toast } from '../kit'
import { recordScore } from '../../domain/progress'
import { AddIcon, CheckIcon, CloseIcon, ExpandMoreIcon } from '../icons'
import { ExercisePicker } from '../workouts/ExercisePicker'
import { autoBackup } from '../../data/drive'
import { blockLabel, openFormVideo } from '../workouts/helpers'
import { alarm, beep, buzz, keepScreenOn, unlockAudio } from './feedback'
import { FocusIcon, ListIcon, MoreVert, RepeatIcon } from './glyphs'
import { currentGroupIndex, groupSets, groupSetsByExercise, nextUntickedSet, type Group } from './groups'
import { HoldOverlay, RestBar } from './Overlays'
import { SetRowView, SetTick, type SetRowProps } from './SetRowView'
import {
  adjustRest, clock, holdElapsed, restLeft, holdSaved, loadTimers, saveTimers, startHold, startRest, restDone,
  type HoldTimer, type RestTimer,
} from './timers'
import { evaluateBadges, getProgressSnapshot, markBadgesSeen } from '../../data/progress'
import { dayOfWeek, todayEpochDay } from '../../domain/dates'
import { nextUpDay, workoutMinutes, type WorkoutSummaryData } from './summary'
import { WorkoutSummary } from './WorkoutSummary'
import './live.css'

/**
 * After finishSession: the backup runs in the background. The debrief on the summary is calculated when the card opens.
 * The per-set effort chips (Easy, OK, Hard, Pain) are ported in SetRowView.
 */
export function onWorkoutFinished(_sessionId: number, _result: FinishResult): void { void autoBackup('workout') }

const FOCUS_KEY = 'fq.live.focus'
const store = (): Storage | null => { try { return sessionStorage } catch { return null } }
const readFocus = () => { try { return localStorage.getItem(FOCUS_KEY) === '1' } catch { return false } }
const leave = () => { if (history.length > 1) history.back(); else location.hash = '#/workouts' }

type PickerMode = { kind: 'add' } | { kind: 'swap'; position: number }

export function LiveWorkoutScreen(_: { param?: string }) {
  useLang()
  const session = useOpenSession()
  const sets = useSets(session?.id ?? -1)
  const library = useLibrary()
  const settings = useSettings()
  const unit = weightUnit(settings)
  const equipment = useMemo(() => configuredEquipment(settings), [settings])
  const exercises = useMemo(() => new Map(library.map((e) => [e.id, e])), [library])

  const [now, setNow] = useState(Date.now())
  const [rest, setRest] = useState<RestTimer | null>(null)
  const [hold, setHold] = useState<HoldTimer | null>(null)
  const [focus, setFocus] = useState(readFocus)
  const [page, setPage] = useState(0)
  const [effortSetId, setEffortSetId] = useState<number | null>(null)
  const [picker, setPicker] = useState<PickerMode | null>(null)
  const [menu, setMenu] = useState<{ position: number; swapOnly: boolean } | null>(null)
  const [confirmFinish, setConfirmFinish] = useState(false)
  const [confirmExit, setConfirmExit] = useState(false)
  const [finishing, setFinishing] = useState(false)
  const [summary, setDone] = useState<WorkoutSummaryData | null>(null)
  const [previous, setPrevious] = useState<Map<string, SetRow[]>>(new Map())
  const [harder, setHarder] = useState<Map<string, Hint>>(new Map())
  // Best score ever per exercise (finished workouts only), so a set that beats it shows a gold tick.
  const [best, setBest] = useState<Map<string, number>>(new Map())

  const restRef = useRef(rest); restRef.current = rest
  const holdRef = useRef(hold); holdRef.current = hold
  const nowRef = useRef(now)
  const setsRef = useRef(sets); setsRef.current = sets
  const hapticsOn = settings.hapticsEnabled !== false
  const hapticsRef = useRef(hapticsOn); hapticsRef.current = hapticsOn
  const lastCue = useRef(Number.MIN_SAFE_INTEGER)
  const restored = useRef<number | null>(null)
  // iOS Safari has no vibration: a flash on the dock when rest ends, and the countdown in the tab title.
  const [flash, setFlash] = useState(false)
  const [overTitle, setOverTitle] = useState(false)
  const baseTitle = useRef<string | null>(null)
  useEffect(() => {
    baseTitle.current ??= document.title
    document.title = rest ? `${clock(restLeft(rest.endsAt, now))} · ${t('live_rest')}` : overTitle ? t('live_web_rest_over') : baseTitle.current
  }, [rest, now, overTitle])
  useEffect(() => () => { if (baseTitle.current != null) document.title = baseTitle.current }, [])

  // Screen on while open, and audio unlocked by the first tap (iOS).
  useEffect(() => {
    const release = keepScreenOn()
    const unlock = () => unlockAudio()
    document.addEventListener('pointerdown', unlock, { once: true })
    return () => { release(); document.removeEventListener('pointerdown', unlock) }
  }, [])

  // Bring back a rest or hold that was running before a reload.
  useEffect(() => {
    if (!session?.id || restored.current === session.id) return
    restored.current = session.id
    const saved = loadTimers(store(), session.id, Date.now())
    if (saved.rest) setRest(saved.rest)
    if (saved.hold) setHold(saved.hold)
  }, [session?.id])

  const persist = (r: RestTimer | null, h: HoldTimer | null) => { if (session?.id) saveTimers(store(), { sessionId: session.id, rest: r, hold: h }) }
  const setRestSaved = (r: RestTimer | null) => { setRest(r); restRef.current = r; persist(r, holdRef.current) }
  const setHoldSaved = (h: HoldTimer | null) => { setHold(h); holdRef.current = h; persist(restRef.current, h) }

  // Clock: wall time only. Runs on an interval and again the moment the tab comes back.
  useEffect(() => {
    const tick = () => {
      const n = Date.now()
      const r = restRef.current
      const h = holdRef.current
      if (r && restDone(r, n)) {
        restRef.current = null; setRest(null)
        if (session?.id) saveTimers(store(), { sessionId: session.id, rest: null, hold: holdRef.current })
        if (n - r.endsAt < 15000) {
          alarm(hapticsRef.current)
          setFlash(true); window.setTimeout(() => setFlash(false), 700)
          if (document.visibilityState === 'hidden') setOverTitle(true)
        }
      }
      if (h) {
        const s = holdElapsed(h, n)
        if (s !== lastCue.current) {
          lastCue.current = s
          const left = h.target - s
          if (s === h.target) alarm(hapticsRef.current)
          else if (s < 0 || (left >= 1 && left <= 3) || (h.target >= 10 && s === Math.floor(h.target / 2))) { beep(660, 0.1); if (hapticsRef.current) buzz(30) }
        }
      }
      if (Math.floor(n / 1000) !== Math.floor(nowRef.current / 1000) || h) { nowRef.current = n; setNow(n) }
    }
    const id = window.setInterval(tick, 250)
    const onVisible = () => { if (document.visibilityState === 'visible') { setOverTitle(false); tick() } }
    document.addEventListener('visibilitychange', onVisible)
    return () => { clearInterval(id); document.removeEventListener('visibilitychange', onVisible) }
  }, [session?.id])

  // What each exercise looked like last time, loaded once per exercise.
  useEffect(() => {
    const missing = [...new Set(sets.map((s) => s.exerciseId))].filter((id) => !previous.has(id))
    if (!missing.length || !session?.id) return
    let live = true
    void Promise.all(missing.map(async (id) => {
      const recent = (await recentDoneSets(id)).filter((s) => s.sessionId !== session.id)
      const first = recent[0]?.sessionId
      return [id, recent.filter((s) => s.sessionId === first).sort((a, b) => a.setIndex - b.setIndex)] as const
    })).then((rows) => { if (live) setPrevious((m) => new Map([...m, ...rows])) })
    return () => { live = false }
  }, [sets.map((s) => s.exerciseId).join('|'), session?.id])

  useEffect(() => {
    const missing = [...new Set(sets.map((s) => s.exerciseId))].filter((id) => !best.has(id))
    if (!missing.length || exercises.size === 0) return
    let live = true
    void Promise.all(missing.map(async (id) => {
      const kind = exercises.get(id)?.kind ?? 'REPS'
      const done = await recentDoneSetsAll(id)
      return [id, done.length ? Math.max(...done.map((s) => recordScore(kind, s.reps, s.seconds, s.weightKg, id))) : Infinity] as const
    })).then((rows) => { if (live) setBest((m) => new Map([...m, ...rows])) })
    return () => { live = false }
  }, [sets.map((s) => s.exerciseId).join('|'), exercises.size])
  const prIds = useMemo(() => new Set(sets.filter((s) => {
    const b = best.get(s.exerciseId)
    return b != null && Number.isFinite(b) && recordScore(exercises.get(s.exerciseId)?.kind ?? 'REPS', s.reps, s.seconds, s.weightKg, s.exerciseId) > b
  }).map((s) => s.id!)), [sets, best, exercises])

  // A bodyweight move that has become easy offers its harder version (or a slower tempo); checked once per exercise.
  const hintChecked = useRef(new Set<string>())
  useEffect(() => {
    if (!session?.id || exercises.size === 0) return
    const ids = [...new Set(sets.map((s) => s.exerciseId))].filter((id) => !hintChecked.current.has(id))
    ids.forEach((id) => hintChecked.current.add(id))
    void Promise.all(ids.map(async (id) => {
      const ex = exercises.get(id)
      const warmup = sets.some((s) => s.exerciseId === id && s.block.toLowerCase() === 'warm-up')
      return [id, ex ? await harderHint(ex, warmup, session.id!) : undefined] as const
    })).then((rows) => {
      const ready = rows.filter((r): r is readonly [string, Hint] => r[1] !== undefined)
      if (ready.length) setHarder((m) => new Map([...m, ...ready]))
    })
  }, [sets.map((s) => s.exerciseId).join('|'), session?.id, exercises.size])

  const groups = useMemo(() => groupSetsByExercise(sets), [sets])
  const doneCount = sets.filter((s) => s.done).length
  const current = currentGroupIndex(groups)
  const nextSet = nextUntickedSet(groups)
  const nameOf = (id: string) => exercises.get(id)?.name ?? id
  const nextLabel = nextSet ? `${nameOf(nextSet.exerciseId)} · ${setSummary(nextSet, exercises.get(nextSet.exerciseId)?.kind ?? 'REPS', unit)}` : null

  // Follow along: after a tick, bring the exercise of the next set into view; in focus mode go to its page.
  const lastDone = useRef(doneCount)
  useEffect(() => {
    if (doneCount > lastDone.current && current >= 0) {
      if (!focus) document.querySelector(`[data-gkey="${groups[current].key}"]`)?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    }
    lastDone.current = doneCount
  }, [doneCount])
  useEffect(() => { if (current >= 0) setPage(current) }, [current])

  if (summary) {
    return <WorkoutSummary sum={summary} onDone={() => { location.hash = '#/' }} onView={() => { location.hash = `#/session/${summary.sessionId}` }} />
  }
  if (!session) {
    if (finishing) return <div class="shell" />
    return (
      <div class="shell lw">
        <LiveHeader onClose={leave} />
        <div class="scroll"><EmptyState title={t('live_web_empty_title')} body={t('live_web_empty_body')}
          action={<Button onClick={() => { location.hash = '#/workouts' }}>{t('live_web_empty_cta')}</Button>} /></div>
      </div>
    )
  }
  const sid = session.id!

  const startRestFor = (set: SetRow) => {
    const ordered = [...setsRef.current].sort((a, b) => a.itemPosition - b.itemPosition || a.setIndex - b.setIndex)
    const next = ordered.find((s) => !s.done && s.id !== set.id)
    setRestSaved(startRest(set.restSeconds, Date.now(), next ? nameOf(next.exerciseId) : null))
  }
  const save = (next: SetRow) => { void updateSet(next).catch(() => toast(t('live_web_save_failed'))) }
  /** The warm-up's single tick: marks every set of that exercise done (or all undone), and starts no rest timer. */
  const toggleAll = (group: SetRow[]) => {
    unlockAudio()
    const done = !group.every((s) => s.done)
    setEffortSetId(null)
    if (done) haptics.check(); else haptics.tick()
    group.forEach((s) => { if (s.done !== done) save({ ...s, done, doneAt: done ? Date.now() : null }) })
  }
  const toggle = (set: SetRow) => {
    unlockAudio()
    const done = !set.done
    setEffortSetId(done ? (set.id ?? null) : null)
    if (!done) haptics.tick(); else if (prIds.has(set.id!)) haptics.pr(); else haptics.check()
    save({ ...set, done, doneAt: done ? Date.now() : null })
    if (done && set.restSeconds > 0) startRestFor(set)
    else if (!done) setRestSaved(null)
  }
  const beginHold = (set: SetRow) => {
    unlockAudio()
    setRestSaved(null)
    lastCue.current = Number.MIN_SAFE_INTEGER
    setHoldSaved(startHold(set.id!, nameOf(set.exerciseId), set.seconds, Date.now()))
  }
  const stopHold = () => {
    const h = holdRef.current
    if (!h) return
    setHoldSaved(null)
    const held = holdSaved(h, Date.now())
    const set = setsRef.current.find((s) => s.id === h.setId)
    if (!set || held === 0) return
    save({ ...set, seconds: held, done: true, doneAt: Date.now() })
    if (hapticsOn) buzz(40)
    if (set.restSeconds > 0) startRestFor(set)
  }
  const addRest = (delta: number) => {
    const r = restRef.current
    if (r) setRestSaved(adjustRest(r, delta))
  }
  const removeLast = (position: number) => {
    const mine = sets.filter((s) => s.itemPosition === position)
    if (mine.length <= 1) return
    void removeSet(mine.reduce((m, s) => (s.setIndex > m.setIndex ? s : m)).id!)
  }
  const doFinish = async (tickAll: boolean) => {
    if (finishing) return
    unlockAudio()
    setFinishing(true)
    setRestSaved(null); setHoldSaved(null)
    try {
      const startedAt = session.startedAt
      const before = (await getProgressSnapshot()).level
      const result = await finishSession(sid, tickAll)
      const badges = await evaluateBadges()
      // The summary shows them, so Home shouldn't announce them a second time.
      if (badges.length > 0) await markBadgesSeen()
      const snap = await getProgressSnapshot()
      if (hapticsOn) buzz([60, 40, 120])
      onWorkoutFinished(sid, result)
      setDone({
        sessionId: sid, title: result.sessionTitle, doneSets: result.doneSets, minutes: workoutMinutes(startedAt, result.endedAt),
        prs: result.prs, newBadges: badges, xpGained: snap.level.totalXp - before.totalXp, before, after: snap.level,
        nextUp: nextUpDay(snap.plan?.days ?? [], dayOfWeek(todayEpochDay())),
        score: (await allWorkoutScores()).get(sid) ?? null,
        clippedEndAt: result.clipped ? result.endedAt : null,
      })
    } catch {
      toast(t('live_web_save_failed'))
      setFinishing(false)
    }
  }
  const doDiscard = async () => {
    setConfirmExit(false); setFinishing(true)
    setRestSaved(null); setHoldSaved(null)
    try { await discardSession(sid); location.hash = '#/workouts' } catch { toast(t('live_web_save_failed')); setFinishing(false) }
  }
  const pick = async (ex: ExerciseRow) => {
    const mode = picker
    setPicker(null)
    if (mode?.kind === 'swap') await swapExercise(sid, mode.position, ex.id)
    else await addExercise(sid, ex.id)
  }
  const swapping = picker?.kind === 'swap' ? exercises.get(sets.find((s) => s.itemPosition === picker.position)?.exerciseId ?? '') : undefined

  const elapsed = Math.max(0, Math.floor((now - session.startedAt) / 1000))
  const header = plural('live_header', sets.length, Math.floor(elapsed / 60), String(elapsed % 60).padStart(2, '0'), doneCount, sets.length)
  const cardFor = (g: Group, forceOpen: boolean) => (
    <GroupCard key={g.key} g={g} forceOpen={forceOpen} exercises={exercises} previous={previous} prIds={prIds} unit={unit} effortSetId={effortSetId}
      onChange={save} onToggle={toggle} onToggleAll={toggleAll} onStartHold={beginHold} harder={harder}
      onStepUp={(oldId, next) => {
        setHarder((m) => { const n = new Map(m); n.delete(oldId); return n })
        void stepUp(sid, oldId, next.id).then(() => toast(t('harder_switched', next.name)))
      }}
      onMenu={(position, swapOnly) => setMenu({ position, swapOnly })}
      onAddRound={(positions) => { void (async () => { for (const pos of positions) await addSet(sid, pos) })() }} />
  )
  const shownPage = Math.min(page, Math.max(0, groups.length - 1))

  return (
    <div class="shell lw">
      <div class="lw-progress" role="progressbar" aria-label={t('live_web_progress')} aria-valuemin={0} aria-valuemax={sets.length} aria-valuenow={doneCount}>
        <div style={{ inlineSize: `${sets.length ? (doneCount / sets.length) * 100 : 0}%` }} />
      </div>
      <LiveHeader onClose={leave}
        title={session.title} subtitle={header} now={nextSet ? t('live_now', nameOf(nextSet.exerciseId)) : null}>
        <IconButton label={t(focus ? 'cd_list_mode' : 'cd_focus_mode')} onClick={() => {
          const next = !focus; setFocus(next)
          try { localStorage.setItem(FOCUS_KEY, next ? '1' : '0') } catch { /* not remembered */ }
        }}>{focus ? <ListIcon /> : <FocusIcon />}</IconButton>
        <Button variant="text" class="lw-discard" onClick={() => setConfirmExit(true)}>{t('live_discard')}</Button>
      </LiveHeader>

      <div class="scroll lw-body">
        {focus && groups.length > 0 ? (
          <div class="lw-focus">
            <div class="lw-pager">
              <IconButton label={t('live_web_prev')} disabled={shownPage === 0} onClick={() => setPage(shownPage - 1)}><span class="lw-chev lw-chev-prev" /></IconButton>
              <span class="lw-small">{t('live_exercise_of', shownPage + 1, groups.length)}</span>
              <IconButton label={t('live_web_next')} disabled={shownPage >= groups.length - 1} onClick={() => setPage(shownPage + 1)}><span class="lw-chev lw-chev-next" /></IconButton>
            </div>
            {cardFor(groups[shownPage], true)}
          </div>
        ) : (
          <div class="lw-list">
            {groups.map((g) => cardFor(g, false))}
            <Button variant="tonal" block icon={<AddIcon />} onClick={() => setPicker({ kind: 'add' })}>{t('live_add_exercise')}</Button>
          </div>
        )}
      </div>

      <div class="lw-dock">
        {flash && !rest && <div class="lw-rest-flash" role="status">{t('live_web_rest_over')}</div>}
        {rest && <RestBar rest={rest} now={now} nextLabel={nextLabel} onAdd={() => addRest(15)}
          onRemove={() => { if (restLeftNow(rest) > 15) addRest(-15); else setRestSaved(null) }} onSkip={() => setRestSaved(null)} />}
        <Button block disabled={doneCount === 0 || finishing} loading={finishing}
          onClick={() => { unlockAudio(); if (sets.some((s) => !s.done)) setConfirmFinish(true); else void doFinish(false) }}>
          {finishing ? t('live_saving') : t('live_finish_workout')}
        </Button>
      </div>

      {hold && <HoldOverlay hold={hold} now={now} onStop={stopHold} onCancel={() => setHoldSaved(null)} />}

      <FinishSheet open={confirmFinish} left={sets.filter((s) => !s.done).length} onClose={() => setConfirmFinish(false)}
        onFinish={(all) => { setConfirmFinish(false); void doFinish(all) }} />
      <Dialog open={confirmExit} destructive title={t('live_discard_title')} message={t('live_discard_body')}
        confirmLabel={t('live_discard')} cancelLabel={t('live_keep_going')} onConfirm={() => void doDiscard()} onCancel={() => setConfirmExit(false)} />

      <Sheet open={menu != null} onClose={() => setMenu(null)} title={menu ? nameOf(sets.find((s) => s.itemPosition === menu.position)?.exerciseId ?? '') : ''}>
        {menu && (
          <div class="lw-menu">
            <ListRow title={t('cd_watch_form')} chevron={false} onClick={() => { openFormVideo(exercises.get(sets.find((s) => s.itemPosition === menu.position)?.exerciseId ?? '')); setMenu(null) }} />
            <ListRow title={t('live_swap_exercise')} chevron={false} onClick={() => { setPicker({ kind: 'swap', position: menu.position }); setMenu(null) }} />
            {!menu.swapOnly && <ListRow title={t('live_add_set')} chevron={false} onClick={() => { void addSet(sid, menu.position); setMenu(null) }} />}
            {!menu.swapOnly && <ListRow title={t('live_remove_last_set')} chevron={false} onClick={() => { removeLast(menu.position); setMenu(null) }} />}
          </div>
        )}
      </Sheet>

      <ExercisePicker open={picker != null} title={t(picker?.kind === 'swap' ? 'live_swap_exercise' : 'live_add_exercise')} library={library}
        onPick={(ex) => void pick(ex)}
        onCreate={(name, kind, muscle) => { void addCustomExercise(name, kind, [muscle], null).then(async (id) => {
          const mode = picker; setPicker(null)
          if (mode?.kind === 'swap') await swapExercise(sid, mode.position, id); else await addExercise(sid, id)
        }) }}
        onClose={() => setPicker(null)} initialMuscle={swapping?.primary?.[0]} sourceExercise={swapping} userEquipment={equipment} />
    </div>
  )
}

const restLeftNow = (r: RestTimer) => Math.max(0, Math.ceil((r.endsAt - Date.now()) / 1000))


function LiveHeader(p: { onClose: () => void; title?: string; subtitle?: string; now?: string | null; children?: preact.ComponentChildren }) {
  return (
    <header class="lw-head">
      <button type="button" class="icon-button" aria-label={t('cd_minimize')} onClick={p.onClose}><CloseIcon /></button>
      <div class="lw-head-text">
        <h1 class="lw-title">{p.title ?? t('tab_workouts')}</h1>
        {p.subtitle && <div class="lw-small">{p.subtitle}</div>}
        {p.now && <div class="lw-now">{p.now}</div>}
      </div>
      {p.children}
    </header>
  )
}

/** Choice when some sets are still unticked: keep going, finish (unticked are dropped), or tick them all and finish. */
function FinishSheet({ open, left, onClose, onFinish }: { open: boolean; left: number; onClose: () => void; onFinish: (tickAll: boolean) => void }) {
  return (
    <Sheet open={open} onClose={onClose} title={plural('live_finish_unticked_title', left, left)}>
      <p class="lw-finish-msg">{plural('live_finish_unticked_body', left, left)}</p>
      <div class="lw-finish-actions">
        <Button block onClick={() => onFinish(false)}>{t('live_finish')}</Button>
        <Button block variant="text" onClick={() => onFinish(true)}>{t('live_tick_all_finish')}</Button>
        <Button block variant="text" onClick={onClose}>{t('live_keep_going')}</Button>
      </div>
    </Sheet>
  )
}

/** "12 reps · 10 kg" or "30 s", for the next-up line. */
function setSummary(set: SetRow, kind: ExerciseRow['kind'], unit: WeightUnit): string {
  const main = kind === 'HOLD' ? t('rx_seconds', set.seconds ?? 0) : t('rx_reps_n', set.reps ?? 0)
  const kg = set.weightKg
  return kg != null && (kg > 0 || kind === 'WEIGHTED') ? `${main} · ${weightNumber(unit, kg)} ${unitLabel(unit, getLang() === 'ar')}` : main
}

type CardProps = {
  g: Group; forceOpen: boolean; exercises: Map<string, ExerciseRow>; previous: Map<string, SetRow[]>; prIds: Set<number>; unit: WeightUnit; effortSetId: number | null
  onChange: (s: SetRow) => void; onToggle: (s: SetRow) => void; onToggleAll: (sets: SetRow[]) => void; onStartHold: (s: SetRow) => void; onMenu: (position: number, swapOnly: boolean) => void; onAddRound: (positions: number[]) => void
  harder: Map<string, Hint>; onStepUp: (oldId: string, next: ExerciseRow) => void
}

type Hint = { next: ExerciseRow | null }

const prIds = (p: CardProps, set: SetRow) => set.id != null && p.prIds.has(set.id)

/** "Wide knee push-up is getting easy": one tap switches to the harder version, or a tempo tip when there is none. */
function HarderNote({ ex, hint, onStepUp }: { ex: ExerciseRow; hint: Hint | undefined; onStepUp: (oldId: string, next: ExerciseRow) => void }) {
  if (!hint) return null
  const next = hint.next
  return (
    <div class="lw-harder">
      <span class="lw-harder-text">{next ? t('harder_ready', ex.name, next.name) : t('harder_tempo', ex.name)}</span>
      {next && <Button variant="text" onClick={() => onStepUp(ex.id, next)}>{t('harder_switch')}</Button>}
    </div>
  )
}

/** One warm-up exercise as a single row ("Jumping jacks · 5 × 10") with one tick; tapping the row opens the per-set rows. */
function WarmupRow(p: { g: Extract<Group, { kind: 'straight' }>; ex: ExerciseRow | undefined; name: string; rowProps: (set: SetRow, round: number | null) => Omit<SetRowProps, 'label' | 'sublabel'>; onToggleAll: (sets: SetRow[]) => void }) {
  const [expanded, setExpanded] = useState(false)
  const { g } = p
  const first = g.sets[0]
  const doneCount = g.sets.filter((s) => s.done).length
  const allDone = doneCount === g.sets.length
  const each = p.ex?.kind === 'HOLD' ? t('rx_seconds', first.seconds ?? 0) : String(first.reps ?? 0)
  return (
    <div data-gkey={g.key}>
      <Card>
        <div class="lw-warm">
          <button type="button" class="lw-warm-main" aria-expanded={expanded} onClick={() => setExpanded(!expanded)}>
            <span class="lw-warm-text">
              <span class="lw-name">{`${p.name} · ${g.sets.length} × ${each}`}</span>
              {doneCount > 0 && !allDone && <span class="lw-small">{`${doneCount}/${g.sets.length}`}</span>}
            </span>
            <ExpandMoreIcon size={20} />
          </button>
          <SetTick checked={allDone} onToggle={() => p.onToggleAll(g.sets)} />
        </div>
        {expanded && g.sets.map((set) => <SetRowView key={set.id} label={String(set.setIndex + 1)} sublabel={null} {...p.rowProps(set, null)} />)}
      </Card>
    </div>
  )
}

/** An exercise or circuit card; once every set is ticked it folds into one line (tap to open it again). */
function GroupCard(p: CardProps) {
  const { g, exercises } = p
  const all = groupSets(g)
  const allDone = all.length > 0 && all.every((s) => s.done)
  const [open, setOpen] = useState(!allDone)
  useEffect(() => setOpen(!allDone), [allDone])
  const first = all[0]
  const name = (id: string) => exercises.get(id)?.name ?? id
  const rowProps = (set: SetRow, round: number | null) => ({
    set, unit: p.unit, kind: exercises.get(set.exerciseId)?.kind ?? 'REPS', previous: p.previous.get(set.exerciseId)?.[round ?? set.setIndex],
    pr: prIds(p, set), showEffort: p.effortSetId === set.id, onChange: p.onChange, onToggle: p.onToggle, onStartHold: p.onStartHold,
  })

  if (g.kind === 'straight' && isWarmUp(g.block)) {
    return <WarmupRow g={g} ex={exercises.get(first.exerciseId)} name={name(first.exerciseId)} rowProps={rowProps} onToggleAll={p.onToggleAll} />
  }

  if (!open && !p.forceOpen) {
    return (
      <div data-gkey={g.key}>
        <Card onClick={() => setOpen(true)}>
          <div class="lw-folded">
            <span class="lw-folded-check"><CheckIcon size={20} /></span>
            <span class="lw-folded-title">{g.kind === 'straight' ? name(first.exerciseId) : blockLabel(g.block)}</span>
            <span class="lw-small">{plural('live_folded', all.length, all.length)}</span>
            <ExpandMoreIcon size={20} />
          </div>
        </Card>
      </div>
    )
  }
  if (g.kind === 'straight') {
    const target = first.targetLabel
    const ex = exercises.get(first.exerciseId)
    return (
      <div data-gkey={g.key}>
        <Card>
          <div class="lw-cardhead">
            <a class="lw-cardtitle" href={`#/exercise/${first.exerciseId}`}>
              <span class="lw-name">{name(first.exerciseId)}</span>
              <span class="lw-small">{target ? t('live_target', g.sets.length, target) : ex ? [...ex.primary].map((m) => t('muscle_' + m.toLowerCase())).join(' · ') : ''}</span>
            </a>
            <IconButton label={t('cd_options')} onClick={() => p.onMenu(g.position, false)}><MoreVert /></IconButton>
          </div>
          {ex && <HarderNote ex={ex} hint={p.harder.get(ex.id)} onStepUp={p.onStepUp} />}
          {g.sets.map((set) => <SetRowView key={set.id} label={String(set.setIndex + 1)} sublabel={null} {...rowProps(set, null)} />)}
        </Card>
      </div>
    )
  }
  return (
    <div data-gkey={g.key}>
      <Card>
        <div class="lw-cardhead">
          <span class="lw-name">{blockLabel(g.block)}</span>
          <span class="lw-circuit"><RepeatIcon size={16} /> {t('live_circuit')}</span>
        </div>
        <div class="lw-small">{t('live_circuit_hint')}</div>
        {[...new Set((g.rounds[0] ?? []).map((s) => s.exerciseId))].map((id) => exercises.get(id)).filter((e) => e != null)
          .map((e) => <HarderNote key={e.id} ex={e} hint={p.harder.get(e.id)} onStepUp={p.onStepUp} />)}
        {g.rounds.map((round, r) => {
          const done = round.every((s) => s.done)
          return (
            <div key={r}>
              <div class={`lw-round${done ? ' lw-round-done' : ''}`}>{t('live_round', r + 1, g.rounds.length) + (done ? t('live_round_done') : '')}</div>
              {round.map((set) => (
                <SetRowView key={set.id} label={null} sublabel={name(set.exerciseId)} onLabelClick={() => p.onMenu(set.itemPosition, true)} {...rowProps(set, r)} />
              ))}
            </div>
          )
        })}
        <Button variant="text" icon={<AddIcon />} onClick={() => p.onAddRound(g.positions)}>{t('live_add_round')}</Button>
      </Card>
    </div>
  )
}
