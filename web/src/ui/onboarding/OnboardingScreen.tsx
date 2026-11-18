import { useMemo, useState } from 'preact/hooks'
import { getLang, intlLocale, listSep, setLang, t, type Lang } from '../../i18n'
import { logWeight } from '../../data/progress'
import { updateSettings } from '../../data/settings'
import { applyPlan, seedLibrary } from '../../data/workouts'
import { db } from '../../data/db'
import { EXERCISE_CATALOG } from '../../data/seed/exercise-catalog'
import { EQUIPMENT_PRESETS, PRESET_INFO } from '../../domain/exercise-swap-engine'
import { type Experience } from '../../domain/plan-builder'
import {
  allowedCutPace, CUT_PACE, CUT_PACES, DAY_ACTIVITIES, DAY_BASE, dailyBurn, GAIN_PACE, GAIN_PACES, NUTRITION_GOALS, type CutPace, type GainPace, type NutritionGoal,
} from '../../domain/nutrition'
import { bodyStep, fromKg, toKg, unitLabel, type WeightUnit } from '../../domain/units'
import { RestaurantIcon, TabIcon } from '../icons'
import { Button, Card, ChipGroup, Segmented, Sheet, Stat, Stepper, TextField, parseNumber } from '../kit'
import { CsvImportScreen } from '../profile/CsvImportScreen'
import { CheckCircleIcon, DumbbellIcon, FireIcon } from '../home/glyphs'
import { markGettingStartedEligible } from '../home/getting-started'
import {
  DAY_OPTIONS, LAST_STEP, bodyStatsReady, floorOf, initialState, localizeOnboardingPlan, nextStep,
  onboardingPlanChoice, pickGoal, prevStep, progressStep, setWeight, settingsPatch, withSuggestions, type OnbState,
} from './logic'
import './onboarding.css'

/** Tour-only first run (no setup screens): no starting plan, 3 days a week target, everything editable later in Profile. */
export async function quickStart(): Promise<void> {
  await seedLibrary(getLang())
  markGettingStartedEligible()
  await updateSettings((c) => ({ ...c, onboarded: true, weeklyTargetOverride: 3, calorieV2Seen: true }))
}

/** First-run setup: language, welcome, about you, goal, training, weight. Full screen, no tab bar. */
export function OnboardingScreen({ onDone }: { onDone: () => void }) {
  const [s, setS] = useState<OnbState>(initialState)
  const [saving, setSaving] = useState(false)
  const [csvOpen, setCsvOpen] = useState(false)
  const update = (f: (c: OnbState) => OnbState) => setS(f)
  const goNext = () => update((c) => ({ ...c, step: nextStep(c) }))
  const goBack = () => update((c) => ({ ...c, step: prevStep(c) }))

  const skipSetup = async () => {
    if (saving) return
    setSaving(true)
    try {
      await quickStart()
      onDone()
    } catch (e) {
      console.error('skip setup failed', e)
      setSaving(false)
    }
  }

  const finish = async (logEnteredWeight = true) => {
    if (saving) return
    setSaving(true)
    try {
      await seedLibrary(getLang())
      if (!s.justLog) {
        const lib = s.builtForMe ? await db.exercises.toArray() : []
        const { plan, source } = onboardingPlanChoice(s, lib)
        await applyPlan(plan, source)
      }
      if (logEnteredWeight && s.weightTouched) {
        await logWeight(s.weightKg)
      }
      markGettingStartedEligible()
      await updateSettings(settingsPatch(s))
      onDone()
    } catch (e) {
      console.error('onboarding failed', e)
      setSaving(false)
    }
  }

  const prog = progressStep(s)

  return (
    <main class="onb">
      {s.step > 0 && (
        <div class="onb-top">
          <div class="onb-bar" role="progressbar" aria-valuemin={1} aria-valuemax={prog.total} aria-valuenow={prog.current} aria-label={t('v42_onb_step', prog.current, prog.total)}>
            <div class="onb-bar-fill" style={{ inlineSize: `${(prog.current / prog.total) * 100}%` }} />
          </div>
        </div>
      )}
      <div class="onb-body" key={s.step}>
        {s.step === 0 && <StepLanguage onNext={() => update((c) => ({ ...c, step: 1 }))} />}
        {s.step === 1 && <StepWelcome onStart={goNext} onSkip={skipSetup} onImportCsv={() => setCsvOpen(true)} saving={saving} />}
        {s.step === 2 && <StepAbout s={s} update={update} onEnter={goNext} />}
        {s.step === 3 && <StepGoalSetup s={s} update={update} />}
        {s.step === 4 && <StepTraining s={s} update={update} />}
        {s.step === 5 && <StepWeight s={s} update={update} />}
      </div>
      <Sheet open={csvOpen} onClose={() => setCsvOpen(false)} title={t('prog41_csv_import_title')}>
        <CsvImportScreen onDone={() => setCsvOpen(false)} />
      </Sheet>
      {s.step > 0 && (
        <div class="onb-foot">
          <Button variant="text" onClick={goBack} disabled={saving}>{t('action_back')}</Button>
          <span class="onb-spacer" />
          {s.step >= 2 && s.step < LAST_STEP && (
            <Button onClick={goNext}>{t('action_continue')}</Button>
          )}
          {s.step === LAST_STEP && (
            <>
              <Button variant="text" onClick={() => void finish(false)} disabled={saving}>{t('v42_onb_skip_step')}</Button>
              <Button onClick={() => void finish(true)} loading={saving}>{t('onb_finish')}</Button>
            </>
          )}
        </div>
      )}
    </main>
  )
}

function Title({ title, body }: { title: string; body: string }) {
  return (
    <header class="onb-title">
      <h1>{title}</h1>
      <p>{body}</p>
    </header>
  )
}

/** Both languages side by side: the person may not read the phone's language yet. */
function StepLanguage({ onNext }: { onNext: () => void }) {
  const pick = (l: Lang) => { setLang(l); onNext() }
  return (
    <div class="onb-center">
      <h1 class="onb-lang-title" lang="en" dir="ltr">Choose your language</h1>
      <h1 class="onb-lang-title" lang="ar" dir="rtl">اختر لغتك</h1>
      <div class="onb-lang-btns">
        <Button block onClick={() => pick('en')}>English</Button>
        <Button block onClick={() => pick('ar')}>العربية</Button>
      </div>
    </div>
  )
}

export type StepProps = { s: OnbState; update: (f: (c: OnbState) => OnbState) => void }

function StepWelcome({ onStart, onSkip, onImportCsv, saving }: { onStart: () => void; onSkip: () => void; onImportCsv: () => void; saving: boolean }) {
  const values = [
    { key: 'v42_onb_value_train', icon: <DumbbellIcon size={22} /> },
    { key: 'v42_onb_value_food', icon: <RestaurantIcon size={22} /> },
    { key: 'v42_onb_value_progress', icon: <TabIcon name="chart" filled={false} /> },
  ]
  return (
    <>
      <Title title={t('v42_onb_welcome_title')} body={t('v42_onb_welcome_body')} />
      <ul class="onb-values">
        {values.map((v) => (
          <li key={v.key} class="onb-value">
            <span class="onb-value-ic" aria-hidden="true">{v.icon}</span>
            <span class="onb-value-text">{t(v.key)}</span>
          </li>
        ))}
      </ul>
      <div class="onb-welcome-actions">
        <Button block onClick={onStart} disabled={saving}>{t('v42_onb_get_started')}</Button>
        <Button block variant="tonal" onClick={onSkip} loading={saving}>{t('v42_onb_skip_setup')}</Button>
      </div>
      <div class="onb-csv">
        <p class="onb-note">{t('prog41_onb_import_csv')}</p>
        <Button variant="text" onClick={onImportCsv}>{t('prog41_csv_import_title')}</Button>
      </div>
    </>
  )
}

function StepAbout({ s, update, onEnter }: StepProps & { onEnter: () => void }) {
  return (
    <>
      <Title title={t('v42_onb_about_title')} body={t('v42_onb_about_body')} />
      <div onKeyDown={(e) => { if (e.key === 'Enter' && (e.target as HTMLElement)?.tagName === 'INPUT') { e.preventDefault(); onEnter() } }}>
        <TextField label={t('v42_onb_name_optional')} value={s.name} autocomplete="given-name" onInput={(v) => update((c) => ({ ...c, name: v.slice(0, 24) }))} />
      </div>
      <h2 class="onb-h2">{t('onb_web_body_type')}</h2>
      <ChipGroup label={t('onb_web_body_type')} value={s.bodyType} onChange={(v) => update((c) => ({ ...c, bodyType: v }))}
        options={[{ value: 'MALE', label: t('body_male') }, { value: 'FEMALE', label: t('body_female') }]} />
      <h2 class="onb-h2">{t('v42_onb_units')}</h2>
      <ChipGroup label={t('v42_onb_units')} value={s.unit} onChange={(v: WeightUnit) => update((c) => ({ ...c, unit: v }))}
        options={[{ value: 'KG', label: t('unit_kilograms') }, { value: 'LB', label: t('unit_pounds') }]} />
    </>
  )
}

type GoalChoice = NutritionGoal | 'JUST_LOG'

function StepGoalSetup({ s, update }: StepProps) {
  const goalChoice: GoalChoice = s.justLog ? 'JUST_LOG' : s.goal
  const choose = (v: GoalChoice) => update((c) => pickGoal(c, v))
  return (
    <>
      <Title title={t('v42_onb_goal_title')} body={t('v42_onb_goal_body')} />
      <RadioCards<GoalChoice>
        label={t('v42_onb_goal_title')}
        value={goalChoice}
        onPick={choose}
        options={[
          { v: 'GAIN', name: t('v42_onb_goal_muscle'), desc: t('v42_onb_goal_muscle_desc') },
          { v: 'CUT', name: t('v42_onb_goal_fat'), desc: t('v42_onb_goal_fat_desc') },
          { v: 'MAINTAIN', name: t('v42_onb_goal_fit'), desc: t('v42_onb_goal_fit_desc') },
          { v: 'JUST_LOG', name: t('v42_onb_goal_log'), desc: t('v42_onb_goal_log_desc') },
        ]}
      />
      <h2 class="onb-h2">{t('v42_onb_experience')}</h2>
      <ChipGroup
        label={t('v42_onb_experience')}
        value={s.experience}
        onChange={(experience: Experience) => update((c) => ({ ...c, experience }))}
        options={[
          { value: 'NEW', label: t('v42_onb_exp_new') },
          { value: 'SOME', label: t('v42_onb_exp_some') },
          { value: 'SEASONED', label: t('v42_onb_exp_pro') },
        ]}
      />
    </>
  )
}

function StepTraining({ s, update }: StepProps) {
  const lang = getLang()
  const ar = lang === 'ar'
  const builtPreview = useMemo(
    () => localizeOnboardingPlan(s, EXERCISE_CATALOG),
    [s.goal, s.daysPerWeek, s.preset, s.experience, lang],
  )
  const previewDays = builtPreview.days.map((d) => d.title).join(listSep(lang))

  return (
    <>
      <Title title={t('v42_onb_train_title')} body={t('v42_onb_train_body')} />
      <div class="onb-days-head">
        <span class="onb-badge"><FireIcon /></span>
        <span class="onb-days-text">
          <span class="onb-days-title">{t('v42_onb_days_week')}</span>
          <span class="onb-note">{t('onb_days_hint')}</span>
        </span>
      </div>
      <Segmented
        label={t('v42_onb_days_week')}
        value={String(s.daysPerWeek)}
        onChange={(v) => update((c) => ({ ...c, daysPerWeek: Number(v) }))}
        options={DAY_OPTIONS.map((d) => ({ value: String(d), label: new Intl.NumberFormat(intlLocale()).format(d) }))}
      />
      <h2 class="onb-h2">{t('v42_onb_where_train')}</h2>
      <ChipGroup
        label={t('v42_onb_where_train')}
        value={s.preset}
        onChange={(preset) => update((c) => ({ ...c, preset }))}
        options={EQUIPMENT_PRESETS.map((p) => ({ value: p, label: ar ? PRESET_INFO[p].labelAr : PRESET_INFO[p].labelEn }))}
      />
      <p class="onb-note">{t('onb_equipment_hint')}</p>
      <div class="onb-plans" role="radiogroup" aria-label={t('v42_onb_train_title')}>
        <button
          type="button"
          role="radio"
          aria-checked={s.builtForMe}
          class={`onb-plan${s.builtForMe ? ' on' : ''}`}
          onClick={() => update((c) => ({ ...c, builtForMe: true }))}
        >
          <span class="onb-plan-text">
            <span class="onb-plan-name">{t('onb_built_title')}</span>
            <span class="onb-plan-days">{builtPreview.name}</span>
            <span class="onb-plan-desc">{t('onb_built_body', s.daysPerWeek)}</span>
            {previewDays && <span class="onb-preview">{previewDays}</span>}
          </span>
          <span class={`onb-radio${s.builtForMe ? ' on' : ''}`} aria-label={s.builtForMe ? t('onb_web_plan_selected') : undefined}>
            {s.builtForMe && <CheckCircleIcon />}
          </span>
        </button>
        <button
          type="button"
          role="radio"
          aria-checked={!s.builtForMe}
          class={`onb-plan${!s.builtForMe ? ' on' : ''}`}
          onClick={() => update((c) => ({ ...c, builtForMe: false }))}
        >
          <span class="onb-plan-text">
            <span class="onb-plan-name">{t('onb_start_empty')}</span>
            <span class="onb-plan-desc">{t('onb_start_empty_desc')}</span>
          </span>
          <span class={`onb-radio${!s.builtForMe ? ' on' : ''}`} aria-label={!s.builtForMe ? t('onb_web_plan_selected') : undefined}>
            {!s.builtForMe && <CheckCircleIcon />}
          </span>
        </button>
      </div>
    </>
  )
}

function StepWeight({ s, update }: StepProps) {
  const ar = getLang() === 'ar'
  const unit = s.unit
  const shownWeight = Math.round(fromKg(unit, s.weightKg) * 10) / 10
  return (
    <>
      <Title title={t('v42_onb_weight_title')} body={t('v42_onb_weight_body')} />
      <Row label={t('weight_body')}>
        <Stepper
          label={t('weight_body')}
          value={shownWeight}
          step={bodyStep(unit)}
          min={fromKg(unit, 30)}
          max={fromKg(unit, 250)}
          format={(n) => (s.weightTouched ? `${n.toFixed(1)} ${unitLabel(unit, ar)}` : '–')}
          onChange={(v) => update((c) => setWeight({ ...c, weightTouched: true }, toKg(unit, v)))}
        />
      </Row>
    </>
  )
}

/** Big radio cards with a title and a one-line description (goal, normal day). */
function RadioCards<T extends string>({ label, value, options, onPick }: { label: string; value: T; options: { v: T; name: string; desc: string }[]; onPick: (v: T) => void }) {
  return (
    <div class="onb-plans" role="radiogroup" aria-label={label}>
      {options.map((o) => {
        const on = value === o.v
        return (
          <button key={o.v} type="button" role="radio" aria-checked={on} class={`onb-plan${on ? ' on' : ''}`} onClick={() => onPick(o.v)}>
            <span class="onb-plan-text">
              <span class="onb-plan-name">{o.name}</span>
              <span class="onb-plan-desc">{o.desc}</span>
            </span>
            <span class={`onb-radio${on ? ' on' : ''}`}>{on && <CheckCircleIcon />}</span>
          </button>
        )
      })}
    </div>
  )
}

function Row({ label, children }: { label: string; children: preact.ComponentChildren }) {
  return <div class="onb-row"><span class="onb-row-label">{label}</span>{children}</div>
}

/** A whole number typed straight into a row (height, age). Reports 0 while empty or out of range. */
function RowNumber({ label, unit, value, min, max, onChange }: { label: string; unit: string; value: number; min: number; max: number; onChange: (v: number) => void }) {
  const [text, setText] = useState(value > 0 ? String(value) : '')
  return (
    <label class="onb-row">
      <span class="onb-row-label">{label}</span>
      <span class="onb-num-wrap">
        <input class="onb-num-input" inputMode="numeric" enterKeyHint="done" value={text} placeholder="–" aria-label={`${label} (${unit})`}
          onInput={(e) => {
            const v = (e.target as HTMLInputElement).value
            setText(v)
            const n = parseNumber(v)
            onChange(Number.isInteger(n) && n >= min && n <= max ? n : 0)
          }} />
        <span class="onb-num-unit">{unit}</span>
      </span>
    </label>
  )
}

/** Food-targets Step 1: units, weight, height and age, one calm row each. */
export function StepBody({ s, update }: StepProps) {
  const ar = getLang() === 'ar'
  const unit = s.unit
  const shownWeight = Math.round(fromKg(unit, s.weightKg) * 10) / 10
  return (
    <>
      <Title title={t('onb_web_body_title')} body={t('onb_web_body_body')} />
      <ChipGroup label={t('profile_weights')} value={unit} onChange={(u: WeightUnit) => update((c) => ({ ...c, unit: u }))}
        options={[{ value: 'KG', label: t('unit_kilograms') }, { value: 'LB', label: t('unit_pounds') }]} />
      <Row label={t('weight_body')}>
        <Stepper label={t('weight_body')} value={shownWeight} step={bodyStep(unit)} min={fromKg(unit, 30)} max={fromKg(unit, 250)}
          format={(n) => `${n.toFixed(1)} ${unitLabel(unit, ar)}`} onChange={(v) => update((c) => setWeight(c, toKg(unit, v)))} />
      </Row>
      <RowNumber label={t('profile_height')} unit={t('unit_cm')} value={s.heightCm} min={120} max={230}
        onChange={(v) => update((c) => withSuggestions({ ...c, heightCm: v }))} />
      <RowNumber label={t('profile_age')} unit={t('unit_years')} value={s.age} min={10} max={100}
        onChange={(v) => update((c) => withSuggestions({ ...c, age: v }))} />
      <h2 class="onb-h2">{t('day_web_title')}</h2>
      <RadioCards label={t('day_web_title')} value={s.dayActivity} onPick={(v) => update((c) => withSuggestions({ ...c, dayActivity: v }))}
        options={DAY_ACTIVITIES.map((d) => ({ v: d, name: t(`day_web_${d.toLowerCase()}`), desc: t(`day_web_${d.toLowerCase()}_desc`) }))} />
    </>
  )
}

/** Food-targets Step 2 (after the plan, so the target counts its training days): the goal and the calculated target. */
export function StepGoal({ s, update }: StepProps) {
  return (
    <>
      <Title title={t('goal_title')} body={t('onb_web_goal_body')} />
      <RadioCards label={t('goal_title')} value={s.goal} onPick={(g) => update((c) => withSuggestions({ ...c, goal: g }))}
        options={NUTRITION_GOALS.map((g) => ({ v: g, name: t(`goal_${g.toLowerCase()}`), desc: t(`onb_web_goal_${g.toLowerCase()}_desc`) }))} />
      <PaceControl s={s} update={update} />
      <Target s={s} update={update} />
    </>
  )
}

/** Weekly pace under Lose fat (Gentle / Standard / Fast) or Build muscle (Slow / Standard), with the weight it moves. */
function PaceControl({ s, update }: StepProps) {
  if (s.goal === 'MAINTAIN') return null
  const unit = s.unit
  const amount = (kg: number) => `${(Math.round(fromKg(unit, kg) * 10) / 10).toFixed(1)} ${unitLabel(unit, getLang() === 'ar')}`
  const fastBlocked = allowedCutPace('AGGRESSIVE', s.weightKg, s.heightCm, s.age, s.bodyType === 'MALE') !== 'AGGRESSIVE'
  return (
    <>
      <h2 class="onb-h2">{t('pace_web_title')}</h2>
      {s.goal === 'CUT'
        ? <Segmented label={t('pace_web_title')} value={s.cutPace} onChange={(p: CutPace) => update((c) => withSuggestions({ ...c, cutPace: p }))}
            options={CUT_PACES.map((p) => ({
              value: p, label: t(`pace_web_${p === 'AGGRESSIVE' ? 'fast' : p.toLowerCase()}`),
              sub: t('pace_web_week', amount((s.weightKg * CUT_PACE[p].weeklyPct) / 100)), disabled: p === 'AGGRESSIVE' && fastBlocked,
            }))} />
        : <Segmented label={t('pace_web_title')} value={s.gainPace} onChange={(p: GainPace) => update((c) => withSuggestions({ ...c, gainPace: p }))}
            options={GAIN_PACES.map((p) => ({ value: p, label: t(`pace_web_${p.toLowerCase()}`), sub: t('pace_web_month', amount((s.weightKg * GAIN_PACE[p].weeklyPct * 4.35) / 100)) }))} />}
      {s.goal === 'CUT' && fastBlocked && <p class="onb-note">{t('pace_web_blocked')}</p>}
    </>
  )
}

/** The calculated calories and protein, read-only until the person taps Adjust. */
function Target({ s, update }: StepProps) {
  const [editing, setEditing] = useState(false)
  if (!bodyStatsReady(s)) return <p class="onb-note">{t('onb_web_target_missing')}</p>
  const num = (n: number) => new Intl.NumberFormat(intlLocale()).format(n)
  const burn = Math.round(dailyBurn(s.weightKg, s.heightCm, s.age, s.bodyType === 'MALE', s.daysPerWeek, 0, DAY_BASE[s.dayActivity]) / 10) * 10
  // The difference keeps its sign next to the number in Arabic too (left-to-right isolate).
  const diff = `⁦${s.kcal >= burn ? '+' : '−'}${num(Math.abs(s.kcal - burn))}⁩`
  return (
    <Card>
      <div class="onb-target-head">
        <h2 class="onb-h2">{t('onb_web_target_title')}</h2>
        {!editing && <Button variant="text" onClick={() => setEditing(true)}>{t('onb_web_adjust')}</Button>}
      </div>
      <div class="onb-target-stats">
        <Stat value={`${num(s.kcal)} ${t('unit_kcal')}`} label={t('profile_daily_calories')} accent="blue" />
        <Stat value={`${num(s.protein)} ${t('unit_g')}`} label={t('profile_daily_protein')} accent="teal" />
      </div>
      {editing && (
        <>
          <Row label={t('profile_daily_calories')}>
            <Stepper label={t('profile_daily_calories')} value={s.kcal} step={50} min={floorOf(s)} max={6000} format={(n) => `${num(n)}`}
              onChange={(v) => update((c) => ({ ...c, kcal: v, goalsTouched: true }))} />
          </Row>
          <Row label={t('profile_daily_protein')}>
            <Stepper label={t('profile_daily_protein')} value={s.protein} step={5} min={60} max={400} format={(n) => `${n} ${t('unit_g')}`}
              onChange={(v) => update((c) => ({ ...c, protein: v, goalsTouched: true }))} />
          </Row>
        </>
      )}
      <p class="onb-note">{s.goal === 'MAINTAIN' ? t('target_web_burn_only', num(burn)) : t('target_web_burn', num(burn), num(s.kcal), diff)}</p>
      <p class="onb-note">{s.goalsTouched ? t('onb_web_target_custom') : t('target_web_from', s.daysPerWeek, t(`day_web_${s.dayActivity.toLowerCase()}`))}</p>
    </Card>
  )
}
