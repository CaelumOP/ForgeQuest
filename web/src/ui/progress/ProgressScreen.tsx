import { useMemo, useState } from 'preact/hooks'
import { getLang, plural, t, intlLocale } from '../../i18n'
import { useProgress, type ProgressSnapshot } from '../../data/progress'
import { weightUnit } from '../../data/settings'
import { trainingDays } from '../../data/workouts'
import { todayEpochDay } from '../../domain/dates'
import type { SetEntity, SessionEntity } from '../../domain/entities'
import type { ExerciseKind } from '../../domain/muscle'
import {
  TREND_RANGE_DAYS, TREND_RANGES, bestSetSeries, consistencyGrid, lifetime, movingAverage, percentChange, proteinDaysPerWeek,
  thenNow, topExercises, trainingLoad, trendRangeFrom, weeklyCounts, weeklySets, type TrendPoint, type TrendRange,
} from '../../domain/trends'
import { fromKg, toKg, unitLabel, weightNumber, formatWeight, type WeightUnit } from '../../domain/units'
import { Card, Chip, ChipGroup, EmptyState, LineChart, Ring } from '../kit'
import { carbWeeks, formatDelta, formatValue, grouped } from './helpers'
import './progress.css'

const RANGE_KEYS: Record<TrendRange, string> = {
  M1: 'progress_range_1m', M3: 'progress_range_3m', M6: 'progress_range_6m', Y1: 'progress_range_1y', ALL: 'progress_range_all',
}
const dateLabel = (day: number) =>
  new Intl.DateTimeFormat(intlLocale(), { month: 'short', day: 'numeric', timeZone: 'UTC' }).format(new Date(day * 86_400_000))

/** Everything shown for one range, built only from the domain functions. */
function derive(s: ProgressSnapshot, range: TrendRange) {
  const today = todayEpochDay()
  const from = trendRangeFrom(range, today)
  const sessions = s.sessions as SessionEntity[]
  const sets = s.doneSets as SetEntity[]
  const sessionDay = new Map(s.sessions.map((x) => [x.id!, x.epochDay]))
  const workoutDays = new Set(s.sessions.map((x) => x.epochDay))
  const firstDay = s.sessions.length ? Math.min(...s.sessions.map((x) => x.epochDay)) : today
  const days = TREND_RANGE_DAYS[range]
  const weeksInRange = Math.min(52, Math.max(4, days != null ? Math.trunc((days + 6) / 7) : Math.trunc((today - firstDay) / 7) + 1))
  const sets12 = weeklySets(sets, sessionDay, 12, today)
  const topIds = topExercises(sets, 6)
  const series = new Map<string, TrendPoint[]>(topIds.map((id) => {
    const kind: ExerciseKind = s.exercises.get(id)?.kind ?? 'REPS'
    return [id, bestSetSeries(sets.filter((x) => x.exerciseId === id), kind, sessionDay)]
  }))
  const recordDays = new Set(sets.filter((x) => x.isPr).flatMap((x) => (sessionDay.has(x.sessionId) ? [sessionDay.get(x.sessionId)!] : [])))
  const weightPoints: TrendPoint[] = s.weights.map((w) => ({ epochDay: w.epochDay, value: w.kg, isRecord: false }))
  return {
    today, from, sessionDay, workoutDays, weeklySets: sets12, topIds, series,
    weeklyWorkouts: weeklyCounts(workoutDays, weeksInRange, today),
    load: trainingLoad(sets12),
    grid: consistencyGrid(workoutDays, recordDays, weeksInRange, today),
    weightPoints, weightAvg: movingAverage(weightPoints),
    proteinWeeks: proteinDaysPerWeek(s.nutritionByDay.map((d) => [d.epochDay, d.protein] as const), s.settings.proteinGoal, 12, today),
    carbWeeks: carbWeeks(s.nutritionByDay, today),
    lifetime: lifetime(sessions, sets),
    workoutDaysInRange: [...workoutDays].filter((d) => d >= from).length,
  }
}
type Derived = ReturnType<typeof derive>
const inRange = (d: Derived, pts: TrendPoint[]) => pts.filter((p) => p.epochDay >= d.from)

function Delta({ text, positive }: { text: string; positive: boolean }) {
  return <span class={`pg-delta${positive ? ' pos' : ''}`}>{text}</span>
}

function Sparkline({ values, color }: { values: number[]; color: string }) {
  if (values.length < 2) return <div class="pg-spark" />
  const W = 120, H = 36
  const lo = Math.min(...values), hi = Math.max(...values)
  const range = hi - lo > 0 ? hi - lo : 1
  const pos = (i: number): [number, number] => [(i * W) / (values.length - 1), 4 + (1 - (values[i] - lo) / range) * (H - 10)]
  const line = values.map((_, i) => pos(i).map((n) => n.toFixed(1)).join(',')).join(' ')
  const [lx, ly] = pos(values.length - 1)
  return (
    <svg class="pg-spark" viewBox={`-4 0 ${W + 8} ${H}`} aria-hidden="true" focusable="false">
      <polygon points={`${line} ${W},${H} 0,${H}`} fill={`var(${color})`} opacity="0.12" />
      <polyline points={line} fill="none" stroke={`var(${color})`} stroke-width="2" stroke-linecap="round" stroke-linejoin="round" vector-effect="non-scaling-stroke" />
      <circle cx={lx} cy={ly} r="4" fill={`var(${color})`} />
      <circle cx={lx} cy={ly} r="1.8" fill="var(--card)" />
    </svg>
  )
}

function LoadCard({ d }: { d: Derived }) {
  const thisWeek = d.weeklySets[d.weeklySets.length - 1]
  const lastWeek = d.weeklySets[d.weeklySets.length - 2] ?? 0
  const change = percentChange(lastWeek, thisWeek)
  const max = Math.max(1, ...d.weeklySets)
  return (
    <Card>
      <div class="pg-load">
        <Ring value={Math.min(1, Math.max(0, d.load / 100))} color="--chart-strength" track="--chart-gain-track" size={84} stroke={8} label={t('progress_load')}>
          <span class="pg-load-num">{Math.round(d.load)}<small>%</small></span>
        </Ring>
        <div class="pg-load-text">
          <h3 class="pg-h3">{t('progress_load')}</h3>
          <p class="pg-hint">{t('progress_load_hint')}</p>
          <div class="pg-load-line">
            <span class="pg-strong">{plural('progress_sets_this_week', thisWeek)}</span>
            {change == null && thisWeek > 0 && <Delta text={t('progress_first_week')} positive />}
            {change != null && change > 0 && <Delta text={t('progress_vs_last_week', `+${change}%`)} positive />}
            {change != null && change <= 0 && <Delta text={t('progress_vs_last_week', `${change}%`)} positive={false} />}
          </div>
        </div>
      </div>
      <div class="pg-bars" aria-hidden="true">
        {d.weeklySets.map((v, i) => (
          <span key={i} class={i === d.weeklySets.length - 1 ? 'last' : ''} style={{ blockSize: `max(3px, ${(v / max) * 100}%)` }} />
        ))}
      </div>
    </Card>
  )
}

type Tile = { label: string; value: string; delta: string | null; positive: boolean; spark: number[]; color: string }

function Tiles({ s, d, unit }: { s: ProgressSnapshot; d: Derived; unit: WeightUnit }) {
  const ar = getLang() === 'ar'
  const sUnit = t('unit_s')
  const uText = unitLabel(unit, ar)
  const tiles: Tile[] = []
  for (const id of d.topIds.slice(0, 3)) {
    const ex = s.exercises.get(id)
    const pts = d.series.get(id) ?? []
    if (!ex || !pts.length) continue
    const pair = thenNow(pts, d.from)
    const shown = inRange(d, pts)
    tiles.push({
      label: t('progress_tile_best', ex.name), value: formatValue(pts[pts.length - 1].value, ex.kind, unit, sUnit, ar),
      delta: pair ? formatDelta(pair[1] - pair[0], ex.kind, unit, sUnit, uText) : null, positive: pair != null && pair[1] > pair[0],
      spark: (shown.length ? shown : pts).slice(-12).map((p) => p.value), color: '--chart-strength',
    })
  }
  if (d.weightAvg.length >= 2) {
    const shown = inRange(d, d.weightAvg)
    const pts = shown.length ? shown : d.weightAvg.slice(-2)
    const diff = fromKg(unit, pts[pts.length - 1].value - pts[0].value)
    tiles.push({
      label: t('progress_tile_weight'), value: weightNumber(unit, pts[pts.length - 1].value),
      delta: Math.abs(diff) < 0.15 ? t('progress_holding') : `${diff > 0 ? '+' : '−'}${Math.abs(diff).toFixed(1)}`,
      positive: false, spark: pts.slice(-12).map((p) => p.value), color: '--chart-weight',
    })
  }
  if (s.settings.trackCarbs && d.carbWeeks.some((n) => n > 0)) {
    const now = d.carbWeeks[11], goal = s.settings.carbsGoal
    const onGoal = goal > 0 && now > 0 && Math.abs(now - goal) <= Math.trunc(goal / 10)
    tiles.push({ label: t('progress_tile_carbs'), value: t('progress_carbs_avg', now, goal), delta: onGoal ? t('progress_on_target') : null, positive: onGoal, spark: d.carbWeeks, color: '--purple' })
  }
  if (d.proteinWeeks.some((n) => n > 0)) {
    const now = d.proteinWeeks[d.proteinWeeks.length - 1], before = d.proteinWeeks[d.proteinWeeks.length - 2] ?? 0
    tiles.push({ label: t('progress_tile_protein'), value: t('progress_of_seven', now), delta: now > before ? `+${now - before}` : null, positive: now > before, spark: d.proteinWeeks, color: '--chart-protein' })
  }
  const now = d.weeklyWorkouts[d.weeklyWorkouts.length - 1]
  const target = s.settings.weeklyTargetOverride > 0 ? s.settings.weeklyTargetOverride : s.plan ? trainingDays(s.plan).size : 0
  tiles.push({ label: t('progress_tile_workouts'), value: `${now}`, delta: target > 0 && now >= target ? t('progress_on_target') : null, positive: target > 0 && now >= target, spark: d.weeklyWorkouts.slice(-12), color: '--chart-calories' })
  return (
    <div class="pg-tiles">
      {tiles.map((x, i) => (
        <div class="pg-tile" key={i}>
          <span class="pg-tile-label">{x.label}</span>
          <div class="pg-tile-val"><b>{x.value}</b>{x.delta && <Delta text={x.delta} positive={x.positive} />}</div>
          <Sparkline values={x.spark} color={x.color} />
        </div>
      ))}
    </div>
  )
}

function StrengthCard({ s, d, unit }: { s: ProgressSnapshot; d: Derived; unit: WeightUnit }) {
  const [pick, setPick] = useState<string | null>(null)
  if (!d.topIds.length) return null
  const selected = pick && d.topIds.includes(pick) ? pick : d.topIds[0]
  const ex = s.exercises.get(selected)
  const kind = ex?.kind ?? 'REPS'
  const pts = inRange(d, d.series.get(selected) ?? [])
  const name = ex?.name ?? selected
  return (
    <Card>
      <h3 class="pg-h3">{t('progress_strength')}</h3>
      <p class="pg-hint">{t('progress_strength_hint')}</p>
      <div class="pg-scroll" role="radiogroup" aria-label={t('progress_strength')}>
        {d.topIds.map((id) => <Chip key={id} role="radio" label={s.exercises.get(id)?.name ?? id} selected={selected === id} onClick={() => setPick(id)} />)}
      </div>
      {pts.length < 2
        ? <p class="pg-need">{t('progress_need_more', name)}</p>
        : <LineChart title={`${t('progress_strength')}: ${name}`} color="--chart-strength"
            data={pts.map((p) => ({ label: dateLabel(p.epochDay), value: kind === 'WEIGHTED' ? fromKg(unit, p.value) : p.value }))}
            format={(v) => (kind === 'WEIGHTED' ? weightNumber(unit, toKg(unit, v)) : v.toFixed(0))} />}
    </Card>
  )
}

function ThenNowCard({ s, d, unit }: { s: ProgressSnapshot; d: Derived; unit: WeightUnit }) {
  const ar = getLang() === 'ar'
  const sUnit = t('unit_s')
  const rows: { then: string; now: string; label: string; up: boolean }[] = []
  for (const id of d.topIds.slice(0, 3)) {
    const ex = s.exercises.get(id)
    const pair = ex ? thenNow(d.series.get(id) ?? [], d.from) : null
    if (ex && pair) rows.push({ then: formatValue(pair[0], ex.kind, unit, sUnit, ar), now: formatValue(pair[1], ex.kind, unit, sUnit, ar), label: ex.name, up: pair[1] > pair[0] })
  }
  if (d.weeklyWorkouts.length >= 2) {
    const a = d.weeklyWorkouts[0], b = d.weeklyWorkouts[d.weeklyWorkouts.length - 1]
    rows.push({ then: t('progress_per_week', a), now: t('progress_per_week', b), label: t('progress_tile_workouts').split(' · ')[0], up: b > a })
  }
  const w = thenNow(d.weightAvg, d.from)
  if (w) rows.push({ then: formatWeight(unit, w[0], ar), now: formatWeight(unit, w[1], ar), label: t('weight_body'), up: false })
  if (!rows.length) return null
  return (
    <Card>
      <h3 class="pg-h3">{t('progress_then_now')}</h3>
      <p class="pg-hint">{t('progress_then_now_hint')}</p>
      {rows.map((r, i) => (
        <div class="pg-tn" key={i}>
          <span class="pg-tn-then">{r.then}</span>
          <span class={`pg-tn-arrow${r.up ? ' up' : ''}`} aria-hidden="true">{ar ? '←' : '→'}</span>
          <span class="pg-tn-now"><b class={r.up ? 'up' : ''}>{r.now}</b><small>{r.label}</small></span>
        </div>
      ))}
    </Card>
  )
}

function Total({ value, label }: { value: string; label: string }) {
  return <div class="pg-total"><b>{value}</b><span>{label}</span></div>
}

function Legend({ color, label, alpha }: { color: string; label: string; alpha?: number }) {
  return <span class="pg-leg"><i class="pg-dot" style={{ background: `var(${color})`, opacity: alpha ?? 1 }} />{label}</span>
}

/** Trends, records and consistency over months (Android ProgressScreen without readiness / Fitbit). */
export function ProgressScreen(_: { param?: string }) {
  const snap = useProgress()
  const [rangeIdx, setRangeIdx] = useState(1)
  const range = TREND_RANGES[rangeIdx]
  const d = useMemo(() => (snap ? derive(snap, range) : null), [snap, range])
  const head = (
    <div class="pg-head">
      <h2 class="pg-title">{t('progress_title')}</h2>
      <p class="pg-sub">{t('progress_subtitle')}</p>
    </div>
  )
  if (!snap || !d) return <div class="content">{head}</div>
  if (snap.sessions.length === 0) {
    return (
      <div class="content">
        {head}
        <div class="pg-list">
          <Card><EmptyState title={t('progress_empty_title')} body={t('progress_empty_body')}
            icon={<svg width="48" height="48" viewBox="0 0 24 24" fill="currentColor"><path d="M3.5 18.49l6-6.01 4 4L22 6.92l-1.41-1.41-7.09 7.97-4-4L2 16.99z" /></svg>} /></Card>
        </div>
      </div>
    )
  }
  const unit = weightUnit(snap.settings)
  const ar = getLang() === 'ar'
  const wShown = inRange(d, d.weightPoints).length ? inRange(d, d.weightAvg) : d.weightAvg.slice(-2)
  const lt = d.lifetime
  return (
    <div class="content">
      {head}
      <div class="pg-list">
        <ChipGroup label={t('progress_title')} value={range} onChange={(v) => setRangeIdx(TREND_RANGES.indexOf(v))}
          options={TREND_RANGES.map((r) => ({ value: r, label: t(RANGE_KEYS[r]) }))} />
        <LoadCard d={d} />
        <Tiles s={snap} d={d} unit={unit} />
        <StrengthCard s={snap} d={d} unit={unit} />
        {d.weightPoints.length >= 2 && (
          <Card>
            <h3 class="pg-h3">{t('weight_body')}</h3>
            <p class="pg-hint">{t('progress_web_weight_hint')}</p>
            <LineChart title={t('weight_body')} color="--chart-weight" data={wShown.map((p) => ({ label: dateLabel(p.epochDay), value: fromKg(unit, p.value) }))} format={(v) => v.toFixed(1)} />
          </Card>
        )}
        <Card>
          <h3 class="pg-h3">{t('progress_consistency')}</h3>
          <p class="pg-hint">{plural('progress_workout_days', d.workoutDaysInRange)}</p>
          <div class="pg-heat" style={{ gridTemplateColumns: `repeat(${d.grid[0].length}, minmax(0, 14px))` }} role="img" aria-label={t('progress_web_heatmap_cd')}>
            {d.grid[0].map((_, col) => d.grid.map((row, r) => <i key={`${col}-${r}`} class={`pg-cell ${row[col].toLowerCase()}`} />))}
          </div>
          <div class="pg-legend">
            <Legend color="--card-high" label={t('progress_legend_rest')} />
            <Legend color="--chart-strength" alpha={0.55} label={t('progress_legend_workout')} />
            <Legend color="--gold" label={t('progress_legend_record')} />
          </div>
        </Card>
        <ThenNowCard s={snap} d={d} unit={unit} />
        <Card>
          <h3 class="pg-h3">{t('progress_all_time')}</h3>
          <div class="pg-totals">
            <Total value={grouped(lt.workouts)} label={t('progress_total_workouts')} />
            <Total value={grouped(lt.reps)} label={t('progress_total_reps')} />
            <Total value={`${Math.trunc(lt.minutes / 60)} ${t('unit_h')}`} label={t('progress_total_hours')} />
            <Total value={grouped(lt.records)} label={t('progress_total_records')} />
          </div>
          {lt.kgLifted > 0 && <p class="pg-hint pg-lifted">{`${grouped(fromKg(unit, lt.kgLifted))} ${unitLabel(unit, ar)} ${t('progress_total_lifted')}`}</p>}
        </Card>
      </div>
    </div>
  )
}
