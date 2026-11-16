import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { getLang, plural, t, useLang } from '../../i18n'
import type { ExerciseRow, SetRow } from '../../data/db'
import { configuredEquipment, updateSettings, useSettings, weightUnit } from '../../data/settings'
import {
  addCustomExercise, addExercise, addSet, allWorkoutScores, discardSession, finishSession, harderHint,
  progressionReasonFor, recentDoneSets, recentDoneSetsAll, removeSet, stepUp, swapExercise, trainingDays,
  updateSet, useActivePlan, useLibrary, useOpenSession, useSets, workoutDebrief, type FinishResult,
} from '../../data/workouts'
import { exerciseCueKeys } from '../../data/seed/exercise-cues'
import { formatWeight, weightNumber, unitLabel, type WeightUnit } from '../../domain/units'
import { isDeloadWeek, type ProgressionReason } from '../../domain/progression'
import { isExtraBlock, pickPrs } from '../../domain/pr-picker'
import {
  appendHistoryEntries, appendHistoryOne, dayType, headlineKey,
  pickHeadlineVariant, scoreTier,
} from '../../domain/finish-summary'
import { muscleWeekTargets, pickInsights } from '../../domain/insights'
import type { PlanItemEntity, SessionEntity, SetEntity } from '../../domain/entities'
import { Button, Card, Dialog, EmptyState, IconButton, Sheet, ListRow, haptics, toast } from '../kit'
import { recordScore, welcomeBack as isWelcomeBack } from '../../domain/progress'
import { AddIcon, CheckIcon, CloseIcon, ExpandMoreIcon } from '../icons'
import { ExercisePicker } from '../workouts/ExercisePicker'
import { autoBackup } from '../../data/drive'
import { syncFriendsQuietly } from '../../data/friends'
import { blockLabel, openFormVideo } from '../workouts/helpers'
import { alarm, beep, buzz, keepScreenOn, unlockAudio } from './feedback'
import { FocusIcon, ListIcon, MoreVert, RepeatIcon } from './glyphs'
import { carryForwardUpdates, currentGroupIndex, groupSets, groupSetsByExercise, nextUntickedSet, type Group } from './groups'
import { HoldOverlay, RestBar } from './Overlays'
import { SetRowView, SetTick, type EditedField, type SetRowProps } from './SetRowView'
import {
  adjustRest, clock, holdElapsed, restLeft, holdSaved, loadTimers, saveTimers, startHold, startRest, restDone,
  type HoldTimer, type RestTimer,
} from './timers'
import { evaluateBadges, getProgressSnapshot, markBadgesSeen } from '../../data/progress'
import { dayOfWeek, todayEpochDay } from '../../domain/dates'
import { nextUpDay, workoutMinutes, type PrHeroData, type WorkoutSummaryData } from './summary'
import { WorkoutSummary } from './WorkoutSummary'
import './live.css'

/**
 * After finishSession: the backup and quiet Friends sync run in the background. The debrief on the summary is calculated when the card opens.
 * The per-set effort chips (Easy, OK, Hard, Pain) are ported in SetRowView.
 */
export function onWorkoutFinished(_sessionId: number, _result: FinishResult): void {
  void autoBackup('workout')
  void syncFriendsQuietly()
}

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
  const [reasons, setReasons] = useState<Map<string, ProgressionReason>>(new Map())
  const [newBestId, setNewBestId] = useState<number | null>(null)
  const [showWelcomeBack, setShowWelcomeBack] = useState(false)
  const activePlan = useActivePlan()
  const deloadWeek = !!activePlan && session?.planDayId != null && isDeloadWeek(activePlan.plan.createdAt, todayEpochDay())
  const editedRepsRef = useRef(new Set<number>())
  const editedWeightRef = useRef(new Set<number>())
  const prBuzzedRef = useRef(new Set<string>())
  const celebratedRef = useRef(new Set<number>())
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
  const prIds = useMemo(
    () => pickPrs(sets, (id) => exercises.get(id)?.kind ?? 'REPS', best),
    [sets, best, exercises],
  )

  useEffect(() => {
    if (!session?.id) return
    let live = true
    void getProgressSnapshot().then((snap) => {
      if (!live) return
      const days = new Set(snap.sessions.map((x) => x.epochDay))
      const today = todayEpochDay()
      const minDay = days.size ? Math.min(...days) : today
      const planned = snap.plan ? trainingDays(snap.plan) : new Set<number>()
      setShowWelcomeBack(isWelcomeBack(today, days, snap.dayTags, planned, minDay))
    })
    return () => { live = false }
  }, [session?.id])

  // A bodyweight move that has become easy offers its harder version (or a slower tempo); checked once per exercise,
  // along with progression "why" reasons.
  const hintChecked = useRef(new Set<string>())
  useEffect(() => {
    if (!session?.id || exercises.size === 0) return
    const ids = [...new Set(sets.map((s) => s.exerciseId))].filter((id) => !hintChecked.current.has(id))
    ids.forEach((id) => hintChecked.current.add(id))
    void Promise.all(ids.map(async (id) => {
      const ex = exercises.get(id)
      const warmup = sets.some((s) => s.exerciseId === id && isExtraBlock(s.block))
      const hint = ex ? await harderHint(ex, warmup, session.id!) : undefined
      const first = sets.find((s) => s.exerciseId === id)
      const planDay = activePlan?.days.find((d) => d.day.id === session.planDayId)
      const planItem = first ? planDay?.items.find((i) => i.position === first.itemPosition && i.exerciseId === id) ?? null : null
      const recent = (await recentDoneSets(id)).filter((s) => s.sessionId !== session.id)
      const reason = await progressionReasonFor(planItem, ex, recent, deloadWeek)
      return [id, hint, reason] as const
    })).then((rows) => {
      const ready = rows.filter((r): r is readonly [string, Hint, ProgressionReason | null] => r[1] !== undefined)
      if (ready.length) setHarder((m) => new Map([...m, ...ready.map(([id, h]) => [id, h] as const)]))
      const foundReasons = rows.filter((r): r is readonly [string, Hint | undefined, ProgressionReason] => r[2] != null)
      if (foundReasons.length) setReasons((m) => new Map([...m, ...foundReasons.map(([id, , r]) => [id, r] as const)]))
    })
  }, [sets.map((s) => s.exerciseId).join('|'), session?.id, exercises.size])

  const groups = useMemo(() => groupSetsByExercise(sets), [sets])
  const doneCount = sets.filter((s) => s.done).length
  const workSets = useMemo(() => sets.filter((s) => !isExtraBlock(s.block)), [sets])
  const workDone = workSets.filter((s) => s.done).length
  const trackedSets = workSets.length > 0 ? workSets : sets
  const trackedDone = trackedSets.filter((s) => s.done).length
  const trackedLeft = trackedSets.length - trackedDone
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

  const startRestFor = (set: SetRow, doneAtMs: number) => {
    const withTick = setsRef.current.map((s) => (s.id === set.id ? { ...set, done: true, doneAt: doneAtMs } : s))
    const next = nextUntickedSet(groupSetsByExercise(withTick))
    setRestSaved(startRest(set.restSeconds, Date.now(), next ? nameOf(next.exerciseId) : null))
  }
  const announcePr = (set: SetRow) => {
    const ex = exercises.get(set.exerciseId)
    if (!ex) return
    const what = ex.kind === 'WEIGHTED'
      ? `${set.reps ?? 0} × ${formatWeight(unit, set.weightKg ?? 0, getLang() === 'ar')}`
      : ex.kind === 'HOLD'
        ? plural('debrief_n_seconds', set.seconds ?? 0, set.seconds ?? 0)
        : plural('debrief_n_reps', set.reps ?? 0, set.reps ?? 0)
    toast(t('live_pr_new', ex.name, what))
  }
  const checkBest = (updated: SetRow) => {
    if (updated.id == null) return
    const withEdit = setsRef.current.map((s) => (s.id === updated.id ? { ...updated, done: true } : s))
    const picked = pickPrs(withEdit, (id) => exercises.get(id)?.kind ?? 'REPS', best)
    if (picked.has(updated.id) && !celebratedRef.current.has(updated.id)) {
      celebratedRef.current.add(updated.id)
      if (!prBuzzedRef.current.has(updated.exerciseId)) {
        prBuzzedRef.current.add(updated.exerciseId)
        haptics.pr()
        announcePr(updated)
      }
      setNewBestId(updated.id)
      window.setTimeout(() => setNewBestId((cur) => (cur === updated.id ? null : cur)), 1400)
    }
  }
  const save = (next: SetRow, editedField?: EditedField) => {
    if (editedField && next.id != null) {
      if (editedField === 'reps') editedRepsRef.current.add(next.id)
      if (editedField === 'weightKg') editedWeightRef.current.add(next.id)
      if (next.done) checkBest(next)
    }
    void updateSet(next).catch(() => toast(t('live_web_save_failed')))
  }
  /** The warm-up's single tick: marks every set of that exercise done (or all undone), and starts no rest timer. */
  const toggleAll = (group: SetRow[]) => {
    unlockAudio()
    const done = !group.every((s) => s.done)
    setEffortSetId(null)
    if (done) haptics.check(); else haptics.tick()
    const doneAt = done ? Date.now() : null
    group.forEach((s) => { if (s.done !== done) save({ ...s, done, doneAt }) })
  }
  const carryForward = (set: SetRow) => {
    for (const updated of carryForwardUpdates(setsRef.current, set, editedRepsRef.current, editedWeightRef.current)) {
      void updateSet(updated).catch(() => {})
    }
  }
  const toggle = (set: SetRow) => {
    unlockAudio()
    const done = !set.done
    const doneAt = done ? Date.now() : null
    setEffortSetId(done ? (set.id ?? null) : null)
    const withTick = setsRef.current.map((s) => (s.id === set.id ? { ...set, done, doneAt } : s))
    const picked = pickPrs(withTick, (id) => exercises.get(id)?.kind ?? 'REPS', best)
    const freshPr = done && set.id != null && picked.has(set.id) && !prBuzzedRef.current.has(set.exerciseId)
    if (!done) haptics.tick()
    else if (freshPr) {
      prBuzzedRef.current.add(set.exerciseId)
      celebratedRef.current.add(set.id!)
      haptics.pr()
      announcePr(set)
      setNewBestId(set.id!)
      window.setTimeout(() => setNewBestId((cur) => (cur === set.id ? null : cur)), 1400)
    } else haptics.check()
    save({ ...set, done, doneAt })
    if (done) carryForward(set)
    if (done && set.restSeconds > 0 && doneAt != null) startRestFor(set, doneAt)
    else if (!done) setRestSaved(null)
  }
  const easeIn = async () => {
    setShowWelcomeBack(false)
    for (const g of groups) {
      const list = groupSets(g)
      if (list.length > 2 && list.every((s) => !s.done)) {
        const last = list.reduce((m, s) => (s.setIndex > m.setIndex ? s : m))
        if (last.id != null) await removeSet(last.id)
      }
    }
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
    const doneAt = Date.now()
    const updated = { ...set, seconds: held, done: true, doneAt }
    save(updated)
    checkBest(updated)
    if (hapticsOn) buzz(40)
    if (set.restSeconds > 0) startRestFor(set, doneAt)
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
      const snap = await getProgressSnapshot()
      const justLog = snap.settings.justLog
      // The summary shows them, so Home shouldn't announce them a second time. "Just log" mode doesn't show them,
      // so they stay new for when it's turned off.
      if (badges.length > 0 && !justLog) await markBadgesSeen()
      if (hapticsOn) buzz([60, 40, 120])
      onWorkoutFinished(sid, result)
      const score = (await allWorkoutScores()).get(sid) ?? null
      const debrief = await workoutDebrief(sid)
      const day = todayEpochDay()
      const history = snap.settings.insightHistory ?? ''
      let insights = justLog || !score || !debrief ? [] : pickInsights({
        sessions: snap.sessions.filter((x): x is SessionEntity => x.id != null && x.finishedAt != null),
        sets: snap.doneSets.filter((x): x is SetEntity => x.id != null),
        exercises: snap.exercises,
        streak: snap.streak,
        muscleWeekTargets: muscleWeekTargets((snap.plan?.days.flatMap((d) => d.items) ?? []).filter((i): i is PlanItemEntity => i.id != null), snap.exercises),
        focusSessionId: sid,
      }, history, day)
      const dType = score && debrief ? dayType(score, debrief, insights) : 'ORDINARY'
      if (dType === 'ROUGH') insights = []
      const hVar = debrief ? pickHeadlineVariant(debrief.headline.type, history) : null
      if (!justLog) {
        let updated = appendHistoryEntries(history, insights, day)
        if (debrief && hVar != null && dType !== 'ROUGH') {
          updated = appendHistoryOne(updated, headlineKey(debrief.headline.type), hVar, day)
        }
        if (updated !== history) await updateSettings((cur) => ({ ...cur, insightHistory: updated }))
      }
      const prWin = debrief?.wins.find((w) => w.type === 'PR')
      let prHero: PrHeroData | null = null
      if (prWin && prWin.type === 'PR') {
        const kind = snap.exercises.get(prWin.exerciseId)?.kind ?? 'REPS'
        let oldSet: SetRow | null = null
        let oldBest = -Infinity
        for (const x of snap.doneSets) {
          if (x.exerciseId !== prWin.exerciseId || x.sessionId === sid || isExtraBlock(x.block)) continue
          const sc = recordScore(kind, x.reps, x.seconds, x.weightKg, prWin.exerciseId)
          if (sc > oldBest) { oldBest = sc; oldSet = x as SetRow }
        }
        prHero = {
          exerciseId: prWin.exerciseId,
          kind: prWin.kind === 'WEIGHT' ? 'WEIGHTED' : prWin.kind,
          weightKg: prWin.weightKg,
          reps: prWin.reps,
          seconds: prWin.seconds,
          oldKg: oldSet?.weightKg != null && oldSet.weightKg > 0 ? oldSet.weightKg : null,
          oldReps: oldSet?.reps ?? null,
          oldSeconds: oldSet?.seconds ?? null,
        }
      }
      const exerciseNames = new Map([...snap.exercises.entries()].map(([k, v]) => [k, v.name]))
      setDone({
        sessionId: sid, title: result.sessionTitle, doneSets: result.doneSets, minutes: workoutMinutes(startedAt, result.endedAt),
        prs: result.prs, newBadges: badges, xpGained: snap.level.totalXp - before.totalXp, before, after: snap.level,
        nextUp: nextUpDay(snap.plan?.days ?? [], dayOfWeek(todayEpochDay())),
        score,
        clippedEndAt: result.clipped ? result.endedAt : null,
        justLog,
        debrief,
        exerciseNames,
        unit: weightUnit(snap.settings),
        dayType: dType,
        tier: score ? scoreTier(score) : null,
        headlineVariant: hVar ?? debrief?.headline.variant ?? null,
        insights,
        prHero,
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
  const header = plural('live_header', workSets.length, Math.floor(elapsed / 60), String(elapsed % 60).padStart(2, '0'), workDone, workSets.length)
  const shownPage = Math.min(page, Math.max(0, groups.length - 1))
  const focusExId = focus && groups[shownPage] ? groupSets(groups[shownPage])[0]?.exerciseId : null
  const nowExerciseId = focusExId ?? nextSet?.exerciseId ?? null
  const cardFor = (g: Group, forceOpen: boolean) => (
    <GroupCard key={g.key} g={g} forceOpen={forceOpen} exercises={exercises} previous={previous} prIds={prIds} newBestId={newBestId} reasons={reasons} unit={unit} effortSetId={effortSetId}
      onChange={save} onToggle={toggle} onToggleAll={toggleAll} onStartHold={beginHold} onDismissEffort={() => setEffortSetId(null)} harder={harder}
      onStepUp={(oldId, next) => {
        setHarder((m) => { const n = new Map(m); n.delete(oldId); return n })
        void stepUp(sid, oldId, next.id).then(() => toast(t('harder_switched', next.name)))
      }}
      onMenu={(position, swapOnly) => setMenu({ position, swapOnly })}
      onSwap={(position) => setPicker({ kind: 'swap', position })}
      onAddRound={(positions) => { void (async () => { for (const pos of positions) await addSet(sid, pos) })() }} />
  )

  return (
    <div class="shell lw">
      <div class="lw-progress" role="progressbar" aria-label={t('live_web_progress')} aria-valuemin={0} aria-valuemax={sets.length} aria-valuenow={doneCount}>
        <div style={{ inlineSize: `${sets.length ? (doneCount / sets.length) * 100 : 0}%` }} />
      </div>
      <LiveHeader onClose={leave}
        title={session.title} subtitle={header} now={nowExerciseId ? t('live_now', nameOf(nowExerciseId)) : null}>
        <IconButton label={t(focus ? 'cd_list_mode' : 'cd_focus_mode')} onClick={() => {
          const next = !focus; setFocus(next)
          try { localStorage.setItem(FOCUS_KEY, next ? '1' : '0') } catch { /* not remembered */ }
        }}>{focus ? <ListIcon /> : <FocusIcon />}</IconButton>
        <Button variant="text" class="lw-discard" onClick={() => setConfirmExit(true)}>{t('live_discard')}</Button>
      </LiveHeader>

      <div class="scroll lw-body">
        {deloadWeek && session.planDayId != null && <p class="lw-deload">{t('deload_week_note')}</p>}
        {showWelcomeBack && doneCount === 0 && (
          <Card class="lw-welcome">
            <div class="lw-name">{t('welcome_back_title')}</div>
            <div class="lw-small">{t('welcome_back_body')}</div>
            <div class="lw-welcome-actions">
              <Button variant="tonal" onClick={() => void easeIn()}>{t('welcome_back_ease_in')}</Button>
              <Button variant="text" onClick={() => setShowWelcomeBack(false)}>{t('welcome_back_full')}</Button>
            </div>
          </Card>
        )}
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
          onRemove={() => { if (restLeftNow(rest) > 15) addRest(-15); else setRestSaved(null) }} onSkip={() => { setRestSaved(null); setEffortSetId(null) }} />}
        {doneCount === 0 && <div class="lw-small lw-center">{t('live_finish_hint')}</div>}
        <Button block disabled={doneCount === 0 || finishing} loading={finishing}
          onClick={() => { unlockAudio(); if (trackedLeft > 0) setConfirmFinish(true); else void doFinish(false) }}>
          {finishing ? t('live_saving') : t('live_finish_workout')}
        </Button>
      </div>

      {hold && <HoldOverlay hold={hold} now={now} onStop={stopHold} onCancel={() => setHoldSaved(null)} />}

      <FinishSheet open={confirmFinish} left={trackedLeft} canTickAll={trackedDone * 2 >= trackedSets.length} onClose={() => setConfirmFinish(false)}
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
function FinishSheet({ open, left, canTickAll, onClose, onFinish }: { open: boolean; left: number; canTickAll: boolean; onClose: () => void; onFinish: (tickAll: boolean) => void }) {
  return (
    <Sheet open={open} onClose={onClose} title={plural('live_finish_unticked_title', left, left)}>
      <p class="lw-finish-msg">{plural('live_finish_unticked_body', left, left)}</p>
      <div class="lw-finish-actions">
        <Button block onClick={() => onFinish(false)}>{t('live_finish')}</Button>
        {canTickAll && <Button block variant="text" onClick={() => onFinish(true)}>{t('live_tick_all_finish')}</Button>}
        <Button block variant="text" onClick={onClose}>{t('live_keep_going')}</Button>
      </div>
    </Sheet>
  )
}

/** "12 reps · 10 kg" or "30 s", for the next-up line. */
function setSummary(set: SetRow, kind: ExerciseRow['kind'], unit: WeightUnit): string {
  const main = kind === 'HOLD' ? t('rx_seconds', set.seconds ?? 0) : plural('rx_reps_n', set.reps ?? 0, set.reps ?? 0)
  const kg = set.weightKg
  return kg != null && (kg > 0 || kind === 'WEIGHTED') ? `${main} · ${weightNumber(unit, kg)} ${unitLabel(unit, getLang() === 'ar')}` : main
}

/** Localizes stored English target labels ("8-12 reps", "10 reps", "30 s") for display. */
function formatTargetLabel(label: string): string {
  const rangeMatch = /^(\d+)-(\d+)\s+reps$/i.exec(label.trim())
  if (rangeMatch) return `${rangeMatch[1]}–${rangeMatch[2]} ${t('unit_reps')}`
  const singleMatch = /^(\d+)\s+reps$/i.exec(label.trim())
  if (singleMatch) return plural('rx_reps_n', Number(singleMatch[1]), Number(singleMatch[1]))
  const secMatch = /^(\d+)\s+s$/i.exec(label.trim())
  if (secMatch) return t('rx_seconds', Number(secMatch[1]))
  return label
}

type CardProps = {
  g: Group; forceOpen: boolean; exercises: Map<string, ExerciseRow>; previous: Map<string, SetRow[]>; prIds: Set<number>; newBestId: number | null; reasons: Map<string, ProgressionReason>; unit: WeightUnit; effortSetId: number | null
  onChange: (s: SetRow, editedField?: EditedField) => void; onToggle: (s: SetRow) => void; onToggleAll: (sets: SetRow[]) => void; onStartHold: (s: SetRow) => void; onDismissEffort: () => void; onMenu: (position: number, swapOnly: boolean) => void; onSwap: (position: number) => void; onAddRound: (positions: number[]) => void
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
function WarmupRow(p: { g: Extract<Group, { kind: 'straight' }>; ex: ExerciseRow | undefined; name: string; rowProps: (set: SetRow, round: number | null) => Omit<SetRowProps, 'label' | 'sublabel'>; onToggleAll: (sets: SetRow[]) => void; onSwap: (position: number) => void }) {
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
              <span class="lw-warm-note">{t('live_warmup_not_counted')}{doneCount > 0 && !allDone ? ` · ${doneCount}/${g.sets.length}` : ''}</span>
            </span>
            <ExpandMoreIcon size={20} />
          </button>
          <SetTick checked={allDone} onToggle={() => p.onToggleAll(g.sets)} />
        </div>
        <div class="lw-warm-foot">
          <div class="lw-quick">
            <a class="lw-quickbtn" href={`#/exercise/${encodeURIComponent(first.exerciseId)}/live`}>{t('cues_title')}</a>
            <button type="button" class="lw-quickbtn" onClick={() => p.onSwap(g.position)}>{t('live_swap_exercise')}</button>
          </div>
          {!allDone && <Button variant="text" onClick={() => p.onToggleAll(g.sets)}>{t('live_warmup_done')}</Button>}
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
    pr: prIds(p, set), flash: set.id != null && p.newBestId === set.id, showEffort: p.effortSetId === set.id,
    onChange: p.onChange, onToggle: p.onToggle, onStartHold: p.onStartHold, onDismissEffort: p.onDismissEffort,
  })

  if (g.kind === 'straight' && isExtraBlock(g.block)) {
    return <WarmupRow g={g} ex={exercises.get(first.exerciseId)} name={name(first.exerciseId)} rowProps={rowProps} onToggleAll={p.onToggleAll} onSwap={p.onSwap} />
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
    const target = first.targetLabel ? formatTargetLabel(first.targetLabel) : ''
    const ex = exercises.get(first.exerciseId)
    const reason = p.reasons.get(first.exerciseId)
    const firstCueKey = exerciseCueKeys(first.exerciseId)?.[0]
    return (
      <div data-gkey={g.key}>
        <Card>
          <div class="lw-cardhead">
            <a class="lw-cardtitle" href={`#/exercise/${encodeURIComponent(first.exerciseId)}/live`}>
              <span class="lw-name">{name(first.exerciseId)}</span>
              <span class="lw-small">{target ? t('live_target', g.sets.length, target) : ex ? [...ex.primary].map((m) => t('muscle_' + m.toLowerCase())).join(' · ') : ''}</span>
              {reason && <span class="lw-small">{t(`rx_why_${reason.toLowerCase()}`)}</span>}
              {firstCueKey && <span class="lw-small">{t(firstCueKey)}</span>}
            </a>
            <IconButton label={t('cd_options')} onClick={() => p.onMenu(g.position, false)}><MoreVert /></IconButton>
          </div>
          {/* Always visible, not only in the menu: how to do the move, and swap it for another. */}
          <div class="lw-quick">
            <a class="lw-quickbtn" href={`#/exercise/${encodeURIComponent(first.exerciseId)}/live`}>{t('cues_title')}</a>
            <button type="button" class="lw-quickbtn" onClick={() => p.onSwap(g.position)}>{t('live_swap_exercise')}</button>
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
