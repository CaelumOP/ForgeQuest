// Port of app/.../ui/profile/ProfileScreen.kt: rank, badges, body weight and the seven folding settings
// sections. Web leaves out Health Connect/Fitbit, reminders, app lock, the tour, Gemini bridge, APK updates
// and the Drive Docs import. Backup (Drive + file) lives in BackupSection.tsx.
import type { ComponentChildren } from 'preact'
import { useState } from 'preact/hooks'
import { getLang, plural, setLang, t, useLang, type Lang, intlLocale } from '../../i18n'
import { setTheme, useTheme, type Theme } from '../theme'
import { updateSettings, useSettings, weightUnit, type Settings } from '../../data/settings'
import { getProgressSnapshot, logWeight, useProgress, type ProgressSnapshot } from '../../data/progress'
import { trainingDays } from '../../data/workouts'
import { todayEpochDay } from '../../domain/dates'
import {
  ageFromBirthYear, allowedCutPace, CUT_PACE, CUT_PACES, DAY_ACTIVITIES, GAIN_PACE, GAIN_PACES, MAX_SAFE_KCAL, MIN_PROTEIN_PER_KG, MIN_SAFE_KCAL,
  NUTRITION_GOALS, weightTrendNote, type CutPace, type DayActivity, type GainPace, type NutritionGoal,
} from '../../domain/nutrition'
import { calorieInputs, floorFor, setTarget } from '../../data/calories'
import {
  EQUIPMENT, EQUIPMENT_LABEL, EQUIPMENT_PRESETS, PRESET_INFO, encodePreset, presetFromEncoded, parseEquipmentSet,
  type Equipment, type EquipmentPreset,
} from '../../domain/exercise-swap-engine'
import { bodyStep, fromKg, toKg, unitLabel } from '../../domain/units'
import { BADGES, BADGES_BY_ID } from '../../domain/badges'
import { Button, Card, ChipGroup, LineChart, ListRow, NumberField, Ring, SectionHeader, Segmented, Sheet, Stat, Stepper, TextField, toast } from '../kit'
import {
  addNote, applyNutrition, carbsFor, hasBodyStats, kcalFor, noteLines, proteinFor, removeNote, stepWeekly, summary, type NutritionPatch,
  toggleEquipment, trainingDaysFor,
} from './helpers'
import { BackupSection, backupSummary } from './BackupSection'
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
  weight: 'M12 3a4 4 0 00-4 4h8a4 4 0 00-4-4zM5 9l-2 12h18L19 9z',
  chart: 'M3.5 18.5l6-6 4 4L22 6.92 20.59 5.5l-7.09 8-4-4L2 17z',
  dumbbell: 'M20.57 14.86L22 13.43 20.57 12 17 15.57 8.43 7 12 3.43 10.57 2 2.71 4.14 2 7.71z',
}
const Glyph = ({ name, size = 22 }: { name: string; size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d={ICONS[name]} /></svg>
)
const Chevron = () => (
  <svg class="pf-chev" width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7.41 8.59L12 13.17l4.59-4.58L18 10l-6 6-6-6z" /></svg>
)

const OPEN_KEY = 'fq.profile.open'
function loadOpen(): string[] {
  try {
    const v = JSON.parse(localStorage.getItem(OPEN_KEY) ?? '[]')
    return Array.isArray(v) ? v.filter((x): x is string => typeof x === 'string') : []
  } catch { return [] }
}
function saveOpen(ids: string[]) {
  try { localStorage.setItem(OPEN_KEY, JSON.stringify(ids)) } catch { /* remembered for this visit only */ }
}

const set = (patch: Partial<Settings>) => updateSettings((s) => ({ ...s, ...patch }))

/** Finished weeks keep the target they had: the snapshot locks them (saveWeekTargets) before the change lands. */
const lockThenSet = async (patch: Partial<Settings>) => { await getProgressSnapshot(); await set(patch) }

function Group({ id, accent, icon, title, sum, open, onToggle, children }: {
  id: string; accent: Accent; icon: string; title: string; sum: string; open: boolean; onToggle: () => void; children: ComponentChildren
}) {
  return (
    <section class="pf-group">
      <button type="button" class="pf-ghead" aria-expanded={open} aria-controls={`pf-${id}`} onClick={onToggle}>
        <span class={`pf-ic pf-${accent}`}><Glyph name={icon} /></span>
        <span class="pf-gtext"><span class="pf-gtitle">{title}</span><span class="pf-gsum">{sum}</span></span>
        <span class={open ? 'pf-chevwrap pf-up' : 'pf-chevwrap'}><Chevron /></span>
      </button>
      {open && <div class="pf-gbody" id={`pf-${id}`}>{children}</div>}
    </section>
  )
}

function Switch({ label, hint, on, onChange, disabled }: { label: string; hint?: string; on: boolean; onChange: (v: boolean) => void; disabled?: boolean }) {
  return (
    <button type="button" class="pf-switchrow" role="switch" aria-checked={on} disabled={disabled} onClick={() => onChange(!on)}>
      <span class="pf-grow"><span class="pf-t">{label}</span>{hint && <span class="pf-s">{hint}</span>}</span>
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
      <CardTitle icon="coach" accent="blue" title={title} hint={status} />
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

export function ProfileScreen() {
  const settings = useSettings()
  const snap = useProgress()
  const lang = useLang()
  const theme = useTheme()
  const [openIds, setOpenIds] = useState<string[]>(loadOpen)
  const [weightOpen, setWeightOpen] = useState(false)
  const [note, setNote] = useState('')
  const toggle = (id: string) => {
    const next = openIds.includes(id) ? openIds.filter((x) => x !== id) : [...openIds, id]
    setOpenIds(next); saveOpen(next)
  }
  const openGroup = (id: string) => { if (!openIds.includes(id)) { const next = [...openIds, id]; setOpenIds(next); saveOpen(next) } }
  const isOpen = (id: string) => openIds.includes(id)

  const s = settings
  const ar = getLang() === 'ar'
  const thisYear = new Date().getFullYear()
  const age = ageFromBirthYear(s.birthYear, thisYear)
  const planDays = snap?.plan ? trainingDays(snap.plan).size : 0
  const latestKg = snap?.latestWeight ?? null
  const gi = { settings: s, latestKg, age, planDays }
  const unit = weightUnit(s)
  const configured = parseEquipmentSet(s.equipment)
  const preset = presetFromEncoded(s.equipment)
  const bodySet = s.heightCm > 0 && age > 0
  const weekly = s.weeklyTargetOverride === 0 ? planDays : s.weeklyTargetOverride
  const num = (n: number) => n.toLocaleString(intlLocale(lang))
  const goalLabel = (g: NutritionGoal) => t(g === 'CUT' ? 'goal_cut' : g === 'GAIN' ? 'goal_gain' : 'goal_maintain')
  const presetLabel = (p: EquipmentPreset) => (ar ? PRESET_INFO[p].labelAr : PRESET_INFO[p].labelEn)
  const eqLabel = (e: Equipment) => EQUIPMENT_LABEL[e][ar ? 'ar' : 'en']
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
  const suggestedProt = latestKg == null ? null : proteinFor(gi, Math.max(s.proteinPerKg, MIN_PROTEIN_PER_KG))
  const fat = Math.max(s.fatSharePct, 20)
  const suggestedCarb = carbsFor(gi, s.kcalGoal, s.proteinGoal)
  const notes = noteLines(s.coachNotes)

  return (
    <div class="content pf">
      {s.apiKey === '' && (
        <Card>
          <CardTitle icon="coach" accent="blue" title={t('profile_setup_title')} hint={t('profile_setup_body')} />
          <div class="pf-actions"><Button onClick={() => { openGroup('coach'); document.getElementById('pf-coach')?.scrollIntoView({ block: 'center' }) }}>{t('profile_add_key')}</Button></div>
        </Card>
      )}

      {snap && <RankCard snap={snap} name={s.name} />}

      <Card href="#/badges">
        <div class="pf-row">
          <span class="pf-grow">
            <span class="pf-h3 pf-block">{t('profile_badges')}</span>
            <span class="pf-s">{t('profile_badges_unlocked', snap?.badges.length ?? 0, BADGES.length)}</span>
          </span>
          <span class="pf-more">{t('action_see_all')}</span>
        </div>
        {snap && <BadgeRow snap={snap} />}
      </Card>

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
        <Card href="#/library"><span class="pf-short"><span class="pf-ic pf-blue"><Glyph name="training" /></span>{t('profile_exercise_library')}</span></Card>
      </div>

      <SectionHeader title={t('settings_section_settings')} />

      <Group id="body" accent="coral" icon="body" title={t('settings_group_body')} open={isOpen('body')} onToggle={() => toggle('body')}
        sum={bodySet ? summary(t(s.bodyType === 'FEMALE' ? 'body_female' : 'body_male'), `${s.heightCm} ${t('unit_cm')}`, `${age} ${t('unit_years')}`, dayLabel) : t('summary_body_not_set')}>
        <Card>
          <CardTitle icon="body" accent="coral" title={t('profile_about_you')} hint={t('profile_about_you_hint')} />
          <ChipGroup label={t('profile_about_you')} accent="coral" value={s.bodyType} onChange={(v) => void set({ bodyType: v })}
            options={[{ value: 'MALE', label: t('body_male') }, { value: 'FEMALE', label: t('body_female') }]} />
          <Setting label={t('profile_height')}>
            <Stepper label={t('profile_height')} value={s.heightCm > 0 ? s.heightCm : 170} min={120} max={230} onChange={(v) => void set({ heightCm: v })} format={(n) => s.heightCm > 0 ? `${n} ${t('unit_cm')}` : `– ${t('unit_cm')}`} />
          </Setting>
          <Setting label={t('profile_age')}>
            <Stepper label={t('profile_age')} value={age > 0 ? age : 25} min={10} max={100} onChange={(v) => void set({ birthYear: thisYear - v })} format={(n) => age > 0 ? `${n} ${t('unit_years')}` : `– ${t('unit_years')}`} />
          </Setting>
          <Setting label={t('profile_body_fat_optional')}>
            <Stepper label={t('profile_body_fat_optional')} value={s.bodyFatPct} min={0} max={55} step={0.5} onChange={(v) => void set({ bodyFatPct: v < 4 ? 0 : v })} format={(n) => `${n.toFixed(1)}%`} />
          </Setting>
          <p class="pf-s">{t('pf_web_bodyfat_hint')}</p>
          <span class="pf-t">{t('pf_web_day_title')}</span>
          <Segmented label={t('day_web_title')} accent="coral" value={s.dayActivity} onChange={(d: DayActivity) => nutrition({ dayActivity: d })}
            options={DAY_ACTIVITIES.map((d) => ({ value: d, label: t(`day_web_${d.toLowerCase()}`) }))} />
          <p class="pf-s">{t(`day_web_${s.dayActivity.toLowerCase()}_desc`)}</p>
          {!bodySet && <p class="pf-s pf-warn">{t('profile_body_not_set')}</p>}
        </Card>
        <Card>
          <CardTitle icon="body" accent="purple" title={t('profile_hand_size')} hint={s.handLengthCm > 0 ? t('profile_hand_value', s.handLengthCm.toFixed(1), s.palmWidthCm.toFixed(1)) : t('profile_hand_hint')} />
          <div class="pf-two">
            <NumberField label={t('hand_length')} unit={t('unit_cm')} decimal min={5} max={40} value={s.handLengthCm > 0 ? s.handLengthCm : null} onChange={(v) => void set({ handLengthCm: v ?? 0 })} />
            <NumberField label={t('hand_palm')} unit={t('unit_cm')} decimal min={3} max={20} value={s.palmWidthCm > 0 ? s.palmWidthCm : null} onChange={(v) => void set({ palmWidthCm: v ?? 0 })} />
          </div>
        </Card>
      </Group>

      <Group id="goals" accent="gold" icon="goals" title={t('settings_group_goals')} open={isOpen('goals')} onToggle={() => toggle('goals')}
        sum={summary(goalLabel(s.nutritionGoal), `${num(s.kcalGoal)} ${t('unit_kcal')}`, `${s.proteinGoal} ${t('unit_g')}`, t('summary_per_week', weekly))}>
        <Card>
          <CardTitle icon="goals" accent="gold" title={t('profile_daily_targets')} />
          <span class="pf-t">{t('goal_title')}</span>
          <ChipGroup label={t('goal_title')} accent="gold" value={s.nutritionGoal} onChange={(g) => nutrition({ nutritionGoal: g })}
            options={NUTRITION_GOALS.map((g) => ({ value: g, label: goalLabel(g) }))} />
          {s.nutritionGoal !== 'MAINTAIN' && <span class="pf-t">{t('pace_web_title')}</span>}
          {s.nutritionGoal === 'CUT' && (
            <Segmented label={t('pace_web_title')} accent="gold" value={s.cutPace} onChange={(p: CutPace) => nutrition({ cutPace: p })}
              options={CUT_PACES.map((p) => ({
                value: p, label: t(`pace_web_${p === 'AGGRESSIVE' ? 'fast' : p.toLowerCase()}`),
                sub: latestKg != null ? t('pace_web_week', paceAmount((latestKg * CUT_PACE[p].weeklyPct) / 100)) : undefined,
                disabled: p === 'AGGRESSIVE' && fastBlocked,
              }))} />
          )}
          {s.nutritionGoal === 'CUT' && fastBlocked && <p class="pf-s">{t('pace_web_blocked')}</p>}
          {s.nutritionGoal === 'GAIN' && (
            <Segmented label={t('pace_web_title')} accent="gold" value={s.gainPace} onChange={(p: GainPace) => nutrition({ gainPace: p })}
              options={GAIN_PACES.map((p) => ({
                value: p, label: t(`pace_web_${p.toLowerCase()}`),
                sub: latestKg != null ? t('pace_web_month', paceAmount((latestKg * GAIN_PACE[p].weeklyPct * 4.35) / 100)) : undefined,
              }))} />
          )}
          {trend && <p class="pf-s pf-warn">{t(trend === 'CUT_TOO_FAST' ? 'goal_trend_cut_fast' : trend === 'GAIN_STALLED' ? 'goal_trend_gain_stalled' : trend === 'GAIN_TOO_FAST' ? 'goal_trend_gain_fast' : 'goal_trend_diet_break')}</p>}
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
          <Setting label={t('profile_daily_protein')}>
            <Stepper label={t('profile_daily_protein')} value={s.proteinGoal} min={60} max={400} step={5} onChange={(v) => void set({ proteinGoal: v })} format={(n) => `${n} ${t('unit_g')}`} />
          </Setting>
          <Setting label={t('profile_protein_per_kg')}>
            <Stepper label={t('profile_protein_per_kg')} value={Math.max(s.proteinPerKg, MIN_PROTEIN_PER_KG)} min={MIN_PROTEIN_PER_KG} max={2.5} step={0.1} onChange={(v) => void set({ proteinPerKg: v })} format={(n) => `${n.toFixed(1)} ${t('unit_g_per_kg')}`} />
          </Setting>
          {suggestedProt != null && suggestedProt !== s.proteinGoal && (
            <div class="pf-setting">
              <span class="pf-s pf-grow">{t('goals_protein_suggested', suggestedProt, Math.max(s.proteinPerKg, MIN_PROTEIN_PER_KG).toFixed(1))}</span>
              <Button variant="tonal" onClick={() => void set({ proteinGoal: suggestedProt })}>{t('goals_use')}</Button>
            </div>
          )}
          <Switch label={t('profile_track_carbs')} on={s.trackCarbs} onChange={(v) => void set({ trackCarbs: v })} />
          {s.trackCarbs && (
            <>
              <Setting label={t('profile_daily_carbs')}>
                <Stepper label={t('profile_daily_carbs')} value={s.carbsGoal} min={20} max={800} step={10} onChange={(v) => void set({ carbsGoal: v })} format={(n) => `${n} ${t('unit_g')}`} />
              </Setting>
              <Setting label={t('profile_fat_share')}>
                <Stepper label={t('profile_fat_share')} value={fat} min={20} max={40} step={5} onChange={(v) => void set({ fatSharePct: v })} format={(n) => `${n}%`} />
              </Setting>
              {suggestedCarb > 0 && suggestedCarb !== s.carbsGoal && (
                <div class="pf-setting">
                  <span class="pf-s pf-grow">{t('profile_carbs_suggested', suggestedCarb, fat)}</span>
                  <Button variant="tonal" onClick={() => void set({ carbsGoal: suggestedCarb })}>{t('goals_use')}</Button>
                </div>
              )}
            </>
          )}
          <Setting label={t('profile_workouts_per_week')} hint={s.weeklyTargetOverride === 0 ? t('profile_from_plan') : undefined}>
            <Stepper label={t('profile_workouts_per_week')} value={weekly} min={1} max={7}
              onChange={(v) => void lockThenSet({ weeklyTargetOverride: stepWeekly(weekly, v > weekly ? 1 : -1, planDays) })} />
          </Setting>
          <p class="pf-s">{t('profile_streak_explain')}</p>
        </Card>
      </Group>

      <Group id="training" accent="teal" icon="training" title={t('settings_group_training')} open={isOpen('training')} onToggle={() => toggle('training')}
        sum={preset ? presetLabel(preset) : configured.size === 0 ? t('summary_equipment_unset') : t('equipment_custom')}>
        <Card>
          <CardTitle icon="training" accent="teal" title={t('equipment_title')} hint={t('equipment_hint')} />
          <span class="pf-t">{t('equipment_step_setup')}</span>
          <ChipGroup label={t('equipment_step_setup')} value={preset} onChange={(p) => void set({ equipment: encodePreset(p) })}
            options={EQUIPMENT_PRESETS.map((p) => ({ value: p, label: presetLabel(p) }))} />
          <span class="pf-t">{t('equipment_step_items')}</span>
          <span class="pf-s">{preset == null && configured.size > 0 ? t('equipment_custom_hint') : t('equipment_step_items_hint')}</span>
          <ChipGroup multiple label={t('equipment_step_items')} value={EQUIPMENT.filter((e) => configured.has(e))}
            onChange={(next) => { const added = EQUIPMENT.find((e) => next.includes(e) && !configured.has(e)); const removed = EQUIPMENT.find((e) => !next.includes(e) && configured.has(e)); const item = added ?? removed; if (item) void set({ equipment: toggleEquipment(configured, item) }) }}
            options={EQUIPMENT.map((e) => ({ value: e, label: eqLabel(e) }))} />
        </Card>
        <Card>
          <Switch label={t('profile_vibration')} hint={t('profile_vibration_hint')} on={s.hapticsEnabled} onChange={(v) => void set({ hapticsEnabled: v })} />
        </Card>
      </Group>

      <Group id="coach" accent="purple" icon="coach" title={t('settings_group_coach')} open={isOpen('coach')} onToggle={() => toggle('coach')}
        sum={summary(t(s.apiKey ? 'summary_gemini_on' : 'summary_gemini_off'), t(s.groqKey ? 'summary_groq_on' : 'summary_groq_off'))}>
        <KeyField title={t('profile_ai_key')} value={s.apiKey} status={t(s.apiKey ? 'profile_key_connected' : 'profile_key_not_set')} body={`${t('profile_web_key_body')} ${t('key_dialog_privacy')}`}
          link="https://aistudio.google.com/apikey" onSave={(k) => set({ apiKey: k })} onRemove={() => set({ apiKey: '' })} />
        <KeyField title={t('groq_key_title')} value={s.groqKey} status={t(s.groqKey ? 'groq_key_on' : 'groq_key_off')} onSave={(k) => set({ groqKey: k })} onRemove={() => set({ groqKey: '' })} />
        <Card>
          <span class="pf-h3">{t('coach_model_header')}</span>
          <ChipGroup label={t('coach_model_header')} accent="purple" value={s.coachModel} onChange={(v) => void set({ coachModel: v })}
            options={[
              { value: 'auto', label: t('coach_model_auto') }, { value: 'lite', label: t('coach_model_lite') },
              { value: 'flash', label: t('coach_model_flash') }, { value: 'pro', label: t('coach_model_pro') },
            ]} />
          <span class="pf-s">{t(`coach_model_${['lite', 'flash', 'pro'].includes(s.coachModel) ? s.coachModel : 'auto'}_hint`)}</span>
        </Card>
        <Card>
          <CardTitle icon="coach" accent="purple" title={t('profile_coach_memory')} hint={notes.length === 0 ? t('profile_memory_empty') : t('profile_memory_hint')} />
          {notes.map((n) => (
            <button key={n} type="button" class="pf-note" onClick={() => void set({ coachNotes: removeNote(s.coachNotes, n) })} aria-label={`${t('cd_remove')}: ${n}`}>
              <span class="pf-bullet" aria-hidden="true">•</span><span class="pf-grow">{n}</span><span class="pf-x" aria-hidden="true">×</span>
            </button>
          ))}
          <TextField label={t('notes_dialog_title')} multiline value={note} onInput={setNote} hint={t('notes_dialog_body')} />
          <div class="pf-actions">
            <Button variant="tonal" disabled={note.trim() === ''} onClick={() => { void set({ coachNotes: addNote(s.coachNotes, note) }); setNote('') }}>{t('action_add')}</Button>
          </div>
        </Card>
      </Group>

      <Group id="app" accent="blue" icon="app" title={t('settings_group_app')} open={isOpen('app')} onToggle={() => toggle('app')}
        sum={summary(themeLabel(theme), lang === 'ar' ? 'العربية' : 'English', unitLabel(unit, ar))}>
        <Card>
          <CardTitle icon="app" accent="blue" title={t('profile_appearance')} />
          <span class="pf-t">{t('profile_theme')}</span>
          <ChipGroup label={t('profile_theme')} accent="blue" value={theme} onChange={changeTheme}
            options={(['system', 'light', 'dark'] as Theme[]).map((v) => ({ value: v, label: themeLabel(v) }))} />
          <span class="pf-t">{t('profile_language')}</span>
          <ChipGroup label={t('profile_language')} accent="blue" value={lang} onChange={(v: Lang) => setLang(v)}
            options={[{ value: 'en', label: 'English' }, { value: 'ar', label: 'العربية' }]} />
          <span class="pf-t">{t('profile_weights')}</span>
          <ChipGroup label={t('profile_weights')} accent="blue" value={unit} onChange={(v) => void set({ unit: v === 'LB' ? 'lb' : 'kg' })}
            options={[{ value: 'KG', label: t('unit_kilograms') }, { value: 'LB', label: t('unit_pounds') }]} />
        </Card>
      </Group>

      <Group id="backup" accent="teal" icon="backup" title={t('settings_group_backup')} open={isOpen('backup')} onToggle={() => toggle('backup')} sum={backupSummary(s)}>
        <BackupSection s={s} Title={({ title, hint }) => <CardTitle icon="backup" accent="teal" title={title} hint={hint} />} />
      </Group>

      <Group id="about" accent="text" icon="about" title={t('settings_group_about')} open={isOpen('about')} onToggle={() => toggle('about')} sum={t('profile_credits')}>
        <Card padded={false}><ListRow title={t('settings_group_about')} subtitle={t('profile_credits_body')} icon={<Glyph name="about" />} href="#/about" /></Card>
      </Group>

      <WeightSheet latest={latestKg} s={s} open={weightOpen} onClose={() => setWeightOpen(false)} />
    </div>
  )
}

function RankCard({ snap, name }: { snap: ProgressSnapshot; name: string }) {
  const { level } = snap
  return (
    <Card>
      <div class="pf-row">
        <Ring value={level.progress} color="--blue" track="--blue-track" size={64} stroke={4} label={level.rank.letter}>
          <span class="pf-rank">{level.rank.letter}</span>
        </Ring>
        <span class="pf-grow">
          <h2 class="pf-name">{name.trim() || t('profile_default_name')}</h2>
          <span class="pf-s">{t('profile_rank_line', level.rank.letter, level.rank.title, level.level)}</span>
        </span>
      </div>
      <progress class="pf-xp" max={1} value={level.progress} aria-label={t('profile_xp_line', level.xpIntoLevel, level.xpForNextLevel, level.level + 1)} />
      <span class="pf-s">{t('profile_xp_line', level.xpIntoLevel, level.xpForNextLevel, level.level + 1)}</span>
      <div class="pf-stats">
        <Stat value={snap.sessions.length} label={t('tab_workouts')} accent="teal" />
        <Stat value={snap.streak.best} label={t('profile_best_streak')} accent="gold" />
        <Stat value={`${snap.badges.length}/${BADGES.length}`} label={t('profile_badges')} accent="purple" />
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
