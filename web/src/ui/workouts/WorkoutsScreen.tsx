import { useMemo, useState } from 'preact/hooks'
import { getLang, plural, t, useLang } from '../../i18n'
import type { ExerciseRow } from '../../data/db'
import { useProgress } from '../../data/progress'
import { configuredEquipment, weightUnit } from '../../data/settings'
import { startSession, useOpenSession, type PlanDayWithItems } from '../../data/workouts'
import { dayOfWeek, todayEpochDay } from '../../domain/dates'
import type { SetEntity } from '../../domain/entities'
import { bestSetSeries } from '../../domain/trends'
import type { WeightUnit } from '../../domain/units'
import { Button, Card, EmptyState } from '../kit'
import { BedIcon, EditIcon, HistoryIcon, PlayCircleIcon, PlayIcon, RepeatIcon, Sparkline } from './glyphs'
import { blockLabel, dayName, openFormVideo, prescription } from './helpers'
import { BodyMap } from '../progress/BodyMap'
import type { Equipment } from '../../domain/exercise-swap-engine'
import type { Muscle } from '../../domain/muscle'
import { listSize, muscleExercises } from '../../domain/muscle-exercises'
import { MuscleSheet } from './MuscleSheet'
import './workouts.css'

const go = (hash: string) => { location.hash = hash }

export function WorkoutsScreen(_: { param?: string }) {
  const lang = useLang()
  const s = useProgress()
  const open = useOpenSession()
  const today = dayOfWeek(todayEpochDay())
  const [selected, setSelected] = useState(today)
  const [starting, setStarting] = useState(false)
  const [muscle, setMuscle] = useState<Muscle | null>(null)
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

  if (!s) return <div class="content wk" />
  const plan = s.plan
  const day = plan?.days.find((d) => d.day.dayOfWeek === selected)
  const unit = weightUnit(s.settings)

  const start = async (d: PlanDayWithItems | null) => {
    if (starting) return
    setStarting(true)
    try {
      await startSession(d && !d.day.isRest ? d : null)
      go('#/live')
    } finally { setStarting(false) }
  }

  return (
    <div class="content wk">
      <div class="wk-head">
        <div class="wk-head-text">
          <h1 class="wk-title">{t('workouts_title')}</h1>
          <div class="wk-sub">{plan?.plan.name ?? t('today_no_plan')}</div>
        </div>
        <a class="wk-iconlink" href="#/history" aria-label={t('cd_workout_history')}><HistoryIcon /></a>
        <a class="wk-iconlink" href="#/plan" aria-label={t('cd_edit_plan')}><EditIcon /></a>
      </div>

      {open && (
        <Card>
          <div class="wk-live">
            <span class="wk-dot" aria-hidden="true" />
            <div class="wk-grow">
              <div class="wk-t">{t('today_in_progress')}</div>
              <div class="wk-s">{open.title}</div>
            </div>
            <Button onClick={() => go('#/live')}>{t('action_resume')}</Button>
          </div>
        </Card>
      )}

      <div class="wk-strip" role="tablist" aria-label={t('workouts_web_days')}>
        {[1, 2, 3, 4, 5, 6, 7].map((dow) => {
          const rest = plan?.days.find((d) => d.day.dayOfWeek === dow)?.day.isRest ?? true
          const sel = dow === selected
          return (
            <button
              key={dow} type="button" role="tab" aria-selected={sel} class={`wk-day${dow === today ? ' today' : ''}`}
              aria-label={t(rest ? 'day_cd_rest' : 'day_cd_training', dayName(dow, lang))}
              onClick={() => setSelected(dow)}
            >
              <span>{dayName(dow, lang, 'short')}</span>
              <span class={`wk-day-dot${rest ? ' rest' : ''}`} />
            </button>
          )
        })}
      </div>

      {!plan ? (
        <Card>
          <EmptyState
            icon={<EditIcon size={32} />} title={t('today_no_plan')} body={t('today_no_plan_hint')}
            action={<div class="wk-empty-btn"><Button onClick={() => go('#/plan')}>{t('workouts_set_up_plan')}</Button></div>}
          />
        </Card>
      ) : (
        <DayCard
          day={day} exercises={s.exercises} isToday={selected === today} canStart={!open} starting={starting}
          unit={unit} trends={trends} onStart={() => start(day ?? null)}
        />
      )}

      <Card>
        <h2 class="wk-card-title">{t('recovery_title')}</h2>
        <div class="wk-sub">{t('recovery_subtitle')}</div>
        <BodyMap recovery={s.recovery} bodyType={s.settings.bodyType} onSeeExercises={setMuscle}
          exerciseCount={(m) => listSize(muscleExercises(m, library, planIds, equipment))} />
      </Card>
      <MuscleSheet muscle={muscle} library={library} planIds={planIds} equipment={equipment}
        recovery={muscle ? s.recovery.get(muscle) : undefined} onClose={() => setMuscle(null)} />

      <Button variant="tonal" block icon={<PlayIcon />} onClick={() => start(null)}>{t('workouts_quick')}</Button>
    </div>
  )
}

function DayCard({ day, exercises, isToday, canStart, starting, unit, trends, onStart }: {
  day: PlanDayWithItems | undefined; exercises: Map<string, ExerciseRow>; isToday: boolean; canStart: boolean; starting: boolean
  unit: WeightUnit; trends: Map<string, number[]>; onStart: () => void
}) {
  const arabic = getLang() === 'ar'
  if (!day || day.day.isRest) {
    return (
      <Card>
        <EmptyState
          icon={<BedIcon size={32} />} title={t('today_rest_day')} body={day?.day.note.trim() || t('workouts_rest_hint')}
          action={<div class="wk-empty-btn"><Button variant="tonal" onClick={() => { location.hash = '#/plan' }}>{t('workouts_add_exercises')}</Button></div>}
        />
      </Card>
    )
  }
  const blocks: [string, typeof day.items][] = []
  for (const item of day.items) {
    const last = blocks[blocks.length - 1]
    if (last && last[0] === item.block) last[1].push(item)
    else blocks.push([item.block, [item]])
  }
  return (
    <Card>
      <h2 class="wk-card-title">{day.day.title}</h2>
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
                <a class="wk-item-main" href={`#/exercise/${encodeURIComponent(item.exerciseId)}`}>
                  <span class="wk-item-name">{ex?.name ?? item.exerciseId}</span>
                  <span class="wk-s">{prescription(item, ex?.kind, unit, arabic)}</span>
                </a>
                {pts && pts.length >= 2 && <Sparkline points={pts} label={t('workouts_web_trend_cd', ex?.name ?? item.exerciseId)} />}
                <button type="button" class="kit-iconbtn wk-ibtn blue" aria-label={`${t('cd_watch_form')}: ${ex?.name ?? item.exerciseId}`} onClick={() => openFormVideo(ex)}>
                  <PlayCircleIcon />
                </button>
              </div>
            )
          })}
        </div>
      ))}
      {canStart && (
        <div style={{ marginBlockStart: '16px' }}>
          <Button block loading={starting} icon={<PlayIcon />} onClick={onStart}>{isToday ? t('workouts_start') : t('workouts_start_this')}</Button>
        </div>
      )}
    </Card>
  )
}
