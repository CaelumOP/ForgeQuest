import type { RefObject } from 'preact'
import { useEffect, useMemo, useRef, useState } from 'preact/hooks'
import { plural, t, useLang } from '../../i18n'
import type { ExerciseRow } from '../../data/db'
import { useProgress } from '../../data/progress'
import { configuredEquipment, weightUnit } from '../../data/settings'
import { startSession, useOpenSession, type PlanDayWithItems } from '../../data/workouts'
import { dayOfWeek, todayEpochDay } from '../../domain/dates'
import type { SetEntity } from '../../domain/entities'
import { bestSetSeries } from '../../domain/trends'
import type { WeightUnit } from '../../domain/units'
import { Button, Card, Sheet } from '../kit'
import { BoltIcon, EditIcon, HistoryIcon, PlayCircleIcon, PlayIcon, RepeatIcon, Sparkline } from './glyphs'
import {
  blockLabel, dayName, dayShort, fitExerciseRows, openFormVideo, prescription, ROW_HEIGHT_2COL,
} from './helpers'
import { BodyMap } from '../progress/BodyMap'
import type { Equipment } from '../../domain/exercise-swap-engine'
import type { Muscle } from '../../domain/muscle'
import { MuscleSheet } from './MuscleSheet'
import './workouts.css'

const go = (hash: string) => { location.hash = hash }
const MAP_MAX_FIGURES_HEIGHT = 300

export function WorkoutsScreen(_: { param?: string }) {
  const lang = useLang()
  const s = useProgress()
  const open = useOpenSession()
  const today = dayOfWeek(todayEpochDay())
  const [selected, setSelected] = useState(today)
  const [starting, setStarting] = useState(false)
  const [dayDetailsOpen, setDayDetailsOpen] = useState(false)
  const [muscle, setMuscle] = useState<Muscle | null>(null)
  const figuresRef = useRef<HTMLDivElement>(null)

  const library = useMemo(() => (s ? [...s.exercises.values()] : []), [s?.exercises])
  const planIds = useMemo(() => new Set(s?.plan?.days.flatMap((d) => d.items.map((i) => i.exerciseId)) ?? []), [s?.plan])
  const equipment = useMemo(() => (s ? configuredEquipment(s.settings) : new Set<Equipment>()), [s?.settings])

  // Best set per session for every exercise on the plan, so the plan reads like a progress board.
  const trends = useMemo(() => {
    const out = new Map<string, number[]>()
    if (!s) return out
    const sessionDay = new Map(s.sessions.map((x) => [x.id!, x.epochDay]))
    const by = new Map<string, typeof s.doneSets>()
    for (const set of s.doneSets) by.set(set.exerciseId, [...(by.get(set.exerciseId) ?? []), set])
    for (const [id, sets] of by) {
      out.set(id, bestSetSeries(sets as SetEntity[], s.exercises.get(id)?.kind ?? 'REPS', sessionDay).slice(-12).map((p) => p.value))
    }
    return out
  }, [s?.doneSets, s?.sessions, s?.exercises])

  const plan = s?.plan
  const day = plan?.days.find((d) => d.day.dayOfWeek === selected)
  const isRest = !!plan && (!day || day.day.isRest)

  if (!s) return <div class="content wk wk-one" />
  const unit = weightUnit(s.settings)
  const isToday = selected === today
  const arabic = lang === 'ar'

  const start = async (d: PlanDayWithItems | null) => {
    if (starting) return
    setStarting(true)
    try {
      await startSession(d && !d.day.isRest ? d : null)
      go('#/live')
    } finally { setStarting(false) }
  }

  const heroTag = open ? t('today_in_progress')
    : !plan || isToday ? t('day_today')
    : dayName(selected, lang)

  const heroTitle = open ? open.title
    : !plan ? t('today_no_plan')
    : isRest ? t('today_rest_day')
    : (day?.day.title ?? '')

  const heroCount = plan && !isRest && day
    ? plural('coach_exercises', day.items.length, day.items.length)
    : ''

  const restHint = isRest ? (day?.day.note.trim() || t('workouts_rest_hint')) : ''

  return (
    <div class="content wk wk-one">
      <div class="wk-head">
        <div class="wk-head-text">
          {/* The top bar already says Workouts: this line names the plan. */}
          <h1 class="wk-plan">{plan?.plan.name ?? t('today_no_plan')}</h1>
        </div>
        <a class="wk-iconlink" href="#/history" aria-label={t('cd_workout_history')}><HistoryIcon /></a>
        {plan && <a class="wk-iconlink" href="#/plan" aria-label={t('cd_edit_plan')}><EditIcon /></a>}
      </div>

      {plan && (
        <div class="wk-strip" role="tablist" aria-label={t('workouts_web_days')}>
          {[1, 2, 3, 4, 5, 6, 7].map((dow) => {
            const rest = plan.days.find((d) => d.day.dayOfWeek === dow)?.day.isRest ?? true
            const sel = dow === selected
            return (
              <button
                key={dow} type="button" role="tab" aria-selected={sel} class={`wk-day${dow === today ? ' today' : ''}`}
                aria-label={t(rest ? 'day_cd_rest' : 'day_cd_training', dayName(dow, lang))}
                onClick={() => setSelected(dow)}
              >
                <span>{dayShort(dow, lang)}</span>
                <span class={`wk-day-dot${rest ? ' rest' : ''}`} />
              </button>
            )
          })}
        </div>
      )}

      <Card padded={false} class="wk-hero">
        <div class="wk-hero-top">
          <div class="wk-hero-lead">
            <span class="wk-hero-tag">{heroTag}</span>
            <span class="wk-hero-title">{heroTitle}</span>
          </div>
          {heroCount && <span class="wk-hero-count">{heroCount}</span>}
        </div>
        <BodyMap
          recovery={s.recovery}
          bodyType={s.settings.bodyType}
          onSeeExercises={setMuscle}
          figuresRef={figuresRef}
        />
      </Card>

      {!plan ? (
        <div class="wk-noplan-line wk-clip">{t('pb_entry_info')}</div>
      ) : isRest ? (
        <Card padded={false} class="wk-rest-card">
          <div class="wk-s wk-clip">{restHint}</div>
          <a class="wk-rest-link" href="#/plan">{t('workouts_add_exercises')}</a>
        </Card>
      ) : day ? (
        <ExerciseGlanceCard
          day={day}
          exercises={s.exercises}
          unit={unit}
          arabic={arabic}
          figuresRef={figuresRef}
          onOpenDetails={() => setDayDetailsOpen(true)}
        />
      ) : null}

      <div class="wk-go">
        {open ? (
          <Button block icon={<PlayIcon />} onClick={() => go('#/live')}>{t('action_resume')}</Button>
        ) : !plan ? (
          <>
            <Button class="wk-go-main" onClick={() => go('#/plan-builder')}>{t('action_build_plan')}</Button>
            <button
              type="button"
              class="kit-btn kit-btn-tonal wk-go-quick"
              aria-label={t('workouts_quick')}
              disabled={starting}
              onClick={() => start(null)}
            >
              <BoltIcon size={18} />
              <span class="kit-btn-label">{t('workouts_quick_short')}</span>
            </button>
          </>
        ) : isRest ? (
          <Button variant="tonal" block icon={<BoltIcon size={18} />} loading={starting} onClick={() => start(null)}>
            {t('workouts_quick')}
          </Button>
        ) : (
          <>
            <Button class="wk-go-main" loading={starting} icon={<PlayIcon />} onClick={() => start(day ?? null)}>
              {s.workoutDoneToday && isToday ? t('today_train_again') : isToday ? t('workouts_start') : t('workouts_start_this')}
            </Button>
            <button
              type="button"
              class="kit-btn kit-btn-tonal wk-go-quick"
              aria-label={t('workouts_quick')}
              disabled={starting}
              onClick={() => start(null)}
            >
              <BoltIcon size={18} />
              <span class="kit-btn-label">{t('workouts_quick_short')}</span>
            </button>
          </>
        )}
      </div>

      <Sheet
        open={dayDetailsOpen && !!day && !day.day.isRest}
        onClose={() => setDayDetailsOpen(false)}
        title={day?.day.title ?? ''}
      >
        {day && !day.day.isRest && (
          <DayDetailsContent
            day={day}
            exercises={s.exercises}
            unit={unit}
            arabic={arabic}
            trends={trends}
            onClose={() => setDayDetailsOpen(false)}
          />
        )}
      </Sheet>

      <MuscleSheet
        muscle={muscle}
        library={library}
        planIds={planIds}
        equipment={equipment}
        recovery={muscle ? s.recovery.get(muscle) : undefined}
        onClose={() => setMuscle(null)}
      />
    </div>
  )
}

function ExerciseGlanceCard({ day, exercises, unit, arabic, figuresRef, onOpenDetails }: {
  day: PlanDayWithItems
  exercises: Map<string, ExerciseRow>
  unit: WeightUnit
  arabic: boolean
  figuresRef: RefObject<HTMLDivElement | null>
  onOpenDetails: () => void
}) {
  const rowsRef = useRef<HTMLDivElement>(null)
  const [availHeight, setAvailHeight] = useState(ROW_HEIGHT_2COL * 2)

  useEffect(() => {
    const rowsEl = rowsRef.current
    const figsEl = figuresRef.current
    if (!rowsEl || !figsEl || typeof ResizeObserver === 'undefined') return
    const update = () => {
      const shared = figsEl.clientHeight + rowsEl.clientHeight
      if (shared > 0) setAvailHeight(Math.max(ROW_HEIGHT_2COL * 2, shared - MAP_MAX_FIGURES_HEIGHT))
    }
    update()
    // Re-measure on the next frame: changing layout inside the observer callback makes the browser report a
    // "ResizeObserver loop" error, which the crash recorder would log as a crash.
    let frame = 0
    const ro = new ResizeObserver(() => { cancelAnimationFrame(frame); frame = requestAnimationFrame(update) })
    ro.observe(rowsEl)
    ro.observe(figsEl)
    return () => { cancelAnimationFrame(frame); ro.disconnect() }
  }, [day.items.length, figuresRef])

  const blocks: [string, typeof day.items][] = []
  for (const item of day.items) {
    const last = blocks[blocks.length - 1]
    if (last && last[0] === item.block) last[1].push(item)
    else blocks.push([item.block, [item]])
  }
  const blockSummary = blocks.map(([b, items]) => `${blockLabel(b)} ${items.length}`).join(' · ')
  const fit = fitExerciseRows(availHeight, day.items.length)
  const visibleItems = day.items.slice(0, fit.visibleCount)

  return (
    <Card padded={false} class="wk-list-card" onClick={onOpenDetails}>
      <div class="wk-lh">
        <span class="wk-clip">{blockSummary}</span>
        <span class="wk-lh-details">
          <span>{t('workouts_details')}</span>
          <span class="wk-lh-chev" aria-hidden="true">›</span>
        </span>
      </div>
      <div class="wk-rows wk-two" style={{ '--wk-rows': fit.rows } as Record<string, number>} ref={rowsRef}>
        {visibleItems.map((item) => {
          const ex = exercises.get(item.exerciseId)
          const isWarmUp = item.block === 'Warm-up'
          return (
            <div class={`wk-r${isWarmUp ? ' w' : ''}`} key={item.id}>
              <b>{ex?.name ?? item.exerciseId}</b>
              <span>{prescription(item, ex?.kind, unit, arabic)}</span>
            </div>
          )
        })}
        {fit.hiddenCount > 0 && (
          <div class="wk-r wk-r-more">
            <b>{plural('workouts_more_n', fit.hiddenCount, fit.hiddenCount)}</b>
          </div>
        )}
      </div>
    </Card>
  )
}

function DayDetailsContent({ day, exercises, unit, arabic, trends, onClose }: {
  day: PlanDayWithItems
  exercises: Map<string, ExerciseRow>
  unit: WeightUnit
  arabic: boolean
  trends: Map<string, number[]>
  onClose: () => void
}) {
  const blocks: [string, typeof day.items][] = []
  for (const item of day.items) {
    const last = blocks[blocks.length - 1]
    if (last && last[0] === item.block) last[1].push(item)
    else blocks.push([item.block, [item]])
  }
  return (
    <div class="wk-details-sheet">
      {day.day.note.trim() && <p class="wk-s">{day.day.note}</p>}
      {blocks.map(([block, items], bi) => (
        <div key={bi}>
          <div class="wk-block">
            <span>{blockLabel(block)}</span>
            {items[0].circuit && <small><RepeatIcon size={14} />{plural('workouts_circuit_rounds', items[0].sets)}</small>}
          </div>
          {items.map((item) => {
            const ex = exercises.get(item.exerciseId)
            const pts = trends.get(item.exerciseId)
            return (
              <div class="wk-item" key={item.id}>
                <a class="wk-item-main" href={`#/exercise/${encodeURIComponent(item.exerciseId)}`} onClick={onClose}>
                  <span class="wk-item-name">{ex?.name ?? item.exerciseId}</span>
                  <span class="wk-s">{prescription(item, ex?.kind, unit, arabic)}</span>
                </a>
                {pts && pts.length >= 2 && <Sparkline points={pts} label={t('workouts_web_trend_cd', ex?.name ?? item.exerciseId)} />}
                <button
                  type="button"
                  class="kit-iconbtn wk-ibtn blue"
                  aria-label={`${t('cd_watch_form')}: ${ex?.name ?? item.exerciseId}`}
                  onClick={() => openFormVideo(ex)}
                >
                  <PlayCircleIcon />
                </button>
              </div>
            )
          })}
        </div>
      ))}
    </div>
  )
}
