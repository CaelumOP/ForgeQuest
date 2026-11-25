import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { getLang, plural, t, useLang } from '../../i18n'
import type { ExerciseRow, PlanItemRow, SessionRow, SetRow } from '../../data/db'
import { configuredEquipment, updateSettings, useSettings, weightUnit } from '../../data/settings'
import {
  addCustomExercise, addExercise, addSet, allWorkoutScores, discardSession, finishedSessions,
  finishSession, harderHint, progressionReasonFor, baselineStatusFor, recentDoneSets, recentDoneSetsAll, removeSet,
  stepUp, swapExercise, trainingDays, updateExercise, updateSet, useActivePlan, useLibrary,
  getSets, useOpenSession, useSets, workoutDebrief, type FinishResult,
} from '../../data/workouts'
import { exerciseCueKeys } from '../../data/seed/exercise-cues'
import { pickEffort, type SetEffort } from '../../domain/effort'
import { recordedHold } from '../../domain/hold-math'
import {
  computeAdjustment, extendedRest, newAdjustmentLedger, numbersToRestore, openLaterSets, recordAdjustment, releaseAdjustment,
  resolveRepsMin, triggersAdjustment,
} from '../../domain/intra-session'
import { formatWeight, unitLabel, weightNumber, type WeightUnit } from '../../domain/units'
import { isDeloadWeek, type ProgressionReason } from '../../domain/progression'
import { isExtraBlock, pickPrs } from '../../domain/pr-picker'
import { countsForPR, countsForScore } from '../../domain/set-types'
import type { RecapExercise } from '../../domain/workout-recap'
import {
  appendHistoryEntries, appendHistoryOne, dayType, headlineKey,
  pickHeadlineVariant, scoreTier,
} from '../../domain/finish-summary'
import { muscleWeekTargets, pickInsights } from '../../domain/insights'
import type { PlanItemEntity, SessionEntity, SetEntity } from '../../domain/entities'
import { Button, Card, Dialog, EmptyState, IconButton, ListRow, Sheet, TextField, haptics, toast } from '../kit'
import { recordScore, welcomeBack as isWelcomeBack } from '../../domain/progress'
import { AddIcon, CheckIcon, CloseIcon, ExpandMoreIcon } from '../icons'
import { ExercisePicker } from '../workouts/ExercisePicker'
import { autoBackup } from '../../data/drive'
import { syncFriendsQuietly } from '../../data/friends'
import { blockLabel, openFormVideo } from '../workouts/helpers'
import { alarm, beep, buzz, keepScreenOn, unlockAudio } from './feedback'
import { FocusIcon, ListIcon, MoreVert, RepeatIcon } from './glyphs'
import {
  carryForwardUpdates, currentGroupIndex, effectiveRest, groupSets,
  groupSetsByExercise, nextUntickedSet, type Group,
} from './groups'
import { HoldOverlay, RestBar } from './Overlays'
import { QuickPeekSheet, type PeekHistoryEntry } from './QuickPeekSheet'
import { SetRowView, SetTick, type EditedField, type SetRowProps } from './SetRowView'
import {
  adjustRest, clock, holdElapsed, restLeft, holdSaved, loadTimers, saveTimers, startHold, startRest, restDone,
  type HoldTimer, type RestTimer,
} from './timers'
import { holdCueEvent, speakCue, stopSpeech } from './tts'
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
type LiveAdjustment = { exerciseId: string; sourceSetId: number; percent?: number; reps?: number; seconds?: number }

export function LiveWorkoutScreen(_: { param?: string }) {
  const lang = useLang()
  const session = useOpenSession()
  const sets = useSets(session?.id ?? -1)
  const library = useLibrary()
  const settings = useSettings()
  const defaultUnit = weightUnit(settings)
  const equipment = useMemo(() => configuredEquipment(settings), [settings])
  const exercises = useMemo(() => new Map(library.map((e) => [e.id, e])), [library])
  const unitFor = (exerciseId: string): WeightUnit =>
    weightUnit(settings, exercises.get(exerciseId)?.unitOverride ?? '')

  const [now, setNow] = useState(Date.now())
  const [rest, setRest] = useState<RestTimer | null>(null)
  const [hold, setHold] = useState<HoldTimer | null>(null)
  const [focus, setFocus] = useState(readFocus)
  const [page, setPage] = useState(0)
  const [effortSetId, setEffortSetId] = useState<number | null>(null)
  const [picker, setPicker] = useState<PickerMode | null>(null)
  const [menu, setMenu] = useState<{ position: number; swapOnly: boolean } | null>(null)
  const [topMenuOpen, setTopMenuOpen] = useState(false)
  const [peekExId, setPeekExId] = useState<string | null>(null)
  const [peekHistory, setPeekHistory] = useState<Map<string, PeekHistoryEntry[]>>(new Map())
  const [setupNoteEx, setSetupNoteEx] = useState<ExerciseRow | null>(null)
  const [unitEx, setUnitEx] = useState<ExerciseRow | null>(null)
  const [confirmFinish, setConfirmFinish] = useState(false)
  const [confirmExit, setConfirmExit] = useState(false)
  const [finishing, setFinishing] = useState(false)
  const [summary, setDone] = useState<WorkoutSummaryData | null>(null)
  const [previous, setPrevious] = useState<Map<string, SetRow[]>>(new Map())
  const [harder, setHarder] = useState<Map<string, Hint>>(new Map())
  const [reasons, setReasons] = useState<Map<string, ProgressionReason>>(new Map())
  const [estimated, setEstimated] = useState<Set<string>>(new Set())
  const [newBestId, setNewBestId] = useState<number | null>(null)
  const [showWelcomeBack, setShowWelcomeBack] = useState(false)
  const activePlan = useActivePlan()
  const deloadWeek = !!activePlan && session?.planDayId != null && isDeloadWeek(activePlan.plan.createdAt, todayEpochDay())
  const editedRepsRef = useRef(new Set<number>())
  const editedWeightRef = useRef(new Set<number>())
  const editedSecondsRef = useRef(new Set<number>())
  const adjustedByRef = useRef(new Set<number>())
  const ledgerRef = useRef(newAdjustmentLedger())
  const preCarryRef = useRef(new Map<number, SetRow[]>())
  const restExtendedByRef = useRef(new Set<number>())
  const liveQueue = useRef<Promise<unknown>>(Promise.resolve())
  const [adjustment, setAdjustment] = useState<LiveAdjustment | null>(null)
  const prBuzzedRef = useRef(new Set<string>())
  const celebratedRef = useRef(new Set<number>())
  const [best, setBest] = useState<Map<string, number>>(new Map())

  const restRef = useRef(rest); restRef.current = rest
  const holdRef = useRef(hold); holdRef.current = hold
  const nowRef = useRef(now)
  const setsRef = useRef(sets); setsRef.current = sets
  const exercisesRef = useRef(exercises); exercisesRef.current = exercises
  const settingsRef = useRef(settings); settingsRef.current = settings
  const hapticsOn = settings.hapticsEnabled !== false
  const hapticsRef = useRef(hapticsOn); hapticsRef.current = hapticsOn
  const ttsRef = useRef(settings.ttsCues); ttsRef.current = settings.ttsCues
  const langRef = useRef(lang); langRef.current = lang
  const lastCue = useRef(Number.MIN_SAFE_INTEGER)
  const restored = useRef<number | null>(null)

  const [flash, setFlash] = useState(false)
  const [overTitle, setOverTitle] = useState(false)
  const baseTitle = useRef<string | null>(null)
  useEffect(() => {
    baseTitle.current ??= document.title
    document.title = rest ? `${clock(restLeft(rest.endsAt, now))} · ${t('live_rest')}` : overTitle ? t('live_web_rest_over') : baseTitle.current
  }, [rest, now, overTitle])
  useEffect(() => () => { if (baseTitle.current != null) document.title = baseTitle.current; stopSpeech() }, [])

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
      const foreground = typeof document !== 'undefined' && document.visibilityState === 'visible'
      if (r && restDone(r, n)) {
        restRef.current = null; setRest(null)
        if (session?.id) saveTimers(store(), { sessionId: session.id, rest: null, hold: holdRef.current })
        if (n - r.endsAt < 15000) {
          if (ttsRef.current && foreground) {
            const phrase = r.nextExercise ? t('live41_tts_rest_over_next', r.nextExercise) : t('live41_tts_rest_over')
            const spoke = speakCue(phrase, langRef.current, true, true, () => alarm(hapticsRef.current))
            if (spoke && hapticsRef.current) buzz([200, 100, 200])
          } else {
            alarm(hapticsRef.current)
          }
          setFlash(true); window.setTimeout(() => setFlash(false), 700)
          if (!foreground) setOverTitle(true)
        }
      }
      if (h) {
        const s = holdElapsed(h, n)
        if (s !== lastCue.current) {
          lastCue.current = s
          const left = h.target - s
          const cueEv = ttsRef.current && foreground ? holdCueEvent(s, h.target) : null
          if (cueEv) {
            const cueText =
              cueEv === 'READY_3' || cueEv === 'COUNT_3' ? '3'
              : cueEv === 'READY_2' || cueEv === 'COUNT_2' ? '2'
              : cueEv === 'READY_1' || cueEv === 'COUNT_1' ? '1'
              : cueEv === 'START' ? t('live41_tts_hold_start')
              : cueEv === 'HALFWAY' ? t('live41_tts_hold_halfway')
              : t('live41_tts_hold_done')
            speakCue(cueText, langRef.current, true, true, () => {
              if (s === h.target) alarm(hapticsRef.current)
              else beep(660, 0.1)
            })
            if (hapticsRef.current) buzz(s === h.target ? [200, 100, 200] : 30)
          } else {
            if (s === h.target) alarm(hapticsRef.current)
            else if (s < 0 || (left >= 1 && left <= 3) || (h.target >= 10 && s === Math.floor(h.target / 2))) { beep(660, 0.1); if (hapticsRef.current) buzz(30) }
          }
        }
      }
      if (Math.floor(n / 1000) !== Math.floor(nowRef.current / 1000) || h) { nowRef.current = n; setNow(n) }
    }
    const id = window.setInterval(tick, 250)
    const onVisible = () => { if (document.visibilityState === 'visible') { setOverTitle(false); tick() } else stopSpeech() }
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
      const done = (await recentDoneSetsAll(id)).filter((s) => countsForPR(s))
      return [id, done.length ? Math.max(...done.map((s) => recordScore(kind, s.reps, s.seconds, s.weightKg, id))) : Infinity] as const
    })).then((rows) => { if (live) setBest((m) => new Map([...m, ...rows])) })
    return () => { live = false }
  }, [sets.map((s) => s.exerciseId).join('|'), exercises.size])

  const prIds = useMemo(
    () => pickPrs(sets.filter((s) => countsForPR(s)), (id) => exercises.get(id)?.kind ?? 'REPS', best),
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

  const hintChecked = useRef(new Map<string, string>())
  useEffect(() => {
    if (!session?.id || exercises.size === 0) return
    const planDay = activePlan?.days.find((d) => d.day.id === session.planDayId)
    const ids = [...new Set(sets.map((s) => s.exerciseId))].filter((id) => {
      const first = sets.find((s) => s.exerciseId === id)
      const planItem = first ? planDay?.items.find((i) => i.position === first.itemPosition && i.exerciseId === id) ?? null : null
      const key = `${session.id}:${planItem?.repsMax ?? ''}:${planItem?.sets ?? ''}:${planItem?.repsMin ?? ''}:${deloadWeek}`
      if (hintChecked.current.get(id) === key) return false
      hintChecked.current.set(id, key)
      return true
    })
    if (ids.length === 0) return
    void Promise.all(ids.map(async (id) => {
      const ex = exercises.get(id)
      const warmup = sets.some((s) => s.exerciseId === id && isExtraBlock(s.block))
      const first = sets.find((s) => s.exerciseId === id)
      const planItem = first ? planDay?.items.find((i) => i.position === first.itemPosition && i.exerciseId === id) ?? null : null
      const hint = ex ? await harderHint(ex, warmup, session.id!, planItem?.repsMax ?? planItem?.repsMin ?? null) : undefined
      const recent = (await recentDoneSets(id)).filter((s) => s.sessionId !== session.id)
      const reason = await progressionReasonFor(planItem, ex, recent, deloadWeek)
      return [id, hint, reason] as const
    })).then((rows) => {
      setHarder((m) => {
        const next = new Map(m)
        for (const [id, h] of rows) {
          if (h !== undefined) next.set(id, h)
          else next.delete(id)
        }
        return next
      })
      setReasons((m) => {
        const next = new Map(m)
        for (const [id, , r] of rows) {
          if (r != null) next.set(id, r)
          else next.delete(id)
        }
        return next
      })
    })
  }, [sets.map((s) => s.exerciseId).join('|'), session?.id, exercises.size, activePlan, deloadWeek])

  const planItemsByPos = useMemo(() => {
    const map = new Map<number, PlanItemRow>()
    const planDay = activePlan?.days.find((d) => d.day.id === session?.planDayId)
    for (const item of planDay?.items ?? []) map.set(item.position, item)
    return map
  }, [activePlan, session?.planDayId])
  const planItemsRef = useRef(planItemsByPos); planItemsRef.current = planItemsByPos

  useEffect(() => {
    let live = true
    if (!session?.id) { setEstimated(new Set()); return }
    // Worked out when exercises appear (like the reason line), not on every tap: a cold-start check reads all history.
    const rowsNow = setsRef.current
    void Promise.all([...new Set(rowsNow.map((s) => s.exerciseId))].map(async (id) => {
      const ownSets = rowsNow.filter((s) => s.exerciseId === id)
      const item = planItemsByPos.get(ownSets[0].itemPosition)
      const recent = await recentDoneSets(id)
      const status = await baselineStatusFor(exercises.get(id), item?.exerciseId === id ? item : null, ownSets, recent)
      return [id, status] as const
    })).then((rows) => {
      if (live) setEstimated(new Set(rows.filter(([, status]) => status === 'ESTIMATED').map(([id]) => id)))
    })
    return () => { live = false }
  }, [session?.id, sets.map((s) => s.exerciseId).join('|'), exercises.size, planItemsByPos, settings.unit])

  const supersetMap = useMemo(() => {
    const map = new Map<number, number | null>()
    for (const [pos, item] of planItemsByPos) map.set(pos, item.supersetGroup ?? null)
    return map
  }, [planItemsByPos])

  const groups = useMemo(() => groupSetsByExercise(sets, (pos) => supersetMap.get(pos) ?? null), [sets, supersetMap])
  const supersetLetters = useMemo(() => {
    const out = new Map<string, string>()
    let idx = 0
    for (const g of groups) {
      if (g.kind === 'circuit' && g.supersetGroup != null) {
        out.set(g.key, String.fromCharCode(65 + (idx++ % 26)))
      }
    }
    return out
  }, [groups])

  const doneCount = sets.filter((s) => s.done).length
  const workSets = useMemo(() => sets.filter((s) => countsForScore(s)), [sets])
  const workDone = workSets.filter((s) => s.done).length
  const trackedSets = workSets.length > 0 ? workSets : sets
  const trackedDone = trackedSets.filter((s) => s.done).length
  const trackedLeft = trackedSets.length - trackedDone
  const current = currentGroupIndex(groups)
  const nextSet = nextUntickedSet(groups)
  const nameOf = (id: string) => exercises.get(id)?.name ?? id
  const nextLabel = nextSet ? `${nameOf(nextSet.exerciseId)} · ${setSummary(nextSet, exercises.get(nextSet.exerciseId)?.kind ?? 'REPS', unitFor(nextSet.exerciseId))}` : null

  const copySourceFor = (target: SetRow | undefined): SetRow | undefined => {
    if (!target) return undefined
    const inSession = sets
      .filter((s) => s.exerciseId === target.exerciseId && s.done && s.id !== target.id)
      .sort((a, b) => (b.doneAt ?? b.setIndex) - (a.doneAt ?? a.setIndex))[0]
    if (inSession) return inSession
    const prevList = previous.get(target.exerciseId) ?? []
    return prevList[target.setIndex] ?? prevList[prevList.length - 1]
  }
  const copySource = copySourceFor(nextSet)

  const openQuickPeek = (exerciseId: string) => {
    setPeekExId(exerciseId)
    void Promise.all([recentDoneSetsAll(exerciseId), finishedSessions()]).then(([allSets, finished]) => {
      const bySession = new Map<number, SessionRow>(finished.map((fs) => [fs.id!, fs]))
      const grouped = new Map<number, SetRow[]>()
      for (const s of allSets) {
        if (s.sessionId === session?.id || !bySession.has(s.sessionId)) continue
        grouped.set(s.sessionId, [...(grouped.get(s.sessionId) ?? []), s])
      }
      const entries: PeekHistoryEntry[] = [...grouped.entries()]
        .map(([id, list]) => ({ session: bySession.get(id)!, sets: list.sort((a, b) => a.setIndex - b.setIndex) }))
        .sort((a, b) => b.session.epochDay - a.session.epochDay || b.session.startedAt - a.session.startedAt)
        .slice(0, 3)
      setPeekHistory((m) => new Map(m).set(exerciseId, entries))
    })
  }

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
    const nextGroups = groupSetsByExercise(withTick, (pos) => supersetMap.get(pos) ?? null)
    const restPlan = effectiveRest(set, nextGroups)
    if (restPlan.seconds <= 0) return
    const next = nextUntickedSet(nextGroups)
    const isRp = (set.setType ?? 'WORKING') === 'RESTPAUSE'
    setRestSaved(startRest(restPlan.seconds, Date.now(), next ? nameOf(next.exerciseId) : null, isRp, set.id, restPlan.isShortTransition))
  }

  const announcePr = (set: SetRow) => {
    const ex = exercises.get(set.exerciseId)
    if (!ex) return
    const u = unitFor(set.exerciseId)
    const what = ex.kind === 'WEIGHTED'
      ? `${set.reps ?? 0} × ${formatWeight(u, set.weightKg ?? 0, getLang() === 'ar')}`
      : ex.kind === 'HOLD'
        ? plural('debrief_n_seconds', set.seconds ?? 0, set.seconds ?? 0)
        : plural('debrief_n_reps', set.reps ?? 0, set.reps ?? 0)
    toast(t('live_pr_new', ex.name, what))
  }

  const checkBest = (updated: SetRow) => {
    if (updated.id == null || !countsForPR(updated)) return
    const withEdit = setsRef.current.map((s) => (s.id === updated.id ? { ...updated, done: true } : s)).filter((s) => countsForPR(s))
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

  // A render can put an older liveQuery snapshot back into setsRef while a queued step is between writes, so queued
  // steps read the rows from Dexie itself before deciding anything.
  const sessionIdRef = useRef(session?.id); sessionIdRef.current = session?.id
  const freshRows = async (): Promise<SetRow[]> => {
    const id = sessionIdRef.current
    if (id == null) return setsRef.current
    const rows = await getSets(id)
    setsRef.current = rows
    return rows
  }

  const locked = <T,>(fn: () => Promise<T>): Promise<T> => {
    const run = async () => { await freshRows(); return fn() }
    const next = liveQueue.current.then(run, run)
    liveQueue.current = next.then(() => {}, () => {})
    return next
  }

  const isEdited = (id: number) =>
    editedRepsRef.current.has(id) || editedWeightRef.current.has(id) || editedSecondsRef.current.has(id)

  const persistSet = async (row: SetRow) => {
    try {
      await updateSet(row)
      if (row.id != null) setsRef.current = setsRef.current.map((s) => (s.id === row.id ? row : s))
    } catch {
      toast(t('live_web_save_failed'))
    }
  }

  const writeNumbers = async (values: readonly SetRow[]) => {
    for (const row of numbersToRestore(values, await freshRows(), isEdited)) {
      await persistSet(row)
    }
  }

  const applyIntraSession = async (source: SetRow) => {
    if (source.id == null || adjustedByRef.current.has(source.id)) return
    const currentOpen = openLaterSets(await freshRows(), source, isEdited)
    const openIds = new Set(currentOpen.map((s) => s.id!))
    const base = (preCarryRef.current.get(source.id) ?? currentOpen).filter((s) => s.id != null && openIds.has(s.id))
    const ex = exercisesRef.current.get(source.exerciseId)
    const kind = ex?.kind ?? 'REPS'
    const repsMin = resolveRepsMin(source, kind, planItemsRef.current.get(source.itemPosition))
    const unit = weightUnit(settingsRef.current, ex?.unitOverride ?? '')
    if (!triggersAdjustment(source, kind, repsMin)) return
    // Targets come from the pre-carry rows; the write compares against the rows as they are now (after carry-forward).
    const result = computeAdjustment(source, base, kind, repsMin, unit)
    const changedById = new Map((result?.changed ?? []).map((s) => [s.id!, s]))
    const toWrite = numbersToRestore(base.map((s) => changedById.get(s.id!) ?? s), currentOpen, isEdited)
    adjustedByRef.current.add(source.id)
    if (!result) {
      // The cut changes nothing against the plan, but carry-forward may have raised targets (did 12 of 10, then
      // Failure): put the pre-carry numbers back silently, with no cue and nothing for Undo.
      if (toWrite.length > 0) await writeNumbers(toWrite)
      return
    }
    // A real cut gets the cue and Undo even when carry-forward already wrote the same numbers.
    recordAdjustment(ledgerRef.current, source.id, base.filter((s) => changedById.has(s.id!)))
    if (toWrite.length > 0) await writeNumbers(toWrite)
    setAdjustment({
      exerciseId: source.exerciseId,
      sourceSetId: source.id,
      percent: result.percent,
      reps: result.reps,
      seconds: result.seconds,
    })
  }

  /**
   * Hand edits join the same queue as tick / effort / Undo and write only the fields in `patch` onto the row as it is
   * when their turn comes, so a stale rendered row never overwrites a Failure tag or an adjustment, and vice versa.
   */
  const save = (set: SetRow, patch: Partial<SetRow>, editedField?: EditedField) => {
    const id = set.id
    if (id == null) { void persistSet({ ...set, ...patch }); return }
    const latest = { ...(setsRef.current.find((s) => s.id === id) ?? set), ...patch }
    if (editedField) {
      if (editedField === 'reps') editedRepsRef.current.add(id)
      if (editedField === 'weightKg') editedWeightRef.current.add(id)
      if (editedField === 'seconds') editedSecondsRef.current.add(id)
      if (latest.done) checkBest(latest)
    }
    setsRef.current = setsRef.current.map((s) => (s.id === id ? { ...s, ...patch } : s))
    void locked(async () => {
      const fresh = setsRef.current.find((s) => s.id === id)
      if (fresh) await persistSet({ ...fresh, ...patch })
    })
  }

  const toggleAll = (group: SetRow[]) => {
    unlockAudio()
    const done = !group.every((s) => s.done)
    if (done) setAdjustment((cur) => (cur && !group.some((s) => s.exerciseId === cur.exerciseId) ? null : cur))
    setEffortSetId(null)
    if (done) haptics.check(); else haptics.tick()
    const doneAt = done ? Date.now() : null
    group.forEach((s) => { if (s.done !== done) save(s, { done, doneAt }) })
  }

  const carryForward = async (set: SetRow) => {
    for (const updated of carryForwardUpdates(await freshRows(), set, editedRepsRef.current, editedWeightRef.current)) {
      await persistSet(updated)
    }
  }

  const toggle = (set: SetRow) => {
    unlockAudio()
    const done = !set.done
    if (done) setAdjustment((cur) => (cur && cur.exerciseId !== set.exerciseId ? null : cur))
    const doneAt = done ? Date.now() : null
    const effort = done ? set.effort : ''
    setEffortSetId(done ? (set.id ?? null) : null)
    const withTick = setsRef.current.map((s) => (s.id === set.id ? { ...set, done, doneAt, effort } : s))
    const picked = pickPrs(withTick.filter((s) => countsForPR(s)), (id) => exercises.get(id)?.kind ?? 'REPS', best)
    const freshPr = done && countsForPR(set) && set.id != null && picked.has(set.id) && !prBuzzedRef.current.has(set.exerciseId)
    if (!done) haptics.tick()
    else if (freshPr) {
      prBuzzedRef.current.add(set.exerciseId)
      celebratedRef.current.add(set.id!)
      haptics.pr()
      announcePr(set)
      setNewBestId(set.id!)
      window.setTimeout(() => setNewBestId((cur) => (cur === set.id ? null : cur)), 1400)
    } else haptics.check()
    void locked(async () => {
      const fresh = (set.id != null ? setsRef.current.find((s) => s.id === set.id) : undefined) ?? set
      // Unticking clears the effort; a FAILURE type the Failure tag set goes too, or A11 would still read the row as Failure.
      const setType = !done && fresh.effort === 'FAILURE' && fresh.setType === 'FAILURE' ? 'WORKING' : fresh.setType
      const updatedSet: SetRow = { ...fresh, done, doneAt, effort: done ? fresh.effort : '', setType }
      if (done) {
        if (fresh.id != null) preCarryRef.current.set(fresh.id, openLaterSets(setsRef.current, fresh, isEdited))
        await persistSet(updatedSet)
        await carryForward(updatedSet)
        await applyIntraSession(updatedSet)
      } else {
        await persistSet(updatedSet)
        if (fresh.id != null) {
          const prev = releaseAdjustment(ledgerRef.current, fresh.id)
          if (prev.length > 0) await writeNumbers(prev as SetRow[])
          adjustedByRef.current.delete(fresh.id)
          restExtendedByRef.current.delete(fresh.id)
          preCarryRef.current.delete(fresh.id)
          setAdjustment((cur) => (cur?.sourceSetId === fresh.id ? null : cur))
        }
      }
    })
    if (done && doneAt != null) startRestFor(set, doneAt)
    else if (!done) setRestSaved(null)
  }

  const handleSetEffort = (set: SetRow, picked: SetEffort) => {
    haptics.tick()
    void locked(async () => {
      const fresh = (set.id != null ? setsRef.current.find((s) => s.id === set.id) : undefined) ?? set
      const { effort, setType } = pickEffort(fresh, picked)
      const updated: SetRow = { ...fresh, effort, setType }
      await persistSet(updated)
      if (effort === 'FAILURE' && updated.id != null) {
        const r = restRef.current
        if (r && r.setId === updated.id && !r.isRestPause && !r.isShortTransition && !restExtendedByRef.current.has(updated.id)) {
          restExtendedByRef.current.add(updated.id)
          const delta = extendedRest(r.totalSeconds) - r.totalSeconds
          if (delta > 0) addRest(delta)
        }
        await applyIntraSession(updated)
      }
    })
  }

  /** Shows the effort chips again for a done set; never unticks it or touches the rest timer. */
  const reopenEffort = (set: SetRow) => {
    if (set.id == null || !setsRef.current.some((s) => s.id === set.id && s.done)) return
    haptics.tick()
    setEffortSetId(set.id)
  }

  const undoAdjustment = () => {
    const sourceId = adjustment?.sourceSetId
    if (sourceId == null) return
    setAdjustment(null)
    haptics.tick()
    void locked(async () => {
      const prev = releaseAdjustment(ledgerRef.current, sourceId)
      if (prev.length > 0) await writeNumbers(prev as SetRow[])
    })
  }

  const copyLastSetTo = (target: SetRow) => {
    const src = copySourceFor(target)
    if (!src || target.id == null) return
    editedRepsRef.current.add(target.id)
    editedWeightRef.current.add(target.id)
    editedSecondsRef.current.add(target.id)
    haptics.tick()
    save(target, {
      reps: src.reps ?? target.reps,
      seconds: src.seconds ?? target.seconds,
      weightKg: src.weightKg ?? target.weightKg,
    })
    toast(t('live41_copied_last_set'))
  }

  const useLastTimeForExercise = (exerciseId: string) => {
    const prevList = previous.get(exerciseId) ?? []
    if (prevList.length === 0) return
    const targets = sets.filter((s) => s.exerciseId === exerciseId && !s.done).sort((a, b) => a.setIndex - b.setIndex)
    if (targets.length === 0) return
    haptics.tick()
    targets.forEach((s, idx) => {
      const src = prevList[idx] ?? prevList[prevList.length - 1]
      if (!src || s.id == null) return
      editedRepsRef.current.add(s.id)
      editedWeightRef.current.add(s.id)
      editedSecondsRef.current.add(s.id)
      save(s, {
        reps: src.reps ?? s.reps,
        seconds: src.seconds ?? s.seconds,
        weightKg: src.weightKg ?? s.weightKg,
      })
    })
    toast(t('live41_peek_used_last'))
    setPeekExId(null)
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
    setAdjustment((cur) => (cur && cur.exerciseId !== set.exerciseId ? null : cur))
    const saved = recordedHold(held, h.target)
    const doneAt = Date.now()
    const updated: SetRow = { ...set, seconds: saved, done: true, doneAt }
    setEffortSetId(updated.id ?? null)
    void locked(async () => {
      const fresh = (updated.id != null ? setsRef.current.find((s) => s.id === updated.id) : undefined) ?? set
      const freshUpdated: SetRow = { ...fresh, seconds: saved, done: true, doneAt }
      if (fresh.id != null) preCarryRef.current.set(fresh.id, openLaterSets(setsRef.current, fresh, isEdited))
      await persistSet(freshUpdated)
      await carryForward(freshUpdated)
      await applyIntraSession(freshUpdated)
    })
    checkBest(updated)
    if (hapticsOn) buzz(40)
    startRestFor(updated, doneAt)
  }

  const addRest = (delta: number) => {
    const r = restRef.current
    if (r) setRestSaved(adjustRest(r, delta))
  }

  const selectRestPreset = (seconds: number) => {
    const r = restRef.current
    if (!r) return
    haptics.tick()
    setRestSaved(startRest(seconds, Date.now(), r.nextExercise, r.isRestPause, r.setId, r.isShortTransition))
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
    setRestSaved(null); setHoldSaved(null); stopSpeech()
    try {
      const startedAt = session.startedAt
      const before = (await getProgressSnapshot()).level
      const result = await finishSession(sid, tickAll)
      const badges = await evaluateBadges()
      const snap = await getProgressSnapshot()
      const justLog = snap.settings.justLog
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
          if (x.exerciseId !== prWin.exerciseId || x.sessionId === sid || !countsForPR(x)) continue
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
      const mins = workoutMinutes(startedAt, result.endedAt)
      const sessionSets = snap.doneSets
        .filter((x) => x.sessionId === sid)
        .sort((a, b) => a.itemPosition - b.itemPosition || a.setIndex - b.setIndex)
      const byEx = new Map<string, SetRow[]>()
      for (const x of sessionSets) byEx.set(x.exerciseId, [...(byEx.get(x.exerciseId) ?? []), x])
      const recapExercises: RecapExercise[] = [...byEx.entries()].map(([exId, list]) => {
        const ex = snap.exercises.get(exId)
        return {
          exerciseId: exId,
          name: ex?.name ?? exId,
          kind: ex?.kind ?? 'REPS',
          unit: weightUnit(snap.settings, ex?.unitOverride ?? ''),
          sets: list.map((x) => ({
            reps: x.reps,
            seconds: x.seconds,
            weightKg: x.weightKg,
            isPr: x.isPr,
            countsForPr: countsForPR(x),
          })),
        }
      })
      setDone({
        sessionId: sid, title: result.sessionTitle, doneSets: result.doneSets, minutes: mins,
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
        recap: {
          title: result.sessionTitle,
          epochDay: session.epochDay,
          minutes: mins,
          doneSetsCount: result.doneSets,
          defaultUnit: weightUnit(snap.settings),
          exercises: recapExercises,
          prs: result.prs,
          arabic: getLang() === 'ar',
        },
      })
    } catch {
      toast(t('live_web_save_failed'))
      setFinishing(false)
    }
  }

  const doDiscard = async () => {
    setConfirmExit(false); setFinishing(true)
    setRestSaved(null); setHoldSaved(null); stopSpeech()
    try { await discardSession(sid); location.hash = '#/workouts' } catch { toast(t('live_web_save_failed')); setFinishing(false) }
  }

  const swapAnchorsRef = useRef(new Map<number, string>())
  const pick = async (ex: ExerciseRow) => {
    const mode = picker
    setPicker(null)
    if (mode?.kind === 'swap') {
      const cur = sets.find((s) => s.itemPosition === mode.position)?.exerciseId
      if (cur && !swapAnchorsRef.current.has(mode.position)) swapAnchorsRef.current.set(mode.position, cur)
      await swapExercise(sid, mode.position, ex.id)
    } else {
      await addExercise(sid, ex.id)
    }
  }
  const currentSwapId = picker?.kind === 'swap' ? sets.find((s) => s.itemPosition === picker.position)?.exerciseId : undefined
  const anchorSwapId = picker?.kind === 'swap' ? (swapAnchorsRef.current.get(picker.position) ?? currentSwapId) : undefined
  const swapping = anchorSwapId ? exercises.get(anchorSwapId) : undefined

  const elapsed = Math.max(0, Math.floor((now - session.startedAt) / 1000))
  const header = plural('live_header', workSets.length, Math.floor(elapsed / 60), String(elapsed % 60).padStart(2, '0'), workDone, workSets.length)
  const shownPage = Math.min(page, Math.max(0, groups.length - 1))
  const focusExId = focus && groups[shownPage] ? groupSets(groups[shownPage])[0]?.exerciseId : null
  const nowExerciseId = focusExId ?? nextSet?.exerciseId ?? null

  const cardFor = (g: Group, forceOpen: boolean) => (
    <GroupCard
      key={g.key}
      g={g}
      forceOpen={forceOpen}
      supersetLetter={supersetLetters.get(g.key) ?? null}
      exercises={exercises}
      previous={previous}
      prIds={prIds}
      newBestId={newBestId}
      reasons={reasons}
      estimated={estimated}
      unitFor={unitFor}
      barWeightKg={settings.barWeightKg}
      plateInventory={settings.plateInventory}
      effortSetId={effortSetId}
      onChange={save}
      onToggle={toggle}
      onToggleAll={toggleAll}
      onSetEffort={handleSetEffort}
      onReopenEffort={reopenEffort}
      onStartHold={beginHold}
      onDismissEffort={() => setEffortSetId(null)}
      adjustment={adjustment}
      onUndoAdjustment={undoAdjustment}
      harder={harder}
      onStepUp={(oldId, next) => {
        setHarder((m) => { const n = new Map(m); n.delete(oldId); return n })
        void stepUp(sid, oldId, next.id).then((swapped) =>
          toast(swapped > 0 ? t('harder_switched', next.name) : t('v42_harder_next_time', next.name)),
        )
      }}
      onMenu={(position, swapOnly) => setMenu({ position, swapOnly })}
      onSwap={(position) => setPicker({ kind: 'swap', position })}
      onPeek={openQuickPeek}
      onAddRound={(positions) => { void (async () => { for (const pos of positions) await addSet(sid, pos) })() }}
    />
  )

  const menuExId = menu ? sets.find((s) => s.itemPosition === menu.position)?.exerciseId ?? '' : ''
  const menuEx = menuExId ? exercises.get(menuExId) : undefined
  const peekEx = peekExId ? exercises.get(peekExId) ?? null : null

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
        <IconButton label={t('cd_more')} onClick={() => setTopMenuOpen(true)}><MoreVert /></IconButton>
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
        {rest && (
          <RestBar
            rest={rest}
            now={now}
            nextLabel={nextLabel}
            canCopyLastSet={nextSet != null && copySource != null}
            onAdd={() => addRest(15)}
            onRemove={() => { if (restLeftNow(rest) > 15) addRest(-15); else setRestSaved(null) }}
            onSelectPreset={selectRestPreset}
            onCopyLastSet={() => { if (nextSet) copyLastSetTo(nextSet) }}
            onSkip={() => { setRestSaved(null); setEffortSetId(null) }}
          />
        )}
        {doneCount === 0 && <div class="lw-small lw-center">{t('live_finish_hint')}</div>}
        <Button block disabled={doneCount === 0 || finishing} loading={finishing}
          onClick={() => { unlockAudio(); if (trackedLeft > 0) setConfirmFinish(true); else void doFinish(false) }}>
          {finishing ? t('live_saving') : t('live_finish_workout')}
        </Button>
      </div>

      {hold && <HoldOverlay hold={hold} now={now} onStop={stopHold} onCancel={() => { setHoldSaved(null); stopSpeech() }} />}

      <FinishSheet open={confirmFinish} left={trackedLeft} canTickAll={trackedDone * 2 >= trackedSets.length} onClose={() => setConfirmFinish(false)}
        onFinish={(all) => { setConfirmFinish(false); void doFinish(all) }} />
      <Dialog open={confirmExit} destructive title={t('live_discard_title')} message={t('live_discard_body')}
        confirmLabel={t('live_discard')} cancelLabel={t('live_keep_going')} onConfirm={() => void doDiscard()} onCancel={() => setConfirmExit(false)} />

      <Sheet open={topMenuOpen} onClose={() => setTopMenuOpen(false)} title={t('cd_more')}>
        <div class="lw-menu">
          <ListRow
            title={t('live41_tts_toggle')}
            trailing={settings.ttsCues ? t('action_ok') : undefined}
            chevron={false}
            onClick={() => {
              haptics.tick()
              void updateSettings((cur) => ({ ...cur, ttsCues: !cur.ttsCues }))
              setTopMenuOpen(false)
            }}
          />
          <ListRow
            title={t('live_discard')}
            chevron={false}
            onClick={() => { setTopMenuOpen(false); setConfirmExit(true) }}
          />
        </div>
      </Sheet>

      <Sheet open={menu != null} onClose={() => setMenu(null)} title={menuExId ? nameOf(menuExId) : ''}>
        {menu && (
          <div class="lw-menu">
            <ListRow title={t('cues_title')} chevron={false} onClick={() => { const id = menuExId; setMenu(null); if (id) openQuickPeek(id) }} />
            <ListRow title={t('cd_watch_form')} chevron={false} onClick={() => { openFormVideo(menuEx); setMenu(null) }} />
            {menuEx && (
              <ListRow title={t('live41_setup_note_label')} chevron={false} onClick={() => { setSetupNoteEx(menuEx); setMenu(null) }} />
            )}
            {menuEx && menuEx.kind === 'WEIGHTED' && (
              <ListRow
                title={t('live41_unit_menu', unitLabel(unitFor(menuEx.id), lang === 'ar'))}
                chevron={false}
                onClick={() => { setUnitEx(menuEx); setMenu(null) }}
              />
            )}
            <ListRow title={t('live_swap_exercise')} chevron={false} onClick={() => { setPicker({ kind: 'swap', position: menu.position }); setMenu(null) }} />
            {!menu.swapOnly && <ListRow title={t('live_add_set')} chevron={false} onClick={() => { void addSet(sid, menu.position); setMenu(null) }} />}
            {!menu.swapOnly && <ListRow title={t('live_remove_last_set')} chevron={false} onClick={() => { removeLast(menu.position); setMenu(null) }} />}
          </div>
        )}
      </Sheet>

      <QuickPeekSheet
        open={peekEx != null}
        exercise={peekEx}
        unit={peekEx ? unitFor(peekEx.id) : defaultUnit}
        history={peekEx ? (peekHistory.get(peekEx.id) ?? []) : []}
        canUseLastTime={peekEx != null && (previous.get(peekEx.id)?.length ?? 0) > 0 && sets.some((s) => s.exerciseId === peekEx.id && !s.done)}
        onClose={() => setPeekExId(null)}
        onEditSetupNote={() => { if (peekEx) { setSetupNoteEx(peekEx); setPeekExId(null) } }}
        onUseLastTime={() => { if (peekEx) useLastTimeForExercise(peekEx.id) }}
        onFullStats={(id) => { setPeekExId(null); location.hash = `#/exercise/${encodeURIComponent(id)}/live` }}
      />

      {setupNoteEx && (
        <SetupNoteSheet
          exercise={exercises.get(setupNoteEx.id) ?? setupNoteEx}
          onClose={() => setSetupNoteEx(null)}
          onSave={(note) => {
            const cur = exercises.get(setupNoteEx.id) ?? setupNoteEx
            void updateExercise({ ...cur, setupNote: note.trim().slice(0, 120) })
            setSetupNoteEx(null)
          }}
        />
      )}

      {unitEx && (
        <UnitOverrideSheet
          exercise={exercises.get(unitEx.id) ?? unitEx}
          defaultUnit={defaultUnit}
          arabic={lang === 'ar'}
          onClose={() => setUnitEx(null)}
          onSelect={(code) => {
            const cur = exercises.get(unitEx.id) ?? unitEx
            void updateExercise({ ...cur, unitOverride: code })
            setUnitEx(null)
          }}
        />
      )}

      <ExercisePicker open={picker != null} title={t(picker?.kind === 'swap' ? 'live_swap_exercise' : 'live_add_exercise')} library={library}
        onPick={(ex) => void pick(ex)}
        onCreate={(name, kind, muscle) => { void addCustomExercise(name, kind, [muscle], null).then(async (id) => {
          const mode = picker; setPicker(null)
          if (mode?.kind === 'swap') {
            const cur = sets.find((s) => s.itemPosition === mode.position)?.exerciseId
            if (cur && !swapAnchorsRef.current.has(mode.position)) swapAnchorsRef.current.set(mode.position, cur)
            await swapExercise(sid, mode.position, id)
          } else {
            await addExercise(sid, id)
          }
        }) }}
        onClose={() => setPicker(null)} initialMuscle={swapping?.primary?.[0]} sourceExercise={swapping} currentExerciseId={currentSwapId} userEquipment={equipment} />
    </div>
  )
}

function SetupNoteSheet({ exercise, onClose, onSave }: { exercise: ExerciseRow; onClose: () => void; onSave: (note: string) => void }) {
  const [text, setText] = useState(exercise.setupNote ?? '')
  return (
    <Sheet open onClose={onClose} title={`${t('live41_setup_note_label')} · ${exercise.name}`}>
      <div class="lw-notebody">
        <TextField label={t('live41_setup_note_label')} value={text} placeholder={t('live41_setup_note_hint')} onInput={(v) => setText(v.slice(0, 120))} />
        <div class="lw-actions">
          {(exercise.setupNote ?? '') !== '' && <Button variant="text" class="kit-danger" onClick={() => onSave('')}>{t('action_clear')}</Button>}
          <Button variant="text" onClick={onClose}>{t('action_cancel')}</Button>
          <Button onClick={() => onSave(text)}>{t('action_save')}</Button>
        </div>
      </div>
    </Sheet>
  )
}

function UnitOverrideSheet({
  exercise,
  defaultUnit,
  arabic,
  onClose,
  onSelect,
}: {
  exercise: ExerciseRow
  defaultUnit: WeightUnit
  arabic: boolean
  onClose: () => void
  onSelect: (code: string) => void
}) {
  const cur = (exercise.unitOverride ?? '').trim().toLowerCase()
  const opts = [
    { code: '', label: t('live41_unit_default', unitLabel(defaultUnit, arabic)) },
    { code: 'kg', label: t('live41_unit_kg') },
    { code: 'lb', label: t('live41_unit_lb') },
  ]
  return (
    <Sheet open onClose={onClose} title={`${t('live41_unit_title')} · ${exercise.name}`}>
      <div class="lw-type-list">
        {opts.map((o) => {
          const sel = cur === o.code
          return (
            <button key={o.code || 'default'} type="button" class={`lw-type-opt${sel ? ' lw-type-opt-on' : ''}`} onClick={() => onSelect(o.code)}>
              <span class="lw-type-opt-text"><span class="lw-type-opt-title">{o.label}</span></span>
              {sel && <span class="lw-type-check"><CheckIcon size={20} /></span>}
            </button>
          )
        })}
      </div>
    </Sheet>
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

function setSummary(set: SetRow, kind: ExerciseRow['kind'], unit: WeightUnit): string {
  const main = kind === 'HOLD' ? t('rx_seconds', set.seconds ?? 0) : plural('rx_reps_n', set.reps ?? 0, set.reps ?? 0)
  const kg = set.weightKg
  return kg != null && (kg > 0 || kind === 'WEIGHTED') ? `${main} · ${weightNumber(unit, kg)} ${unitLabel(unit, getLang() === 'ar')}` : main
}

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
  g: Group
  forceOpen: boolean
  supersetLetter: string | null
  exercises: Map<string, ExerciseRow>
  previous: Map<string, SetRow[]>
  prIds: Set<number>
  newBestId: number | null
  reasons: Map<string, ProgressionReason>
  estimated: Set<string>
  unitFor: (exerciseId: string) => WeightUnit
  barWeightKg: number
  plateInventory: readonly number[]
  effortSetId: number | null
  onChange: (s: SetRow, patch: Partial<SetRow>, editedField?: EditedField) => void
  onToggle: (s: SetRow) => void
  onToggleAll: (sets: SetRow[]) => void
  onSetEffort: (s: SetRow, picked: SetEffort) => void
  onReopenEffort: (s: SetRow) => void
  onStartHold: (s: SetRow) => void
  onDismissEffort: () => void
  adjustment: LiveAdjustment | null
  onUndoAdjustment: () => void
  onMenu: (position: number, swapOnly: boolean) => void
  onSwap: (position: number) => void
  onPeek: (exerciseId: string) => void
  onAddRound: (positions: number[]) => void
  harder: Map<string, Hint>
  onStepUp: (oldId: string, next: ExerciseRow) => void
}

type Hint = { next: ExerciseRow | null }

const prIds = (p: CardProps, set: SetRow) => set.id != null && p.prIds.has(set.id)

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

function AdjustmentCue({ adjustment, exerciseId, onUndo }: { adjustment: LiveAdjustment | null; exerciseId: string; onUndo: () => void }) {
  if (!adjustment || adjustment.exerciseId !== exerciseId) return null
  const text =
    adjustment.percent != null
      ? t('live_adjusted_load', adjustment.percent)
      : adjustment.reps != null
        ? t('live_adjusted_reps', adjustment.reps)
        : adjustment.seconds != null
          ? t('live_adjusted_seconds', adjustment.seconds)
          : null
  if (!text) return null
  return (
    <div class="lw-adjust" role="status">
      <span class="lw-adjust-text">{text}</span>
      <button type="button" class="lw-adjust-undo" aria-label={t('cd_undo_adjustment')} onClick={onUndo}>
        {t('live_adjust_undo')}
      </button>
    </div>
  )
}

function WarmupRow(p: {
  g: Extract<Group, { kind: 'straight' }>
  ex: ExerciseRow | undefined
  name: string
  rowProps: (set: SetRow, round: number | null) => Omit<SetRowProps, 'label' | 'sublabel'>
  onToggleAll: (sets: SetRow[]) => void
  onSwap: (position: number) => void
  onPeek: (exerciseId: string) => void
}) {
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
            <button type="button" class="lw-quickbtn" onClick={() => p.onPeek(first.exerciseId)}>{t('cues_title')}</button>
            <button type="button" class="lw-quickbtn" onClick={() => p.onSwap(g.position)}>{t('live_swap_exercise')}</button>
          </div>
          {!allDone && <Button variant="text" onClick={() => p.onToggleAll(g.sets)}>{t('live_warmup_done')}</Button>}
        </div>
        {expanded && g.sets.map((set) => <SetRowView key={set.id} label={String(set.setIndex + 1)} sublabel={null} {...p.rowProps(set, null)} />)}
      </Card>
    </div>
  )
}

function GroupCard(p: CardProps) {
  const { g, exercises } = p
  const all = groupSets(g)
  const allDone = all.length > 0 && all.every((s) => s.done)
  const [open, setOpen] = useState(!allDone)
  useEffect(() => setOpen(!allDone), [allDone])
  const first = all[0]
  const name = (id: string) => exercises.get(id)?.name ?? id
  const rowProps = (set: SetRow, round: number | null) => ({
    set,
    unit: p.unitFor(set.exerciseId),
    exercise: exercises.get(set.exerciseId),
    barWeightKg: p.barWeightKg,
    plateInventory: p.plateInventory,
    kind: exercises.get(set.exerciseId)?.kind ?? 'REPS',
    previous: p.previous.get(set.exerciseId)?.[round ?? set.setIndex],
    pr: prIds(p, set),
    flash: set.id != null && p.newBestId === set.id,
    showEffort: p.effortSetId === set.id,
    onChange: p.onChange,
    onToggle: p.onToggle,
    onSetEffort: p.onSetEffort,
    onReopenEffort: p.onReopenEffort,
    onStartHold: p.onStartHold,
    onDismissEffort: p.onDismissEffort,
  })

  if (g.kind === 'straight' && isExtraBlock(g.block)) {
    return <WarmupRow g={g} ex={exercises.get(first.exerciseId)} name={name(first.exerciseId)} rowProps={rowProps} onToggleAll={p.onToggleAll} onSwap={p.onSwap} onPeek={p.onPeek} />
  }

  if (!open && !p.forceOpen) {
    const foldedTitle = g.kind === 'straight'
      ? name(first.exerciseId)
      : g.supersetGroup != null
        ? [...new Set(all.map((s) => name(s.exerciseId)))].join(' + ')
        : blockLabel(g.block)
    return (
      <div data-gkey={g.key}>
        <Card onClick={() => setOpen(true)}>
          <div class="lw-folded">
            <span class="lw-folded-check"><CheckIcon size={20} /></span>
            <span class="lw-folded-title">{foldedTitle}</span>
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
            <button type="button" class="lw-cardtitle lw-cardtitle-btn" onClick={() => p.onPeek(first.exerciseId)}>
              <span class="lw-name">{name(first.exerciseId)}</span>
              <span class="lw-small">{target ? t('live_target', g.sets.length, target) : ex ? [...ex.primary].map((m) => t('muscle_' + m.toLowerCase())).join(' · ') : ''}</span>
              {reason && <span class="lw-small">{t(`rx_why_${reason.toLowerCase()}`)}</span>}
              {p.estimated.has(first.exerciseId) && <span class="lw-small" title={t('live_baseline_estimated_cd')} aria-label={t('live_baseline_estimated_cd')}>{t('live_baseline_estimated')}</span>}
              {firstCueKey && <span class="lw-small">{t(firstCueKey)}</span>}
              {ex?.setupNote && <span class="lw-setup-note">{t('live41_setup_note_prefix', ex.setupNote)}</span>}
            </button>
            <IconButton label={t('cd_options')} onClick={() => p.onMenu(g.position, false)}><MoreVert /></IconButton>
          </div>
          <div class="lw-quick">
            <button type="button" class="lw-quickbtn" onClick={() => p.onPeek(first.exerciseId)}>{t('cues_title')}</button>
            <button type="button" class="lw-quickbtn" onClick={() => p.onSwap(g.position)}>{t('live_swap_exercise')}</button>
          </div>
          {ex && <HarderNote ex={ex} hint={p.harder.get(ex.id)} onStepUp={p.onStepUp} />}
          <AdjustmentCue adjustment={p.adjustment} exerciseId={first.exerciseId} onUndo={p.onUndoAdjustment} />
          {g.sets.map((set) => <SetRowView key={set.id} label={String(set.setIndex + 1)} sublabel={null} {...rowProps(set, null)} />)}
        </Card>
      </div>
    )
  }

  const isSuperset = g.supersetGroup != null
  const letter = p.supersetLetter ?? 'A'
  const posTag = new Map<number, string>()
  if (isSuperset) g.positions.forEach((pos, idx) => posTag.set(pos, `${letter}${idx + 1}`))
  const distinctEx = [...new Set((g.rounds[0] ?? []).map((s) => s.exerciseId))].map((id) => exercises.get(id)).filter((e): e is ExerciseRow => e != null)

  return (
    <div data-gkey={g.key}>
      <Card>
        <div class="lw-cardhead">
          <span class="lw-name">{isSuperset ? t('live41_superset') : blockLabel(g.block)}</span>
          <span class="lw-circuit">
            <RepeatIcon size={16} />
            {' '}
            {isSuperset ? g.positions.map((_, i) => `${letter}${i + 1}`).join('/') : t('live_circuit')}
          </span>
        </div>
        <div class="lw-small">
          {isSuperset
            ? t('live41_superset_hint', `${letter}1`, `${letter}${g.positions.length}`)
            : t('live_circuit_hint')}
        </div>
        {distinctEx.map((e) => (
          <div key={e.id}>
            {p.estimated.has(e.id) && <div class="lw-small" title={t('live_baseline_estimated_cd')} aria-label={t('live_baseline_estimated_cd')}>{e.name}: {t('live_baseline_estimated')}</div>}
            {e.setupNote && <div class="lw-setup-note">{`${e.name}: ${e.setupNote}`}</div>}
            <HarderNote ex={e} hint={p.harder.get(e.id)} onStepUp={p.onStepUp} />
            <AdjustmentCue adjustment={p.adjustment} exerciseId={e.id} onUndo={p.onUndoAdjustment} />
          </div>
        ))}
        {g.rounds.map((round, r) => {
          const done = round.every((s) => s.done)
          return (
            <div key={r}>
              <div class={`lw-round${done ? ' lw-round-done' : ''}`}>{t('live_round', r + 1, g.rounds.length) + (done ? t('live_round_done') : '')}</div>
              {round.map((set) => {
                const slot = posTag.get(set.itemPosition) ?? null
                const sub = slot ? `${slot} · ${name(set.exerciseId)}` : name(set.exerciseId)
                return (
                  <SetRowView
                    key={set.id}
                    label={slot}
                    sublabel={sub}
                    onLabelClick={() => p.onMenu(set.itemPosition, true)}
                    {...rowProps(set, r)}
                  />
                )
              })}
            </div>
          )
        })}
        <Button variant="text" icon={<AddIcon />} onClick={() => p.onAddRound(g.positions)}>{t('live_add_round')}</Button>
      </Card>
    </div>
  )
}
