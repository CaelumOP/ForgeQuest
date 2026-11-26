// "Find your starting point" screen (Android BaselineScreen.kt): tests bodyweight reps, holds and submax weighted lifts
// into `baselineTests` without creating workout sessions or sets.
import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { plural, t, useLang, type Lang } from '../../i18n'
import { saveBaselineTest, useLatestBaselines } from '../../data/baseline-tests'
import type { BaselineTestRow } from '../../data/db'
import { configuredEquipment, useSettings, weightUnit } from '../../data/settings'
import { useActivePlan, useLibrary } from '../../data/workouts'
import { expandLegacyEquipment } from '../../domain/exercise-swap-engine'
import { fromKg, loadStep, toKg, unitLabel } from '../../domain/units'
import { AddIcon, BackChevron, ForwardChevron, RemoveIcon } from '../icons'
import { Button, Card, Segmented, haptics } from '../kit'
import { ExerciseThumbnail } from '../kit/ExerciseMediaView'
import { NumberStepper } from '../kit/NumberStepper'
import { HoldOverlay } from '../live/Overlays'
import { alarm, beep, buzz, keepScreenOn, unlockAudio } from '../live/feedback'
import { clock, holdElapsed, holdSaved, READY_SECONDS, restLeft, type HoldTimer } from '../live/timers'
import { goBack } from '../nav'
import { ExercisePicker } from '../workouts/ExercisePicker'
import { musclesLabel } from '../workouts/helpers'
import {
  BASELINE_HOLD_BENCHMARK_SECONDS,
  BASELINE_REST_SECONDS,
  buildBaselineMoves,
  baselineCallerEquipment,
  computeTargetDisplay,
  DEFAULT_WEIGHTED_REPS_MIN,
  formatLastResult,
  formatTargetText,
  loadBaselineRest,
  saveBaselineRest,
  type BaselineMoveItem,
  type BaselineSaveResult,
} from './helpers'
import './baseline.css'

const sessionStore = (): Storage | null => {
  try { return typeof sessionStorage !== 'undefined' ? sessionStorage : null } catch { return null }
}

const CheckCircleIcon = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
    <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z" />
  </svg>
)

const TimerIcon = () => (
  <svg width="28" height="28" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" focusable="false">
    <path d="M15 1H9v2h6V1zm-4 13h2V8h-2v6zm8.03-6.61l1.42-1.42c-.43-.51-.9-.99-1.41-1.41l-1.42 1.42A8.962 8.962 0 0012 4c-4.97 0-9 4.03-9 9s4.02 9 9 9a8.994 8.994 0 007.03-14.61zM12 20c-3.87 0-7-3.13-7-7s3.13-7 7-7 7 3.13 7 7-3.13 7-7 7z" />
  </svg>
)

export function BaselineScreen({ param }: { param?: string }) {
  const lang = useLang()
  const library = useLibrary()
  const settings = useSettings()
  const activePlan = useActivePlan()
  const latestById = useLatestBaselines()

  const callerEquipment = useMemo(() => baselineCallerEquipment(param), [param])
  const initialExerciseId = callerEquipment ? null : param?.trim() || null
  const openedSingleMove = initialExerciseId != null

  const [activeExerciseId, setActiveExerciseId] = useState<string | null>(initialExerciseId)
  const [lastResult, setLastResult] = useState<BaselineSaveResult | null>(null)
  const [hold, setHold] = useState<HoldTimer | null>(null)
  const [restEndsAt, setRestEndsAt] = useState<number | null>(() => loadBaselineRest(sessionStore(), Date.now()))
  const [now, setNow] = useState(() => Date.now())
  const [pickerOpen, setPickerOpen] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveFailed, setSaveFailed] = useState(false)

  useEffect(() => {
    setActiveExerciseId(initialExerciseId)
    setLastResult(null)
    setHold(null)
  }, [initialExerciseId])

  const equipment = useMemo(() => {
    const active = expandLegacyEquipment(callerEquipment ?? configuredEquipment(settings))
    active.add('BODYWEIGHT')
    return active
  }, [settings, callerEquipment])
  const moves = useMemo(
    () => buildBaselineMoves(library, settings, activePlan, latestById, equipment),
    [library, settings, activePlan, latestById, equipment],
  )

  const activeMove = useMemo<BaselineMoveItem | null>(() => {
    if (!activeExerciseId) return null
    const listed = moves.find((m) => m.exercise.id === activeExerciseId)
    if (listed) return listed
    const ex = library.find((e) => e.id === activeExerciseId)
    if (!ex) return null
    const unit = weightUnit(settings, ex.unitOverride)
    const rawLatest = latestById.get(ex.id) ?? null
    const latest = rawLatest && rawLatest.kind === ex.kind ? rawLatest : null
    return {
      exercise: ex,
      latest,
      target: latest ? computeTargetDisplay(ex, latest, library, equipment, unit, DEFAULT_WEIGHTED_REPS_MIN) : null,
      unit,
      repsMin: DEFAULT_WEIGHTED_REPS_MIN,
      isSuggested: false,
    }
  }, [activeExerciseId, moves, library, settings, latestById, equipment])

  const holdRef = useRef(hold); holdRef.current = hold
  const restRef = useRef(restEndsAt); restRef.current = restEndsAt
  const hapticsOn = settings.hapticsEnabled !== false
  const hapticsRef = useRef(hapticsOn); hapticsRef.current = hapticsOn
  const lastCueRef = useRef(Number.MIN_SAFE_INTEGER)

  useEffect(() => {
    if (!hold) return
    return keepScreenOn()
  }, [hold != null])

  useEffect(() => {
    if (!hold && restEndsAt == null) return
    const tick = () => {
      const current = Date.now()
      setNow(current)
      const h = holdRef.current
      if (h) {
        const s = holdElapsed(h, current)
        if (s !== lastCueRef.current) {
          lastCueRef.current = s
          const left = h.target - s
          if (s === h.target) alarm(hapticsRef.current)
          else if (s < 0 || (left >= 1 && left <= 3) || s === Math.floor(h.target / 2)) {
            beep(660, 0.1)
            if (hapticsRef.current) buzz(30)
          }
        }
      }
      const end = restRef.current
      if (end != null && current >= end) {
        restRef.current = null
        setRestEndsAt(null)
        saveBaselineRest(sessionStore(), null)
        haptics.check()
      }
    }
    tick()
    const id = window.setInterval(tick, 200)
    const onVis = () => { if (document.visibilityState === 'visible') tick() }
    document.addEventListener('visibilitychange', onVis)
    return () => {
      clearInterval(id)
      document.removeEventListener('visibilitychange', onVis)
    }
  }, [hold != null, restEndsAt])

  const openMove = (id: string) => {
    setActiveExerciseId(id)
    setLastResult(null)
    setSaveFailed(false)
    setRestEndsAt(null)
    saveBaselineRest(sessionStore(), null)
    setHold(null)
  }

  const closeMove = () => {
    setActiveExerciseId(null)
    setLastResult(null)
    setSaveFailed(false)
    setHold(null)
  }

  const retest = () => {
    setLastResult(null)
    setSaveFailed(false)
    setRestEndsAt(null)
    saveBaselineRest(sessionStore(), null)
  }

  const skipRest = () => {
    setRestEndsAt(null)
    saveBaselineRest(sessionStore(), null)
    haptics.tick()
  }

  const handleBack = () => {
    if (hold) { setHold(null); return }
    if (activeMove && !openedSingleMove) { closeMove(); return }
    goBack()
  }

  const persistResult = async (
    move: BaselineMoveItem,
    patch: { reps?: number | null; seconds?: number | null; weightKg?: number | null },
  ) => {
    if (saving) return
    setSaving(true)
    setSaveFailed(false)
    const createdAt = Date.now()
    try {
      const row: BaselineTestRow = {
        exerciseId: move.exercise.id,
        kind: move.exercise.kind,
        reps: patch.reps ?? null,
        seconds: patch.seconds ?? null,
        weightKg: patch.weightKg ?? null,
        createdAt,
      }
      await saveBaselineTest(row)
      haptics.check()
      const display = computeTargetDisplay(
        move.exercise, row, library, equipment, move.unit, move.repsMin,
      )
      setLastResult({ exercise: move.exercise, test: row, target: display, unit: move.unit })
      const ends = createdAt + BASELINE_REST_SECONDS * 1000
      setRestEndsAt(ends)
      saveBaselineRest(sessionStore(), ends)
      setNow(createdAt)
    } catch {
      setSaveFailed(true)
    } finally {
      setSaving(false)
    }
  }

  const startHoldTimer = (move: BaselineMoveItem) => {
    unlockAudio()
    setRestEndsAt(null)
    saveBaselineRest(sessionStore(), null)
    lastCueRef.current = Number.MIN_SAFE_INTEGER
    const current = Date.now()
    setNow(current)
    setHold({
      setId: 0,
      exerciseName: move.exercise.name,
      target: BASELINE_HOLD_BENCHMARK_SECONDS,
      startsAt: current + READY_SECONDS * 1000,
    })
  }

  const stopHoldTimer = () => {
    const h = holdRef.current
    if (!h || !activeMove) return
    setHold(null)
    const held = holdSaved(h, Date.now())
    if (held <= 0) {
      setLastResult({
        exercise: activeMove.exercise,
        test: {
          exerciseId: activeMove.exercise.id,
          kind: 'HOLD',
          reps: null,
          seconds: 0,
          weightKg: null,
          createdAt: Date.now(),
        },
        target: { kind: 'HOLD_TOO_SHORT' },
        unit: activeMove.unit,
      })
      return
    }
    void persistResult(activeMove, { seconds: held })
  }

  const resultForActive = lastResult && activeMove && lastResult.exercise.id === activeMove.exercise.id
    ? lastResult
    : null

  return (
    <div class="shell bl-shell">
      <header class="bl-head">
        <button type="button" class="icon-button back" aria-label={t('cd_back')} onClick={handleBack}>
          <BackChevron />
        </button>
        <h1 class="bl-head-title">{activeMove ? activeMove.exercise.name : t('baseline_title')}</h1>
      </header>

      <div class="scroll">
        <div class="bl-content">
          {saveFailed && <p class="bl-err" role="alert">{t('baseline_save_failed')}</p>}

          {activeMove ? (
            resultForActive ? (
              <BaselineResultStep
                result={resultForActive}
                lang={lang}
                restEndsAt={restEndsAt}
                now={now}
                openedSingleMove={openedSingleMove}
                onSkipRest={skipRest}
                onRetest={retest}
                onTestEasier={(id) => openMove(id)}
                onBackToList={closeMove}
                onDoneSingle={goBack}
              />
            ) : (
              <BaselineTestMoveStep
                key={activeMove.exercise.id}
                move={activeMove}
                lang={lang}
                saving={saving}
                onStartHold={() => startHoldTimer(activeMove)}
                onSaveReps={(reps) => void persistResult(activeMove, { reps: Math.max(0, Math.min(999, Math.trunc(reps))) })}
                onSaveHold={(seconds) => void persistResult(activeMove, { seconds: Math.max(0, Math.min(3600, Math.trunc(seconds))) })}
                onSaveWeighted={(displayWeight, reps) => {
                  const weightKg = Math.max(0, Math.min(999, toKg(activeMove.unit, displayWeight)))
                  void persistResult(activeMove, { weightKg, reps: Math.max(1, Math.min(999, Math.trunc(reps))) })
                }}
              />
            )
          ) : (
            <>
              <p class="bl-intro">{t('baseline_intro')}</p>
              {moves.map((move) => (
                <BaselineMoveRow key={move.exercise.id} move={move} lang={lang} onClick={() => openMove(move.exercise.id)} />
              ))}
              <Button variant="tonal" block icon={<AddIcon />} onClick={() => setPickerOpen(true)}>
                {t('baseline_test_another')}
              </Button>
            </>
          )}
        </div>
      </div>

      {restEndsAt != null && !activeMove && (
        <div class="bl-rest-dock">
          <BaselineRestBar secondsLeft={restLeft(restEndsAt, now)} onSkip={skipRest} />
        </div>
      )}

      {hold && (
        <HoldOverlay
          hold={hold}
          now={now}
          onStop={stopHoldTimer}
          onCancel={() => setHold(null)}
        />
      )}

      <ExercisePicker
        open={pickerOpen}
        title={t('baseline_picker_title')}
        library={library}
        userEquipment={equipment}
        showAllMovesToggle
        onPick={(ex) => {
          setPickerOpen(false)
          openMove(ex.id)
        }}
        onClose={() => setPickerOpen(false)}
      />
    </div>
  )
}

function BaselineMoveRow({ move, lang, onClick }: { move: BaselineMoveItem; lang: Lang; onClick: () => void }) {
  const lastText = formatLastResult(move.latest, move.exercise.kind, move.unit, lang)
  const targetText = lastText ? formatTargetText(move.target, lang) : null
  const muscles = musclesLabel(move.exercise)
  return (
    <Card onClick={onClick}>
      <div class="bl-moverow">
        <ExerciseThumbnail exercise={move.exercise} />
        <div class="bl-move-text">
          <span class="bl-move-name">{move.exercise.name}</span>
          {lastText ? (
            <>
              <span class="bl-move-sub">{lastText}</span>
              {targetText && <span class="bl-move-target">{targetText}</span>}
            </>
          ) : (
            <span class="bl-move-sub">{muscles || t('baseline_not_tested')}</span>
          )}
        </div>
        {move.latest && <span class="bl-move-check"><CheckCircleIcon /></span>}
        <span class="bl-move-chev"><ForwardChevron size={20} /></span>
      </div>
    </Card>
  )
}

function BaselineTestMoveStep({
  move,
  lang,
  saving,
  onStartHold,
  onSaveReps,
  onSaveHold,
  onSaveWeighted,
}: {
  move: BaselineMoveItem
  lang: Lang
  saving: boolean
  onStartHold: () => void
  onSaveReps: (reps: number) => void
  onSaveHold: (seconds: number) => void
  onSaveWeighted: (displayWeight: number, reps: number) => void
}) {
  const ex = move.exercise
  const arabic = lang === 'ar'
  const [mode, setMode] = useState<'guided' | 'manual'>('guided')
  const [count, setCount] = useState(0)
  const [manualReps, setManualReps] = useState(move.latest?.reps ?? 0)
  const [manualSeconds, setManualSeconds] = useState(
    move.latest?.seconds != null && move.latest.seconds > 0 ? move.latest.seconds : 30,
  )
  const [displayWeight, setDisplayWeight] = useState(
    move.latest?.weightKg != null && move.latest.weightKg > 0
      ? Math.round(fromKg(move.unit, move.latest.weightKg) * 10) / 10
      : 0,
  )
  const [weightedReps, setWeightedReps] = useState(
    move.latest?.reps != null && move.latest.reps > 0 ? move.latest.reps : 8,
  )

  const muscles = musclesLabel(ex)
  const modeOptions = [
    { value: 'guided' as const, label: t('baseline_mode_guided') },
    { value: 'manual' as const, label: t('baseline_mode_manual') },
  ]

  return (
    <>
      {muscles && <p class="bl-muted">{muscles}</p>}

      {ex.kind === 'REPS' && (
        <>
          <Segmented label={t('baseline_reps_label')} options={modeOptions} value={mode} onChange={setMode} />
          {mode === 'guided' ? (
            <>
              <Card>
                <div class="bl-center-card">
                  <button
                    type="button"
                    class="bl-counter-btn"
                    aria-label={t('baseline_reps_counter_cd', count)}
                    onClick={() => {
                      haptics.check()
                      setCount((c) => (c < 999 ? c + 1 : c))
                    }}
                  >
                    <span class="bl-counter-num">{count}</span>
                    <span class="bl-counter-cap">{t('baseline_tap_after_rep')}</span>
                  </button>
                  {count > 0 && (
                    <Button
                      variant="text"
                      icon={<RemoveIcon size={18} />}
                      onClick={() => {
                        haptics.tick()
                        setCount((c) => Math.max(0, c - 1))
                      }}
                    >
                      {t('baseline_undo_rep')}
                    </Button>
                  )}
                </div>
              </Card>
              <Button block disabled={saving} loading={saving} onClick={() => onSaveReps(count)}>
                {t('hold_done')}
              </Button>
            </>
          ) : (
            <>
              <Card>
                <div class="bl-field-block">
                  <span class="bl-field-label">{t('baseline_reps_label')}</span>
                  <NumberStepper
                    label={t('stepper_reps')}
                    value={manualReps}
                    min={0}
                    max={999}
                    step={1}
                    unit={t('unit_reps')}
                    format={(n) => plural('rx_reps', n, n)}
                    onChange={(v) => setManualReps(Math.max(0, Math.min(999, Math.trunc(v))))}
                  />
                </div>
              </Card>
              <Button block disabled={saving} loading={saving} onClick={() => onSaveReps(manualReps)}>
                {t('action_save')}
              </Button>
            </>
          )}
        </>
      )}

      {ex.kind === 'HOLD' && (
        <>
          <Segmented label={t('baseline_seconds_label')} options={modeOptions} value={mode} onChange={setMode} />
          {mode === 'guided' ? (
            <>
              <Card>
                <div class="bl-center-card">
                  <span class="bl-timer-badge" aria-hidden="true"><TimerIcon /></span>
                  <p class="bl-muted">{t('baseline_hold_guide_hint')}</p>
                </div>
              </Card>
              <Button block onClick={onStartHold}>{t('baseline_start_hold')}</Button>
            </>
          ) : (
            <>
              <Card>
                <div class="bl-field-block">
                  <span class="bl-field-label">{t('baseline_seconds_label')}</span>
                  <NumberStepper
                    label={t('stepper_hold_time')}
                    value={manualSeconds}
                    min={0}
                    max={3600}
                    step={5}
                    unit={t('unit_s')}
                    onChange={(v) => setManualSeconds(Math.max(0, Math.min(3600, Math.trunc(v))))}
                  />
                </div>
              </Card>
              <Button block disabled={saving || manualSeconds <= 0} loading={saving} onClick={() => onSaveHold(manualSeconds)}>
                {t('action_save')}
              </Button>
            </>
          )}
        </>
      )}

      {ex.kind === 'WEIGHTED' && (() => {
        const u = unitLabel(move.unit, arabic)
        return (
          <>
            <p class="bl-muted">{t('baseline_weighted_hint')}</p>
            <Card>
              <div class="bl-field-stack">
                <div class="bl-field-block">
                  <span class="bl-field-label">{t('baseline_weight_label')}</span>
                  <NumberStepper
                    label={t('stepper_weight')}
                    value={displayWeight}
                    min={0}
                    max={Math.round(fromKg(move.unit, 999))}
                    step={loadStep(move.unit)}
                    decimals={1}
                    unit={u}
                    format={(n) => `${Math.round(n * 10) / 10} ${u}`}
                    onChange={setDisplayWeight}
                  />
                </div>
                <div class="bl-field-block">
                  <span class="bl-field-label">{t('baseline_reps_label')}</span>
                  <NumberStepper
                    label={t('stepper_reps')}
                    value={weightedReps}
                    min={1}
                    max={50}
                    step={1}
                    unit={t('unit_reps')}
                    format={(n) => plural('rx_reps', n, n)}
                    onChange={(v) => setWeightedReps(Math.max(1, Math.min(50, Math.trunc(v))))}
                  />
                </div>
              </div>
            </Card>
            <Button
              block
              disabled={saving || displayWeight <= 0 || weightedReps <= 0}
              loading={saving}
              onClick={() => onSaveWeighted(displayWeight, weightedReps)}
            >
              {t('action_save')}
            </Button>
          </>
        )
      })()}
    </>
  )
}

function BaselineResultStep({
  result,
  lang,
  restEndsAt,
  now,
  openedSingleMove,
  onSkipRest,
  onRetest,
  onTestEasier,
  onBackToList,
  onDoneSingle,
}: {
  result: BaselineSaveResult
  lang: Lang
  restEndsAt: number | null
  now: number
  openedSingleMove: boolean
  onSkipRest: () => void
  onRetest: () => void
  onTestEasier: (exerciseId: string) => void
  onBackToList: () => void
  onDoneSingle: () => void
}) {
  const recorded = formatLastResult(result.test, result.exercise.kind, result.unit, lang)
  const targetText = formatTargetText(result.target, lang)
  const tgt = result.target

  return (
    <>
      <Card>
        <div class="bl-result-stack">
          <h2 class="bl-result-title">{result.exercise.name}</h2>
          {recorded && <p class="bl-muted">{recorded}</p>}
          {(tgt.kind === 'REPS' || tgt.kind === 'HOLD' || tgt.kind === 'WEIGHT') && targetText && (
            <div class="bl-target-box">{targetText}</div>
          )}
          {tgt.kind === 'EASIER_VARIATION' && (
            <>
              <p class="bl-result-msg">
                {tgt.easierExercise
                  ? t('baseline_try_easier', tgt.easierExercise.name)
                  : t('baseline_try_easier_generic')}
              </p>
              {tgt.easierExercise && (
                <Button block onClick={() => onTestEasier(tgt.easierExercise!.id)}>
                  {t('baseline_test_move_named', tgt.easierExercise.name)}
                </Button>
              )}
            </>
          )}
          {(tgt.kind === 'TOO_LIGHT' || tgt.kind === 'WEIGHT_NO_TARGET' || tgt.kind === 'HOLD_TOO_SHORT') && (
            <>
              {targetText && <p class="bl-muted">{targetText}</p>}
              <Button variant="tonal" block onClick={onRetest}>{t('baseline_retest')}</Button>
            </>
          )}
        </div>
      </Card>

      {restEndsAt != null && (
        <BaselineRestBar secondsLeft={restLeft(restEndsAt, now)} onSkip={onSkipRest} />
      )}

      {openedSingleMove ? (
        <>
          <Button block onClick={onDoneSingle}>{t('hold_done')}</Button>
          <Button variant="text" block onClick={onBackToList}>{t('baseline_see_all_moves')}</Button>
        </>
      ) : (
        <Button block onClick={onBackToList}>{t('baseline_back_to_list')}</Button>
      )}
    </>
  )
}

function BaselineRestBar({ secondsLeft, onSkip }: { secondsLeft: number; onSkip: () => void }) {
  const pct = Math.max(0, Math.min(100, (secondsLeft / BASELINE_REST_SECONDS) * 100))
  return (
    <div class="bl-rest" role="timer" aria-label={t('baseline_rest_between')}>
      <div class="bl-rest-row">
        <div class="bl-rest-meta">
          <span class="bl-rest-label">{t('baseline_rest_between')}</span>
          <span class="bl-rest-clock">{clock(secondsLeft)}</span>
        </div>
        <Button variant="text" onClick={onSkip}>{t('live_skip')}</Button>
      </div>
      <div class="bl-rest-bar"><div style={{ inlineSize: `${pct}%` }} /></div>
    </div>
  )
}
