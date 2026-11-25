import { useMemo, useState } from 'preact/hooks'
import { getLang, t, intlLocale } from '../../i18n'
import {
  deleteMeasurement, logMeasurement, MEASUREMENT_METRICS, measurementTrend, useMeasurements, type MeasurementMetric,
} from '../../data/measurements'
import type { BodyMeasurementRow } from '../../data/db'
import type { ProgressSnapshot } from '../../data/progress'
import { todayEpochDay } from '../../domain/dates'
import { countsForScore } from '../../domain/set-types'
import { trendRangeFrom, type TrendRange } from '../../domain/trends'
import { fromKg, loadStep, toKg, unitLabel, weightNumber, type WeightUnit } from '../../domain/units'
import { Button, Card, ChipGroup, CoachMark, LineChart, NumberField, Sheet, Stepper, toast } from '../kit'
import { VolumeBodyMap } from './BodyMap'
import { epley1Rm, epleyPercentages, strengthToBodyweight, weeklyMuscleVolume } from './metrics'

const metricLabel = (m: MeasurementMetric) => t(`prog41_meas_metric_${m.toLowerCase()}`)
const DEFAULT_CM: Record<MeasurementMetric, number> = { WAIST: 82, CHEST: 100, ARMS: 35, THIGHS: 56, HIPS: 96 }

const dateLabel = (day: number) =>
  new Intl.DateTimeFormat(intlLocale(), { month: 'short', day: 'numeric', timeZone: 'UTC' }).format(new Date(day * 86_400_000))

export function WeeklyVolumeCard({ s }: { s: ProgressSnapshot }) {
  const volume = useMemo(
    () => weeklyMuscleVolume(s.sessions, s.doneSets, s.exercises, todayEpochDay()),
    [s.sessions, s.doneSets, s.exercises],
  )
  return (
    <Card>
      <h3 class="pg-h3">{t('prog41_vol_title')}</h3>
      <p class="pg-hint">{t('prog41_vol_subtitle')}</p>
      <VolumeBodyMap volume={volume} bodyType={s.settings.bodyType} />
      <p class="pg-hint" style={{ marginBlockStart: '8px', marginBlockEnd: 0 }}>{t('prog41_vol_explain')}</p>
    </Card>
  )
}

export function MoreToolsSection({ s, unit }: { s: ProgressSnapshot; unit: WeightUnit }) {
  const [open, setOpen] = useState(false)
  return (
    <Card>
      <button
        type="button"
        class="pg-more-toggle"
        aria-expanded={open}
        aria-controls="pg-more-tools-body"
        onClick={() => setOpen(!open)}
      >
        <span class="pg-more-title">{t('prog41_more_tools')}</span>
        <span class={open ? 'pg-more-chev open' : 'pg-more-chev'} aria-hidden="true">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor">
            <path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z" />
          </svg>
        </span>
      </button>
      {open && (
        <div id="pg-more-tools-body" class="pg-more-body">
          <MaxStrengthEstimateBody s={s} unit={unit} />
        </div>
      )}
    </Card>
  )
}

function MaxStrengthEstimateBody({ s, unit }: { s: ProgressSnapshot; unit: WeightUnit }) {
  const ar = getLang() === 'ar'
  const uText = unitLabel(unit, ar)
  const bestSample = useMemo(() => {
    let best: { exerciseId: string; weightKg: number; reps: number; score: number } | null = null
    for (const set of s.doneSets) {
      if (s.exercises.get(set.exerciseId)?.kind !== 'WEIGHTED') continue
      if (!countsForScore(set) || !set.weightKg || set.weightKg <= 0 || !set.reps || set.reps <= 0) continue
      const score = epley1Rm(set.weightKg, set.reps)
      if (!best || score > best.score) best = { exerciseId: set.exerciseId, weightKg: set.weightKg, reps: set.reps, score }
    }
    return best
  }, [s.doneSets, s.exercises])

  const [weightKg, setWeightKg] = useState(() => bestSample?.weightKg ?? 60)
  const [reps, setReps] = useState(() => Math.min(20, Math.max(1, bestSample?.reps ?? 5)))
  const estKg = epley1Rm(weightKg, reps)
  const ratio = strengthToBodyweight(estKg, s.latestWeight)

  return (
    <>
      <h3 class="pg-h3">{t('prog41_1rm_title')}</h3>
      <p class="pg-hint">{t('prog41_1rm_hint')}</p>
      {bestSample && (
        <div class="pg-actions">
          <Button
            variant="tonal"
            onClick={() => {
              setWeightKg(bestSample.weightKg)
              setReps(Math.min(20, Math.max(1, bestSample.reps)))
            }}
          >
            {t('prog41_1rm_use_best', `${s.exercises.get(bestSample.exerciseId)?.name ?? bestSample.exerciseId} (${bestSample.reps}×${weightNumber(unit, bestSample.weightKg)})`)}
          </Button>
        </div>
      )}
      <div class="pg-calc-row">
        <span class="pg-calc-label">{t('prog41_1rm_weight', uText)}</span>
        <Stepper
          label={t('prog41_1rm_weight', uText)}
          value={Math.round(fromKg(unit, weightKg) * 10) / 10}
          step={loadStep(unit)}
          min={fromKg(unit, 1)}
          max={fromKg(unit, 500)}
          format={(n) => `${n.toFixed(1)} ${uText}`}
          onChange={(v) => setWeightKg(Math.min(500, Math.max(1, toKg(unit, v))))}
        />
      </div>
      <div class="pg-calc-row">
        <span class="pg-calc-label">{t('prog41_1rm_reps')}</span>
        <Stepper
          label={t('prog41_1rm_reps')}
          value={reps}
          step={1}
          min={1}
          max={20}
          onChange={(v) => setReps(Math.min(20, Math.max(1, Math.round(v))))}
        />
      </div>
      <div class="pg-1rm-box">
        <div class="pg-1rm-col">
          <span class="pg-1rm-lbl">{t('prog41_1rm_result_label')}</span>
          <b class="pg-1rm-val pg-data-blue">{`${fromKg(unit, estKg).toFixed(1)} ${uText}`}</b>
        </div>
        <div class="pg-1rm-col">
          <span class="pg-1rm-lbl">{t('prog41_1rm_ratio_label')}</span>
          {ratio != null ? (
            <b class="pg-1rm-val pg-data-purple">{t('prog41_1rm_ratio_val', ratio.toFixed(2))}</b>
          ) : (
            <span class="pg-hint" style={{ margin: 0 }}>{t('prog41_1rm_ratio_missing')}</span>
          )}
        </div>
      </div>
      <div class="pg-pcts">
        {epleyPercentages(estKg).map(({ pct, value }) => (
          <span key={pct} class="pg-pct">{`${pct}% · ${weightNumber(unit, value, 0)} ${uText}`}</span>
        ))}
      </div>
    </>
  )
}

export function MeasurementsCard({ range }: { range: TrendRange }) {
  const rows = useMeasurements()
  const [metric, setMetric] = useState<MeasurementMetric>('WAIST')
  const [sheetOpen, setSheetOpen] = useState(false)
  const [draftMetric, setDraftMetric] = useState<MeasurementMetric>('WAIST')
  const [draftValue, setDraftValue] = useState<number | null>(DEFAULT_CM.WAIST)

  const allPoints = useMemo(() => measurementTrend(rows, metric), [rows, metric])
  const from = trendRangeFrom(range, todayEpochDay())
  const shown = useMemo(() => {
    const f = allPoints.filter((p) => p.epochDay >= from)
    return f.length >= 2 ? f : allPoints.slice(-10)
  }, [allPoints, from])
  const latest = allPoints.length ? allPoints[allPoints.length - 1] : null
  const prev = allPoints.length >= 2 ? allPoints[allPoints.length - 2] : null
  const recentRows = useMemo(
    () => rows.filter((r) => r.metric === metric).sort((a, b) => b.epochDay - a.epochDay || b.loggedAt - a.loggedAt).slice(0, 5),
    [rows, metric],
  )

  const openLog = () => {
    setDraftMetric(metric)
    setDraftValue(latest?.value ?? DEFAULT_CM[metric])
    setSheetOpen(true)
  }

  const save = async () => {
    if (draftValue == null || draftValue < 10 || draftValue > 300) {
      toast(t('prog41_meas_invalid'))
      return
    }
    await logMeasurement(draftMetric, draftValue)
    setMetric(draftMetric)
    setSheetOpen(false)
    toast(t('prog41_meas_saved'))
  }

  const remove = async (row: BodyMeasurementRow) => {
    if (row.id == null) return
    await deleteMeasurement(row.id)
    toast(t('prog41_meas_deleted'))
  }

  return (
    <>
      <Card>
        <div class="pg-meas-head">
          <div>
            <h3 class="pg-h3">{t('prog41_meas_title')}</h3>
            <p class="pg-hint" style={{ marginBlockEnd: 0 }}>{t('prog41_meas_hint')}</p>
          </div>
          <CoachMark id="coach_progress" title={t('v42_coach_progress_t')} body={t('v42_coach_progress_b')} below>
            <Button variant="tonal" onClick={openLog}>{t('prog41_meas_log')}</Button>
          </CoachMark>
        </div>
        <div style={{ marginBlock: '10px 8px' }}>
          <ChipGroup
            label={t('prog41_meas_title')}
            value={metric}
            onChange={setMetric}
            options={MEASUREMENT_METRICS.map((m) => ({ value: m, label: metricLabel(m) }))}
          />
        </div>
        {!latest ? (
          <p class="pg-hint">{t('prog41_meas_empty')}</p>
        ) : (
          <>
            <div class="pg-meas-latest">
              <b class="pg-data-purple">{t('prog41_meas_latest', latest.value.toFixed(1))}</b>
              {prev && Math.abs(latest.value - prev.value) >= 0.05 && (
                <span class="pg-meas-delta">
                  {`${latest.value - prev.value > 0 ? '+' : '−'}${Math.abs(latest.value - prev.value).toFixed(1)} cm`}
                </span>
              )}
            </div>
            {shown.length >= 2 ? (
              <LineChart
                title={`${t('prog41_meas_title')}: ${metricLabel(metric)}`}
                color="--purple"
                data={shown.map((p) => ({ label: dateLabel(p.epochDay), value: p.value }))}
                format={(v) => `${v.toFixed(1)} cm`}
              />
            ) : (
              <p class="pg-hint">{t('prog41_meas_need_two')}</p>
            )}
            <div class="pg-meas-list">
              {recentRows.map((r) => (
                <div key={r.id ?? `${r.epochDay}-${r.loggedAt}`} class="pg-meas-item">
                  <span class="pg-hint" style={{ margin: 0 }}>{dateLabel(r.epochDay)}</span>
                  <span class="pg-strong" style={{ fontVariantNumeric: 'tabular-nums' }}>{`${r.valueCm.toFixed(1)} cm`}</span>
                  <button
                    type="button"
                    class="kit-iconbtn"
                    aria-label={t('prog41_meas_delete_cd')}
                    onClick={() => void remove(r)}
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          </>
        )}
      </Card>
      <Sheet open={sheetOpen} onClose={() => setSheetOpen(false)} title={t('prog41_meas_log_title')}>
        <div class="pg-meas-sheet">
          <ChipGroup
            label={t('prog41_meas_log_title')}
            value={draftMetric}
            onChange={(m) => { setDraftMetric(m); setDraftValue(DEFAULT_CM[m]) }}
            options={MEASUREMENT_METRICS.map((m) => ({ value: m, label: metricLabel(m) }))}
          />
          <NumberField
            label={t('prog41_meas_value_cm')}
            value={draftValue}
            onChange={setDraftValue}
            min={10}
            max={300}
            decimal
            error={draftValue == null ? t('prog41_meas_invalid') : undefined}
          />
          <Button block onClick={() => void save()} disabled={draftValue == null}>{t('action_save')}</Button>
        </div>
      </Sheet>
    </>
  )
}
