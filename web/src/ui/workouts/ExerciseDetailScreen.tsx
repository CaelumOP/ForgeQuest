import { useState } from 'preact/hooks'
import { getLang, t } from '../../i18n'
import type { SetRow } from '../../data/db'
import { useLive } from '../../data/live'
import { useSettings, weightUnit } from '../../data/settings'
import { recentDoneSetsAll, setVideoUrl, useFinishedSessions, useLibrary } from '../../data/workouts'
import type { ExerciseKind } from '../../domain/muscle'
import { recordScore } from '../../domain/progress'
import { fromKg, unitLabel, weightNumber, type WeightUnit } from '../../domain/units'
import { Button, Card, ChipGroup, LineChart, Sheet, TextField, toast } from '../kit'
import { ExerciseEditor } from './ExerciseEditor'
import { LinkIcon, PlayIcon } from './glyphs'
import { kindLabel, musclesLabel, openFormVideo } from './helpers'
import './workouts.css'

type Metric = { label: string; unit: string; value: (sets: SetRow[]) => number }

function metricsFor(kind: ExerciseKind, wu: WeightUnit, arabic: boolean): Metric[] {
  const sum = (s: SetRow[], f: (x: SetRow) => number) => s.reduce((a, x) => a + f(x), 0)
  const max = (s: SetRow[], f: (x: SetRow) => number) => s.reduce((a, x) => Math.max(a, f(x)), 0)
  const reps = t('unit_reps'), sec = t('unit_s'), u = unitLabel(wu, arabic)
  if (kind === 'HOLD') return [
    { label: t('metric_max_hold'), unit: sec, value: (s) => max(s, (x) => x.seconds ?? 0) },
    { label: t('metric_total_time'), unit: sec, value: (s) => sum(s, (x) => x.seconds ?? 0) },
  ]
  if (kind === 'WEIGHTED') return [
    { label: t('metric_best_set'), unit: t('metric_est_1rm', u), value: (s) => fromKg(wu, max(s, (x) => recordScore(kind, x.reps, x.seconds, x.weightKg, x.exerciseId))) },
    { label: t('metric_max_weight'), unit: u, value: (s) => fromKg(wu, max(s, (x) => x.weightKg ?? 0)) },
    { label: t('metric_volume'), unit: u, value: (s) => fromKg(wu, sum(s, (x) => (x.weightKg ?? 0) * (x.reps ?? 0))) },
    { label: t('metric_max_reps'), unit: reps, value: (s) => max(s, (x) => x.reps ?? 0) },
  ]
  return [
    { label: t('metric_max_reps'), unit: reps, value: (s) => max(s, (x) => x.reps ?? 0) },
    { label: t('metric_total_reps'), unit: reps, value: (s) => sum(s, (x) => x.reps ?? 0) },
  ]
}

const dateText = (epochDay: number, style: 'long' | 'short') =>
  new Date(epochDay * 86_400_000).toLocaleDateString(getLang(), style === 'long'
    ? { weekday: 'short', month: 'short', day: 'numeric', timeZone: 'UTC' }
    : { month: 'short', day: 'numeric', timeZone: 'UTC' })

export function ExerciseDetailScreen({ param }: { param?: string }) {
  const id = param ?? ''
  const library = useLibrary()
  const settings = useSettings()
  const sessions = useFinishedSessions()
  const sets = useLive(() => recentDoneSetsAll(id), [id], [] as SetRow[])
  const [metricIdx, setMetricIdx] = useState(0)
  const [linkOpen, setLinkOpen] = useState(false)
  const [editing, setEditing] = useState(false)

  const ex = library.find((e) => e.id === id)
  if (!ex) return <div class="content wk">{library.length > 0 && <p class="wk-muted">{t('workouts_web_not_found')}</p>}</div>

  const arabic = getLang() === 'ar'
  const unit = weightUnit(settings)
  const metrics = metricsFor(ex.kind, unit, arabic)
  const metric = metrics[Math.min(metricIdx, metrics.length - 1)]
  const byId = new Map(sessions.map((s) => [s.id!, s]))
  const groups = new Map<number, SetRow[]>()
  for (const s of sets) if (byId.has(s.sessionId)) groups.set(s.sessionId, [...(groups.get(s.sessionId) ?? []), s])
  const history = [...groups].map(([sid, list]) => ({ session: byId.get(sid)!, sets: list.sort((a, b) => a.setIndex - b.setIndex) }))
    .sort((a, b) => (a.session.finishedAt ?? 0) - (b.session.finishedAt ?? 0))
  const points = history.map((h) => ({ label: dateText(h.session.epochDay, 'short'), value: metric.value(h.sets) }))
  const best = points.length ? Math.max(...points.map((p) => p.value)) : null
  const sec = t('unit_s')
  const setText = (s: SetRow) => ex.kind === 'HOLD' ? `${s.seconds ?? 0}${sec}` : ex.kind === 'WEIGHTED' ? `${s.reps ?? 0}×${weightNumber(unit, s.weightKg ?? 0)}` : `${s.reps ?? 0}`

  return (
    <div class="content wk">
      <div>
        <h1 class="wk-title">{ex.name}</h1>
        <div class="wk-sub">{[kindLabel(ex.kind), musclesLabel(ex) || null].filter(Boolean).join(' · ')}</div>
      </div>
      <div class="wk-detail-actions">
        <Button variant="tonal" icon={<PlayIcon />} onClick={() => openFormVideo(ex)}>{t('cd_watch_form')}</Button>
        <Button variant="tonal" icon={<LinkIcon />} onClick={() => setLinkOpen(true)}>{ex.videoUrl?.trim() ? t('detail_change_link') : t('detail_add_link')}</Button>
        {ex.custom && <Button variant="tonal" onClick={() => setEditing(true)}>{t('lib_edit')}</Button>}
      </div>

      <Card>
        <h2 class="wk-card-title" style={{ fontSize: '18px' }}>{t('detail_progress')}</h2>
        <div style={{ marginBlock: '10px 16px' }}>
          <ChipGroup label={t('workouts_web_metric')} options={metrics.map((m, i) => ({ value: String(i), label: m.label }))} value={String(Math.min(metricIdx, metrics.length - 1))} onChange={(v) => setMetricIdx(Number(v))} />
        </div>
        {points.length < 2
          ? <p class="wk-muted" style={{ paddingBlock: '24px' }}>{points.length === 0 ? t('detail_chart_empty') : t('detail_chart_one')}</p>
          : <LineChart data={points} title={`${ex.name}: ${metric.label}`} color="--teal" format={(n) => String(Math.round(n * 10) / 10)} />}
        {best != null && <div class="wk-best" style={{ marginBlockStart: '8px' }}>{t('detail_best', Math.round(best), metric.unit)}</div>}
      </Card>

      {history.length > 0 && (
        <Card>
          <h2 class="wk-card-title" style={{ fontSize: '18px' }}>{t('detail_history')}</h2>
          {[...history].reverse().slice(0, 20).map(({ session, sets: list }) => (
            <a key={session.id} class="wk-hist" href={`#/session/${session.id}`}>
              <span class="wk-hist-date">{dateText(session.epochDay, 'long')}</span>
              <span class="wk-hist-sets">{list.map(setText).join('  ')}</span>
              {list.some((s) => s.isPr) && <span class="wk-pr">{t('live_pr')}</span>}
            </a>
          ))}
        </Card>
      )}

      {linkOpen && <LinkSheet url={ex.videoUrl ?? ''} onClose={() => setLinkOpen(false)} onSave={async (v) => {
        setLinkOpen(false)
        if (await setVideoUrl(ex.id, v.trim() || null)) toast(t('workouts_web_link_saved')); else toast(t('detail_bad_link'))
      }} />}
      {editing && <ExerciseEditor ex={ex} library={library} onClose={() => setEditing(false)} onGone={() => window.history.back()} />}
    </div>
  )
}

function LinkSheet({ url, onClose, onSave }: { url: string; onClose: () => void; onSave: (v: string) => void }) {
  const [v, setV] = useState(url)
  return (
    <Sheet open onClose={onClose} title={t('detail_link_title')}>
      <p class="wk-s">{t('detail_link_body')}</p>
      <TextField label={t('detail_link_title')} type="url" value={v} placeholder={t('workouts_web_link_placeholder')} onInput={setV} />
      <div class="wk-pair">
        <Button variant="tonal" onClick={onClose}>{t('action_cancel')}</Button>
        <Button onClick={() => onSave(v)}>{t('action_save')}</Button>
      </div>
    </Sheet>
  )
}
