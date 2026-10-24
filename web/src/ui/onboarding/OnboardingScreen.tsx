import { useState } from 'preact/hooks'
import { getLang, setLang, t, type Lang } from '../../i18n'
import { updateSettings } from '../../data/settings'
import { logWeight } from '../../data/progress'
import { applyPlan, seedLibrary } from '../../data/workouts'
import { EXERCISE_CATALOG } from '../../data/seed/exercise-catalog'
import { STARTER_PLANS, localizePlan, type PlanDraft } from '../../data/seed/starter-plans'
import { todayEpochDay } from '../../domain/dates'
import { EQUIPMENT_PRESETS, PRESET_INFO, adaptPlanToEquipment, presetEquipment } from '../../domain/exercise-swap-engine'
import {
  allowedCutPace, CUT_PACE, CUT_PACES, DAY_ACTIVITIES, DAY_BASE, dailyBurn, GAIN_PACE, GAIN_PACES, NUTRITION_GOALS, type CutPace, type GainPace,
} from '../../domain/nutrition'
import { bodyStep, fromKg, toKg, unitLabel, type WeightUnit } from '../../domain/units'
import { Button, Card, ChipGroup, Segmented, Stat, Stepper, TextField, parseNumber } from '../kit'
import { CheckCircleIcon, OpenIcon, SparkleIcon } from '../home/glyphs'
import { LAST_STEP, bodyStatsReady, floorOf, initialState, setWeight, settingsPatch, withSuggestions, type OnbState } from './logic'
import './onboarding.css'

const GEMINI_MODELS = 'https://generativelanguage.googleapis.com/v1beta/models?pageSize=1'
type KeyCheck = { busy: boolean; ok: boolean; msg: string | null }

/** Straight from the browser, like the AI check screen. Only a working key is saved. */
async function checkKey(key: string): Promise<KeyCheck> {
  try {
    const r = await fetch(GEMINI_MODELS, { headers: { 'x-goog-api-key': key } })
    if (r.ok) return { busy: false, ok: true, msg: t('onb_key_works') }
    return { busy: false, ok: false, msg: r.status === 400 || r.status === 401 || r.status === 403 ? t('onb_web_key_rejected') : t('onb_key_check_failed') }
  } catch {
    return { busy: false, ok: false, msg: t('onb_key_check_failed') }
  }
}

const emptyPlan = (): PlanDraft => ({
  name: t('onb_my_plan'), description: t('onb_my_plan_desc'),
  days: [1, 2, 3, 4, 5, 6, 7].map((d) => ({ dayOfWeek: d, title: t('today_rest_day'), isRest: true, note: '', items: [] })),
})

const trainingDaysOf = (i: number) => STARTER_PLANS[i]?.days.filter((d) => !d.isRest && d.items.length > 0).length ?? 3

/** First-run setup: language, name, goals, starter plan, optional Gemini key. Full screen, no tab bar. */
export function OnboardingScreen({ onDone }: { onDone: () => void }) {
  const [s, setS] = useState<OnbState>(initialState)
  const [key, setKey] = useState('')
  const [check, setCheck] = useState<KeyCheck>({ busy: false, ok: false, msg: null })
  const [saving, setSaving] = useState(false)
  const update = (f: (c: OnbState) => OnbState) => setS(f)
  const go = (d: number) => update((c) => ({ ...c, step: Math.min(LAST_STEP, Math.max(0, c.step + d)) }))

  const finish = async () => {
    if (saving) return
    setSaving(true)
    try {
      await seedLibrary(getLang())
      const chosen = STARTER_PLANS[s.planIndex]
      const plan = chosen ? localizePlan(chosen, getLang()) : emptyPlan()
      const adapted = chosen ? adaptPlanToEquipment(plan, new Map(EXERCISE_CATALOG.map((e) => [e.id, e])), presetEquipment(s.preset))[0] : plan
      await applyPlan(adapted, chosen ? 'starter' : 'manual')
      await logWeight(s.weightKg)
      await updateSettings(settingsPatch(s, todayEpochDay(), new Date().getFullYear(), check.ok ? key.trim() : null))
      onDone()
    } catch (e) {
      console.error('onboarding failed', e)
      setSaving(false)
    }
  }

  const runCheck = async () => {
    setCheck({ busy: true, ok: false, msg: null })
    setCheck(await checkKey(key.trim()))
  }

  return (
    <main class="onb">
      <div class="onb-top">
        <div class="onb-bar" role="progressbar" aria-valuemin={1} aria-valuemax={LAST_STEP + 1} aria-valuenow={s.step + 1} aria-label={t('onb_web_step', s.step + 1, LAST_STEP + 1)}>
          <div class="onb-bar-fill" style={{ inlineSize: `${((s.step + 1) / (LAST_STEP + 1)) * 100}%` }} />
        </div>
      </div>
      <div class="onb-body" key={s.step}>
        {s.step === 0 && <StepLanguage onNext={() => go(1)} />}
        {s.step === 1 && <StepWelcome s={s} update={update} />}
        {s.step === 2 && <StepBody s={s} update={update} />}
        {s.step === 3 && <StepPlan s={s} update={update} />}
        {s.step === 4 && <StepGoal s={s} update={update} />}
        {s.step === 5 && (
          <StepKey value={key} onInput={(v) => { setKey(v.trim()); setCheck({ busy: false, ok: false, msg: null }) }} check={check} onCheck={runCheck} />
        )}
      </div>
      {s.step > 0 && (
        <div class="onb-foot">
          <Button variant="text" onClick={() => go(-1)}>{t('action_back')}</Button>
          <span class="onb-spacer" />
          {s.step < LAST_STEP
            ? <Button onClick={() => go(1)} disabled={(s.step === 1 && !s.name.trim()) || (s.step === 2 && !bodyStatsReady(s))}>{t('action_continue')}</Button>
            : <Button onClick={finish} loading={saving}>{check.ok ? t('onb_lets_go') : t('onb_skip')}</Button>}
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

type StepProps = { s: OnbState; update: (f: (c: OnbState) => OnbState) => void }

function StepWelcome({ s, update }: StepProps) {
  return (
    <>
      <Title title={t('onb_welcome_title')} body={t('onb_welcome_body')} />
      <TextField label={t('onb_your_name')} value={s.name} autocomplete="given-name" onInput={(v) => update((c) => ({ ...c, name: v.slice(0, 24) }))} />
      <h2 class="onb-h2">{t('onb_web_body_type')}</h2>
      <ChipGroup label={t('onb_web_body_type')} value={s.bodyType} onChange={(v) => update((c) => ({ ...c, bodyType: v }))}
        options={[{ value: 'MALE', label: t('body_male') }, { value: 'FEMALE', label: t('body_female') }]} />
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

/** Step 2: units, weight, height and age, one calm row each. */
function StepBody({ s, update }: StepProps) {
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
          format={(n) => `${n.toFixed(1)} ${unitLabel(unit, ar)}`} onChange={(v) => update((c) => setWeight(c, toKg(unit, v), trainingDaysOf))} />
      </Row>
      <RowNumber label={t('profile_height')} unit={t('unit_cm')} value={s.heightCm} min={120} max={230}
        onChange={(v) => update((c) => withSuggestions({ ...c, heightCm: v }, trainingDaysOf))} />
      <RowNumber label={t('profile_age')} unit={t('unit_years')} value={s.age} min={10} max={100}
        onChange={(v) => update((c) => withSuggestions({ ...c, age: v }, trainingDaysOf))} />
      <h2 class="onb-h2">{t('day_web_title')}</h2>
      <RadioCards label={t('day_web_title')} value={s.dayActivity} onPick={(v) => update((c) => withSuggestions({ ...c, dayActivity: v }, trainingDaysOf))}
        options={DAY_ACTIVITIES.map((d) => ({ v: d, name: t(`day_web_${d.toLowerCase()}`), desc: t(`day_web_${d.toLowerCase()}_desc`) }))} />
    </>
  )
}

/** Step 4 (after the plan, so the target counts its training days): the goal and the calculated target. */
function StepGoal({ s, update }: StepProps) {
  return (
    <>
      <Title title={t('goal_title')} body={t('onb_web_goal_body')} />
      <RadioCards label={t('goal_title')} value={s.goal} onPick={(g) => update((c) => withSuggestions({ ...c, goal: g }, trainingDaysOf))}
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
        ? <Segmented label={t('pace_web_title')} value={s.cutPace} onChange={(p: CutPace) => update((c) => withSuggestions({ ...c, cutPace: p }, trainingDaysOf))}
            options={CUT_PACES.map((p) => ({
              value: p, label: t(`pace_web_${p === 'AGGRESSIVE' ? 'fast' : p.toLowerCase()}`),
              sub: t('pace_web_week', amount((s.weightKg * CUT_PACE[p].weeklyPct) / 100)), disabled: p === 'AGGRESSIVE' && fastBlocked,
            }))} />
        : <Segmented label={t('pace_web_title')} value={s.gainPace} onChange={(p: GainPace) => update((c) => withSuggestions({ ...c, gainPace: p }, trainingDaysOf))}
            options={GAIN_PACES.map((p) => ({ value: p, label: t(`pace_web_${p.toLowerCase()}`), sub: t('pace_web_month', amount((s.weightKg * GAIN_PACE[p].weeklyPct * 4.35) / 100)) }))} />}
      {s.goal === 'CUT' && fastBlocked && <p class="onb-note">{t('pace_web_blocked')}</p>}
    </>
  )
}

/** The calculated calories and protein, read-only until the person taps Adjust. */
function Target({ s, update }: StepProps) {
  const [editing, setEditing] = useState(false)
  if (!bodyStatsReady(s)) return <p class="onb-note">{t('onb_web_target_missing')}</p>
  const num = (n: number) => new Intl.NumberFormat(getLang()).format(n)
  const burn = Math.round(dailyBurn(s.weightKg, s.heightCm, s.age, s.bodyType === 'MALE', trainingDaysOf(s.planIndex), 0, DAY_BASE[s.dayActivity]) / 10) * 10
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
      <p class="onb-note">{s.goalsTouched ? t('onb_web_target_custom') : t('target_web_from', trainingDaysOf(s.planIndex), t(`day_web_${s.dayActivity.toLowerCase()}`))}</p>
    </Card>
  )
}

function StepPlan({ s, update }: StepProps) {
  const ar = getLang() === 'ar'
  const plans = STARTER_PLANS.map((p) => localizePlan(p, getLang()))
  const options = [...plans.map((p) => ({ name: p.name, desc: p.description })), { name: t('onb_start_empty'), desc: t('onb_start_empty_desc') }]
  return (
    <>
      <Title title={t('onb_plan_title')} body={t('onb_plan_body')} />
      <h2 class="onb-h2">{t('onb_available_equipment')}</h2>
      <ChipGroup label={t('onb_available_equipment')} value={s.preset} onChange={(p) => update((c) => ({ ...c, preset: p }))}
        options={EQUIPMENT_PRESETS.map((p) => ({ value: p, label: ar ? PRESET_INFO[p].labelAr : PRESET_INFO[p].labelEn }))} />
      <div class="onb-plans" role="radiogroup" aria-label={t('onb_plan_title')}>
        {options.map((o, i) => {
          const on = s.planIndex === i
          return (
            <button key={i} type="button" role="radio" aria-checked={on} class={`onb-plan${on ? ' on' : ''}`}
              onClick={() => update((c) => withSuggestions({ ...c, planIndex: i, preset: i === 2 ? 'FULL_GYM' : c.preset }, trainingDaysOf))}>
              <span class="onb-plan-text">
                <span class="onb-plan-name">{o.name}</span>
                <span class="onb-plan-desc">{o.desc}</span>
              </span>
              <span class={`onb-radio${on ? ' on' : ''}`} aria-label={on ? t('onb_web_plan_selected') : undefined}>{on && <CheckCircleIcon />}</span>
            </button>
          )
        })}
      </div>
    </>
  )
}

function StepKey({ value, onInput, check, onCheck }: { value: string; onInput: (v: string) => void; check: KeyCheck; onCheck: () => void }) {
  return (
    <>
      <div class="onb-optional">
        <span class="onb-badge"><SparkleIcon /></span>
        <span>{t('onb_optional')}</span>
      </div>
      <Title title={t('onb_ai_title')} body={t('onb_ai_body')} />
      <ol class="onb-steps">
        {['onb_ai_step1', 'onb_ai_step2', 'onb_ai_step3'].map((k, i) => (
          <li key={k}><span class="onb-num" aria-hidden="true">{new Intl.NumberFormat(getLang()).format(i + 1)}</span><span>{t(k)}</span></li>
        ))}
      </ol>
      <a class="kit-btn kit-btn-tonal onb-getkey" href="https://aistudio.google.com/apikey" target="_blank" rel="noopener noreferrer">
        <OpenIcon size={18} /><span class="kit-btn-label">{t('onb_get_key')}</span>
      </a>
      <TextField label={t('onb_key_label')} type="password" value={value} autocomplete="off" onInput={onInput} />
      <div class="onb-check">
        <Button onClick={onCheck} loading={check.busy} disabled={!value}>{check.busy ? t('onb_checking') : t('onb_check_key')}</Button>
        {check.msg && <span class={`onb-msg ${check.ok ? 'ok' : 'bad'}`} role="status">{check.msg}</span>}
      </div>
      <p class="onb-note">{t('onb_privacy')}</p>
      <p class="onb-note">{t('onb_groq_hint')}</p>
    </>
  )
}
