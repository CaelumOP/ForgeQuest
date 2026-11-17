import { useState } from 'preact/hooks'
import { getLang, t, intlLocale, listSep } from '../../i18n'
import type { SetRow } from '../../data/db'
import { useLive } from '../../data/live'
import { useSettings, weightUnit } from '../../data/settings'
import { addPlanItem, recentDoneSetsAll, setVideoUrl, swapPlanItem, updateExercise, useActivePlan, useFinishedSessions, useLibrary } from '../../data/workouts'
import { exerciseCueKeys } from '../../data/seed/exercise-cues'
import { EQUIPMENT_LABEL, profileFor } from '../../domain/exercise-swap-engine'
import { tierOf } from '../../domain/exercise-tiers'
import type { ExerciseKind } from '../../domain/muscle'
import { recordScore } from '../../domain/progress'
import { fromKg, unitLabel, weightNumber, type WeightUnit } from '../../domain/units'
import { Button, Card, ChipGroup, IconButton, LineChart, Sheet, TextField, toast } from '../kit'
import { ExerciseMediaPair } from '../kit/ExerciseMediaView'
import { MuscleMapMini } from '../kit/MuscleMapMini'
import { ExerciseEditor } from './ExerciseEditor'
import { PutInPlanSheet } from './PutInPlanSheet'
import { TierBadge } from './MuscleSheet'
import { EditIcon, LinkIcon, PlayIcon } from './glyphs'
import { dayName, kindLabel, muscleLabel, openFormVideo } from './helpers'
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
  new Date(epochDay * 86_400_000).toLocaleDateString(intlLocale(), style === 'long'
    ? { weekday: 'short', month: 'short', day: 'numeric', timeZone: 'UTC' }
    : { month: 'short', day: 'numeric', timeZone: 'UTC' })

const PIN_ICON = (filled: boolean) => (
  <svg width="22" height="22" viewBox="0 0 24 24" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false">
    <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />
  </svg>
)

export function ExerciseDetailScreen({ param }: { param?: string }) {
  const [rawId, flag] = (param ?? '').split('/')
  const id = rawId ?? ''
  const fromLive = flag === 'live'
  const library = useLibrary()
  const settings = useSettings()
  const sessions = useFinishedSessions()
  const sets = useLive(() => recentDoneSetsAll(id), [id], [] as SetRow[])
  const [metricIdx, setMetricIdx] = useState(0)
  const [linkOpen, setLinkOpen] = useState(false)
  const [noteOpen, setNoteOpen] = useState(false)
  const [editing, setEditing] = useState(false)
  const [putOpen, setPutOpen] = useState(false)
  const plan = useActivePlan()

  const ex = library.find((e) => e.id === id)
  if (!ex) return <div class="content wk">{library.length > 0 && <p class="wk-muted">{t('workouts_web_not_found')}</p>}</div>

  const lang = getLang()
  const arabic = lang === 'ar'
  const unit = weightUnit(settings, ex.unitOverride ?? '')
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
  const tier = tierOf(ex)
  const prof = profileFor(ex)
  const setText = (s: SetRow) => ex.kind === 'HOLD' ? `${s.seconds ?? 0}${sec}` : ex.kind === 'WEIGHTED' ? `${s.reps ?? 0}×${weightNumber(unit, s.weightKg ?? 0)}` : `${s.reps ?? 0}`
  const sep = listSep(lang)

  return (
    <div class="content wk">
      <div class="wk-head">
        <div class="wk-head-text">
          <h1 class="wk-title">{ex.name}</h1>
        </div>
        <IconButton
          label={t(ex.pinned ? 'picker_unpin_toggle' : 'picker_pin_toggle')}
          onClick={() => void updateExercise({ ...ex, pinned: !ex.pinned })}
        >
          {PIN_ICON(Boolean(ex.pinned))}
        </IconButton>
      </div>

      {/* (a) Start -> Finish photo pair card + YouTube form link */}
      <ExerciseMediaPair exercise={ex} />
      <div class="wk-detail-actions">
        <Button variant="tonal" icon={<PlayIcon />} onClick={() => openFormVideo(ex)}>{t('cd_watch_form')}</Button>
        <Button variant="tonal" icon={<LinkIcon />} onClick={() => setLinkOpen(true)}>{ex.videoUrl?.trim() ? t('detail_change_link') : t('detail_add_link')}</Button>
        {ex.custom && <Button variant="tonal" onClick={() => setEditing(true)}>{t('lib_edit')}</Button>}
      </div>

      {/* (b) Muscles & specs card: equipment / kind / tier pills + mini front/back muscle map */}
      <Card>
        <h2 class="wk-card-title" style={{ fontSize: '18px', marginBlockEnd: '8px' }}>{t('detail_muscles_specs')}</h2>
        <div class="wk-chip-wrap" style={{ marginBlockEnd: '12px' }}>
          <span class="wk-pill" style={{ background: 'var(--card-high)', color: 'var(--text)' }}>{EQUIPMENT_LABEL[prof.equipment][lang]}</span>
          <span class="wk-pill" style={{ background: 'var(--card-high)', color: 'var(--text-2)' }}>{kindLabel(ex.kind)}</span>
          {tier && <TierBadge tier={tier} />}
          {!tier && ex.custom && <span class="wk-pill custom">{t('lib_custom')}</span>}
        </div>
        <MuscleMapMini primary={ex.primary} secondary={ex.secondary} bodyType={settings.bodyType} />
        {ex.primary.length > 0 && (
          <p class="wk-s" style={{ margin: '10px 0 0', color: 'var(--text)' }}>
            {t('detail_main_muscles')}: {ex.primary.map(muscleLabel).join(sep)}
          </p>
        )}
        {ex.secondary.length > 0 && (
          <p class="wk-s" style={{ margin: '2px 0 0' }}>
            {t('detail_secondary_muscles')}: {ex.secondary.map(muscleLabel).join(sep)}
          </p>
        )}
      </Card>

      {/* (c) "How to do it": 2-3 cues + "Common mistake" */}
      {(() => {
        const cues = exerciseCueKeys(ex.id)
        if (!cues) return null
        return (
          <Card>
            <h2 class="wk-card-title" style={{ fontSize: '18px', marginBlockEnd: '8px' }}>{t('cues_title')}</h2>
            <p style={{ margin: '0 0 4px', fontSize: '14px', lineHeight: '20px' }}>{t(cues[0])}</p>
            <p style={{ margin: '0 0 10px', fontSize: '14px', lineHeight: '20px' }}>{t(cues[1])}</p>
            <div class="wk-s" style={{ fontWeight: 500 }}>{t('cues_mistake')}</div>
            <p class="wk-s" style={{ margin: '2px 0 0' }}>{t(cues[2])}</p>
          </Card>
        )
      })()}

      {/* (d) Setup note */}
      <Card>
        <div class="wk-head" style={{ paddingBlockStart: 0 }}>
          <h2 class="wk-card-title wk-head-text" style={{ fontSize: '18px' }}>{t('detail_setup_note')}</h2>
          <IconButton label={t('cd_edit')} onClick={() => setNoteOpen(true)}><EditIcon size={20} /></IconButton>
        </div>
        <p class={ex.setupNote?.trim() ? '' : 'wk-muted'} style={{ margin: '4px 0 0', fontSize: '14px' }}>
          {ex.setupNote?.trim() || t('detail_setup_note_hint')}
        </p>
      </Card>

      {/* (e) History / PRs */}
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

      {/* (f) "Put in my plan" (hidden when opened from live) */}
      {plan && !fromLive && <Button variant="tonal" block onClick={() => setPutOpen(true)}>{t('swap_into_plan')}</Button>}

      {linkOpen && <LinkSheet url={ex.videoUrl ?? ''} onClose={() => setLinkOpen(false)} onSave={async (v) => {
        setLinkOpen(false)
        if (await setVideoUrl(ex.id, v.trim() || null)) toast(t('workouts_web_link_saved')); else toast(t('detail_bad_link'))
      }} />}
      {noteOpen && <SetupNoteSheet note={ex.setupNote ?? ''} onClose={() => setNoteOpen(false)} onSave={async (v) => {
        setNoteOpen(false)
        await updateExercise({ ...ex, setupNote: v.trim() })
        toast(t('lib_saved', ex.name))
      }} />}
      {putOpen && plan && <PutInPlanSheet ex={ex} plan={plan} library={library} onClose={() => setPutOpen(false)}
        onReplace={async (day, item, old) => {
          setPutOpen(false)
          await swapPlanItem(item, ex.id)
          toast(t('plan_put_in_replaced', ex.name, old.name, dayName(day.day.dayOfWeek, lang)))
        }}
        onAdd={async (day) => {
          setPutOpen(false)
          await addPlanItem(day.day.id!, ex.id)
          toast(t('plan_put_in_added', ex.name, dayName(day.day.dayOfWeek, lang)))
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

function SetupNoteSheet({ note, onClose, onSave }: { note: string; onClose: () => void; onSave: (v: string) => void }) {
  const [v, setV] = useState(note)
  return (
    <Sheet open onClose={onClose} title={t('detail_setup_note')}>
      <TextField label={t('detail_setup_note')} value={v} placeholder={t('detail_setup_note_hint')} onInput={(x) => setV(x.slice(0, 120))} />
      <div class="wk-pair">
        <Button variant="tonal" onClick={onClose}>{t('action_cancel')}</Button>
        <Button onClick={() => onSave(v)}>{t('action_save')}</Button>
      </div>
    </Sheet>
  )
}
