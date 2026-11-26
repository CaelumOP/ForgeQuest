// Port of app/.../ui/profile/ProfileScreen.kt: rank, badges, body weight and the seven folding settings
// sections. Web leaves out Health Connect/Fitbit, reminders, app lock, the tour, Gemini bridge, APK updates
// and the Drive Docs import. Backup (Drive + file) lives in BackupSection.tsx.
import type { ComponentChildren } from 'preact'
import { useEffect, useState } from 'preact/hooks'
import { getLang, plural, setLang, t, useLang, type Lang, intlLocale } from '../../i18n'
import { setTheme, useTheme, type Theme } from '../theme'
import { isAiOn, DEFAULT_PLATE_INVENTORY, updateSettings, useSettings, weightUnit, type Settings } from '../../data/settings'
import { useLatestBaselines } from '../../data/baseline-tests'
import { db, type ExerciseRow } from '../../data/db'
import { foodSkillPrompt, PLAN_PROMPT } from '../../data/ai'
import { logWeight, useProgress, type ProgressSnapshot } from '../../data/progress'
import { applyPlan, seedLibrary, trainingDays, useLibrary } from '../../data/workouts'
import { todayEpochDay } from '../../domain/dates'
import {
  ageFromBirthYear, allowedCutPace, CUT_PACE, CUT_PACES, DAY_ACTIVITIES, GAIN_PACE, GAIN_PACES, MAX_SAFE_KCAL, MIN_FAT_SHARE_PCT, MIN_PROTEIN_PER_KG, MIN_SAFE_KCAL,
  NUTRITION_GOALS, weightTrendNote, type CutPace, type DayActivity, type GainPace, type NutritionGoal,
} from '../../domain/nutrition'
import { calorieInputs, floorFor, setTarget } from '../../data/calories'
import { bodyStep, fromKg, toKg, unitLabel } from '../../domain/units'
import { BADGES, BADGES_BY_ID } from '../../domain/badges'
import { EditIcon } from '../icons'
import { Button, Card, Chip, ChipGroup, LineChart, ListRow, Ring, SectionHeader, Segmented, Sheet, Stat, Stepper, TextField, toast } from '../kit'
import { PastePlanSheet } from '../coach/PastePlanSheet'
import {
  applyNutrition, carbsFor, hasBodyStats, impliedFatPct, kcalFor, proteinFor, summary, type NutritionPatch,
  trainingDaysFor,
} from './helpers'
import { BackupSection, backupSummary } from './BackupSection'
import { FriendsSection, friendsSummary } from './FriendsSection'
import { useFriends } from '../../data/friends'
import './profile.css'


type Accent = 'coral' | 'gold' | 'teal' | 'purple' | 'blue' | 'text'

const ICONS: Record<string, string> = {
  body: 'M12 2c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2zm9 7h-6v13h-2v-6h-2v6H9V9H3V7h18v2z',
  goals: 'M12 2a10 10 0 100 20 10 10 0 000-20zm0 18a8 8 0 110-16 8 8 0 010 16zm0-14a6 6 0 100 12 6 6 0 000-12zm0 10a4 4 0 110-8 4 4 0 010 8z',
  training: 'M20.57 14.86L22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 9.14 3.43 7.71 2 5.57 4.14 4.14 2.71 2.71 4.14l1.43 1.43L2 7.71l1.43 1.43L2 10.57 3.43 12 7 8.43 15.57 17 12 20.57 13.43 22l1.43-1.43L16.29 22l2.14-2.14 1.43 1.43 1.43-1.43-1.43-1.43L22 16.29z',
  coach: 'M12 2l2.4 7.6L22 12l-7.6 2.4L12 22l-2.4-7.6L2 12l7.6-2.4z',
  app: 'M12 3a9 9 0 000 18c1.1 0 2-.9 2-2 0-.5-.2-1-.5-1.3-.3-.3-.5-.8-.5-1.2 0-1.1.9-2 2-2h2.4c2.8 0 5-2.2 5-5 0-4.4-4.5-7.5-10.4-7.5z',
  backup: 'M19.35 10.04A7.49 7.49 0 0012 4C9.11 4 6.6 5.64 5.35 8.04A5.994 5.994 0 000 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z',
  about: 'M12 2a10 10 0 100 20 10 10 0 000-20zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z',
  friends: 'M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z',
  weight: 'M12 3a4 4 0 00-4 4h8a4 4 0 00-4-4zM5 9l-2 12h18L19 9z',
  chart: 'M3.5 18.5l6-6 4 4L22 6.92 20.59 5.5l-7.09 8-4-4L2 17z',
  checklist: 'M22 7h-9v2h9V7zm0 8h-9v2h9v-2zM5.54 11L2 7.46l1.41-1.41 2.12 2.12 4.24-4.24 1.41 1.41L5.54 11zm0 8L2 15.46l1.41-1.41 2.12 2.12 4.24-4.24 1.41 1.41L5.54 19z',
  tune: 'M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z',
  dumbbell: 'M20.57 14.86L22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 2.71 4.14 2 7.71z',
}
const Glyph = ({ name, size = 22 }: { name: string; size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d={ICONS[name]} /></svg>
)
const Chevron = () => (
  <svg class="pf-chev" width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z" /></svg>
)

const OPEN_KEY = 'fq.profile.open'
const LEGACY_GROUP_MAP: Record<string, string> = { body: 'you', goals: 'you', training: 'app' }
function loadOpen(): string[] {
  try {
    const v = JSON.parse(localStorage.getItem(OPEN_KEY) ?? '[]')
    if (!Array.isArray(v)) return []
    return [...new Set(v.filter((x): x is string => typeof x === 'string').map((x) => LEGACY_GROUP_MAP[x] ?? x))]
  } catch { return [] }
}
function saveOpen(ids: string[]) {
  try { localStorage.setItem(OPEN_KEY, JSON.stringify(ids)) } catch { /* remembered for this visit only */ }
}

const set = (patch: Partial<Settings>) => updateSettings((s) => ({ ...s, ...patch }))

function Group({ id, accent, icon, title, sum, open, onToggle, children }: {
  id: string; accent: Accent; icon: string; title: string; sum: string; open: boolean; onToggle: () => void; children: ComponentChildren
}) {
  return (
    <section class="pf-group" id={`pf-group-${id}`}>
      <button type="button" class="pf-ghead" aria-expanded={open} aria-controls={`pf-${id}`} onClick={onToggle}>
        <span class={`pf-ic pf-${accent}`}><Glyph name={icon} /></span>
        <span class="pf-gtext"><span class="pf-gtitle">{title}</span><span class="pf-gsum">{sum}</span></span>
        <span class={open ? 'pf-chevwrap pf-up' : 'pf-chevwrap'}><Chevron /></span>
      </button>
      {open && <div class="pf-gbody" id={`pf-${id}`}>{children}</div>}
    </section>
  )
}

/** A switch row; with `icon` it is a card header (icon + title + subtitle + switch), like Android's SwitchHeader. */
function Switch({ label, hint, on, onChange, disabled, icon, accent = 'teal' }: { label: string; hint?: string; on: boolean; onChange: (v: boolean) => void; disabled?: boolean; icon?: string; accent?: Accent }) {
  return (
    <button type="button" class="pf-switchrow" role="switch" aria-checked={on} disabled={disabled} onClick={() => onChange(!on)}>
      {icon && <span class={`pf-ic pf-${accent}`}><Glyph name={icon} /></span>}
      <span class="pf-grow"><span class={icon ? 'pf-h3 pf-block' : 'pf-t'}>{label}</span>{hint && <span class="pf-s">{hint}</span>}</span>
      <span class={on ? 'pf-switch pf-switch-on' : 'pf-switch'} aria-hidden="true"><span class="pf-knob" /></span>
    </button>
  )
}

function Setting({ label, children, hint }: { label: string; children: ComponentChildren; hint?: string }) {
  return (
    <div class="pf-setting">
      <span class="pf-grow"><span class="pf-t">{label}</span>{hint && <span class="pf-s">{hint}</span>}</span>
      {children}
    </div>
  )
}

function CardTitle({ icon, accent, title, hint }: { icon: string; accent: Accent; title: string; hint?: string }) {
  return (
    <div class="pf-cardhead">
      <span class={`pf-ic pf-${accent}`}><Glyph name={icon} /></span>
      <span class="pf-grow"><h3 class="pf-h3">{title}</h3>{hint && <span class="pf-s">{hint}</span>}</span>
    </div>
  )
}

function WeightSheet({ latest, s, open, onClose }: { latest: number | null; s: Settings; open: boolean; onClose: () => void }) {
  const unit = weightUnit(s)
  const ar = getLang() === 'ar'
  const [kg, setKg] = useState<number | null>(null)
  const cur = kg ?? latest ?? 75
  const close = () => { setKg(null); onClose() }
  return (
    <Sheet open={open} onClose={close} title={t('weight_log_title')}>
      <div class="pf-sheet">
        <Stepper label={t('weight_body')} value={Math.round(fromKg(unit, cur) * 10) / 10} step={bodyStep(unit)} min={fromKg(unit, 30)} max={fromKg(unit, 250)}
          onChange={(v) => setKg(toKg(unit, v))} format={(n) => `${n.toFixed(1)} ${unitLabel(unit, ar)}`} />
        <Button block onClick={async () => { await logWeight(cur); close() }}>{t('action_save')}</Button>
      </div>
    </Sheet>
  )
}

function KeyField({ title, value, status, onSave, onRemove, body, link }: {
  title: string; value: string; status: string; onSave: (k: string) => Promise<unknown>; onRemove: () => Promise<unknown>; body?: string; link?: string
}) {
  const [text, setText] = useState('')
  const [busy, setBusy] = useState(false)
  const save = async () => {
    setBusy(true)
    try { await onSave(text.trim()); setText(''); toast(t('profile_key_saved')) } finally { setBusy(false) }
  }
  return (
    <Card>
      <CardTitle icon="coach" accent="teal" title={title} hint={status} />
      {body && <p class="pf-s pf-block">{body}</p>}
      <TextField label={title} type="password" autocomplete="off" value={text} onInput={setText} placeholder={value ? '••••••••' : ''} />
      <div class="pf-actions">
        <Button loading={busy} disabled={text.trim() === ''} onClick={save}>{t('action_save')}</Button>
        {value && <Button variant="tonal" onClick={async () => { await onRemove(); toast(t('profile_key_removed')) }}>{t('action_remove')}</Button>}
        {link && <a class="pf-link" href={link} target="_blank" rel="noopener noreferrer">{t('key_dialog_open_studio')}</a>}
      </div>
    </Card>
  )
}

function TargetRow({ title, sub, grams, onClick }: { title: string; sub: string; grams: number; onClick: () => void }) {
  return (
    <button type="button" class="pf-target" onClick={onClick}>
      <span class="pf-grow">
        <span class="pf-t">{title}</span>
        <span class="pf-s">{sub}</span>
      </span>
      <span class="pf-target-val">{`${grams} ${t('unit_g')}`}</span>
      <span class="pf-target-edit" aria-hidden="true"><EditIcon size={18} /></span>
    </button>
  )
}

function TargetDialog({
  title, body, ratio, displayRatio, range, stepSize, formatRatio, gramsFor, grams: initGrams, gramsStep, gramsMin, gramsMax, onClose, onSave,
}: {
  title: string
  body: string
  ratio: number | null
  displayRatio?: number | null
  range: [number, number]
  stepSize: number
  formatRatio: (r: number) => string
  gramsFor: (r: number) => number
  grams: number
  gramsStep: number
  gramsMin: number
  gramsMax: number
  onClose: () => void
  onSave: (ratio: number | null, grams: number) => void
}) {
  const [r, setR] = useState(ratio ?? range[0])
  const [g, setG] = useState(initGrams)
  const [moved, setMoved] = useState(false)
  const shownRatio = moved || displayRatio == null ? r : displayRatio
  const ratioLabel = formatRatio(shownRatio)

  return (
    <Sheet open onClose={onClose} title={title}>
      <div class="pf-tdialog">
        <p class="pf-s" style={{ margin: 0 }}>{body}</p>
        {ratio != null && (
          <>
            <span class="pf-h3">{ratioLabel}</span>
            <input
              type="range"
              class="pf-slider"
              min={range[0]}
              max={range[1]}
              step={stepSize}
              value={r}
              aria-label={ratioLabel}
              aria-valuetext={ratioLabel}
              onInput={(e) => {
                const raw = Number((e.currentTarget as HTMLInputElement).value)
                const snapped = Math.min(
                  range[1],
                  Math.max(range[0], Math.round(((Math.round((raw - range[0]) / stepSize) * stepSize + range[0]) * 10)) / 10),
                )
                if (snapped !== r) {
                  setR(snapped)
                  setG(gramsFor(snapped))
                  setMoved(true)
                }
              }}
            />
          </>
        )}
        <span class="pf-h3">{t('you_protein_grams')}</span>
        <Stepper
          label={t('you_protein_grams')}
          value={g}
          step={gramsStep}
          min={gramsMin}
          max={gramsMax}
          format={(n) => `${Math.round(n)} ${t('unit_g')}`}
          onChange={(v) => setG(Math.round(v))}
        />
        <div class="pf-tdialog-actions">
          <Button variant="text" onClick={onClose}>{t('action_cancel')}</Button>
          <Button onClick={() => onSave(ratio != null && moved ? r : null, g)}>{t('action_save')}</Button>
        </div>
      </div>
    </Sheet>
  )
}

export function ProfileScreen({ param }: { param?: string }) {
  const settings = useSettings()
  const snap = useProgress()
  const friendsState = useFriends()
  const lang = useLang()
  const theme = useTheme()
  const [openIds, setOpenIds] = useState<string[]>(loadOpen)
  const [weightOpen, setWeightOpen] = useState(false)
  const [editingTarget, setEditingTarget] = useState<'PROTEIN' | 'CARBS' | null>(null)
  const [advOpen, setAdvOpen] = useState(false)
  const [nameDraft, setNameDraft] = useState<string | null>(null)
  const [pastePlanOpen, setPastePlanOpen] = useState(false)
  const [exUnitsOpen, setExUnitsOpen] = useState(false)
  const [exUnitsQuery, setExUnitsQuery] = useState('')
  const library = useLibrary()
  const baselineRows = useLatestBaselines()
  const toggle = (id: string) => {
    const next = openIds.includes(id) ? openIds.filter((x) => x !== id) : [...openIds, id]
    setOpenIds(next); saveOpen(next)
  }
  const openGroup = (id: string) => { if (!openIds.includes(id)) { const next = [...openIds, id]; setOpenIds(next); saveOpen(next) } }
  const isOpen = (id: string) => openIds.includes(id)
  useEffect(() => {
    if (param !== 'backup' && param !== 'coach') return
    openGroup(param)
    const frame = requestAnimationFrame(() => {
      document.getElementById(`pf-group-${param}`)?.scrollIntoView({ block: 'start' })
    })
    return () => cancelAnimationFrame(frame)
  }, [param])

  const s = settings
  const ar = getLang() === 'ar'
  const thisYear = new Date().getFullYear()
  const age = ageFromBirthYear(s.birthYear, thisYear)
  const planDays = snap?.plan ? trainingDays(snap.plan).size : 0
  const latestKg = snap?.latestWeight ?? null
  const gi = { settings: s, latestKg, age, planDays }
  const unit = weightUnit(s)
  const bodySet = s.heightCm > 0 && age > 0
  const num = (n: number) => n.toLocaleString(intlLocale(lang))
  const goalLabel = (g: NutritionGoal) => t(g === 'CUT' ? 'goal_cut' : g === 'GAIN' ? 'goal_gain' : 'goal_maintain')
  const themeLabel = (v: string) => t(v === 'light' ? 'theme_light' : v === 'dark' ? 'theme_dark' : 'theme_system')

  const changeTheme = (v: Theme) => { setTheme(v); void set({ theme: v }) }
  const suggestedKcal = kcalFor(gi, s.nutritionGoal)
  const ci = calorieInputs(s, latestKg, planDays, thisYear)
  const floor = ci ? floorFor(ci) : MIN_SAFE_KCAL
  const dayLabel = t(`day_web_${s.dayActivity.toLowerCase()}`)
  const nutrition = (patch: NutritionPatch) => void updateSettings((cur) => applyNutrition({ ...gi, settings: cur }, patch, todayEpochDay()))
  const fastBlocked = latestKg != null && allowedCutPace('AGGRESSIVE', latestKg, s.heightCm, age, s.bodyType === 'MALE', s.bodyFatPct) !== 'AGGRESSIVE'
  const paceAmount = (kg: number) => `${(Math.round(fromKg(unit, kg) * 10) / 10).toFixed(1)} ${unitLabel(unit, ar)}`
  const trend = weightTrendNote(s.nutritionGoal, s.goalSetDay, (snap?.weights ?? []).map((w) => [w.epochDay, w.kg] as const), todayEpochDay(), s.cutPace, s.gainPace, s.bodyType === 'FEMALE')
  const fat = Math.max(s.fatSharePct, MIN_FAT_SHARE_PCT)
  const proteinSub = latestKg != null && latestKg > 0 ? t('you_protein_ratio', (s.proteinGoal / latestKg).toFixed(1)) : t('you_protein_tap')
  const carbsSub = t('you_carbs_ratio', impliedFatPct(s) ?? fat)

  return (
    <div class="content pf">
      {snap && <RankCard snap={snap} name={s.name} justLog={s.justLog} />}

      {/* "Just log" mode: no badges entry (they keep unlocking and come back when it's turned off). */}
      {!s.justLog && <Card href="#/badges">
        <div class="pf-row">
          <span class="pf-grow">
            <span class="pf-h3 pf-block">{t('profile_badges')}</span>
            <span class="pf-s">{t('profile_badges_unlocked', snap?.badges.length ?? 0, BADGES.length)}</span>
          </span>
          <span class="pf-more">{t('action_see_all')}</span>
        </div>
        {snap && <BadgeRow snap={snap} />}
      </Card>}

      <Card>
        <div class="pf-row">
          <span class={`pf-ic pf-teal`}><Glyph name="weight" /></span>
          <span class="pf-grow">
            <h3 class="pf-h3">{t('weight_body')}</h3>
            <span class="pf-s">{latestKg != null ? `${fromKg(unit, latestKg).toFixed(1)} ${unitLabel(unit, ar)}` : t('profile_not_logged')}</span>
          </span>
          <Button variant="tonal" onClick={() => setWeightOpen(true)}>{t('action_log')}</Button>
        </div>
        {snap && snap.weights.length >= 2 && (
          <LineChart title={t('weight_body')} data={snap.weights.slice(-30).map((w) => ({ label: String(w.epochDay), value: fromKg(unit, w.kg) }))} height={120} />
        )}
      </Card>

      <div class="pf-shortcuts">
        <Card href="#/progress"><span class="pf-short"><span class="pf-ic pf-teal"><Glyph name="chart" /></span>{t('profile_progress_title')}</span></Card>
        <Card href="#/library"><span class="pf-short"><span class="pf-ic pf-teal"><Glyph name="training" /></span>{t('profile_exercise_library')}</span></Card>
      </div>

      <div id="pf-settings"><SectionHeader title={t('settings_section_settings')} /></div>

      <Group id="app" accent="teal" icon="app" title={t('settings_group_app')} open={isOpen('app')} onToggle={() => toggle('app')}
        sum={summary(themeLabel(theme), lang === 'ar' ? 'العربية' : 'English', unitLabel(unit, ar), s.justLog ? t('just_log_title') : null)}>
        <Card>
          <CardTitle icon="app" accent="teal" title={t('profile_appearance')} />
          <span class="pf-t">{t('profile_theme')}</span>
          <ChipGroup label={t('profile_theme')} accent="teal" value={theme} onChange={changeTheme}
            options={(['system', 'light', 'dark'] as Theme[]).map((v) => ({ value: v, label: themeLabel(v) }))} />
          <span class="pf-t">{t('profile_language')}</span>
          <ChipGroup label={t('profile_language')} accent="teal" value={lang} onChange={(v: Lang) => { setLang(v); void seedLibrary(v) }}
            options={[{ value: 'en', label: 'English' }, { value: 'ar', label: 'العربية' }]} />
          <span class="pf-t">{t('profile_weights')}</span>
          <ChipGroup label={t('profile_weights')} accent="teal" value={unit} onChange={(v) => void set({ unit: v === 'LB' ? 'lb' : 'kg' })}
            options={[{ value: 'KG', label: t('unit_kilograms') }, { value: 'LB', label: t('unit_pounds') }]} />
        </Card>
        <Card>
          <Switch icon="checklist" label={t('just_log_title')} hint={t('just_log_subtitle')} on={s.justLog} onChange={(v) => void set({ justLog: v })} />
        </Card>
        <Card>
          <Switch label={t('profile_vibration')} hint={t('profile_vibration_hint')} on={s.hapticsEnabled} onChange={(v) => void set({ hapticsEnabled: v })} />
        </Card>
        <Card>
          <Switch label={t('prog41_tts_title')} hint={t('prog41_tts_hint')} on={s.ttsCues} onChange={(v) => void set({ ttsCues: v })} />
        </Card>
        <Card>
          <CardTitle icon="training" accent="teal" title={t('prog41_bar_weight_title')} hint={t('prog41_bar_weight_hint')} />
          <div class="pf-actions">
            {[20, 15, 10].map((kg) => (
              <Chip
                key={kg}
                label={`${Math.round(fromKg(unit, kg))} ${unitLabel(unit, ar)}`}
                selected={Math.abs(s.barWeightKg - kg) < 0.1}
                onClick={() => void set({ barWeightKg: kg })}
              />
            ))}
          </div>
          <span class="pf-t" style={{ display: 'block', marginBlockStart: '12px' }}>{t('prog41_plates_title')}</span>
          <span class="pf-s" style={{ display: 'block', marginBlockEnd: '6px' }}>{t('prog41_plates_hint')}</span>
          <div class="pf-actions">
            {DEFAULT_PLATE_INVENTORY.map((plateKg) => {
              const on = s.plateInventory.some((p) => Math.abs(p - plateKg) < 0.01)
              return (
                <Chip
                  key={plateKg}
                  label={`${Number(fromKg(unit, plateKg).toFixed(2))} ${unitLabel(unit, ar)}`}
                  selected={on}
                  onClick={() => {
                    const next = on
                      ? s.plateInventory.filter((p) => Math.abs(p - plateKg) >= 0.01)
                      : [...s.plateInventory, plateKg].sort((a, b) => b - a)
                    void set({ plateInventory: next })
                  }}
                />
              )
            })}
          </div>
        </Card>
        <Card padded={false}>
          <ListRow
            title={t('prog41_ex_units_title')}
            subtitle={(() => {
              const cnt = library.filter((e) => !!e.unitOverride).length
              return cnt === 0
                ? t('prog41_ex_units_summary_none', unitLabel(unit, ar))
                : t('prog41_ex_units_summary_count', cnt)
            })()}
            icon={<span class="pf-ic pf-teal"><Glyph name="tune" /></span>}
            onClick={() => { setExUnitsQuery(''); setExUnitsOpen(true) }}
          />
        </Card>
        <Card padded={false}>
          <ListRow title={t('pb_rebuild')} subtitle={t('pb_rebuild_hint')} icon={<span class="pf-ic pf-teal"><Glyph name="tune" /></span>} href="#/plan-builder" />
        </Card>
      </Group>

      <Group id="you" accent="teal" icon="goals" title={t('settings_group_you')} open={isOpen('you')} onToggle={() => toggle('you')}
        sum={summary(!bodySet ? t('summary_body_not_set') : null, goalLabel(s.nutritionGoal), `${num(s.kcalGoal)} ${t('unit_kcal')}`, t('summary_protein', s.proteinGoal))}>
        <Card padded={false}>
          <ListRow
            title={t('baseline_entry_title')}
            subtitle={plural('baseline_tested_count', baselineRows.size, baselineRows.size)}
            icon={<span class="pf-ic pf-teal"><Glyph name="goals" /></span>}
            href="#/baseline"
          />
        </Card>
        <Card>
          <CardTitle icon="body" accent="teal" title={t('profile_about_you')} hint={t('profile_about_you_hint')} />
          <TextField label={t('onb_your_name')} value={nameDraft ?? s.name} onInput={(v) => { const next = v.slice(0, 24); setNameDraft(next); void set({ name: next.trim() }) }} />
          <ChipGroup label={t('profile_about_you')} accent="teal" value={s.bodyType} onChange={(v) => void set({ bodyType: v })}
            options={[{ value: 'MALE', label: t('body_male') }, { value: 'FEMALE', label: t('body_female') }]} />
          <Setting label={t('profile_height')}>
            <Stepper label={t('profile_height')} value={s.heightCm > 0 ? s.heightCm : 170} min={120} max={230} onChange={(v) => void set({ heightCm: v })} format={(n) => s.heightCm > 0 ? `${n} ${t('unit_cm')}` : `– ${t('unit_cm')}`} />
          </Setting>
          <Setting label={t('profile_age')}>
            <Stepper label={t('profile_age')} value={age > 0 ? age : 25} min={10} max={100} onChange={(v) => void set({ birthYear: thisYear - v })} format={(n) => age > 0 ? `${n} ${t('unit_years')}` : `– ${t('unit_years')}`} />
          </Setting>
          <span class="pf-t">{t('pf_web_day_title')}</span>
          <Segmented label={t('day_web_title')} accent="teal" value={s.dayActivity} onChange={(d: DayActivity) => nutrition({ dayActivity: d })}
            options={DAY_ACTIVITIES.map((d) => ({ value: d, label: t(`day_web_${d.toLowerCase()}`) }))} />
          <p class="pf-s">{t(`day_web_${s.dayActivity.toLowerCase()}_desc`)}</p>
          {!bodySet && <p class="pf-s pf-warn">{t('profile_body_not_set')}</p>}
        </Card>

        <Card>
          <CardTitle icon="goals" accent="teal" title={t('goal_title')} />
          <ChipGroup label={t('goal_title')} accent="teal" value={s.nutritionGoal} onChange={(g) => nutrition({ nutritionGoal: g })}
            options={NUTRITION_GOALS.map((g) => ({ value: g, label: goalLabel(g) }))} />
          {s.nutritionGoal !== 'MAINTAIN' && <span class="pf-t">{t('pace_web_title')}</span>}
          {s.nutritionGoal === 'CUT' && (
            <Segmented label={t('pace_web_title')} accent="teal" value={s.cutPace} onChange={(p: CutPace) => nutrition({ cutPace: p })}
              options={CUT_PACES.map((p) => ({
                value: p, label: t(`pace_web_${p === 'AGGRESSIVE' ? 'fast' : p.toLowerCase()}`),
                sub: latestKg != null ? t('pace_web_week', paceAmount((latestKg * CUT_PACE[p].weeklyPct) / 100)) : undefined,
                disabled: p === 'AGGRESSIVE' && fastBlocked,
              }))} />
          )}
          {s.nutritionGoal === 'CUT' && fastBlocked && <p class="pf-s">{t('pace_web_blocked')}</p>}
          {s.nutritionGoal === 'GAIN' && (
            <Segmented label={t('pace_web_title')} accent="teal" value={s.gainPace} onChange={(p: GainPace) => nutrition({ gainPace: p })}
              options={GAIN_PACES.map((p) => ({
                value: p, label: t(`pace_web_${p.toLowerCase()}`),
                sub: latestKg != null ? t('pace_web_month', paceAmount((latestKg * GAIN_PACE[p].weeklyPct * 4.35) / 100)) : undefined,
              }))} />
          )}
          {trend && <p class="pf-s pf-warn">{t(trend === 'CUT_TOO_FAST' ? 'goal_trend_cut_fast' : trend === 'GAIN_STALLED' ? 'goal_trend_gain_stalled' : trend === 'GAIN_TOO_FAST' ? 'goal_trend_gain_fast' : 'goal_trend_diet_break')}</p>}
        </Card>

        <Card>
          <CardTitle icon="goals" accent="teal" title={t('profile_daily_targets')} hint={t('you_targets_hint')} />
          <Setting label={t('profile_daily_calories')}>
            <Stepper label={t('profile_daily_calories')} value={s.kcalGoal} min={floor} max={MAX_SAFE_KCAL} step={50} onChange={(v) => void setTarget(v, latestKg, todayEpochDay())} format={(n) => `${num(n)} ${t('unit_kcal')}`} />
          </Setting>
          <div class="pf-setting">
            <span class="pf-s pf-grow">
              {!hasBodyStats(s, age) || suggestedKcal == null ? t('goals_suggest_missing') : t('pf_web_suggested', num(suggestedKcal), trainingDaysFor(s, planDays), dayLabel)}
            </span>
            {suggestedKcal != null && hasBodyStats(s, age) && suggestedKcal !== s.kcalGoal && <Button variant="tonal" onClick={() => void setTarget(suggestedKcal, latestKg, todayEpochDay())}>{t('goals_use')}</Button>}
          </div>
          {s.kcalGoal <= floor && <p class="pf-s">{t('pf_web_floor_hint', num(floor))}</p>}
          <hr class="pf-div" />
          <TargetRow title={t('profile_daily_protein')} sub={proteinSub} grams={s.proteinGoal} onClick={() => setEditingTarget('PROTEIN')} />
          <hr class="pf-div" />
          <Switch label={t('profile_track_carbs')} on={s.trackCarbs} onChange={(v) => void set({ trackCarbs: v })} />
          {s.trackCarbs && (
            <TargetRow title={t('profile_daily_carbs')} sub={carbsSub} grams={s.carbsGoal} onClick={() => setEditingTarget('CARBS')} />
          )}
        </Card>

        <Card>
          <button type="button" class="pf-adv" aria-expanded={advOpen} onClick={() => setAdvOpen(!advOpen)}>
            <span class="pf-grow">
              <span class="pf-h3 pf-block">{t('you_advanced')}</span>
              <span class="pf-s">{s.bodyFatPct > 0 ? t('you_advanced_bodyfat', s.bodyFatPct.toFixed(1)) : t('you_advanced_hint')}</span>
            </span>
            <span class={advOpen ? 'pf-chevwrap pf-up' : 'pf-chevwrap'}><Chevron /></span>
          </button>
          {advOpen && (
            <>
              <Setting label={t('you_body_fat')}>
                <Stepper label={t('you_body_fat')} value={s.bodyFatPct} min={0} max={55} step={0.5} onChange={(v) => void set({ bodyFatPct: v < 4 ? 0 : v })} format={(n) => `${n.toFixed(1)}%`} />
              </Setting>
              <p class="pf-s">{t('profile_bodyfat_hint')}</p>
            </>
          )}
        </Card>
      </Group>

      <Group id="friends" accent="blue" icon="friends" title={t('settings_group_friends')} open={isOpen('friends')} onToggle={() => toggle('friends')} sum={friendsSummary(friendsState)}>
        <FriendsSection Title={({ title, hint }) => <CardTitle icon="friends" accent="blue" title={title} hint={hint} />} />
      </Group>

      <Group id="backup" accent="teal" icon="backup" title={t('settings_group_backup')} open={isOpen('backup')} onToggle={() => toggle('backup')} sum={backupSummary(s)}>
        <BackupSection s={s} Title={({ title, hint }) => <CardTitle icon="backup" accent="teal" title={title} hint={hint} />} />
      </Group>

      <Group id="coach" accent="teal" icon="coach" title={t('settings_group_coach')} open={isOpen('coach')} onToggle={() => toggle('coach')}
        sum={isAiOn(s) ? t(s.apiKey ? 'summary_gemini_on' : 'summary_gemini_off') : t('summary_ai_off')}>
        <h3 class="pf-h3">{t('settings_ai_gemini_title')}</h3>
        <p class="pf-s">{t('settings_ai_gemini_body')}</p>
        <Card>
          <div class="pf-actions">
            <Button variant="tonal" onClick={() => { void navigator.clipboard?.writeText(PLAN_PROMPT); toast(t('plan_prompt_copied')) }}>{t('plan_gemini_copy')}</Button>
            <Button variant="tonal" onClick={() => setPastePlanOpen(true)}>{t('plan_gemini_paste')}</Button>
          </div>
        </Card>
        <Card>
          <CardTitle icon="coach" accent="teal" title={t('meals_title')} hint={t('meals_hint')} />
          <div class="pf-actions">
            <Button variant="tonal" onClick={() => { void navigator.clipboard?.writeText(foodSkillPrompt(lang)); toast(t('bridge_prompt_copied')) }}>{t('meals_copy_gem')}</Button>
          </div>
        </Card>
        <h3 class="pf-h3">{t('settings_ai_key_title')}</h3>
        <p class="pf-s">{t('settings_ai_key_body')}</p>
        <Card>
          <Switch icon="coach" accent="teal" label={t('ai_toggle_title')} hint={t('ai_toggle_body')} on={isAiOn(s)} onChange={(on) => void set({ aiOptIn: on })} />
        </Card>
        {isAiOn(s) && (
          <KeyField title={t('profile_ai_key')} value={s.apiKey} status={t(s.apiKey ? 'profile_key_connected' : 'profile_key_not_set')} body={`${t('profile_web_key_body')} ${t('key_dialog_privacy')}`}
            link="https://aistudio.google.com/apikey" onSave={(k) => set({ apiKey: k, aiOptIn: true })} onRemove={() => set({ apiKey: '' })} />
        )}
      </Group>

      <PastePlanSheet open={pastePlanOpen} onClose={() => setPastePlanOpen(false)} onParsed={(draft) => { void applyPlan(draft, 'gemini').then(() => { setPastePlanOpen(false); toast(t('coach_plan_applied')); location.hash = '#/workouts' }) }} />

      <Card padded={false}>
        <ListRow title={t('v42_guide_title')} subtitle={t('v42_guide_row_sub')} icon={<Glyph name="about" />} href="#/guide" />
      </Card>

      <Group id="about" accent="text" icon="about" title={t('settings_group_about')} open={isOpen('about')} onToggle={() => toggle('about')} sum={t('profile_credits')}>
        <Card padded={false}><ListRow title={t('settings_group_about')} subtitle={t('profile_credits_body')} icon={<Glyph name="about" />} href="#/about" /></Card>
      </Group>

      <WeightSheet latest={latestKg} s={s} open={weightOpen} onClose={() => setWeightOpen(false)} />
      <Sheet open={exUnitsOpen} onClose={() => setExUnitsOpen(false)} title={t('prog41_ex_units_title')}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <TextField label={t('prog41_csv_search_hint')} type="search" value={exUnitsQuery} onInput={setExUnitsQuery} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', maxBlockSize: '320px', overflowY: 'auto' }}>
            {(() => {
              const q = exUnitsQuery.trim().toLowerCase()
              const list = q
                ? library.filter((e) => e.name.toLowerCase().includes(q) || (e.aliases ?? '').toLowerCase().includes(q))
                : [...library].sort((a, b) => Number(!!b.unitOverride) - Number(!!a.unitOverride))
              return list.slice(0, 35).map((ex: ExerciseRow) => (
                <div key={ex.id} style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <span class="pf-t">{ex.name}</span>
                  <div class="pf-actions" style={{ marginBlockStart: 0 }}>
                    <Chip
                      label={`${t('prog41_ex_units_default')} (${unitLabel(unit, ar)})`}
                      selected={!ex.unitOverride}
                      onClick={() => void db.exercises.update(ex.id, { unitOverride: '' })}
                    />
                    <Chip label="kg" selected={ex.unitOverride?.toLowerCase() === 'kg'} onClick={() => void db.exercises.update(ex.id, { unitOverride: 'kg' })} />
                    <Chip label="lb" selected={ex.unitOverride?.toLowerCase() === 'lb'} onClick={() => void db.exercises.update(ex.id, { unitOverride: 'lb' })} />
                  </div>
                </div>
              ))
            })()}
          </div>
        </div>
      </Sheet>
      {editingTarget === 'PROTEIN' && (() => {
        const hasKg = latestKg != null && latestKg > 0
        const initRatio = hasKg ? Math.min(2.5, Math.max(MIN_PROTEIN_PER_KG, Math.round((s.proteinGoal / latestKg!) * 10) / 10)) : null
        return (
          <TargetDialog
            key="protein"
            title={t('profile_daily_protein')}
            body={t(hasKg ? 'you_protein_dialog_body' : 'you_protein_no_weight')}
            ratio={initRatio}
            range={[MIN_PROTEIN_PER_KG, 2.5]}
            stepSize={0.1}
            formatRatio={(r) => t('you_protein_slider_state', r.toFixed(1))}
            gramsFor={(r) => proteinFor(gi, r) ?? s.proteinGoal}
            grams={s.proteinGoal}
            gramsStep={5}
            gramsMin={60}
            gramsMax={400}
            onClose={() => setEditingTarget(null)}
            onSave={(r, g) => {
              void set({
                proteinGoal: Math.max(60, g),
                ...(r != null ? { proteinPerKg: Math.min(2.5, Math.max(MIN_PROTEIN_PER_KG, Math.round(r * 10) / 10)) } : {}),
              })
              setEditingTarget(null)
            }}
          />
        )
      })()}
      {editingTarget === 'CARBS' && (() => {
        const rawRatio = Math.min(45, Math.max(MIN_FAT_SHARE_PCT, impliedFatPct(s) ?? s.fatSharePct))
        const snapped = Math.min(45, Math.max(MIN_FAT_SHARE_PCT, Math.round(rawRatio / 5) * 5))
        return (
          <TargetDialog
            key="carbs"
            title={t('profile_daily_carbs')}
            body={t('you_carbs_dialog_body')}
            ratio={snapped}
            displayRatio={rawRatio}
            range={[MIN_FAT_SHARE_PCT, 45]}
            stepSize={5}
            formatRatio={(r) => t('you_carbs_ratio', Math.round(r))}
            gramsFor={(r) => carbsFor({ ...gi, settings: { ...s, fatSharePct: Math.round(r) } }, s.kcalGoal, s.proteinGoal)}
            grams={s.carbsGoal}
            gramsStep={10}
            gramsMin={20}
            gramsMax={800}
            onClose={() => setEditingTarget(null)}
            onSave={(r, g) => {
              void set({
                carbsGoal: Math.min(800, Math.max(20, g)),
                ...(r != null ? { fatSharePct: Math.min(45, Math.max(MIN_FAT_SHARE_PCT, Math.round(r))) } : {}),
              })
              setEditingTarget(null)
            }}
          />
        )
      })()}
    </div>
  )
}

/** "Just log" mode: the name and workout count only; XP keeps adding up out of sight. */
function RankCard({ snap, name, justLog }: { snap: ProgressSnapshot; name: string; justLog: boolean }) {
  const { level } = snap
  const ar = getLang() === 'ar'
  const mark = ar ? String(level.level) : level.rank.letter
  const rankKey = `rank_${level.rank.title.toLowerCase()}`
  const rankTitle = t(rankKey) === rankKey ? level.rank.title : t(rankKey)
  return (
    <Card>
      <div class="pf-row">
        {!justLog && (
          <Ring value={level.progress} color="--blue" track="--blue-track" size={64} stroke={4} label={mark}>
            <span class="pf-rank">{mark}</span>
          </Ring>
        )}
        <span class="pf-grow">
          <h2 class="pf-name">{name.trim() || t('profile_default_name')}</h2>
          {!justLog && <span class="pf-s">{t('profile_rank_line', level.rank.letter, rankTitle, level.level)}</span>}
        </span>
      </div>
      {!justLog && <>
        <progress class="pf-xp" max={1} value={level.progress} aria-label={t('profile_xp_line', level.xpIntoLevel, level.xpForNextLevel, level.level + 1)} />
        <span class="pf-s">{t('profile_xp_line', level.xpIntoLevel, level.xpForNextLevel, level.level + 1)}</span>
      </>}
      <div class="pf-stats">
        <Stat value={snap.sessions.length} label={t('tab_workouts')} accent="teal" />
        {!justLog && <Stat value={snap.streak.best} label={t('profile_best_streak')} accent="gold" />}
        {!justLog && <Stat value={`${snap.badges.length}/${BADGES.length}`} label={t('profile_badges')} accent="purple" />}
      </div>
    </Card>
  )
}

function BadgeRow({ snap }: { snap: ProgressSnapshot }) {
  const unseen = snap.badges.filter((b) => !b.seen).length
  const latest = [...snap.badges].sort((a, b) => b.unlockedAt - a.unlockedAt).slice(0, 4)
  return (
    <>
      {unseen > 0 && <p class="pf-new">{plural('home_new_badges', unseen)}</p>}
      {latest.length > 0 && (
        <div class="pf-badges">
          {latest.map((b) => {
            const def = BADGES_BY_ID.get(b.id)
            const key = `badge_${b.id}_title`
            const title = t(key) === key ? def?.title ?? b.id : t(key)
            return <span key={b.id} class={b.seen ? 'pf-badge' : 'pf-badge pf-badge-new'}>{title}</span>
          })}
        </div>
      )}
    </>
  )
}
